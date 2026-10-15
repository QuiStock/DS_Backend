package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.AnalyzeProductRequest;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.service.FlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Análises")
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
@RequestMapping("/flows")
public class FlowController {
  private final FlowService flowService;

  public FlowController(FlowService service) {
    this.flowService = service;
  }

  @PostMapping("/analyze")
  @Operation(
      summary = "Analisar produto",
      description = "Gera análise operacional do estoque do produto persistido.")
  @ApiResponse(responseCode = "201", description = "Recurso criado.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Parâmetros ou corpo inválidos. Credenciais de autenticação ambíguas também retornam"
              + " 400.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Recurso não encontrado ou fora do escopo permitido.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  @Parameter(
      name = "X-XSRF-TOKEN",
      in = ParameterIn.HEADER,
      schema = @Schema(type = "string"),
      description =
          "Token de GET /csrf; exigido apenas com access_token em cookie. Preserve também"
              + " XSRF-TOKEN.")
  public ResponseEntity<FlowDTO> analyzeProduct(@Valid @RequestBody AnalyzeProductRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(flowService.analyzeProduct(request.productId()));
  }

  @GetMapping
  @Operation(
      summary = "Listar análises",
      description = "Filtra análises por tipo, produto e status.",
      parameters = {
        @Parameter(name = "flow_type", description = "HIGH, MEDIUM ou LOW."),
        @Parameter(name = "product_id", description = "ID composto do produto e filial."),
        @Parameter(name = "status", description = "ANALYZED (único status atual).")
      })
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Parâmetros ou corpo inválidos. Credenciais de autenticação ambíguas também retornam"
              + " 400.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  public List<FlowDTO> listFlows(
      @RequestParam(name = "flow_type", required = false) String flowType,
      @RequestParam(name = "product_id", required = false) String productId,
      @RequestParam(name = "status", required = false) String status) {
    return flowService.listFlows(flowType, productId, status);
  }
}
