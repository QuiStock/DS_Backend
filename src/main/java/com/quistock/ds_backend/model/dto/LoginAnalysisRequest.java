package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LoginAnalysisRequest(
    @Schema(
            description =
                "UUID novo por login bem-sucedido, reutilizado em retries. Não enviar no refresh.")
        @JsonProperty("login_id")
        @NotNull
        UUID loginId) {}
