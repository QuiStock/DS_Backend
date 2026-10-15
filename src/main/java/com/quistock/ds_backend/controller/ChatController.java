package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.ChatRequest;
import com.quistock.ds_backend.model.dto.ChatResponse;
import com.quistock.ds_backend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat")
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
@RequestMapping("/chat")
public class ChatController {
  private final ChatService chatService;

  public ChatController(ChatService service) {
    this.chatService = service;
  }

  @PostMapping
  @Operation(
      summary = "Enviar mensagem à IA",
      description =
          "Cria UUID string quando conversation_id é omitido. Retome com o ID existente e"
              + " is_resuming_conversation=true. sent_at é gerado na Core; o JWT validado é"
              + " encaminhado como Bearer à IA.")
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
  @ApiResponse(
      responseCode = "409",
      description = "Conflito de email, atribuição, estado ou conversa.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  @ApiResponse(
      responseCode = "422",
      description =
          "Mensagem rejeitada pelos guardrails da IA; retorna o contrato ChatResponse com status"
              + " rejected.",
      content = @Content(schema = @Schema(implementation = ChatResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "Limite de requisições na IA excedido.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  @ApiResponse(
      responseCode = "502",
      description = "Resposta inválida ou falha interna do serviço de IA.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  @ApiResponse(
      responseCode = "503",
      description = "Dependência indisponível; no chat, também pode indicar IA não configurada.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  @ApiResponse(
      responseCode = "504",
      description = "Timeout ao aguardar o serviço de IA.",
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
  public ResponseEntity<ChatResponse> answer(
      @Valid @RequestBody ChatRequest request, @AuthenticationPrincipal Jwt jwt) {
    ResponseEntity<ChatResponse> result = chatService.answer(request, jwt.getTokenValue());
    return ResponseEntity.status(result.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(result.getBody());
  }
}
