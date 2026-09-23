package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.DashboardSummaryDTO;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.ProductDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  private static final String SUGGESTED_STATUS = "SUGGESTED";
  private static final Set<String> ACTIVE_ACTION_STATUSES = Set.of("SUGGESTED", "APPROVED");

  private final FlowService flowService;
  private final ActionService actionService;
  private final ProductService productService;
  private final int shortExpiryDays;

  public DashboardService(
      FlowService flows,
      ActionService actions,
      ProductService products,
      @Value("${dashboard.short-expiry-days:30}") int expiryDays) {
    this.flowService = flows;
    this.actionService = actions;
    this.productService = products;
    this.shortExpiryDays = expiryDays;
  }

  public DashboardSummaryDTO getSummary() {
    List<FlowDTO> latestFlows = latestFlowsByProduct();
    List<ActionListItemDTO> allActions = actionService.listActions();
    List<ActionListItemDTO> suggestedActions =
        allActions.stream().filter(action -> SUGGESTED_STATUS.equals(action.status())).toList();
    List<ProductDTO> products = productService.listProducts(null, null, null);

    return new DashboardSummaryDTO(
        latestFlows.size(),
        countByFlowType(latestFlows, "HIGH"),
        countByFlowType(latestFlows, "MEDIUM"),
        countByFlowType(latestFlows, "LOW"),
        suggestedActions.size(),
        countByActionType(suggestedActions, "PROMOTION"),
        countByActionType(suggestedActions, "STOCK_ORDER"),
        products.stream().filter(this::isNearExpiration).count(),
        products.stream().filter(this::isStockout).count(),
        products.stream().filter(this::isOverstock).count(),
        allActions.stream().filter(this::isActiveAction).count());
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

  private boolean isNearExpiration(ProductDTO product) {
    return product.currentStock() != null
        && product.currentStock() > 0
        && product.expirationDays() != null
        && product.expirationDays() <= shortExpiryDays;
  }

  private boolean isStockout(ProductDTO product) {
    return product.currentStock() != null && product.currentStock() <= 0;
  }

  private boolean isOverstock(ProductDTO product) {
    return product.currentStock() != null
        && product.minimumStock() != null
        && product.currentStock() > product.minimumStock();
  }

  private boolean isActiveAction(ActionListItemDTO action) {
    return ACTIVE_ACTION_STATUSES.contains(action.status());
  }
}
