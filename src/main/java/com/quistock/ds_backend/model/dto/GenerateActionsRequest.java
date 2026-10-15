package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record GenerateActionsRequest(
    @Schema(description = "ID da análise retornada por POST /flows/analyze.")
        @JsonProperty("flow_id")
        @NotBlank(message = "flow_id is required")
        String flowId,
    @Schema(
            description =
                "Início da promoção (YYYY-MM-DD); informar junto com promotion_valid_until somente"
                    + " para promoção.")
        @JsonProperty("promotion_valid_from")
        LocalDate promotionValidFrom,
    @Schema(description = "Fim da promoção (YYYY-MM-DD), igual ou posterior ao início.")
        @JsonProperty("promotion_valid_until")
        LocalDate promotionValidUntil) {
  public GenerateActionsRequest(String flowId) {
    this(flowId, null, null);
  }
}
