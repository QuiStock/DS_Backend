package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.RegionDTO;
import com.quistock.ds_backend.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Regiões")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "accessCookie")
@ApiResponse(
    responseCode = "401",
    description = "Access JWT ausente, expirado ou inválido.",
    content = @Content)
@ApiResponse(
    responseCode = "403",
    description = "Permissão insuficiente ou CSRF inválido em autenticação por cookie.",
    content = @Content)
@RestController
@RequestMapping("/regions")
public class RegionController {
  private final UserManagementService service;

  public RegionController(UserManagementService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Listar regiões",
      description =
          "ADMIN e GERENTE consultam regiões ERP ativas; GERENTE_REGIONAL consulta apenas"
              + " sua própria região ativa. Retorna IDs internos.")
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  public List<RegionDTO> listRegions(@AuthenticationPrincipal Jwt jwt) {
    return service.listRegions(Long.parseLong(jwt.getSubject()));
  }
}
