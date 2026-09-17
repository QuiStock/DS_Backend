package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.ActionDTO;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.GenerateActionsResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class ActionService {
  private static final String SUGGESTED_STATUS = "SUGGESTED";

  private final FlowService flowService;
  private final AtomicLong nextId = new AtomicLong(500);
  private final List<ActionDTO> actions = new CopyOnWriteArrayList<>();

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
    actions.add(action);
    return new GenerateActionsResponse(flow.id(), List.of(action));
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
}
