package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
    @JsonProperty("user_id") @NotBlank(message = "user_id is required") String userId,
    @NotBlank(message = "message is required") String message) {}
