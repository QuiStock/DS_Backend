package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.ProductDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class FlowService {
  private static final BigDecimal DAYS_IN_WEEK = BigDecimal.valueOf(7);
  private static final BigDecimal DAYS_IN_MONTH = BigDecimal.valueOf(30);
  private static final int DECIMAL_SCALE = 2;

  private final ProductService productService;
  private final Clock clock;
  private final AtomicLong nextId = new AtomicLong(100);
  private final List<FlowDTO> flows = new CopyOnWriteArrayList<>();

  public FlowService(ProductService service, Clock applicationClock) {
    this.productService = service;
    this.clock = applicationClock;
  }

  public FlowDTO analyzeProduct(String productId) {
    ProductDTO product = productService.findProductById(productId);
    BigDecimal dailySalesAverage = calculateDailySalesAverage(product);
    BigDecimal stockCoverageDays = calculateCoverage(product, dailySalesAverage);
    Classification classification = classify(product, stockCoverageDays);

    FlowDTO flow =
        new FlowDTO(
            String.valueOf(nextId.incrementAndGet()),
            product.id(),
            product.name(),
            classification.type(),
            "ANALYZED",
            classification.reason(),
            dailySalesAverage,
            stockCoverageDays,
            product.expirationDays(),
            product.supplierLeadTime(),
            Instant.now(clock));
    flows.add(flow);
    return flow;
  }

  public List<FlowDTO> listFlows() {
    return List.copyOf(flows);
  }

  private BigDecimal calculateDailySalesAverage(ProductDTO product) {
    int sales7d = valueOrZero(product.sales7d());
    int sales30d = valueOrZero(product.sales30d());
    if (sales7d > 0) {
      return divide(sales7d, DAYS_IN_WEEK);
    }
    if (sales30d > 0) {
      return divide(sales30d, DAYS_IN_MONTH);
    }
    return BigDecimal.ZERO.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal calculateCoverage(ProductDTO product, BigDecimal dailySalesAverage) {
    if (dailySalesAverage.signum() == 0) {
      return BigDecimal.ZERO.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }
    return divide(valueOrZero(product.currentStock()), dailySalesAverage);
  }

  private BigDecimal divide(int value, BigDecimal divisor) {
    return BigDecimal.valueOf(value).divide(divisor, DECIMAL_SCALE, RoundingMode.HALF_UP);
  }

  private Classification classify(ProductDTO product, BigDecimal stockCoverageDays) {
    Integer expirationDays = product.expirationDays();
    if (expirationDays != null
        && BigDecimal.valueOf(expirationDays).compareTo(stockCoverageDays) <= 0) {
      return new Classification("LOW", "Product is expired or close to expiration.");
    }

    int currentStock = valueOrZero(product.currentStock());
    if (stockCoverageDays.signum() == 0 && currentStock > 0) {
      return new Classification("LOW", "Product has no recent sales and has available stock.");
    }

    int minimumStock = valueOrZero(product.minimumStock());
    int leadTime = valueOrZero(product.supplierLeadTime());
    if (currentStock <= 0
        || currentStock < minimumStock
        || stockCoverageDays.compareTo(BigDecimal.valueOf(leadTime)) <= 0) {
      return new Classification(
          "HIGH", "Stockout risk: stock is below minimum or coverage is below supplier lead time.");
    }

    return new Classification("MEDIUM", "Stock is adequate for current demand.");
  }

  private int valueOrZero(Integer value) {
    return value == null ? 0 : value;
  }

  private record Classification(String type, String reason) {}
}
