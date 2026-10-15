package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.CreateManagerRequest;
import com.quistock.ds_backend.model.dto.UpdateManagerRequest;
import com.quistock.ds_backend.model.dto.UserDTO;
import com.quistock.ds_backend.service.UserManagementService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Gerentes")
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
@RequestMapping("/managers")
public class ManagerController {
  private final UserManagementService service;

  public ManagerController(UserManagementService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Listar gerentes",
      description = "Exige ADMIN. Inclui contas ativas e inativas.")
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  public List<UserDTO> listManagers(@AuthenticationPrincipal Jwt jwt) {
    return service.listManagers(actorId(jwt));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Cadastrar gerente",
      description = "Exige ADMIN. Armazena senha inicial como BCrypt e define GERENTE.")
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
      responseCode = "409",
      description = "Conflito de email, atribuição, estado ou conversa.",
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
  public UserDTO createManager(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateManagerRequest request) {
    return service.createManager(actorId(jwt), request);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Consultar gerente",
      description = "Exige ADMIN.",
      parameters = {@Parameter(name = "id", description = "ID SQL do gerente.")})
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  @ApiResponse(
      responseCode = "404",
      description = "Recurso não encontrado ou fora do escopo permitido.",
      content =
          @Content(
              schema = @Schema(implementation = com.quistock.ds_backend.model.dto.ErrorDTO.class)))
  public UserDTO getManager(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    return service.getManager(actorId(jwt), id);
  }

  @PatchMapping("/{id}")
  @Operation(
      summary = "Atualizar gerente",
      description =
          "Exige ADMIN. Desativação com subordinados exige replacement_manager_id ativo; transfere"
              + " a equipe na mesma transação.",
      parameters = {@Parameter(name = "id", description = "ID SQL do gerente.")})
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
  @Parameter(
      name = "X-XSRF-TOKEN",
      in = ParameterIn.HEADER,
      schema = @Schema(type = "string"),
      description =
          "Token de GET /csrf; exigido apenas com access_token em cookie. Preserve também"
              + " XSRF-TOKEN.")
  public UserDTO updateManager(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long id,
      @Valid @RequestBody UpdateManagerRequest request) {
    return service.updateManager(actorId(jwt), id, request);
  }

  private long actorId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
