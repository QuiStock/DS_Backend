package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.AssignmentTargetNotFoundException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.model.dto.UpdateTeamMemberRequest;
import com.quistock.ds_backend.model.dto.UserDTO;
import com.quistock.ds_backend.repository.UserAssignmentRepository;
import org.springframework.stereotype.Component;

@Component
class TeamMemberAssignmentService {
  private final UserAssignmentRepository assignmentRepository;

  TeamMemberAssignmentService(UserAssignmentRepository assignmentRepository) {
    this.assignmentRepository = assignmentRepository;
  }

  void lockUser(long userId) {
    assignmentRepository.lockUsers(userId);
  }

  void validateCreate(String role, Long storeId, Long regionId) {
    validateRoleFields(role, storeId, regionId);
    validateTarget(storeId);
  }

  void assign(long userId, String role, Long storeId, Long regionId) {
    validateRoleFields(role, storeId, regionId);
    assignmentRepository.insertStoreAssignment(userId, storeId);
  }

  void applyUpdate(long userId, UserDTO member, TeamMemberChanges changes) {
    if (UserManagementRules.INACTIVE.equals(changes.status())) {
      assignmentRepository.closeStoreAssignments(userId);
      return;
    }
    if (UserManagementRules.ACTIVE.equals(changes.status())
        && !UserManagementRules.ACTIVE.equals(member.status())) {
      if (changes.storeId() == null) {
        throw new InvalidRequestException();
      }
      validateTarget(changes.storeId());
      assign(userId, member.role(), changes.storeId(), changes.regionId());
      return;
    }
    if (UserManagementRules.ACTIVE.equals(member.status()) && changes.assignmentRequested()) {
      validateTarget(changes.storeId());
      reassignIfChanged(userId, member, changes);
    }
  }

  void validatePatchRole(String role, UpdateTeamMemberRequest request) {
    if (request.storeId() != null && !UserManagementRules.EMPLOYEE.equals(role)) {
      throw new InvalidRequestException();
    }
    if (request.regionId() != null) {
      throw new InvalidRequestException();
    }
  }

  private void validateRoleFields(String role, Long storeId, Long regionId) {
    if (!UserManagementRules.EMPLOYEE.equals(role) || storeId == null || regionId != null) {
      throw new InvalidRequestException();
    }
  }

  private void validateTarget(Long storeId) {
    if (storeId == null || !assignmentRepository.storeIsActive(storeId)) {
      throw new AssignmentTargetNotFoundException();
    }
  }

  private void reassignIfChanged(long userId, UserDTO member, TeamMemberChanges changes) {
    if (changes.storeId() != null && !changes.storeId().equals(member.storeId())) {
      assignmentRepository.reassignStore(userId, changes.storeId());
    }
  }
}
