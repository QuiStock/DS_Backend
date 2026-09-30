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
import com.quistock.ds_backend.model.dto.UpdateActionStatusRequest;
import com.quistock.ds_backend.repository.ActionRepository;
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
  private ActionRepository actionRepository;
  private ActionService actionService;

  @BeforeEach
  void setUp() {
    flowService = mock(FlowService.class);
    actionRepository = mock(ActionRepository.class);
    actionService = new ActionService(flowService, actionRepository);
  }

  @Test
  void shouldGeneratePromotionForLowFlow() {
    FlowDTO flow = flow("101", "LOW");
    ActionDTO action = action("501", "PROMOTION", "GENERATED");
    when(flowService.findFlowById("101")).thenReturn(flow);
    when(actionRepository.createGenerated(
            flow,
            "PROMOTION",
            "Expiration or excess-stock risk: create a promotion.",
            PROMOTION_START,
            PROMOTION_END))
        .thenReturn(action);

    GenerateActionsResponse response =
        actionService.generateActions("101", PROMOTION_START, PROMOTION_END);

    assertThat(response.flowId()).isEqualTo("101");
    assertThat(response.generatedActions()).containsExactly(action);
  }

  @Test
  void shouldGenerateStockOrderForHighFlow() {
    FlowDTO flow = flow("101", "HIGH");
    ActionDTO action = action("501", "ORDER", "GENERATED");
    when(flowService.findFlowById("101")).thenReturn(flow);
    when(actionRepository.createGenerated(
            flow, "ORDER", "Stockout risk: replenish stock.", null, null))
        .thenReturn(action);

    ActionDTO generated = actionService.generateActions("101").generatedActions().get(0);

    assertThat(generated.actionType()).isEqualTo("ORDER");
    assertThat(generated.status()).isEqualTo("GENERATED");
  }

  @Test
  void shouldGenerateNoActionForMediumFlow() {
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
  void shouldRejectPromotionDatesForNonPromotionFlow() {
    when(flowService.findFlowById("101")).thenReturn(flow("101", "HIGH"));

    assertThatThrownBy(() -> actionService.generateActions("101", PROMOTION_START, PROMOTION_END))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void shouldListActionsUsingContractFilters() {
    ActionListItemDTO action =
        new ActionListItemDTO(
            "501",
            "101",
            "Whole Milk 1L",
            "PROMOTION",
            "GENERATED",
            "Expiration or excess-stock risk: create a promotion.",
            PROMOTION_START,
            PROMOTION_END,
            null);
    when(actionRepository.findAll("GENERATED", "PROMOTION", "101")).thenReturn(List.of(action));

    assertThat(actionService.listActions("GENERATED", "PROMOTION", "101")).containsExactly(action);
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
    UpdateActionStatusRequest request = new UpdateActionStatusRequest("APPROVED");
    when(actionRepository.findTypeById("501")).thenReturn("PROMOTION");
    when(actionRepository.updateStatus("501", request))
        .thenReturn(new ActionStatusResponse("501", "APPROVED"));

    ActionStatusResponse response = actionService.updateActionStatus("501", request);

    assertThat(response.id()).isEqualTo("501");
    assertThat(response.status()).isEqualTo("APPROVED");
  }

  @Test
  void shouldRejectFinalPromotionDatesForNonPromotionActions() {
    UpdateActionStatusRequest request =
        new UpdateActionStatusRequest("APPROVED", null, PROMOTION_START, PROMOTION_END);
    when(actionRepository.findTypeById("501")).thenReturn("ORDER");

    assertThatThrownBy(() -> actionService.updateActionStatus("501", request))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void shouldRejectUnsupportedActionStatus() {
    assertThatThrownBy(() -> actionService.updateActionStatus("501", "INVALID"))
        .isInstanceOf(InvalidActionStatusException.class);
  }

  @Test
  void shouldRequireJustificationWhenRejectingAction() {
    UpdateActionStatusRequest request = new UpdateActionStatusRequest("REJECTED", " ", null, null);

    assertThatThrownBy(() -> actionService.updateActionStatus("501", request))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void shouldRejectUnknownAction() {
    assertThatThrownBy(() -> actionService.updateActionStatus("UNKNOWN", "APPROVED"))
        .isInstanceOf(ActionNotFoundException.class);
  }

  private ActionDTO action(String id, String type, String status) {
    return new ActionDTO(id, type, status, "Action reason", PROMOTION_START, PROMOTION_END);
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
