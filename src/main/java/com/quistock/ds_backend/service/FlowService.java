package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.FlowNotFoundException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.repository.FlowRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FlowService {
  private static final BigDecimal DAYS_IN_WEEK = BigDecimal.valueOf(7);
  private static final BigDecimal DAYS_IN_MONTH = BigDecimal.valueOf(30);
  private static final int DECIMAL_SCALE = 2;
  private static final Set<String> VALID_FLOW_TYPES = Set.of("HIGH", "MEDIUM", "LOW");
  private static final Set<String> VALID_STATUSES = Set.of("ANALYZED");

  private final ProductService productService;
  private final FlowRepository flowRepository;
  private final Clock clock;
  private final AtomicLong nextId = new AtomicLong(100);
  private final List<FlowDTO> flows = new CopyOnWriteArrayList<>();

  @Autowired
  public FlowService(ProductService service, FlowRepository repository, Clock applicationClock) {
    this.productService = service;
    this.flowRepository = repository;
    this.clock = applicationClock;
  }

  // Kept for the existing service-level tests.
  public FlowService(ProductService service, Clock applicationClock) {
    this.productService = service;
    this.flowRepository = null;
    this.clock = applicationClock;
  }

  public FlowDTO analyzeProduct(String productId) {
    ProductDTO product = productService.findProductById(productId);
    BigDecimal dailySalesAverage = calculateDailySalesAverage(product);
    BigDecimal stockCoverageDays = calculateCoverage(product, dailySalesAverage);
    Classification classification = classify(product, stockCoverageDays);
    Instant analysisDate = Instant.now(clock);
    String flowId;
    if (flowRepository == null) {
      flowId = String.valueOf(nextId.incrementAndGet());
    } else {
      long id =
          flowRepository.insertAnalysis(
              new FlowRepository.AnalysisSnapshot(
                  product.id(),
                  classification.type(),
                  dailySalesAverage,
                  stockCoverageDays,
                  decimal(product.currentStock()),
                  decimal(product.minimumStock()),
                  decimal(product.sales7d()),
                  decimal(product.sales30d()),
                  product.expirationDays(),
                  product.supplierLeadTime(),
                  classification.reason(),
                  analysisDate));
      if (id < 0) {
        throw new ProductNotFoundException(productId);
      }
      flowId = Long.toString(id);
    }

    FlowDTO flow =
        new FlowDTO(
            flowId,
            product.id(),
            product.name(),
            classification.type(),
            "ANALYZED",
            classification.reason(),
            dailySalesAverage,
            stockCoverageDays,
            product.expirationDays(),
            product.supplierLeadTime(),
            analysisDate);
    if (flowRepository == null) {
      flows.add(flow);
    }
    return flow;
  }

  public List<FlowDTO> listFlows() {
    return listFlows(null, null, null);
  }

  public List<FlowDTO> listFlows(String flowType, String productId, String status) {
    if (flowType != null && !VALID_FLOW_TYPES.contains(flowType)) {
      throw new InvalidRequestException();
    }
    if (status != null && !VALID_STATUSES.contains(status)) {
      throw new InvalidRequestException();
    }

    if (flowRepository != null) {
      return flowRepository.findAll(flowType, productId, status);
    }
    return flows.stream()
        .filter(flow -> flowType == null || flowType.equals(flow.flowType()))
        .filter(flow -> productId == null || productId.equals(flow.productId()))
        .filter(flow -> status == null || status.equals(flow.status()))
        .toList();
  }

  public FlowDTO findFlowById(String id) {
    if (flowRepository != null) {
      return flowRepository.findById(id).orElseThrow(() -> new FlowNotFoundException(id));
    }
    return flows.stream()
        .filter(flow -> id != null && id.equals(flow.id()))
        .findFirst()
        .orElseThrow(() -> new FlowNotFoundException(id));
  }

  private BigDecimal calculateDailySalesAverage(ProductDTO product) {
    BigDecimal sales7d = valueOrZero(product.sales7d());
    BigDecimal sales30d = valueOrZero(product.sales30d());
    if (sales7d.signum() > 0) {
      return divide(sales7d, DAYS_IN_WEEK);
    }
    if (sales30d.signum() > 0) {
      return divide(sales30d, DAYS_IN_MONTH);
    }
    return BigDecimal.ZERO.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal calculateCoverage(ProductDTO product, BigDecimal dailySalesAverage) {
    if (dailySalesAverage.signum() == 0) {
      return BigDecimal.ZERO.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }
    return valueOrZero(product.currentStock())
        .divide(dailySalesAverage, DECIMAL_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal divide(BigDecimal value, BigDecimal divisor) {
    return value.divide(divisor, DECIMAL_SCALE, RoundingMode.HALF_UP);
  }

  private Classification classify(ProductDTO product, BigDecimal stockCoverageDays) {
    Integer expirationDays = product.expirationDays();
    if (expirationDays != null
        && BigDecimal.valueOf(expirationDays).compareTo(stockCoverageDays) <= 0) {
      return new Classification("LOW", "Product is expired or close to expiration.");
    }

    BigDecimal currentStock = valueOrZero(product.currentStock());
    if (stockCoverageDays.signum() == 0 && currentStock.signum() > 0) {
      return new Classification("LOW", "Product has no recent sales and has available stock.");
    }

    BigDecimal minimumStock = valueOrZero(product.minimumStock());
    int leadTime = valueOrZero(product.supplierLeadTime());
    if (currentStock.signum() <= 0
        || currentStock.compareTo(minimumStock) < 0
        || stockCoverageDays.compareTo(BigDecimal.valueOf(leadTime)) <= 0) {
      return new Classification(
          "HIGH", "Stockout risk: stock is below minimum or coverage is below supplier lead time.");
    }

    return new Classification("MEDIUM", "Stock is adequate for current demand.");
  }

  private int valueOrZero(Integer value) {
    return value == null ? 0 : value;
  }

  private BigDecimal valueOrZero(Number value) {
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  private BigDecimal decimal(Number value) {
    return value == null ? null : new BigDecimal(value.toString());
  }

  private record Classification(String type, String reason) {}
}
