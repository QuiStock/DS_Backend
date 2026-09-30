package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record ActionDTO(
    String id,
    @JsonProperty("action_type") String actionType,
    String status,
    String justification,
    @JsonProperty("promotion_valid_from") LocalDate promotionValidFrom,
    @JsonProperty("promotion_valid_until") LocalDate promotionValidUntil) {
  public ActionDTO(String id, String actionType, String status, String justification) {
    this(id, actionType, status, justification, null, null);
  }
}
