package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.RegionDTO;
import com.quistock.ds_backend.service.UserManagementService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/regions")
public class RegionController {
  private final UserManagementService service;

  public RegionController(UserManagementService service) {
    this.service = service;
  }

  @GetMapping
  public List<RegionDTO> listRegions(@AuthenticationPrincipal Jwt jwt) {
    return service.listRegions(Long.parseLong(jwt.getSubject()));
  }
}
