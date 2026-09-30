package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.ProductDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
class FlowAnalysisCalculator {
  private static final BigDecimal DAYS_IN_WEEK = BigDecimal.valueOf(7);
  private static final BigDecimal DAYS_IN_MONTH = BigDecimal.valueOf(30);
  private static final BigDecimal ZERO_DAYS = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

  Result calculate(ProductDTO product) {
    BigDecimal dailySalesAverage = calculateDailySalesAverage(product);
    BigDecimal stockCoverageDays = calculateCoverage(product, dailySalesAverage);
    Classification classification = classify(product, stockCoverageDays);
    return new Result(
        classification.type(),
        classification.reason(),
        dailySalesAverage,
        stockCoverageDays,
        decimal(product.currentStock()),
        decimal(product.minimumStock()),
        decimal(product.sales7d()),
        decimal(product.sales30d()));
  }

  private BigDecimal calculateDailySalesAverage(ProductDTO product) {
    BigDecimal sales7d = valueOrZero(product.sales7d());
    BigDecimal sales30d = valueOrZero(product.sales30d());
    if (sales7d.signum() > 0) {
      return sales7d.divide(DAYS_IN_WEEK, 2, RoundingMode.HALF_UP);
    }
    if (sales30d.signum() > 0) {
      return sales30d.divide(DAYS_IN_MONTH, 2, RoundingMode.HALF_UP);
    }
    return ZERO_DAYS;
  }

  private BigDecimal calculateCoverage(ProductDTO product, BigDecimal dailySalesAverage) {
    if (dailySalesAverage.signum() == 0) {
      return ZERO_DAYS;
    }
    return valueOrZero(product.currentStock()).divide(dailySalesAverage, 2, RoundingMode.HALF_UP);
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
    int leadTime = product.supplierLeadTime() == null ? 0 : product.supplierLeadTime();
    if (currentStock.signum() <= 0
        || currentStock.compareTo(minimumStock) < 0
        || stockCoverageDays.compareTo(BigDecimal.valueOf(leadTime)) <= 0) {
      return new Classification(
          "HIGH", "Stockout risk: stock is below minimum or coverage is below supplier lead time.");
    }
    return new Classification("MEDIUM", "Stock is adequate for current demand.");
  }

  private BigDecimal valueOrZero(Number value) {
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  private BigDecimal decimal(Number value) {
    return value == null ? null : new BigDecimal(value.toString());
  }

  record Result(
      String flowType,
      String reason,
      BigDecimal dailySalesAverage,
      BigDecimal stockCoverageDays,
      BigDecimal currentStock,
      BigDecimal minimumStock,
      BigDecimal sales7d,
      BigDecimal sales30d) {}

  private record Classification(String type, String reason) {}
}
