package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record AnalisarProdutoRequest(
    @JsonProperty("produto_id") @NotBlank(message = "produto_id é obrigatório") String produtoId) {}
