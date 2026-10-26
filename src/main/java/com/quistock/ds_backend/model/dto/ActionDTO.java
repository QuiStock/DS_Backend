package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ActionDTO(
    String id,
    @JsonProperty("action_type") String actionType,
    String status,
    String justification,
    @JsonProperty("promotion_valid_from") LocalDate promotionValidFrom,
    @JsonProperty("promotion_valid_until") LocalDate promotionValidUntil,
    Integer quantity,
    @JsonProperty("desired_order_date") LocalDate desiredOrderDate,
    @JsonProperty("discount_percentage") BigDecimal discountPercentage,
    @JsonProperty("reference_sale_price") BigDecimal referenceSalePrice,
    @JsonProperty("promotional_price") BigDecimal promotionalPrice,
    @JsonProperty("product_id") String productId,
    @JsonProperty("store_id") Long storeId) {
  public ActionDTO(
      String id,
      String actionType,
      String status,
      String justification,
      LocalDate from,
      LocalDate until) {
    this(
        id,
        actionType,
        status,
        justification,
        from,
        until,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public ActionDTO(String id, String actionType, String status, String justification) {
    this(id, actionType, status, justification, null, null);
  }
}
