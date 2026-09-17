package com.quistock.ds_backend.model.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateActionStatusRequest(@NotBlank(message = "status is required") String status) {}
