package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ActionNotFoundException;
import com.quistock.ds_backend.exception.InvalidActionStatusException;
import com.quistock.ds_backend.model.dto.ActionDTO;
import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.ActionStatusResponse;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.GenerateActionsResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class ActionService {
  private static final String SUGGESTED_STATUS = "SUGGESTED";
  private static final Set<String> VALID_STATUSES =
      Set.of("SUGGESTED", "APPROVED", "REJECTED", "COMPLETED");

  private final FlowService flowService;
  private final AtomicLong nextId = new AtomicLong(500);
  private final List<StoredAction> actions = new CopyOnWriteArrayList<>();

  public ActionService(FlowService service) {
    this.flowService = service;
  }

  public GenerateActionsResponse generateActions(String flowId) {
    FlowDTO flow = flowService.findFlowById(flowId);
    ActionDTO action =
        new ActionDTO(
            String.valueOf(nextId.incrementAndGet()),
            actionType(flow.flowType()),
            SUGGESTED_STATUS,
            justification(flow.flowType()));
    actions.add(new StoredAction(flow.id(), flow.productName(), action));
    return new GenerateActionsResponse(flow.id(), List.of(action));
  }

  public List<ActionListItemDTO> listActions() {
    return listActions(null, null, null);
  }

  public List<ActionListItemDTO> listActions(String status, String actionType, String flowId) {
    return actions.stream()
        .filter(stored -> status == null || status.equals(stored.action().status()))
        .filter(stored -> actionType == null || actionType.equals(stored.action().actionType()))
        .filter(stored -> flowId == null || flowId.equals(stored.flowId()))
        .map(this::toListItem)
        .toList();
  }

  public ActionStatusResponse updateActionStatus(String actionId, String status) {
    if (!VALID_STATUSES.contains(status)) {
      throw new InvalidActionStatusException(status);
    }

    for (int index = 0; index < actions.size(); index++) {
      StoredAction stored = actions.get(index);
      if (actionId != null && actionId.equals(stored.action().id())) {
        ActionDTO current = stored.action();
        ActionDTO updated =
            new ActionDTO(current.id(), current.actionType(), status, current.justification());
        actions.set(index, new StoredAction(stored.flowId(), stored.productName(), updated));
        return new ActionStatusResponse(updated.id(), updated.status());
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
        action.justification());
  }

  private String actionType(String flowType) {
    return switch (flowType) {
      case "HIGH" -> "STOCK_ORDER";
      case "MEDIUM" -> "MONITOR";
      case "LOW" -> "PROMOTION";
      default -> throw new IllegalArgumentException("Unsupported flow type: " + flowType);
    };
  }

  private String justification(String flowType) {
    return switch (flowType) {
      case "HIGH" -> "Product has stockout risk and requires replenishment.";
      case "MEDIUM" -> "Product stock is adequate and should be monitored.";
      case "LOW" -> "Product has expiration or excess stock risk.";
      default -> throw new IllegalArgumentException("Unsupported flow type: " + flowType);
    };
  }

  private record StoredAction(String flowId, String productName, ActionDTO action) {}
}
