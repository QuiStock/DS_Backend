package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GenerateActionsRequest(
    @JsonProperty("flow_id") @NotBlank String flowId,
    @JsonProperty("promotion_valid_from") LocalDate promotionValidFrom,
    @JsonProperty("promotion_valid_until") LocalDate promotionValidUntil,
    @Schema(
            allowableValues = {"ORDER", "PROMOTION"},
            description = "Obrigatório se ambos os riscos estiverem presentes.")
        @JsonProperty("action_type")
        String actionType,
    @Schema(description = "Quantidade inteira positiva do pedido, preenchida pelo usuário.")
        @Positive
        Integer quantity,
    @Schema(
            description =
                "Data desejada pelo usuário; não garante entrega. Deve ser hoje ou futura.")
        @JsonProperty("desired_order_date")
        LocalDate desiredOrderDate,
    @JsonProperty("discount_percentage") @DecimalMin("0") @DecimalMax("100")
        BigDecimal discountPercentage,
    @Schema(description = "Duração em dias, incluindo hoje. Alternativa às duas datas explícitas.")
        @JsonProperty("promotion_duration_days")
        @Positive
        @Max(36500)
        Integer promotionDurationDays) {
  public GenerateActionsRequest(String flowId) {
    this(flowId, null, null);
  }

  public GenerateActionsRequest(String flowId, LocalDate from, LocalDate until) {
    this(flowId, from, until, null, null, null, null, null);
  }
}
