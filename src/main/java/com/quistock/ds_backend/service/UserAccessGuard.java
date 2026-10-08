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

  Actor requireManagerOrAdmin(long actorId) {
    Actor actor = requireActiveActor(actorId);
    if (!UserManagementRules.ADMIN.equals(actor.role())
        && !UserManagementRules.MANAGER.equals(actor.role())) {
      throw new AccessDeniedException("This role cannot list regions.");
    }
    return actor;
  }

  Actor requireRoleOrActive(long actorId) {
    return requireActiveActor(actorId);
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
