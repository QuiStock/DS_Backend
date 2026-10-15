package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.ActionStatusResponse;
import com.quistock.ds_backend.model.dto.GenerateActionsRequest;
import com.quistock.ds_backend.model.dto.GenerateActionsResponse;
import com.quistock.ds_backend.model.dto.UpdateActionStatusRequest;
import com.quistock.ds_backend.service.ActionService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ações")
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
@RequestMapping("/actions")
public class ActionController {
  private final ActionService actionService;

  public ActionController(ActionService service) {
    this.actionService = service;
  }

  @PostMapping("/generate")
  @Operation(
      summary = "Gerar ações para uma análise",
      description = "Cria sugestões de pedido ou promoção e registra o autor pelo sub do JWT.")
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
  public ResponseEntity<GenerateActionsResponse> generateActions(
      @Valid @RequestBody GenerateActionsRequest request, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            actionService.generateActions(
                request.flowId(),
                request.promotionValidFrom(),
                request.promotionValidUntil(),
                authenticatedUserId(jwt)));
  }

  @GetMapping
  @Operation(
      summary = "Listar ações",
      description = "Filtra sugestões pelo status, tipo e análise.",
      parameters = {
        @Parameter(
            name = "status",
            description = "GENERATED, IN_EMPLOYEE_TRIAGE, SENT_TO_MANAGER, APPROVED ou REJECTED."),
        @Parameter(name = "action_type", description = "ORDER ou PROMOTION."),
        @Parameter(name = "flow_id", description = "ID da análise que originou as sugestões.")
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
  public List<ActionListItemDTO> listActions(
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "action_type", required = false) String actionType,
      @RequestParam(name = "flow_id", required = false) String flowId) {
    return actionService.listActions(status, actionType, flowId);
  }

  @PatchMapping("/{id}/status")
  @Operation(
      summary = "Atualizar status da ação",
      description =
          "REJECTED exige justification. Datas finais só são aceitas para uma promoção aprovada.",
      parameters = {@Parameter(name = "id", description = "ID da sugestão.")})
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
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
  public ActionStatusResponse updateActionStatus(
      @PathVariable("id") String id,
      @Valid @RequestBody UpdateActionStatusRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return actionService.updateActionStatus(id, request, authenticatedUserId(jwt));
  }

  private long authenticatedUserId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
