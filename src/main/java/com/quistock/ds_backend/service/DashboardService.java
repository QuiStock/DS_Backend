package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.DashboardSummaryDTO;
import com.quistock.ds_backend.model.dto.FlowDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  private static final String SUGGESTED_STATUS = "SUGGESTED";

  private final FlowService flowService;
  private final ActionService actionService;

  public DashboardService(FlowService flows, ActionService actions) {
    this.flowService = flows;
    this.actionService = actions;
  }

  public DashboardSummaryDTO getSummary() {
    List<FlowDTO> latestFlows = latestFlowsByProduct();
    List<ActionListItemDTO> suggestedActions =
        actionService.listActions().stream()
            .filter(action -> SUGGESTED_STATUS.equals(action.status()))
            .toList();

    return new DashboardSummaryDTO(
        latestFlows.size(),
        countByFlowType(latestFlows, "HIGH"),
        countByFlowType(latestFlows, "MEDIUM"),
        countByFlowType(latestFlows, "LOW"),
        suggestedActions.size(),
        countByActionType(suggestedActions, "PROMOTION"),
        countByActionType(suggestedActions, "STOCK_ORDER"));
  }

  private List<FlowDTO> latestFlowsByProduct() {
    Map<String, FlowDTO> latestFlows =
        flowService.listFlows().stream()
            .filter(flow -> flow.productId() != null)
            .collect(
                Collectors.toMap(
                    FlowDTO::productId,
                    Function.identity(),
                    (previous, current) -> current,
                    LinkedHashMap::new));
    return latestFlows.values().stream().toList();
  }

  private int countByFlowType(List<FlowDTO> flows, String flowType) {
    return (int) flows.stream().filter(flow -> flowType.equals(flow.flowType())).count();
  }

  private int countByActionType(List<ActionListItemDTO> actions, String actionType) {
    return (int) actions.stream().filter(action -> actionType.equals(action.actionType())).count();
  }
}
