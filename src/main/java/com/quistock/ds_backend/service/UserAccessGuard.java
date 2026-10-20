package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.repository.BranchRepository;
import com.quistock.ds_backend.repository.StoreScope;
import com.quistock.ds_backend.repository.UserAccountReadRepository;
import com.quistock.ds_backend.repository.UserAccountReadRepository.Actor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class UserAccessGuard {
  private final UserAccountReadRepository readRepository;
  private final BranchRepository branchRepository;

  public UserAccessGuard(
      UserAccountReadRepository readRepository, BranchRepository branchRepository) {
    this.readRepository = readRepository;
    this.branchRepository = branchRepository;
  }

  public StoreScope requireStoreScope(long actorId, Long storeId) {
    StoreScope scope = scopeForActor(requireActiveActor(actorId));
    if (storeId == null) {
      return scope;
    }
    if (storeId <= 0) {
      throw new InvalidRequestException();
    }
    if (!branchRepository.isActiveInScope(storeId, scope)) {
      throw new AccessDeniedException("The store is inactive or outside the account's scope.");
    }
    return new StoreScope(actorId, storeId, scope.regionId());
  }

  private StoreScope scopeForActor(Actor actor) {
    return switch (actor.role()) {
      case UserManagementRules.ADMIN -> new StoreScope(actor.id(), null, null);
      case UserManagementRules.REGIONAL_MANAGER ->
          new StoreScope(actor.id(), null, requireManagerManagement(actor.id()));
      case UserManagementRules.MANAGER, UserManagementRules.EMPLOYEE ->
          new StoreScope(
              actor.id(),
              readRepository
                  .findActiveStoreAssignment(actor.id())
                  .orElseThrow(
                      () ->
                          new AccessDeniedException("The account has no active store assignment.")),
              null);
      default -> throw new AccessDeniedException("This role cannot access store data.");
    };
  }

  Actor requireRole(long actorId, String requiredRole) {
    Actor actor = requireActiveActor(actorId);
    if (!requiredRole.equals(actor.role())) {
      throw new AccessDeniedException("This role cannot perform the requested operation.");
    }
    return actor;
  }

  Actor requireManagementRole(long actorId) {
    Actor actor = requireActiveActor(actorId);
    if (!UserManagementRules.ADMIN.equals(actor.role())
        && !UserManagementRules.MANAGER.equals(actor.role())
        && !UserManagementRules.REGIONAL_MANAGER.equals(actor.role())) {
      throw new AccessDeniedException("This role cannot list regions.");
    }
    return actor;
  }

  Actor requireRoleOrActive(long actorId) {
    return requireActiveActor(actorId);
  }

  Long requireManagerManagement(long actorId) {
    Actor actor = requireActiveActor(actorId);
    if (UserManagementRules.ADMIN.equals(actor.role())) {
      return null;
    }
    if (!UserManagementRules.REGIONAL_MANAGER.equals(actor.role())) {
      throw new AccessDeniedException(
          "Only administrators and regional managers can manage managers.");
    }
    return readRepository
        .findActiveRegionalAssignment(actorId)
        .orElseThrow(() -> new AccessDeniedException("The regional manager has no active region."));
  }

  long requireManagerStore(long actorId) {
    requireRole(actorId, UserManagementRules.MANAGER);
    return readRepository
        .findActiveStoreAssignment(actorId)
        .orElseThrow(
            () -> new AccessDeniedException("The manager has no active store assignment."));
  }

  void requireTeamStore(long actorId, long storeId) {
    if (requireManagerStore(actorId) != storeId) {
      throw new AccessDeniedException("The store is outside the manager's scope.");
    }
  }

  private Actor requireActiveActor(long actorId) {
    Actor actor =
        readRepository
            .findActor(actorId)
            .orElseThrow(() -> new AccessDeniedException("The account is not registered."));
    if (!UserManagementRules.ACTIVE.equals(actor.status())) {
      throw new AccessDeniedException("The account is inactive.");
    }
    return actor;
  }
}
