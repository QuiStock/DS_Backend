package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record UpdateActionStatusRequest(
    @Schema(
            allowableValues = {
              "GENERATED",
              "IN_EMPLOYEE_TRIAGE",
              "SENT_TO_MANAGER",
              "APPROVED",
              "REJECTED"
            })
        @NotBlank(message = "status is required")
        String status,
    @Schema(description = "Obrigatória ao rejeitar (REJECTED).") String justification,
    @JsonProperty("final_promotion_valid_from") LocalDate finalPromotionValidFrom,
    @JsonProperty("final_promotion_valid_until") LocalDate finalPromotionValidUntil) {
  public UpdateActionStatusRequest(String status) {
    this(status, null, null, null);
  }
}
