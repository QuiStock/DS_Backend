package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.DashboardSummaryDTO;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.ProductDTO;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
  private FlowService flowService;
  private ActionService actionService;
  private ProductService productService;
  private DashboardService dashboardService;

  @BeforeEach
  void setUp() {
    flowService = mock(FlowService.class);
    actionService = mock(ActionService.class);
    productService = mock(ProductService.class);
    dashboardService = new DashboardService(flowService, actionService, productService, 30);
  }

  @Test
  void shouldAggregateLatestProductRisksAndSuggestedActions() {
    when(flowService.listFlows())
        .thenReturn(
            List.of(
                flow("101", "PROD001:FIL001", "HIGH"),
                flow("102", "PROD002:FIL002", "MEDIUM"),
                flow("103", "PROD003:FIL003", "LOW"),
                flow("104", "PROD001:FIL001", "LOW")));
    when(actionService.listActions())
        .thenReturn(
            List.of(
                action("501", "101", "PROMOTION", "SUGGESTED"),
                action("502", "102", "STOCK_ORDER", "SUGGESTED"),
                action("503", "103", "PROMOTION", "APPROVED"),
                action("504", "104", "MONITOR", "SUGGESTED")));
    when(productService.listProducts(null, null, null))
        .thenReturn(List.of(product(10, 5, 10), product(0, 5, null), product(3, 5, 30)));

    DashboardSummaryDTO summary = dashboardService.getSummary();

    assertThat(summary.totalProducts()).isEqualTo(3);
    assertThat(summary.highRiskProducts()).isEqualTo(0);
    assertThat(summary.mediumRiskProducts()).isEqualTo(1);
    assertThat(summary.lowRiskProducts()).isEqualTo(2);
    assertThat(summary.suggestedActions()).isEqualTo(3);
    assertThat(summary.suggestedPromotions()).isEqualTo(1);
    assertThat(summary.suggestedStockOrders()).isEqualTo(1);
    assertThat(summary.nearExpiryProducts()).isEqualTo(2);
    assertThat(summary.stockoutProducts()).isEqualTo(1);
    assertThat(summary.overstockProducts()).isEqualTo(1);
    assertThat(summary.activeActions()).isEqualTo(4);
  }

  @Test
  void shouldReturnZeroSummaryWhenThereIsNoData() {
    when(flowService.listFlows()).thenReturn(List.of());
    when(actionService.listActions()).thenReturn(List.of());
    when(productService.listProducts(null, null, null)).thenReturn(List.of());

    assertThat(dashboardService.getSummary())
        .isEqualTo(new DashboardSummaryDTO(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
  }

  private FlowDTO flow(String id, String productId, String flowType) {
    return new FlowDTO(
        id,
        productId,
        "Whole Milk 1L",
        flowType,
        "ANALYZED",
        "Analysis reason",
        new BigDecimal("5.14"),
        new BigDecimal("9.92"),
        0,
        3,
        Instant.parse("2026-08-28T12:00:00Z"));
  }

  private ActionListItemDTO action(String id, String flowId, String actionType, String status) {
    return new ActionListItemDTO(
        id, flowId, "Whole Milk 1L", actionType, status, "Action justification");
  }

  private ProductDTO product(Integer currentStock, Integer minimumStock, Integer expirationDays) {
    return new ProductDTO(
        "PROD001:FIL001",
        "PROD001",
        "Whole Milk 1L",
        "Dairy",
        currentStock,
        minimumStock,
        36,
        150,
        expirationDays,
        5,
        new BigDecimal("7.99"),
        new BigDecimal("5.20"),
        Instant.parse("2026-08-20T00:00:00Z"),
        currentStock != null && currentStock > 0,
        "Santana Store");
  }
}
