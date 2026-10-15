package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateTeamMemberRequest(
    @Size(max = 150) String name,
    @Email @Size(max = 255) String email,
    @Schema(description = "Não envie este campo; qualquer role informado é rejeitado com 400.")
        String role,
    @Schema(allowableValues = {"ACTIVE", "INACTIVE"}) String status,
    @Schema(
            description =
                "Filial ativa do funcionário. Nova atribuição obrigatória ao reativar a conta.")
        @JsonProperty("store_id")
        Long storeId,
    @Schema(
            description =
                "Região do gerente regional. Nova atribuição obrigatória ao reativar a conta.")
        @JsonProperty("region_id")
        Long regionId) {}
