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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ActionService {
  private static final String GENERATED_STATUS = "GENERATED";
  private static final Set<String> VALID_STATUSES =
      Set.of("GENERATED", "IN_EMPLOYEE_TRIAGE", "SENT_TO_MANAGER", "APPROVED", "REJECTED");
  private static final Set<String> VALID_ACTION_TYPES = Set.of("ORDER", "PROMOTION");

  private final FlowService flowService;
  private final ActionRepository actionRepository;
  private final AtomicLong nextId = new AtomicLong(500);
  private final List<StoredAction> actions = new CopyOnWriteArrayList<>();

  @Autowired
  public ActionService(FlowService service, ActionRepository repository) {
    this.flowService = service;
    this.actionRepository = repository;
  }

  // Kept for the existing service-level tests.
  public ActionService(FlowService service) {
    this.flowService = service;
    this.actionRepository = null;
  }

  public GenerateActionsResponse generateActions(String flowId) {
    return generateActions(flowId, null, null);
  }

  public GenerateActionsResponse generateActions(
      String flowId, LocalDate promotionValidFrom, LocalDate promotionValidUntil) {
    FlowDTO flow = flowService.findFlowById(flowId);
    String actionType = actionType(flow.flowType());
    if (actionType == null) {
      if (promotionValidFrom != null || promotionValidUntil != null) {
        throw new InvalidRequestException();
      }
      return new GenerateActionsResponse(flow.id(), List.of());
    }
    validatePromotionDates(actionType, promotionValidFrom, promotionValidUntil, true);

    if (actionRepository != null) {
      ActionDTO action =
          actionRepository.createGenerated(
              flow,
              actionType,
              justification(flow.flowType()),
              promotionValidFrom,
              promotionValidUntil);
      return new GenerateActionsResponse(flow.id(), List.of(action));
    }

    ActionDTO action =
        new ActionDTO(
            String.valueOf(nextId.incrementAndGet()),
            actionType,
            GENERATED_STATUS,
            justification(flow.flowType()),
            promotionValidFrom,
            promotionValidUntil);
    actions.add(new StoredAction(flow.id(), flow.productName(), action));
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

    if (actionRepository != null) {
      return actionRepository.findAll(status, actionType, flowId);
    }

    return actions.stream()
        .filter(stored -> status == null || status.equals(stored.action().status()))
        .filter(stored -> actionType == null || actionType.equals(stored.action().actionType()))
        .filter(stored -> flowId == null || flowId.equals(stored.flowId()))
        .map(this::toListItem)
        .toList();
  }

  public ActionStatusResponse updateActionStatus(String actionId, String status) {
    return updateActionStatus(actionId, new UpdateActionStatusRequest(status));
  }

  public ActionStatusResponse updateActionStatus(
      String actionId, UpdateActionStatusRequest request) {
    String status = request.status();
    if (!VALID_STATUSES.contains(status)) {
      throw new InvalidActionStatusException(status);
    }
    if ("REJECTED".equals(status) && isBlank(request.justification())) {
      throw new InvalidRequestException();
    }

    if (actionRepository != null) {
      String actionType = actionRepository.findTypeById(actionId);
      if (actionType == null) {
        throw new ActionNotFoundException(actionId);
      }
      validateFinalDates(actionType, status, request);
      return actionRepository.updateStatus(actionId, request);
    }

    for (int index = 0; index < actions.size(); index++) {
      StoredAction stored = actions.get(index);
      if (actionId != null && actionId.equals(stored.action().id())) {
        ActionDTO current = stored.action();
        LocalDate validFrom = current.promotionValidFrom();
        LocalDate validUntil = current.promotionValidUntil();
        boolean hasFinalValidity =
            request.finalPromotionValidFrom() != null || request.finalPromotionValidUntil() != null;
        if (hasFinalValidity) {
          if (!"APPROVED".equals(status) || !"PROMOTION".equals(current.actionType())) {
            throw new InvalidRequestException();
          }
          validatePromotionDates(
              current.actionType(),
              request.finalPromotionValidFrom(),
              request.finalPromotionValidUntil(),
              true);
          validFrom = request.finalPromotionValidFrom();
          validUntil = request.finalPromotionValidUntil();
        }

        ActionDTO updated =
            new ActionDTO(
                current.id(),
                current.actionType(),
                status,
                current.justification(),
                validFrom,
                validUntil);
        String decisionJustification =
            "REJECTED".equals(status) ? request.justification() : stored.decisionJustification();
        actions.set(
            index,
            new StoredAction(stored.flowId(), stored.productName(), updated, decisionJustification));
        return new ActionStatusResponse(
            updated.id(), updated.status(), updated.promotionValidFrom(), updated.promotionValidUntil());
      }
    }
    throw new ActionNotFoundException(actionId);
  }

  private ActionListItemDTO toListItem(StoredAction stored) {
    ActionDTO action = stored.action();
    return new ActionListItemDTO(
        action.id(),
        stored.flowId(),
        stored.productName(),
        action.actionType(),
        action.status(),
        action.justification(),
        action.promotionValidFrom(),
        action.promotionValidUntil(),
        stored.decisionJustification());
  }

  private String actionType(String flowType) {
    return switch (flowType) {
      case "HIGH" -> "ORDER";
      case "MEDIUM" -> null;
      case "LOW" -> "PROMOTION";
      default -> throw new IllegalArgumentException("Unsupported flow type: " + flowType);
    };
  }

  private String justification(String flowType) {
    return switch (flowType) {
      case "HIGH" -> "Stockout risk: replenish stock.";
      case "LOW" -> "Expiration or excess-stock risk: create a promotion.";
      default -> throw new IllegalArgumentException("Unsupported flow type: " + flowType);
    };
  }

  private void validatePromotionDates(
      String actionType, LocalDate validFrom, LocalDate validUntil, boolean required) {
    if (!"PROMOTION".equals(actionType)) {
      if (validFrom != null || validUntil != null) {
        throw new InvalidRequestException();
      }
      return;
    }

    if (required && (validFrom == null || validUntil == null)) {
      throw new InvalidRequestException();
    }
    if ((validFrom == null) != (validUntil == null)
        || (validFrom != null && validUntil.isBefore(validFrom))) {
      throw new InvalidRequestException();
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private void validateFinalDates(
      String actionType, String status, UpdateActionStatusRequest request) {
    boolean hasFinalValidity =
        request.finalPromotionValidFrom() != null || request.finalPromotionValidUntil() != null;
    if (hasFinalValidity && (!"APPROVED".equals(status) || !"PROMOTION".equals(actionType))) {
      throw new InvalidRequestException();
    }
    if (hasFinalValidity) {
      validatePromotionDates(
          actionType,
          request.finalPromotionValidFrom(),
          request.finalPromotionValidUntil(),
          true);
    }
  }

  private record StoredAction(
      String flowId, String productName, ActionDTO action, String decisionJustification) {
    private StoredAction(String flowId, String productName, ActionDTO action) {
      this(flowId, productName, action, null);
    }
  }
}
