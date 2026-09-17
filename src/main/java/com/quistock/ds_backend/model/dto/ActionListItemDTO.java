package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActionListItemDTO(
    String id,
    @JsonProperty("flow_id") String flowId,
    @JsonProperty("product_name") String productName,
    @JsonProperty("action_type") String actionType,
    String status,
    String justification) {}
