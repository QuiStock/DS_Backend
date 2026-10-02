package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ActionNotFoundException;
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
import org.springframework.stereotype.Service;

@Service
public class ActionService {
  private static final String ORDER = "ORDER";
  private static final String PROMOTION = "PROMOTION";
  private static final Map<String, ActionRule> FLOW_ACTIONS =
      Map.of(
          "HIGH", new ActionRule(ORDER, "Stockout risk: replenish stock."),
          "MEDIUM", new ActionRule(null, null),
          "LOW", new ActionRule(PROMOTION, "Expiration or excess-stock risk: create a promotion."));

  private final FlowService flowService;
  private final ActionRepository actionRepository;

  public ActionService(FlowService flowService, ActionRepository actionRepository) {
    this.flowService = flowService;
    this.actionRepository = actionRepository;
  }

  public GenerateActionsResponse generateActions(
      String flowId, LocalDate promotionValidFrom, LocalDate promotionValidUntil, long actorId) {
    FlowDTO flow = flowService.findFlowById(flowId);
    ActionRule rule = FLOW_ACTIONS.get(flow.flowType());
    if (rule == null) {
      throw new IllegalArgumentException("Unsupported flow type: " + flow.flowType());
    }
    if (rule.actionType() == null) {
      ActionRequestValidator.validatePromotionDates(
          null, promotionValidFrom, promotionValidUntil, false);
      return new GenerateActionsResponse(flow.id(), List.of());
    }

    ActionRequestValidator.validatePromotionDates(
        rule.actionType(), promotionValidFrom, promotionValidUntil, true);
    ActionDTO action =
        actionRepository.createGenerated(
            flow,
            rule.actionType(),
            rule.justification(),
            promotionValidFrom,
            promotionValidUntil,
            actorId);
    return new GenerateActionsResponse(flow.id(), List.of(action));
  }

  public List<ActionListItemDTO> listActions() {
    return listActions(null, null, null);
  }

  public List<ActionListItemDTO> listActions(String status, String actionType, String flowId) {
    ActionRequestValidator.validateFilters(status, actionType);
    return actionRepository.findAll(status, actionType, flowId);
  }

  public ActionStatusResponse updateActionStatus(
      String actionId, UpdateActionStatusRequest request, long actorId) {
    ActionRequestValidator.validateStatus(request);
    String actionType = actionRepository.findTypeById(actionId);
    if (actionType == null) {
      throw new ActionNotFoundException(actionId);
    }
    ActionRequestValidator.validateFinalDates(actionType, request.status(), request);
    return actionRepository.updateStatus(actionId, request, actorId);
  }

  private record ActionRule(String actionType, String justification) {}
}
