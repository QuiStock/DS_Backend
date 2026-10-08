package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.CreateManagerRequest;
import com.quistock.ds_backend.model.dto.UpdateManagerRequest;
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
@RequestMapping("/managers")
public class ManagerController {
  private final UserManagementService service;

  public ManagerController(UserManagementService service) {
    this.service = service;
  }

  @GetMapping
  public List<UserDTO> listManagers(@AuthenticationPrincipal Jwt jwt) {
    return service.listManagers(actorId(jwt));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserDTO createManager(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateManagerRequest request) {
    return service.createManager(actorId(jwt), request);
  }

  @GetMapping("/{id}")
  public UserDTO getManager(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    return service.getManager(actorId(jwt), id);
  }

  @PatchMapping("/{id}")
  public UserDTO updateManager(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long id,
      @Valid @RequestBody UpdateManagerRequest request) {
    return service.updateManager(actorId(jwt), id, request);
  }

  private long actorId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
