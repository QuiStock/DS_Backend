package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.exception.FlowNotFoundException;
import com.quistock.ds_backend.model.dto.ActionDTO;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.GenerateActionsResponse;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionServiceTest {
  private FlowService flowService;
  private ActionService actionService;

  @BeforeEach
  void setUp() {
    flowService = mock(FlowService.class);
    actionService = new ActionService(flowService);
  }

  @Test
  void shouldGeneratePromotionForLowFlow() {
    when(flowService.findFlowById("101")).thenReturn(flow("101", "LOW"));

    GenerateActionsResponse response = actionService.generateActions("101");

    assertThat(response.flowId()).isEqualTo("101");
    assertThat(response.generatedActions()).singleElement().satisfies(this::assertPromotion);
  }

  @Test
  void shouldGenerateStockOrderForHighFlow() {
    when(flowService.findFlowById("101")).thenReturn(flow("101", "HIGH"));

    ActionDTO action = actionService.generateActions("101").generatedActions().get(0);

    assertThat(action.actionType()).isEqualTo("STOCK_ORDER");
    assertThat(action.status()).isEqualTo("SUGGESTED");
  }

  @Test
  void shouldGenerateMonitorForMediumFlow() {
    when(flowService.findFlowById("101")).thenReturn(flow("101", "MEDIUM"));

    ActionDTO action = actionService.generateActions("101").generatedActions().get(0);

    assertThat(action.actionType()).isEqualTo("MONITOR");
    assertThat(action.status()).isEqualTo("SUGGESTED");
  }

  @Test
  void shouldPropagateFlowNotFound() {
    when(flowService.findFlowById("UNKNOWN")).thenThrow(new FlowNotFoundException("UNKNOWN"));

    assertThatThrownBy(() -> actionService.generateActions("UNKNOWN"))
        .isInstanceOf(FlowNotFoundException.class);
  }

  private void assertPromotion(ActionDTO action) {
    assertThat(action.id()).isEqualTo("501");
    assertThat(action.actionType()).isEqualTo("PROMOTION");
    assertThat(action.status()).isEqualTo("SUGGESTED");
    assertThat(action.justification()).isEqualTo("Product has expiration or excess stock risk.");
  }

  private FlowDTO flow(String id, String type) {
    return new FlowDTO(
        id,
        "PROD001:FIL001",
        "Whole Milk 1L",
        type,
        "ANALYZED",
        "Analysis reason",
        new BigDecimal("5.14"),
        new BigDecimal("9.92"),
        0,
        3,
        Instant.parse("2026-08-28T12:00:00Z"));
  }
}
