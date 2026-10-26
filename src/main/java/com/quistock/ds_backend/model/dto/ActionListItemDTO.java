package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ActionListItemDTO(
    String id,
    @JsonProperty("flow_id") String flowId,
    @JsonProperty("product_name") String productName,
    @JsonProperty("action_type") String actionType,
    String status,
    String justification,
    @JsonProperty("promotion_valid_from") LocalDate promotionValidFrom,
    @JsonProperty("promotion_valid_until") LocalDate promotionValidUntil,
    @JsonProperty("decision_justification") String decisionJustification,
    Integer quantity,
    @JsonProperty("desired_order_date") LocalDate desiredOrderDate,
    @JsonProperty("discount_percentage") BigDecimal discountPercentage,
    @JsonProperty("reference_sale_price") BigDecimal referenceSalePrice,
    @JsonProperty("promotional_price") BigDecimal promotionalPrice,
    @JsonProperty("product_id") String productId,
    @JsonProperty("store_id") Long storeId) {
  public ActionListItemDTO(
      String id,
      String flowId,
      String productName,
      String actionType,
      String status,
      String justification) {
    this(
        id,
        flowId,
        productName,
        actionType,
        status,
        justification,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
