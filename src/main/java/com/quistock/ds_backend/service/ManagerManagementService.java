package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ConflictException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.exception.UserNotFoundException;
import com.quistock.ds_backend.model.dto.CreateManagerRequest;
import com.quistock.ds_backend.model.dto.UpdateManagerRequest;
import com.quistock.ds_backend.model.dto.UserDTO;
import com.quistock.ds_backend.repository.NewUserAccount;
import com.quistock.ds_backend.repository.UserAccountReadRepository;
import com.quistock.ds_backend.repository.UserAccountWriteRepository;
import com.quistock.ds_backend.repository.UserAssignmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ManagerManagementService {
  private final UserAccountReadRepository readRepository;
  private final UserAccountWriteRepository writeRepository;
  private final UserAssignmentRepository assignmentRepository;
  private final UserAccessGuard accessGuard;
  private final UserInputNormalizer normalizer;
  private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

  ManagerManagementService(
      UserAccountReadRepository readRepository,
      UserAccountWriteRepository writeRepository,
      UserAssignmentRepository assignmentRepository,
      UserAccessGuard accessGuard,
      UserInputNormalizer normalizer,
      org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
    this.readRepository = readRepository;
    this.writeRepository = writeRepository;
    this.assignmentRepository = assignmentRepository;
    this.accessGuard = accessGuard;
    this.normalizer = normalizer;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public java.util.List<UserDTO> listManagers(long actorId) {
    accessGuard.requireRole(actorId, UserManagementRules.ADMIN);
    return readRepository.findManagers();
  }

  @Transactional
  public UserDTO createManager(long actorId, CreateManagerRequest request) {
    accessGuard.requireRole(actorId, UserManagementRules.ADMIN);
    String email = normalizer.email(request.email());
    try {
      long id =
          writeRepository.createUser(
              new NewUserAccount(
                  UserManagementRules.MANAGER,
                  request.name().trim(),
                  email,
                  passwordEncoder.encode(request.password()),
                  actorId));
      writeRepository.insertAuditEvent(actorId, id, "CREATE", UserManagementRules.MANAGER, email);
      return readRepository.findManager(id).orElseThrow(UserNotFoundException::new);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException(exception);
    }
  }

  @Transactional(readOnly = true)
  public UserDTO getManager(long actorId, long managerId) {
    accessGuard.requireRole(actorId, UserManagementRules.ADMIN);
    return readRepository.findManager(managerId).orElseThrow(UserNotFoundException::new);
  }

  @Transactional
  public UserDTO updateManager(long actorId, long managerId, UpdateManagerRequest request) {
    accessGuard.requireRole(actorId, UserManagementRules.ADMIN);
    UserDTO manager = readRepository.findManager(managerId).orElseThrow(UserNotFoundException::new);
    ManagerChanges changes = changes(request);
    try {
      updateStatus(actorId, manager, managerId, request.replacementManagerId(), changes.status());
      writeRepository.updateUser(managerId, changes.name(), changes.email(), changes.status());
      writeRepository.insertAuditEvent(
          actorId,
          managerId,
          "UPDATE",
          UserManagementRules.MANAGER,
          changes.email() == null ? manager.email() : changes.email());
      return readRepository.findManager(managerId).orElseThrow(UserNotFoundException::new);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException(exception);
    }
  }

  private void updateStatus(
      long actorId, UserDTO manager, long managerId, Long replacementId, String status) {
    if (UserManagementRules.INACTIVE.equals(status)) {
      transferReportsAndCloseAssignments(actorId, manager, managerId, replacementId);
    } else if (replacementId != null) {
      throw new InvalidRequestException();
    }
  }

  private void transferReportsAndCloseAssignments(
      long actorId, UserDTO manager, long managerId, Long replacementId) {
    int directReports = writeRepository.countDirectReports(managerId);
    if (directReports > 0 && replacementId == null) {
      throw new InvalidRequestException();
    }
    if (replacementId != null) {
      transferReports(actorId, manager, managerId, replacementId);
    }
    assignmentRepository.closeStoreAssignments(managerId);
    assignmentRepository.closeRegionAssignments(managerId);
  }

  private void transferReports(long actorId, UserDTO manager, long managerId, long replacementId) {
    UserDTO replacement =
        readRepository.findManager(replacementId).orElseThrow(UserNotFoundException::new);
    if (replacementId == managerId || !UserManagementRules.ACTIVE.equals(replacement.status())) {
      throw new InvalidRequestException();
    }
    int transferred = writeRepository.transferDirectReports(managerId, replacementId);
    if (transferred > 0) {
      writeRepository.insertAuditEvent(
          actorId,
          managerId,
          "TRANSFER_REPORTS_TO_" + replacement.id(),
          UserManagementRules.MANAGER,
          manager.email());
    }
  }

  private ManagerChanges changes(UpdateManagerRequest request) {
    if (request.role() != null) {
      throw new InvalidRequestException();
    }
    String name = normalizer.optional(request.name());
    String email = request.email() == null ? null : normalizer.email(request.email());
    String status = request.status() == null ? null : normalizer.status(request.status());
    if (name == null && email == null && status == null) {
      throw new InvalidRequestException();
    }
    return new ManagerChanges(name, email, status);
  }

  private record ManagerChanges(String name, String email, String status) {}
}
