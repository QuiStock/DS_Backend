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
import com.quistock.ds_backend.model.dto.UpdateActionStatusRequest;
import com.quistock.ds_backend.repository.ActionRepository;
import com.quistock.ds_backend.repository.StoreScope;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionServiceTest {
  private static final StoreScope SCOPE = new StoreScope(1001L, null, null);
  private static final LocalDate PROMOTION_START = LocalDate.of(2026, 10, 1);
  private static final LocalDate PROMOTION_END = LocalDate.of(2026, 10, 7);

  private FlowService flowService;
  private ActionRepository actionRepository;
  private ActionService actionService;

  @BeforeEach
  void setUp() {
    flowService = mock(FlowService.class);
    actionRepository = mock(ActionRepository.class);
    actionService =
        new ActionService(
            flowService,
            actionRepository,
            mock(UserAccessGuard.class),
            new ActionCreationValidator(
                java.time.Clock.fixed(
                    Instant.parse("2026-10-01T12:00:00Z"), java.time.ZoneOffset.UTC)));
  }

  @Test
  void shouldGeneratePromotionBasedOnRiskRatherThanSalesClass() {
    FlowDTO flow = flow("101", "HIGH");
    ActionDTO action = action("501", "PROMOTION", "GENERATED");
    when(flowService.findFlowById("101", SCOPE))
        .thenReturn(withRisks(flow, List.of("NEAR_EXPIRY")));
    when(actionRepository.createGenerated(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(SCOPE)))
        .thenReturn(action);
    var request =
        new com.quistock.ds_backend.model.dto.GenerateActionsRequest(
            "101", null, null, "PROMOTION", null, null, new BigDecimal("15"), 7);
    assertThat(actionService.generateActions(request, SCOPE).generatedActions())
        .containsExactly(action);
  }

  @Test
  void shouldGenerateOrderFromStockoutRisk() {
    when(flowService.findFlowById("101", SCOPE))
        .thenReturn(withRisks(flow("101", "LOW"), List.of("STOCKOUT")));
    when(actionRepository.createGenerated(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(SCOPE)))
        .thenReturn(action("501", "ORDER", "GENERATED"));
    var request =
        new com.quistock.ds_backend.model.dto.GenerateActionsRequest(
            "101", null, null, "ORDER", new BigDecimal("20"), PROMOTION_START, null, null);
    assertThat(actionService.generateActions(request, SCOPE).generatedActions().get(0).actionType())
        .isEqualTo("ORDER");
  }

  @Test
  void shouldGenerateNoActionWithoutRisk() {
    when(flowService.findFlowById("101", SCOPE)).thenReturn(flow("101", "MEDIUM"));
    assertThat(
            actionService
                .generateActions(
                    new com.quistock.ds_backend.model.dto.GenerateActionsRequest("101"), SCOPE)
                .generatedActions())
        .isEmpty();
  }

  @Test
  void shouldPropagateFlowNotFound() {
    when(flowService.findFlowById("UNKNOWN", SCOPE))
        .thenThrow(new FlowNotFoundException("UNKNOWN"));
    assertThatThrownBy(
            () ->
                actionService.generateActions(
                    new com.quistock.ds_backend.model.dto.GenerateActionsRequest("UNKNOWN"), SCOPE))
        .isInstanceOf(FlowNotFoundException.class);
  }

  @Test
  void shouldRejectPromotionWithoutCompatibleRisk() {
    when(flowService.findFlowById("101", SCOPE))
        .thenReturn(withRisks(flow("101", "HIGH"), List.of("STOCKOUT")));
    var request =
        new com.quistock.ds_backend.model.dto.GenerateActionsRequest(
            "101", PROMOTION_START, PROMOTION_END, "PROMOTION", null, null, BigDecimal.TEN, null);
    assertThatThrownBy(() -> actionService.generateActions(request, SCOPE))
        .isInstanceOf(InvalidRequestException.class);
  }

  private FlowDTO withRisks(FlowDTO flow, List<String> risks) {
    return new FlowDTO(
        flow.id(),
        flow.productId(),
        flow.productName(),
        flow.flowType(),
        flow.status(),
        flow.reason(),
        flow.dailySalesAverage(),
        flow.stockCoverageDays(),
        flow.expirationDays(),
        flow.supplierLeadTime(),
        flow.analysisDate(),
        risks,
        flow.classifier(),
        true,
        flow.storeId());
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
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    when(actionRepository.findAll("GENERATED", "PROMOTION", "101", SCOPE))
        .thenReturn(List.of(action));

    assertThat(actionService.listActions("GENERATED", "PROMOTION", "101", SCOPE))
        .containsExactly(action);
  }

  @Test
  void shouldRejectUnsupportedStatusFilter() {
    assertThatThrownBy(() -> actionService.listActions("ACTIVE", null, null, SCOPE))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void shouldRejectUnsupportedActionTypeFilter() {
    assertThatThrownBy(() -> actionService.listActions(null, "DISCOUNT", null, SCOPE))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void shouldUpdateActionStatus() {
    UpdateActionStatusRequest request = new UpdateActionStatusRequest("APPROVED");
    when(actionRepository.findTypeById("501", SCOPE)).thenReturn("PROMOTION");
    when(actionRepository.updateStatus("501", request, SCOPE))
        .thenReturn(new ActionStatusResponse("501", "APPROVED"));

    ActionStatusResponse response = actionService.updateActionStatus("501", request, SCOPE);

    assertThat(response.id()).isEqualTo("501");
    assertThat(response.status()).isEqualTo("APPROVED");
  }

  @Test
  void shouldRejectFinalPromotionDatesForNonPromotionActions() {
    UpdateActionStatusRequest request =
        new UpdateActionStatusRequest("APPROVED", null, PROMOTION_START, PROMOTION_END);
    when(actionRepository.findTypeById("501", SCOPE)).thenReturn("ORDER");

    assertThatThrownBy(() -> actionService.updateActionStatus("501", request, SCOPE))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void shouldRejectUnsupportedActionStatus() {
    assertThatThrownBy(
            () ->
                actionService.updateActionStatus(
                    "501", new UpdateActionStatusRequest("INVALID"), SCOPE))
        .isInstanceOf(InvalidActionStatusException.class);
  }

  @Test
  void shouldRequireJustificationWhenRejectingAction() {
    UpdateActionStatusRequest request = new UpdateActionStatusRequest("REJECTED", " ", null, null);

    assertThatThrownBy(() -> actionService.updateActionStatus("501", request, SCOPE))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void shouldRejectUnknownAction() {
    assertThatThrownBy(
            () ->
                actionService.updateActionStatus(
                    "UNKNOWN", new UpdateActionStatusRequest("APPROVED"), SCOPE))
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
        Instant.parse("2026-08-28T12:00:00Z"),
        java.util.List.of(),
        "SALES_PERCENTILES_V1",
        true,
        1L);
  }
}
