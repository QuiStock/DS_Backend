package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.DashboardSummaryDTO;
import com.quistock.ds_backend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    description = "Permissão insuficiente ou CSRF inválido em autenticação por cookie.",
    content = @Content)
@RestController
@RequestMapping("/dashboard")
public class DashboardController {
  private final DashboardService dashboardService;

  public DashboardController(DashboardService service) {
    this.dashboardService = service;
  }

  @GetMapping("/summary")
  @Operation(
      summary = "Consultar resumo do dashboard",
      description = "Retorna indicadores operacionais calculados sobre dados persistidos.")
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  public DashboardSummaryDTO getSummary() {
    return dashboardService.getSummary();
  }
}
