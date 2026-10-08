package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ConflictException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.exception.UserNotFoundException;
import com.quistock.ds_backend.model.dto.CreateTeamMemberRequest;
import com.quistock.ds_backend.model.dto.UpdateTeamMemberRequest;
import com.quistock.ds_backend.model.dto.UserDTO;
import com.quistock.ds_backend.repository.NewUserAccount;
import com.quistock.ds_backend.repository.UserAccountReadRepository;
import com.quistock.ds_backend.repository.UserAccountWriteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TeamMemberManagementService {
  private final UserAccountReadRepository readRepository;
  private final UserAccountWriteRepository writeRepository;
  private final UserAccessGuard accessGuard;
  private final UserInputNormalizer normalizer;
  private final TeamMemberAssignmentService assignmentService;
  private final PasswordEncoder passwordEncoder;

  TeamMemberManagementService(
      UserAccountReadRepository readRepository,
      UserAccountWriteRepository writeRepository,
      UserAccessGuard accessGuard,
      UserInputNormalizer normalizer,
      TeamMemberAssignmentService assignmentService,
      PasswordEncoder passwordEncoder) {
    this.readRepository = readRepository;
    this.writeRepository = writeRepository;
    this.accessGuard = accessGuard;
    this.normalizer = normalizer;
    this.assignmentService = assignmentService;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public java.util.List<UserDTO> listTeamMembers(long actorId) {
    accessGuard.requireRole(actorId, UserManagementRules.MANAGER);
    return readRepository.findTeamMembers(actorId);
  }

  @Transactional
  public UserDTO createTeamMember(long actorId, CreateTeamMemberRequest request) {
    accessGuard.requireRole(actorId, UserManagementRules.MANAGER);
    String role = normalizer.teamRole(request.role());
    assignmentService.validateCreate(role, request.storeId(), request.regionId());
    String email = normalizer.email(request.email());
    try {
      long id =
          writeRepository.createUser(
              new NewUserAccount(
                  role,
                  request.name().trim(),
                  email,
                  passwordEncoder.encode(request.password()),
                  actorId));
      assignmentService.assign(id, role, request.storeId(), request.regionId());
      writeRepository.insertAuditEvent(actorId, id, "CREATE", role, email);
      return readRepository.findTeamMember(id, actorId).orElseThrow(UserNotFoundException::new);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException(exception);
    }
  }

  @Transactional(readOnly = true)
  public UserDTO getTeamMember(long actorId, long userId) {
    accessGuard.requireRole(actorId, UserManagementRules.MANAGER);
    return readRepository.findTeamMember(userId, actorId).orElseThrow(UserNotFoundException::new);
  }

  @Transactional
  public UserDTO updateTeamMember(long actorId, long userId, UpdateTeamMemberRequest request) {
    accessGuard.requireRole(actorId, UserManagementRules.MANAGER);
    UserDTO member =
        readRepository.findTeamMember(userId, actorId).orElseThrow(UserNotFoundException::new);
    TeamMemberChanges changes = changes(request);
    validatePatch(member, request, changes);
    if (!hasEffectiveChanges(member, changes)) {
      return member;
    }
    try {
      assignmentService.applyUpdate(userId, member, changes);
      writeRepository.updateUser(userId, changes.name(), changes.email(), changes.status());
      writeRepository.insertAuditEvent(
          actorId,
          userId,
          "UPDATE",
          member.role(),
          changes.email() == null ? member.email() : changes.email());
      return readRepository.findTeamMember(userId, actorId).orElseThrow(UserNotFoundException::new);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException(exception);
    }
  }

  private TeamMemberChanges changes(UpdateTeamMemberRequest request) {
    if (request.role() != null) {
      throw new InvalidRequestException();
    }
    String email = request.email() == null ? null : normalizer.email(request.email());
    return new TeamMemberChanges(
        normalizer.optional(request.name()),
        email,
        request.status() == null ? null : normalizer.status(request.status()),
        request.storeId(),
        request.regionId(),
        request.storeId() != null || request.regionId() != null);
  }

  private void validatePatch(
      UserDTO member, UpdateTeamMemberRequest request, TeamMemberChanges changes) {
    if (changes.name() == null
        && changes.email() == null
        && changes.status() == null
        && !changes.assignmentRequested()) {
      throw new InvalidRequestException();
    }
    assignmentService.validatePatchRole(member.role(), request);
    if (UserManagementRules.INACTIVE.equals(changes.status()) && changes.assignmentRequested()) {
      throw new InvalidRequestException();
    }
  }

  private boolean hasEffectiveChanges(UserDTO member, TeamMemberChanges changes) {
    boolean sameStatus = changes.status() == null || changes.status().equals(member.status());
    boolean sameName = changes.name() == null || changes.name().equals(member.name());
    boolean sameEmail = changes.email() == null || changes.email().equals(member.email());
    boolean sameStore = changes.storeId() == null || changes.storeId().equals(member.storeId());
    boolean sameRegion = changes.regionId() == null || changes.regionId().equals(member.regionId());
    return !(sameStatus && sameName && sameEmail && sameStore && sameRegion);
  }
}
