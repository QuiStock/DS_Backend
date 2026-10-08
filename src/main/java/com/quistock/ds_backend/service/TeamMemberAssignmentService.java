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

  void validateCreate(String role, Long storeId, Long regionId) {
    validateRoleFields(role, storeId, regionId);
    validateTarget(role, storeId, regionId);
  }

  void assign(long userId, String role, Long storeId, Long regionId) {
    if (UserManagementRules.EMPLOYEE.equals(role)) {
      assignmentRepository.insertStoreAssignment(userId, storeId);
    } else {
      assignmentRepository.insertRegionAssignment(userId, regionId);
    }
  }

  void applyUpdate(long userId, UserDTO member, TeamMemberChanges changes) {
    if (UserManagementRules.INACTIVE.equals(changes.status())) {
      assignmentRepository.closeStoreAssignments(userId);
      assignmentRepository.closeRegionAssignments(userId);
      return;
    }
    if (UserManagementRules.ACTIVE.equals(changes.status())
        && !UserManagementRules.ACTIVE.equals(member.status())) {
      requireReactivationAssignment(member.role(), changes);
      validateTarget(member.role(), changes.storeId(), changes.regionId());
      assign(userId, member.role(), changes.storeId(), changes.regionId());
      return;
    }
    if (UserManagementRules.ACTIVE.equals(member.status()) && changes.assignmentRequested()) {
      validateTarget(member.role(), changes.storeId(), changes.regionId());
      reassignIfChanged(userId, member, changes);
    }
  }

  void validatePatchRole(String role, UpdateTeamMemberRequest request) {
    if (request.storeId() != null && !UserManagementRules.EMPLOYEE.equals(role)) {
      throw new InvalidRequestException();
    }
    if (request.regionId() != null && !UserManagementRules.REGIONAL_MANAGER.equals(role)) {
      throw new InvalidRequestException();
    }
  }

  private void validateRoleFields(String role, Long storeId, Long regionId) {
    boolean employeeInvalid =
        UserManagementRules.EMPLOYEE.equals(role) && (storeId == null || regionId != null);
    boolean regionalInvalid =
        UserManagementRules.REGIONAL_MANAGER.equals(role) && (regionId == null || storeId != null);
    if (employeeInvalid || regionalInvalid) {
      throw new InvalidRequestException();
    }
  }

  private void validateTarget(String role, Long storeId, Long regionId) {
    boolean storeMissing =
        UserManagementRules.EMPLOYEE.equals(role)
            && (storeId == null || !assignmentRepository.storeIsActive(storeId));
    boolean regionMissing =
        UserManagementRules.REGIONAL_MANAGER.equals(role)
            && (regionId == null || !assignmentRepository.regionIsActive(regionId));
    if (storeMissing || regionMissing) {
      throw new AssignmentTargetNotFoundException();
    }
  }

  private void requireReactivationAssignment(String role, TeamMemberChanges changes) {
    if (UserManagementRules.EMPLOYEE.equals(role) && changes.storeId() == null) {
      throw new InvalidRequestException();
    }
    if (UserManagementRules.REGIONAL_MANAGER.equals(role) && changes.regionId() == null) {
      throw new InvalidRequestException();
    }
  }

  private void reassignIfChanged(long userId, UserDTO member, TeamMemberChanges changes) {
    if (changes.storeId() != null && !changes.storeId().equals(member.storeId())) {
      assignmentRepository.reassignStore(userId, changes.storeId());
    }
    if (changes.regionId() != null && !changes.regionId().equals(member.regionId())) {
      assignmentRepository.reassignRegion(userId, changes.regionId());
    }
  }
}
