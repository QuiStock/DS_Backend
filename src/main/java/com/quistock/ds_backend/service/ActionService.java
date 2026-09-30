package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ActionNotFoundException;
import com.quistock.ds_backend.exception.InvalidActionStatusException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.model.dto.ActionDTO;
import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.ActionStatusResponse;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.GenerateActionsResponse;
import com.quistock.ds_backend.model.dto.UpdateActionStatusRequest;
import com.quistock.ds_backend.repository.ActionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ActionService {
  private static final String ORDER = "ORDER";
  private static final String PROMOTION = "PROMOTION";
  private static final String GENERATED = "GENERATED";
  private static final String APPROVED = "APPROVED";
  private static final String REJECTED = "REJECTED";
  private static final Map<String, ActionRule> FLOW_ACTIONS =
      Map.of(
          "HIGH", new ActionRule(ORDER, "Stockout risk: replenish stock."),
          "MEDIUM", new ActionRule(null, null),
          "LOW", new ActionRule(PROMOTION, "Expiration or excess-stock risk: create a promotion."));
  private static final Set<String> VALID_STATUSES =
      Set.of(GENERATED, "IN_EMPLOYEE_TRIAGE", "SENT_TO_MANAGER", APPROVED, REJECTED);
  private static final Set<String> VALID_ACTION_TYPES = Set.of(ORDER, PROMOTION);

  private final FlowService flowService;
  private final ActionRepository actionRepository;

  public ActionService(FlowService flowService, ActionRepository actionRepository) {
    this.flowService = flowService;
    this.actionRepository = actionRepository;
  }

  public GenerateActionsResponse generateActions(String flowId) {
    return generateActions(flowId, null, null);
  }

  public GenerateActionsResponse generateActions(
      String flowId, LocalDate promotionValidFrom, LocalDate promotionValidUntil) {
    FlowDTO flow = flowService.findFlowById(flowId);
    ActionRule rule = FLOW_ACTIONS.get(flow.flowType());
    if (rule == null) {
      throw new IllegalArgumentException("Unsupported flow type: " + flow.flowType());
    }
    if (rule.actionType() == null) {
      validatePromotionDates(null, promotionValidFrom, promotionValidUntil, false);
      return new GenerateActionsResponse(flow.id(), List.of());
    }

    validatePromotionDates(rule.actionType(), promotionValidFrom, promotionValidUntil, true);
    ActionDTO action =
        actionRepository.createGenerated(
            flow, rule.actionType(), rule.justification(), promotionValidFrom, promotionValidUntil);
    return new GenerateActionsResponse(flow.id(), List.of(action));
  }

  public List<ActionListItemDTO> listActions() {
    return listActions(null, null, null);
  }

  public List<ActionListItemDTO> listActions(String status, String actionType, String flowId) {
    if (status != null && !VALID_STATUSES.contains(status)) {
      throw new InvalidRequestException();
    }
    if (actionType != null && !VALID_ACTION_TYPES.contains(actionType)) {
      throw new InvalidRequestException();
    }
    return actionRepository.findAll(status, actionType, flowId);
  }

  public ActionStatusResponse updateActionStatus(String actionId, String status) {
    return updateActionStatus(actionId, new UpdateActionStatusRequest(status));
  }

  public ActionStatusResponse updateActionStatus(
      String actionId, UpdateActionStatusRequest request) {
    validateStatus(request);
    String actionType = actionRepository.findTypeById(actionId);
    if (actionType == null) {
      throw new ActionNotFoundException(actionId);
    }
    validateFinalDates(actionType, request.status(), request);
    return actionRepository.updateStatus(actionId, request);
  }

  private void validateStatus(UpdateActionStatusRequest request) {
    String status = request.status();
    if (!VALID_STATUSES.contains(status)) {
      throw new InvalidActionStatusException(status);
    }
    if (REJECTED.equals(status)
        && (request.justification() == null || request.justification().isBlank())) {
      throw new InvalidRequestException();
    }
  }

  private void validatePromotionDates(
      String actionType, LocalDate validFrom, LocalDate validUntil, boolean required) {
    if (!PROMOTION.equals(actionType)) {
      rejectDatesForNonPromotion(validFrom, validUntil);
      return;
    }
    validateRequiredPromotionDates(validFrom, validUntil, required);
    validatePromotionDatePair(validFrom, validUntil);
  }

  private void rejectDatesForNonPromotion(LocalDate validFrom, LocalDate validUntil) {
    if (validFrom != null || validUntil != null) {
      throw new InvalidRequestException();
    }
  }

  private void validateRequiredPromotionDates(
      LocalDate validFrom, LocalDate validUntil, boolean required) {
    if (required && (validFrom == null || validUntil == null)) {
      throw new InvalidRequestException();
    }
  }

  private void validatePromotionDatePair(LocalDate validFrom, LocalDate validUntil) {
    if ((validFrom == null) != (validUntil == null)
        || (validFrom != null && validUntil.isBefore(validFrom))) {
      throw new InvalidRequestException();
    }
  }

  private void validateFinalDates(
      String actionType, String status, UpdateActionStatusRequest request) {
    boolean hasFinalDates =
        request.finalPromotionValidFrom() != null || request.finalPromotionValidUntil() != null;
    if (hasFinalDates && (!APPROVED.equals(status) || !PROMOTION.equals(actionType))) {
      throw new InvalidRequestException();
    }
    if (hasFinalDates) {
      validatePromotionDates(
          actionType, request.finalPromotionValidFrom(), request.finalPromotionValidUntil(), true);
    }
  }

  private record ActionRule(String actionType, String justification) {}
}
