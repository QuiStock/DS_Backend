package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.BranchDTO;
import com.quistock.ds_backend.service.BranchService;
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

@Tag(name = "Filiais")
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
@RequestMapping("/branches")
public class BranchController {
  private final BranchService branchService;

  public BranchController(BranchService service) {
    this.branchService = service;
  }

  @GetMapping
  @Operation(
      summary = "Listar filiais",
      description =
          "Exige conta SQL ativa. ADMIN recebe todas as filiais ativas; REGIONAL_MANAGER recebe"
              + " apenas filiais da sua região ativa. MANAGER e EMPLOYEE recebem somente sua filial"
              + " ativa; sem vínculo ativo retorna 403. Retorna id ERP, store_id e region_id"
              + " internos.")
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  public List<BranchDTO> listBranches(@AuthenticationPrincipal Jwt jwt) {
    return branchService.listBranches(Long.parseLong(jwt.getSubject()));
  }
}
