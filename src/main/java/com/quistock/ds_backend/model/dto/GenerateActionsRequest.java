package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record GenerateActionsRequest(
    @JsonProperty("flow_id") @NotBlank(message = "flow_id is required") String flowId) {}
