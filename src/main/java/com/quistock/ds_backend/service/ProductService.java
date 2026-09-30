package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.model.dto.ErpBatchDTO;
import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.repository.ProductRepository;
import com.quistock.ds_backend.util.ErpValueParser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ProductService {
  private static final int MAX_VALUES_WITHOUT_DIVERGENCE = 1;

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductService.class);
  private static final ParameterizedTypeReference<List<ErpBatchDTO>> BATCHES_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient erpRestClient;
  private final String productsPath;
  private final Clock clock;
  private final ProductRepository productRepository;
  private final ErpSyncService erpSyncService;

  @Autowired
  public ProductService(
      ProductRepository productRepository,
      ObjectProvider<ErpSyncService> erpSyncServiceProvider,
      Clock applicationClock) {
    this.erpRestClient = null;
    this.productsPath = null;
    this.clock = applicationClock;
    this.productRepository = productRepository;
    this.erpSyncService = erpSyncServiceProvider.getIfAvailable();
  }

  // Kept for the existing service-level ERP tests; production uses the persisted projection.
  public ProductService(
      RestClient restClient,
      @Value("${erp.api.products-path:/products}") String path,
      Clock applicationClock) {
    this.erpRestClient = restClient;
    this.productsPath = path;
    this.clock = applicationClock;
    this.productRepository = null;
    this.erpSyncService = null;
  }

  public List<ProductDTO> listProducts(String branch, String category, Boolean status) {
    if (productRepository != null) {
      synchronizeIfNeeded();
      return productRepository.findAll(branch, category, status);
    }

    try {
      List<ErpBatchDTO> batches =
          erpRestClient.get().uri(productsPath).retrieve().body(BATCHES_TYPE);

      List<ProductDTO> consolidatedProducts =
          consolidateProducts(batches == null ? List.of() : batches);
      return applyFilters(consolidatedProducts, branch, category, status);
    } catch (RestClientException | IllegalArgumentException exception) {
      throw new ErpIntegrationException("Could not connect to the external ERP API.", exception);
    }
  }

  public ProductDTO findProductById(String id) {
    if (productRepository != null) {
      synchronizeIfNeeded();
      return productRepository
          .findByPublicId(id)
          .orElseThrow(() -> new ProductNotFoundException(id));
    }
    return listProducts(null, null, null).stream()
        .filter(product -> product.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new ProductNotFoundException(id));
  }

  List<ProductDTO> consolidateProducts(List<ErpBatchDTO> batches) {
    Map<ProductBranchKey, List<ErpBatchDTO>> groupedBatches =
        batches.stream()
            .collect(
                Collectors.groupingBy(this::createKey, LinkedHashMap::new, Collectors.toList()));

    return groupedBatches.entrySet().stream()
        .map(entry -> consolidateProduct(entry.getKey(), entry.getValue()))
        .toList();
  }

  private List<ProductDTO> applyFilters(
      List<ProductDTO> products, String branch, String category, Boolean status) {
    return products.stream()
        .filter(product -> branch == null || branch.equals(product.branch()))
        .filter(product -> category == null || category.equals(product.category()))
        .filter(product -> status == null || status.equals(product.status()))
        .toList();
  }

  private ProductDTO consolidateProduct(ProductBranchKey key, List<ErpBatchDTO> productBatches) {
    ErpBatchDTO firstBatch = productBatches.get(0);
    ErpBatchDTO mostRecentBatch = findMostRecentBatch(productBatches);

    BigDecimal currentStock = sum(productBatches, ErpBatchDTO::quantity);
    BigDecimal sales7d = sum(productBatches, ErpBatchDTO::sales7d);
    BigDecimal sales30d = sum(productBatches, ErpBatchDTO::sales30d);

    return new ProductDTO(
        key.publicId(),
        key.erpProductCode(),
        firstBatch.productName(),
        firstBatch.category(),
        preserveWholeQuantity(currentStock),
        preserveWholeQuantity(
            resolveDecimalConfiguration(
                productBatches, ErpBatchDTO::minimumStock, "minimum_stock", key)),
        preserveWholeQuantity(sales7d),
        preserveWholeQuantity(sales30d),
        calculateExpirationDays(productBatches),
        resolveConfiguration(productBatches, ErpBatchDTO::leadTimeDays, "lead_time_days", key),
        ErpValueParser.toBigDecimal(mostRecentBatch.price()),
        ErpValueParser.toBigDecimal(mostRecentBatch.cost()),
        ErpValueParser.toInstant(mostRecentBatch.entryDate()),
        currentStock.signum() > 0,
        firstBatch.branch());
  }

  private ProductBranchKey createKey(ErpBatchDTO batch) {
    return new ProductBranchKey(batch.erpProductCode(), batch.erpBranchCode());
  }

  private BigDecimal sum(List<ErpBatchDTO> batches, Function<ErpBatchDTO, Object> field) {
    return batches.stream()
        .map(field)
        .map(ErpValueParser::toBigDecimal)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private void synchronizeIfNeeded() {
    if (productRepository.hasSnapshot() || erpSyncService == null) {
      return;
    }
    if (!erpSyncService.hasCompletedSync()) {
      erpSyncService.syncNow();
    }
  }

  private BigDecimal resolveDecimalConfiguration(
      List<ErpBatchDTO> batches,
      Function<ErpBatchDTO, Object> field,
      String fieldName,
      ProductBranchKey key) {
    NavigableSet<BigDecimal> values =
        batches.stream()
            .map(field)
            .map(ErpValueParser::toBigDecimal)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(TreeSet::new));

    if (values.size() > MAX_VALUES_WITHOUT_DIVERGENCE && LOGGER.isWarnEnabled()) {
      LOGGER.warn(
          "Divergent values for {} in group {}:{}; using the highest value: {}",
          fieldName,
          key.erpProductCode(),
          key.erpBranchCode(),
          values.last());
    }
    return values.isEmpty() ? null : values.last();
  }

  private Number preserveWholeQuantity(BigDecimal value) {
    if (value == null) {
      return null;
    }
    try {
      return value.intValueExact();
    } catch (ArithmeticException exception) {
      return value;
    }
  }

  private Integer resolveConfiguration(
      List<ErpBatchDTO> batches,
      Function<ErpBatchDTO, Object> field,
      String fieldName,
      ProductBranchKey key) {
    NavigableSet<Integer> values =
        batches.stream()
            .map(field)
            .map(ErpValueParser::toInteger)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(TreeSet::new));

    if (values.size() > MAX_VALUES_WITHOUT_DIVERGENCE && LOGGER.isWarnEnabled()) {
      LOGGER.warn(
          "Divergent values for {} in group {}:{}; using the highest value: {}",
          fieldName,
          key.erpProductCode(),
          key.erpBranchCode(),
          values.last());
    }

    return values.isEmpty() ? null : values.last();
  }

  private Integer calculateExpirationDays(List<ErpBatchDTO> batches) {
    LocalDate nearestExpirationDate =
        batches.stream()
            .filter(
                batch -> {
                  BigDecimal quantity = ErpValueParser.toBigDecimal(batch.quantity());
                  return quantity != null && quantity.signum() > 0;
                })
            .map(batch -> ErpValueParser.toLocalDate(batch.expirationDate()))
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(null);

    if (nearestExpirationDate == null) {
      return null;
    }

    return Math.toIntExact(ChronoUnit.DAYS.between(LocalDate.now(clock), nearestExpirationDate));
  }

  private ErpBatchDTO findMostRecentBatch(List<ErpBatchDTO> batches) {
    Comparator<ErpBatchDTO> comparator =
        Comparator.comparing(
                (ErpBatchDTO batch) -> ErpValueParser.toInstant(batch.entryDate()),
                Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(batch -> Objects.toString(batch.id(), ""));

    return batches.stream().max(comparator).orElseThrow();
  }

  private record ProductBranchKey(String erpProductCode, String erpBranchCode) {
    private String publicId() {
      return "%s:%s"
          .formatted(Objects.toString(erpProductCode, ""), Objects.toString(erpBranchCode, ""));
    }
  }
}
