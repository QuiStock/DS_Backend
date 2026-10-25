package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamMemberRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Email @Size(max = 255) String email,
    @Schema(
            format = "password",
            accessMode = Schema.AccessMode.WRITE_ONLY,
            description = "Senha inicial, armazenada como BCrypt; máximo de 72 bytes UTF-8.")
        @NotBlank
        @Size(min = 8, max = 72)
        String password,
    @Schema(allowableValues = {"EMPLOYEE"}) @NotBlank String role,
    @Schema(description = "ID SQL da filial do gerente autenticado; obrigatório.")
        @JsonProperty("store_id")
        Long storeId,
    @Schema(
            description =
                "Não envie este campo; gerentes cadastram apenas funcionários da própria filial.")
        @JsonProperty("region_id")
        Long regionId) {}
