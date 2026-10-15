package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.BranchDTO;
import com.quistock.ds_backend.service.BranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
      description = "Retorna filiais ERP e store_id interno usado nas associações de funcionários.")
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  public List<BranchDTO> listBranches() {
    return branchService.listBranches();
  }
}
