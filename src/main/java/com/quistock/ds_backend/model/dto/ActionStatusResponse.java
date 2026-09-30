package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record ActionStatusResponse(
    String id,
    String status,
    @JsonProperty("promotion_valid_from") LocalDate promotionValidFrom,
    @JsonProperty("promotion_valid_until") LocalDate promotionValidUntil) {
  public ActionStatusResponse(String id, String status) {
    this(id, status, null, null);
  }
}
