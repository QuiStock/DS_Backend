package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.exception.UserNotFoundException;
import com.quistock.ds_backend.model.dto.RegionDTO;
import com.quistock.ds_backend.model.dto.UpdateProfileRequest;
import com.quistock.ds_backend.model.dto.UserDTO;
import com.quistock.ds_backend.repository.RegionRepository;
import com.quistock.ds_backend.repository.UserAccountReadRepository;
import com.quistock.ds_backend.repository.UserAccountWriteRepository;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserProfileService {
  private final UserAccountReadRepository readRepository;
  private final RegionRepository regionRepository;
  private final UserAccessGuard accessGuard;
  private final UserAccountWriteRepository writeRepository;

  UserProfileService(
      UserAccountReadRepository readRepository,
      RegionRepository regionRepository,
      UserAccessGuard accessGuard,
      UserAccountWriteRepository writeRepository) {
    this.readRepository = readRepository;
    this.regionRepository = regionRepository;
    this.accessGuard = accessGuard;
    this.writeRepository = writeRepository;
  }

  @Transactional(readOnly = true)
  public UserDTO profile(long actorId) {
    var actor = accessGuard.requireRoleOrActive(actorId);
    if (UserManagementRules.EMPLOYEE.equals(actor.role())
        || UserManagementRules.MANAGER.equals(actor.role())) {
      accessGuard.requireStoreScope(actorId, null);
    }
    return readRepository.findUser(actorId).orElseThrow(UserNotFoundException::new);
  }

  @Transactional
  public UserDTO updateProfile(long actorId, UpdateProfileRequest request) {
    var actor = accessGuard.requireRoleOrActive(actorId);
    if (request.name() == null && request.profilePhotoUrl() == null
        || request.name() != null && request.name().isBlank()) {
      throw new InvalidRequestException();
    }
    String photo = request.profilePhotoUrl();
    validatePhoto(photo);
    writeRepository.updateProfile(
        actorId, request.name() == null ? null : request.name().strip(), photo);
    writeRepository.insertAuditEvent(
        actorId, actorId, "PROFILE_UPDATED", actor.role(), profile(actorId).email());
    return profile(actorId);
  }

  private void validatePhoto(String photo) {
    if (photo == null || photo.isEmpty()) {
      return;
    }
    URI uri;
    try {
      uri = URI.create(photo);
    } catch (IllegalArgumentException exception) {
      throw new InvalidRequestException(exception);
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null) {
      throw new InvalidRequestException();
    }
  }

  @Transactional(readOnly = true)
  public List<RegionDTO> listRegions(long actorId) {
    var actor = accessGuard.requireManagementRole(actorId);
    if (UserManagementRules.REGIONAL_MANAGER.equals(actor.role())) {
      String regionId = Long.toString(accessGuard.requireManagerManagement(actorId));
      return regionRepository.findActiveRegions().stream()
          .filter(region -> region.id().equals(regionId))
          .toList();
    }
    return regionRepository.findActiveRegions();
  }
}
