package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.UpdateProfileRequest;
import com.quistock.ds_backend.model.dto.UserDTO;
import com.quistock.ds_backend.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Perfil")
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
@RequestMapping("/profile")
public class ProfileController {
  private final UserManagementService service;

  public ProfileController(UserManagementService service) {
    this.service = service;
  }

  @PatchMapping
  @Operation(
      summary = "Editar meu perfil",
      description =
          "Altera somente nome e URL HTTPS da foto. URL vazia remove a foto; campos omitidos permanecem iguais.")
  @ApiResponse(responseCode = "200", description = "Perfil atualizado.")
  @ApiResponse(responseCode = "400", description = "Campos inválidos ou não permitidos.")
  public UserDTO updateProfile(
      @Valid @RequestBody UpdateProfileRequest request, @AuthenticationPrincipal Jwt jwt) {
    return service.updateProfile(Long.parseLong(jwt.getSubject()), request);
  }

  @GetMapping
  @Operation(
      summary = "Consultar meu perfil",
      description = "Consulta a conta SQL ativa identificada pelo sub do JWT.")
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  public UserDTO getProfile(@AuthenticationPrincipal Jwt jwt) {
    return service.profile(Long.parseLong(jwt.getSubject()));
  }
}
