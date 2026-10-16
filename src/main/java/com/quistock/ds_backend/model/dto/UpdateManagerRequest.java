package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateManagerRequest(
    @Size(max = 150) String name,
    @Email @Size(max = 255) String email,
    @Schema(description = "Não envie este campo; qualquer role informado é rejeitado com 400.")
        String role,
    @Schema(allowableValues = {"ACTIVE", "INACTIVE"}) String status,
    @Schema(description = "Gerente ativo da mesma filial que assumirá a equipe na desativação.")
        @JsonProperty("replacement_manager_id")
        @Positive
        Long replacementManagerId,
    @Schema(description = "Nova filial ativa; obrigatória ao reativar um gerente.")
        @JsonProperty("store_id")
        @Positive
        Long storeId) {}
