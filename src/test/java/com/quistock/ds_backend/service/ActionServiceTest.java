package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.exception.ActionNotFoundException;
import com.quistock.ds_backend.exception.FlowNotFoundException;
import com.quistock.ds_backend.exception.InvalidActionStatusException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.model.dto.ActionDTO;
import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.ActionStatusResponse;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.GenerateActionsResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionServiceTest {
  private static final LocalDate PROMOTION_START = LocalDate.of(2026, 10, 1);
  private static final LocalDate PROMOTION_END = LocalDate.of(2026, 10, 7);

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

    GenerateActionsResponse response =
        actionService.generateActions("101", PROMOTION_START, PROMOTION_END);

    assertThat(response.flowId()).isEqualTo("101");
    assertThat(response.generatedActions()).singleElement().satisfies(this::assertPromotion);
  }

  @Test
  void shouldGenerateStockOrderForHighFlow() {
    when(flowService.findFlowById("101")).thenReturn(flow("101", "HIGH"));

    ActionDTO action = actionService.generateActions("101").generatedActions().get(0);

    assertThat(action.actionType()).isEqualTo("ORDER");
    assertThat(action.status()).isEqualTo("GENERATED");
  }

  @Test
  void shouldGenerateMonitorForMediumFlow() {
    when(flowService.findFlowById("101")).thenReturn(flow("101", "MEDIUM"));

    assertThat(actionService.generateActions("101").generatedActions()).isEmpty();
  }

  @Test
  void shouldPropagateFlowNotFound() {
    when(flowService.findFlowById("UNKNOWN")).thenThrow(new FlowNotFoundException("UNKNOWN"));

    assertThatThrownBy(() -> actionService.generateActions("UNKNOWN"))
        .isInstanceOf(FlowNotFoundException.class);
  }

  @Test
  void shouldListActionsUsingContractFilters() {
    when(flowService.findFlowById("101")).thenReturn(flow("101", "LOW"));
    when(flowService.findFlowById("102")).thenReturn(flow("102", "HIGH"));

    actionService.generateActions("101", PROMOTION_START, PROMOTION_END);
    actionService.generateActions("102");

    assertThat(actionService.listActions()).hasSize(2);

    List<ActionListItemDTO> actions = actionService.listActions("GENERATED", "PROMOTION", "101");

    assertThat(actions)
        .singleElement()
        .satisfies(
            action -> {
              assertThat(action.id()).isEqualTo("501");
              assertThat(action.flowId()).isEqualTo("101");
              assertThat(action.productName()).isEqualTo("Whole Milk 1L");
              assertThat(action.actionType()).isEqualTo("PROMOTION");
              assertThat(action.status()).isEqualTo("GENERATED");
            });
  }

  @Test
  void shouldRejectUnsupportedStatusFilter() {
    assertThatThrownBy(() -> actionService.listActions("ACTIVE", null, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void shouldRejectUnsupportedActionTypeFilter() {
    assertThatThrownBy(() -> actionService.listActions(null, "DISCOUNT", null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void shouldUpdateActionStatus() {
    when(flowService.findFlowById("101")).thenReturn(flow("101", "LOW"));
    actionService.generateActions("101", PROMOTION_START, PROMOTION_END);

    ActionStatusResponse response = actionService.updateActionStatus("501", "APPROVED");

    assertThat(response.id()).isEqualTo("501");
    assertThat(response.status()).isEqualTo("APPROVED");
    assertThat(actionService.listActions("APPROVED", null, "101"))
        .singleElement()
        .extracting(ActionListItemDTO::status)
        .isEqualTo("APPROVED");
  }

  @Test
  void shouldRejectUnsupportedActionStatus() {
    when(flowService.findFlowById("101")).thenReturn(flow("101", "LOW"));
    actionService.generateActions("101", PROMOTION_START, PROMOTION_END);

    assertThatThrownBy(() -> actionService.updateActionStatus("501", "INVALID"))
        .isInstanceOf(InvalidActionStatusException.class);
  }

  @Test
  void shouldRejectUnknownAction() {
    assertThatThrownBy(() -> actionService.updateActionStatus("UNKNOWN", "APPROVED"))
        .isInstanceOf(ActionNotFoundException.class);
  }

  private void assertPromotion(ActionDTO action) {
    assertThat(action.id()).isEqualTo("501");
    assertThat(action.actionType()).isEqualTo("PROMOTION");
    assertThat(action.status()).isEqualTo("GENERATED");
    assertThat(action.justification())
        .isEqualTo("Expiration or excess-stock risk: create a promotion.");
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
