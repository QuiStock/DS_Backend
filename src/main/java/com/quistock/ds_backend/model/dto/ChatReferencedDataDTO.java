package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatReferencedDataDTO(
    @JsonProperty("product_id") String productId,
    String name,
    @JsonProperty("flow_type") String flowType,
    @JsonProperty("suggested_action") String suggestedAction) {}
