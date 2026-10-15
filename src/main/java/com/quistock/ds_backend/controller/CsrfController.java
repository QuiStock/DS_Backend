package com.quistock.ds_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CSRF")
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
public class CsrfController {
  @GetMapping("/csrf")
  @Operation(
      summary = "Obter token CSRF da Core",
      description =
          "Exige access JWT. Preserve XSRF-TOKEN e envie o token do JSON em X-XSRF-TOKEN nas"
              + " escritas autenticadas por cookie. Bearer não exige CSRF.")
  @ApiResponse(responseCode = "200", description = "Operação concluída.")
  public ResponseEntity<CsrfResponse> token(
      @io.swagger.v3.oas.annotations.Parameter(hidden = true) CsrfToken csrf) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new CsrfResponse(csrf.getHeaderName(), csrf.getToken()));
  }

  public record CsrfResponse(String headerName, String token) {}
}
