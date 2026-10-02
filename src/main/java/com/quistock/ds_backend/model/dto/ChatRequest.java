package com.quistock.ds_backend.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank(message = "message is required") String message) {}
