package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record GenerateActionsRequest(
    @JsonProperty("flow_id") @NotBlank(message = "flow_id is required") String flowId,
    @JsonProperty("promotion_valid_from") LocalDate promotionValidFrom,
    @JsonProperty("promotion_valid_until") LocalDate promotionValidUntil) {
  public GenerateActionsRequest(String flowId) {
    this(flowId, null, null);
  }
}
