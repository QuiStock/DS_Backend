package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.ProductDTO;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowServiceTest {
  private static final Clock TEST_CLOCK =
      Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC);

  private ProductService productService;
  private FlowService flowService;

  @BeforeEach
  void setUp() {
    productService = mock(ProductService.class);
    flowService = new FlowService(productService, TEST_CLOCK);
  }

  @Test
  void shouldClassifyProductCloseToExpirationAsLow() {
    ProductDTO product = product(51, 58, 36, 150, 0, 3);
    when(productService.findProductById(product.id())).thenReturn(product);

    FlowDTO flow = flowService.analyzeProduct(product.id());

    assertThat(flow.id()).isEqualTo("101");
    assertThat(flow.flowType()).isEqualTo("LOW");
    assertThat(flow.status()).isEqualTo("ANALYZED");
    assertThat(flow.reason()).isEqualTo("Product is expired or close to expiration.");
    assertThat(flow.dailySalesAverage()).isEqualByComparingTo("5.14");
    assertThat(flow.stockCoverageDays()).isEqualByComparingTo("9.92");
    assertThat(flow.analysisDate()).isEqualTo(TEST_CLOCK.instant());
  }

  @Test
  void shouldClassifyProductWithStockoutRiskAsHigh() {
    ProductDTO product = product(5, 40, 14, 60, 30, 5);
    when(productService.findProductById(product.id())).thenReturn(product);

    FlowDTO flow = flowService.analyzeProduct(product.id());

    assertThat(flow.flowType()).isEqualTo("HIGH");
    assertThat(flow.stockCoverageDays()).isEqualByComparingTo("2.50");
  }

  @Test
  void shouldClassifyAdequateProductAsMedium() {
    ProductDTO product = product(100, 40, 14, 60, 365, 5);
    when(productService.findProductById(product.id())).thenReturn(product);

    FlowDTO flow = flowService.analyzeProduct(product.id());

    assertThat(flow.flowType()).isEqualTo("MEDIUM");
    assertThat(flow.reason()).isEqualTo("Stock is adequate for current demand.");
  }

  @Test
  void shouldUseThirtyDayAverageWhenThereAreNoWeeklySales() {
    ProductDTO product = product(100, 40, 0, 60, null, 5);
    when(productService.findProductById(product.id())).thenReturn(product);

    FlowDTO flow = flowService.analyzeProduct(product.id());

    assertThat(flow.dailySalesAverage()).isEqualByComparingTo("2.00");
    assertThat(flow.stockCoverageDays()).isEqualByComparingTo("50.00");
  }

  @Test
  void shouldClassifyStockWithoutSalesAsLow() {
    ProductDTO product = product(100, 40, 0, 0, null, 5);
    when(productService.findProductById(product.id())).thenReturn(product);

    FlowDTO flow = flowService.analyzeProduct(product.id());

    assertThat(flow.flowType()).isEqualTo("LOW");
    assertThat(flow.stockCoverageDays()).isEqualByComparingTo("0.00");
  }

  @Test
  void shouldListGeneratedFlows() {
    ProductDTO product = product(100, 40, 14, 60, 30, 5);
    when(productService.findProductById(product.id())).thenReturn(product);

    flowService.analyzeProduct(product.id());

    assertThat(flowService.listFlows()).hasSize(1);
  }

  private ProductDTO product(
      Integer currentStock,
      Integer minimumStock,
      Integer sales7d,
      Integer sales30d,
      Integer expirationDays,
      Integer leadTime) {
    return new ProductDTO(
        "PROD001:FIL001",
        "PROD001",
        "Whole Milk 1L",
        "Dairy",
        currentStock,
        minimumStock,
        sales7d,
        sales30d,
        expirationDays,
        leadTime,
        new BigDecimal("7.99"),
        new BigDecimal("5.20"),
        Instant.parse("2026-08-20T00:00:00Z"),
        true,
        "Santana Store");
  }
}
