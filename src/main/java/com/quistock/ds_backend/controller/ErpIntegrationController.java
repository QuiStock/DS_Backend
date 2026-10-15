package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.ErpIntegrationStatusDTO;
import com.quistock.ds_backend.service.ErpIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ERP")
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
@RequestMapping("/erp-integration")
public class ErpIntegrationController {
  private final ErpIntegrationService erpIntegrationService;

  public ErpIntegrationController(ErpIntegrationService service) {
    this.erpIntegrationService = service;
  }

  @GetMapping("/status")
  @Operation(
      summary = "Consultar integração ERP",
      description = "Retorna conectividade e estado da última sincronização ERP.")
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  @ApiResponse(
      responseCode = "503",
      description = "Dependência indisponível; no chat, também pode indicar IA não configurada.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  public ErpIntegrationStatusDTO getStatus() {
    return erpIntegrationService.getStatus();
  }
}
