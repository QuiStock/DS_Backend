package com.quistock.ds_backend.service;

import com.quistock.ds_backend.repository.UserAccountReadRepository;
import com.quistock.ds_backend.repository.UserAccountReadRepository.Actor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class UserAccessGuard {
  private final UserAccountReadRepository readRepository;

  UserAccessGuard(UserAccountReadRepository readRepository) {
    this.readRepository = readRepository;
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
        .findUser(actorId)
        .map(com.quistock.ds_backend.model.dto.UserDTO::storeId)
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
