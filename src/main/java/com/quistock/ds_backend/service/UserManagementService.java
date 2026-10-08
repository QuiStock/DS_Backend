package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.CreateManagerRequest;
import com.quistock.ds_backend.model.dto.CreateTeamMemberRequest;
import com.quistock.ds_backend.model.dto.RegionDTO;
import com.quistock.ds_backend.model.dto.UpdateManagerRequest;
import com.quistock.ds_backend.model.dto.UpdateTeamMemberRequest;
import com.quistock.ds_backend.model.dto.UserDTO;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserManagementService {
  private final ManagerManagementService managerService;
  private final TeamMemberManagementService teamMemberService;
  private final UserProfileService profileService;

  public UserManagementService(
      ManagerManagementService managerService,
      TeamMemberManagementService teamMemberService,
      UserProfileService profileService) {
    this.managerService = managerService;
    this.teamMemberService = teamMemberService;
    this.profileService = profileService;
  }

  public UserDTO profile(long actorId) {
    return profileService.profile(actorId);
  }

  public List<UserDTO> listManagers(long actorId) {
    return managerService.listManagers(actorId);
  }

  public UserDTO createManager(long actorId, CreateManagerRequest request) {
    return managerService.createManager(actorId, request);
  }

  public UserDTO getManager(long actorId, long managerId) {
    return managerService.getManager(actorId, managerId);
  }

  public UserDTO updateManager(long actorId, long managerId, UpdateManagerRequest request) {
    return managerService.updateManager(actorId, managerId, request);
  }

  public List<UserDTO> listTeamMembers(long actorId) {
    return teamMemberService.listTeamMembers(actorId);
  }

  public UserDTO createTeamMember(long actorId, CreateTeamMemberRequest request) {
    return teamMemberService.createTeamMember(actorId, request);
  }

  public UserDTO getTeamMember(long actorId, long userId) {
    return teamMemberService.getTeamMember(actorId, userId);
  }

  public UserDTO updateTeamMember(long actorId, long userId, UpdateTeamMemberRequest request) {
    return teamMemberService.updateTeamMember(actorId, userId, request);
  }

  public List<RegionDTO> listRegions(long actorId) {
    return profileService.listRegions(actorId);
  }
}
