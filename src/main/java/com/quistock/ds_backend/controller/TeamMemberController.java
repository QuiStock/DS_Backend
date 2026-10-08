package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.CreateTeamMemberRequest;
import com.quistock.ds_backend.model.dto.UpdateTeamMemberRequest;
import com.quistock.ds_backend.model.dto.UserDTO;
import com.quistock.ds_backend.service.UserManagementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/team-members")
public class TeamMemberController {
  private final UserManagementService service;

  public TeamMemberController(UserManagementService service) {
    this.service = service;
  }

  @GetMapping
  public List<UserDTO> listTeamMembers(@AuthenticationPrincipal Jwt jwt) {
    return service.listTeamMembers(actorId(jwt));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserDTO createTeamMember(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTeamMemberRequest request) {
    return service.createTeamMember(actorId(jwt), request);
  }

  @GetMapping("/{id}")
  public UserDTO getTeamMember(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    return service.getTeamMember(actorId(jwt), id);
  }

  @PatchMapping("/{id}")
  public UserDTO updateTeamMember(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long id,
      @Valid @RequestBody UpdateTeamMemberRequest request) {
    return service.updateTeamMember(actorId(jwt), id, request);
  }

  private long actorId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
