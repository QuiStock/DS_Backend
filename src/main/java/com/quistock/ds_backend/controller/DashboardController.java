package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.DashboardSummaryDTO;
import com.quistock.ds_backend.service.DashboardService;
import com.quistock.ds_backend.service.UserAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "accessCookie")
@ApiResponse(
    responseCode = "401",
    description = "Access JWT ausente, expirado ou inválido.",
    content = @Content)
@ApiResponse(
    responseCode = "403",
    description =
        "Permissão insuficiente, ausência de loja ativa (NO_ACTIVE_STORE) ou CSRF inválido.",
    content = @Content)
@RestController
@RequestMapping("/dashboard")
public class DashboardController {
  private final DashboardService dashboardService;
  private final UserAccessGuard accessGuard;

  public DashboardController(DashboardService service, UserAccessGuard accessGuard) {
    this.dashboardService = service;
    this.accessGuard = accessGuard;
  }

  @GetMapping("/summary")
  @Operation(
      summary = "Consultar resumo do dashboard",
      description = "Retorna indicadores operacionais calculados sobre dados persistidos.")
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  @ApiResponse(
      responseCode = "400",
      description = "store_id deve identificar uma loja por um ID SQL positivo.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  public DashboardSummaryDTO getSummary(
      @Parameter(
              description =
                  "ID SQL de uma loja ativa permitida. Omitir consolida o escopo do usuário.")
          @RequestParam(name = "store_id", required = false)
          Long storeId,
      @AuthenticationPrincipal Jwt jwt) {
    return dashboardService.getSummary(
        accessGuard.requireStoreScope(Long.parseLong(jwt.getSubject()), storeId));
  }
}
