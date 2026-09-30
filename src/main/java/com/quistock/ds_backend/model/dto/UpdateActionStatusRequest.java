package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record UpdateActionStatusRequest(
    @NotBlank(message = "status is required") String status,
    String justification,
    @JsonProperty("final_promotion_valid_from") LocalDate finalPromotionValidFrom,
    @JsonProperty("final_promotion_valid_until") LocalDate finalPromotionValidUntil) {
  public UpdateActionStatusRequest(String status) {
    this(status, null, null, null);
  }
}
