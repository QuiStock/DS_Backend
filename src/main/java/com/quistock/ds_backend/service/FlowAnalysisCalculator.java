package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.ProductDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class FlowAnalysisCalculator {
  private static final int NEAR_EXPIRY_DAYS = 7;
  static final String CLASSIFIER = "SALES_PERCENTILES_V1";
  private static final BigDecimal SEVEN = BigDecimal.valueOf(7);

  Result calculate(ProductDTO product, List<BigDecimal> peers) {
    BigDecimal sales = decimal(product.sales7d());
    BigDecimal daily = sales == null ? null : sales.divide(SEVEN, 6, RoundingMode.HALF_UP);
    BigDecimal stock = decimal(product.currentStock());
    BigDecimal coverage =
        daily == null || daily.signum() == 0 || stock == null
            ? null
            : stock.divide(daily, 6, RoundingMode.HALF_UP);
    String type = classify(sales, peers);
    return new Result(
        type,
        type == null
            ? "Missing sales, category/unit or fewer than five comparable products."
            : "Sales speed compared with the same category, unit and store.",
        daily,
        coverage,
        stock,
        decimal(product.minimumStock()),
        sales,
        decimal(product.sales30d()),
        risks(product, daily, coverage));
  }

  private String classify(BigDecimal sales, List<BigDecimal> peers) {
    if (sales == null || peers.size() < 5) {
      return null;
    }
    if (sales.signum() == 0) {
      return "LOW";
    }
    List<BigDecimal> sorted = peers.stream().sorted().toList();
    if (sales.compareTo(percentile(sorted, new BigDecimal("0.25"))) < 0) {
      return "LOW";
    }
    return sales.compareTo(percentile(sorted, new BigDecimal("0.75"))) > 0 ? "HIGH" : "MEDIUM";
  }

  private BigDecimal percentile(List<BigDecimal> sorted, BigDecimal fraction) {
    BigDecimal position = fraction.multiply(BigDecimal.valueOf(sorted.size() - 1L));
    int lower = position.intValue();
    BigDecimal weight = position.subtract(BigDecimal.valueOf(lower));
    BigDecimal left = sorted.get(lower);
    return lower + 1 == sorted.size()
        ? left
        : left.add(sorted.get(lower + 1).subtract(left).multiply(weight));
  }

  private List<String> risks(ProductDTO product, BigDecimal daily, BigDecimal coverage) {
    List<String> risks = new ArrayList<>();
    BigDecimal stock = decimal(product.currentStock());
    if (stock == null) {
      return List.of();
    }
    BigDecimal minimum = decimal(product.minimumStock());
    boolean belowLeadTime =
        coverage != null
            && product.supplierLeadTime() != null
            && coverage.compareTo(BigDecimal.valueOf(product.supplierLeadTime())) <= 0;
    if (stock.signum() <= 0 || minimum != null && stock.compareTo(minimum) < 0 || belowLeadTime) {
      risks.add("STOCKOUT");
    }
    appendExpirationRisks(product.expirationDays(), stock, daily, coverage, risks);
    return List.copyOf(risks);
  }

  private void appendExpirationRisks(
      Integer days, BigDecimal stock, BigDecimal daily, BigDecimal coverage, List<String> risks) {
    if (days == null || stock.signum() <= 0) {
      return;
    }
    if (days < 0) {
      risks.add("EXPIRED");
      return;
    }
    if (days <= NEAR_EXPIRY_DAYS) {
      risks.add("NEAR_EXPIRY");
    }
    if (daily != null && daily.signum() == 0
        || coverage != null && coverage.compareTo(BigDecimal.valueOf(days)) >= 0) {
      risks.add("EXPIRATION");
    }
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
      BigDecimal sales30d,
      List<String> risks) {}
}
