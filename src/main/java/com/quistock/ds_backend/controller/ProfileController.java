package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.UserDTO;
import com.quistock.ds_backend.service.UserManagementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile")
public class ProfileController {
  private final UserManagementService service;

  public ProfileController(UserManagementService service) {
    this.service = service;
  }

  @GetMapping
  public UserDTO getProfile(@AuthenticationPrincipal Jwt jwt) {
    return service.profile(Long.parseLong(jwt.getSubject()));
  }
}
