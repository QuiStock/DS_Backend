package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.AssignmentTargetNotFoundException;
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
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ManagerManagementService {
  private final UserAccountReadRepository readRepository;
  private final UserAccountWriteRepository writeRepository;
  private final UserAssignmentRepository assignmentRepository;
  private final UserAccessGuard accessGuard;
  private final UserInputNormalizer normalizer;
  private final PasswordEncoder passwordEncoder;

  ManagerManagementService(
      UserAccountReadRepository readRepository,
      UserAccountWriteRepository writeRepository,
      UserAssignmentRepository assignmentRepository,
      UserAccessGuard accessGuard,
      UserInputNormalizer normalizer,
      PasswordEncoder passwordEncoder) {
    this.readRepository = readRepository;
    this.writeRepository = writeRepository;
    this.assignmentRepository = assignmentRepository;
    this.accessGuard = accessGuard;
    this.normalizer = normalizer;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public List<UserDTO> listManagers(long actorId, Long storeId) {
    accessGuard.requireManagerManagement(actorId);
    return readRepository.findManagers(accessGuard.requireStoreScope(actorId, storeId));
  }

  @Transactional
  public UserDTO createManager(long actorId, CreateManagerRequest request) {
    Long regionId = accessGuard.requireManagerManagement(actorId);
    validateStore(request.storeId(), regionId);
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
      assignmentRepository.insertStoreAssignment(id, request.storeId());
      writeRepository.insertAuditEvent(actorId, id, "CREATE", UserManagementRules.MANAGER, email);
      return readRepository.findManager(id).orElseThrow(UserNotFoundException::new);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException(exception);
    }
  }

  @Transactional(readOnly = true)
  public UserDTO getManager(long actorId, long managerId) {
    return scopedManager(managerId, accessGuard.requireManagerManagement(actorId));
  }

  @Transactional
  public UserDTO updateManager(long actorId, long managerId, UpdateManagerRequest request) {
    Long regionId = accessGuard.requireManagerManagement(actorId);
    Long replacementId = request.replacementManagerId();
    assignmentRepository.lockUsers(managerId, replacementId == null ? managerId : replacementId);
    UserDTO manager = scopedManager(managerId, regionId);
    ManagerChanges changes = changes(request);
    validateAssignment(manager, changes, regionId);
    try {
      updateAssignments(manager, changes, request.replacementManagerId());
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

  private UserDTO scopedManager(long managerId, Long regionId) {
    UserDTO manager = readRepository.findManager(managerId).orElseThrow(UserNotFoundException::new);
    if (regionId != null && !regionId.equals(manager.regionId())) {
      throw new UserNotFoundException();
    }
    return manager;
  }

  private void validateStore(Long storeId, Long regionId) {
    if (storeId == null) {
      throw new InvalidRequestException();
    }
    long storeRegion =
        assignmentRepository
            .findActiveStoreRegion(storeId)
            .orElseThrow(AssignmentTargetNotFoundException::new);
    if (regionId != null && regionId != storeRegion) {
      throw new AccessDeniedException("The store is outside the regional manager's region.");
    }
  }

  private void validateAssignment(UserDTO manager, ManagerChanges changes, Long regionId) {
    String resultingStatus = changes.status() == null ? manager.status() : changes.status();
    if (changes.storeId() == null) {
      if (changes.requiresStore(manager, resultingStatus)) {
        throw new InvalidRequestException();
      }
      return;
    }
    if (!UserManagementRules.ACTIVE.equals(resultingStatus)) {
      throw new InvalidRequestException();
    }
    validateStore(changes.storeId(), regionId);
  }

  private void updateAssignments(UserDTO manager, ManagerChanges changes, Long replacementId) {
    long managerId = Long.parseLong(manager.id());
    if (UserManagementRules.INACTIVE.equals(changes.status())) {
      validateReplacement(manager, replacementId);
      assignmentRepository.closeStoreAssignments(managerId);
    } else {
      if (replacementId != null) {
        throw new InvalidRequestException();
      }
      if (changes.storeId() != null
          && (!Objects.equals(changes.storeId(), manager.storeId())
              || UserManagementRules.INACTIVE.equals(manager.status()))) {
        assignmentRepository.reassignStore(managerId, changes.storeId());
      }
    }
  }

  private void validateReplacement(UserDTO manager, Long replacementId) {
    if (replacementId == null) {
      if (manager.storeId() != null
          && assignmentRepository.storeHasActiveEmployees(manager.storeId())) {
        throw new InvalidRequestException();
      }
      return;
    }
    UserDTO replacement =
        readRepository.findManager(replacementId).orElseThrow(UserNotFoundException::new);
    if (manager.id().equals(replacement.id())
        || !UserManagementRules.ACTIVE.equals(replacement.status())
        || !Objects.equals(manager.storeId(), replacement.storeId())) {
      throw new InvalidRequestException();
    }
  }

  private ManagerChanges changes(UpdateManagerRequest request) {
    if (request.role() != null) {
      throw new InvalidRequestException();
    }
    String name = normalizer.optional(request.name());
    String email = request.email() == null ? null : normalizer.email(request.email());
    String status = request.status() == null ? null : normalizer.status(request.status());
    ManagerChanges changes = new ManagerChanges(name, email, status, request.storeId());
    if (changes.isEmpty()) {
      throw new InvalidRequestException();
    }
    return changes;
  }

  private record ManagerChanges(String name, String email, String status, Long storeId) {
    boolean isEmpty() {
      return name == null && email == null && status == null && storeId == null;
    }

    boolean requiresStore(UserDTO manager, String resultingStatus) {
      return UserManagementRules.ACTIVE.equals(resultingStatus)
          && (manager.storeId() == null || UserManagementRules.INACTIVE.equals(manager.status()));
    }
  }
}
