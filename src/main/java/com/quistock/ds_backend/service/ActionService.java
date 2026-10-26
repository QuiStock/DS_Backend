package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ActionNotFoundException;
import com.quistock.ds_backend.exception.ConflictException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.ActionStatusResponse;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.GenerateActionsRequest;
import com.quistock.ds_backend.model.dto.GenerateActionsResponse;
import com.quistock.ds_backend.model.dto.UpdateActionStatusRequest;
import com.quistock.ds_backend.repository.ActionRepository;
import com.quistock.ds_backend.repository.StoreScope;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ActionService {
  private static final int SINGLE_TYPE = 1;
  private static final String ORDER = "ORDER";
  private static final String PROMOTION = "PROMOTION";
  private final FlowService flowService;
  private final ActionRepository actionRepository;
  private final UserAccessGuard accessGuard;
  private final ActionCreationValidator creationValidator;

  public ActionService(
      FlowService flowService,
      ActionRepository actionRepository,
      UserAccessGuard accessGuard,
      ActionCreationValidator creationValidator) {
    this.flowService = flowService;
    this.actionRepository = actionRepository;
    this.accessGuard = accessGuard;
    this.creationValidator = creationValidator;
  }

  public GenerateActionsResponse generateActions(GenerateActionsRequest request, StoreScope scope) {
    accessGuard.requireRole(scope.actorId(), UserManagementRules.EMPLOYEE);
    FlowDTO flow = flowService.findFlowById(request.flowId(), scope);
    if (!flow.current() || !"ANALYZED".equals(flow.status())) {
      throw new ConflictException();
    }
    String type = resolveType(request.actionType(), flow);
    if (type == null) {
      requireEmptyActionFields(request);
      return new GenerateActionsResponse(flow.id(), List.of());
    }
    var details = creationValidator.validate(type, request);
    var action = actionRepository.createGenerated(flow, details, scope);
    return new GenerateActionsResponse(flow.id(), List.of(action));
  }

  private void requireEmptyActionFields(GenerateActionsRequest request) {
    if (request.quantity() != null
        || request.desiredOrderDate() != null
        || request.discountPercentage() != null
        || request.promotionDurationDays() != null
        || request.promotionValidFrom() != null
        || request.promotionValidUntil() != null) {
      throw new InvalidRequestException();
    }
  }

  private String resolveType(String requested, FlowDTO flow) {
    List<String> compatible = new ArrayList<>();
    if (flow.risks().contains("STOCKOUT")) {
      compatible.add(ORDER);
    }
    if (promotionRisk(flow)) {
      compatible.add(PROMOTION);
    }
    if (requested != null) {
      if (!compatible.contains(requested)) {
        throw new InvalidRequestException();
      }
      return requested;
    }
    if (compatible.size() > SINGLE_TYPE) {
      throw new InvalidRequestException();
    }
    return compatible.isEmpty() ? null : compatible.get(0);
  }

  private boolean promotionRisk(FlowDTO flow) {
    return !flow.risks().contains("EXPIRED")
        && (flow.risks().contains("EXPIRATION") || flow.risks().contains("NEAR_EXPIRY"));
  }

  public List<ActionListItemDTO> listActions(StoreScope scope) {
    return listActions(null, null, null, scope);
  }

  public List<ActionListItemDTO> listActions(
      String status, String type, String flowId, StoreScope scope) {
    ActionRequestValidator.validateFilters(status, type);
    return actionRepository.findAll(status, type, flowId, scope);
  }

  public ActionStatusResponse updateActionStatus(
      String id, UpdateActionStatusRequest request, StoreScope scope) {
    ActionRequestValidator.validateStatus(request);
    String requiredRole =
        switch (request.status()) {
          case "IN_EMPLOYEE_TRIAGE", "SENT_TO_MANAGER" -> UserManagementRules.EMPLOYEE;
          case "APPROVED", "REJECTED" -> UserManagementRules.MANAGER;
          default -> throw new InvalidRequestException();
        };
    accessGuard.requireRole(scope.actorId(), requiredRole);
    String type = actionRepository.findTypeById(id, scope);
    if (type == null) {
      throw new ActionNotFoundException(id);
    }
    ActionRequestValidator.validateFinalDates(type, request.status(), request);
    return actionRepository.updateStatus(id, request, scope);
  }
}
