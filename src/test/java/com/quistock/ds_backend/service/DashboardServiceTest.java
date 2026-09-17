package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.DashboardSummaryDTO;
import com.quistock.ds_backend.model.dto.FlowDTO;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
  private FlowService flowService;
  private ActionService actionService;
  private DashboardService dashboardService;

  @BeforeEach
  void setUp() {
    flowService = mock(FlowService.class);
    actionService = mock(ActionService.class);
    dashboardService = new DashboardService(flowService, actionService);
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

    DashboardSummaryDTO summary = dashboardService.getSummary();

    assertThat(summary.totalProducts()).isEqualTo(3);
    assertThat(summary.highRiskProducts()).isEqualTo(0);
    assertThat(summary.mediumRiskProducts()).isEqualTo(1);
    assertThat(summary.lowRiskProducts()).isEqualTo(2);
    assertThat(summary.suggestedActions()).isEqualTo(3);
    assertThat(summary.suggestedPromotions()).isEqualTo(1);
    assertThat(summary.suggestedStockOrders()).isEqualTo(1);
  }

  @Test
  void shouldReturnZeroSummaryWhenThereIsNoData() {
    when(flowService.listFlows()).thenReturn(List.of());
    when(actionService.listActions()).thenReturn(List.of());

    assertThat(dashboardService.getSummary())
        .isEqualTo(new DashboardSummaryDTO(0, 0, 0, 0, 0, 0, 0));
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
}
