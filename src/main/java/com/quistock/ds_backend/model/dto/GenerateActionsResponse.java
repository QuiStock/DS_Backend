package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GenerateActionsResponse(
    @JsonProperty("flow_id") String flowId,
    @JsonProperty("generated_actions") List<ActionDTO> generatedActions) {}
