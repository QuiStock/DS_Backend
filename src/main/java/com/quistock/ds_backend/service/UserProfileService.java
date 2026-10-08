package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.UserNotFoundException;
import com.quistock.ds_backend.model.dto.RegionDTO;
import com.quistock.ds_backend.model.dto.UserDTO;
import com.quistock.ds_backend.repository.RegionRepository;
import com.quistock.ds_backend.repository.UserAccountReadRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserProfileService {
  private final UserAccountReadRepository readRepository;
  private final RegionRepository regionRepository;
  private final UserAccessGuard accessGuard;

  UserProfileService(
      UserAccountReadRepository readRepository,
      RegionRepository regionRepository,
      UserAccessGuard accessGuard) {
    this.readRepository = readRepository;
    this.regionRepository = regionRepository;
    this.accessGuard = accessGuard;
  }

  @Transactional(readOnly = true)
  public UserDTO profile(long actorId) {
    accessGuard.requireRoleOrActive(actorId);
    return readRepository.findUser(actorId).orElseThrow(UserNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public List<RegionDTO> listRegions(long actorId) {
    accessGuard.requireManagerOrAdmin(actorId);
    return regionRepository.findActiveRegions();
  }
}
