package com.quistock.ds_backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "QuiStock Core API",
            version = "2.0",
            description =
                "Operações de estoque, ações, perfis e chat. Use JWT Bearer OU o cookie"
                    + " access_token. Escritas autenticadas por cookie exigem X-XSRF-TOKEN obtido"
                    + " em GET /csrf. O sub identifica user_account.id; as permissões de usuários"
                    + " são consultadas no SQL."))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
@SecurityScheme(
    name = "accessCookie",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.COOKIE,
    paramName = "access_token",
    description = "Cookie HTTP-only emitido pela Auth. Não combinar com Bearer.")
public class OpenApiConfig {
  @Bean
  OpenApiCustomizer healthResponses() {
    return api ->
        api.getPaths()
            .forEach(
                (path, item) -> {
                  if ("/health".equals(path) || path.startsWith("/health/")) {
                    item.readOperations()
                        .forEach(
                            operation ->
                                operation
                                    .getResponses()
                                    .addApiResponse(
                                        "503",
                                        new ApiResponse()
                                            .description(
                                                "Dependência indisponível ou timeout no health"
                                                    + " check.")));
                  }
                });
  }
}
