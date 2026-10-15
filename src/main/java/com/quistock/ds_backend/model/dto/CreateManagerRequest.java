package com.quistock.ds_backend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateManagerRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Email @Size(max = 255) String email,
    @Schema(
            format = "password",
            accessMode = Schema.AccessMode.WRITE_ONLY,
            description = "Senha inicial, armazenada como BCrypt; máximo de 72 bytes UTF-8.")
        @NotBlank
        @Size(min = 8, max = 72)
        String password) {}
