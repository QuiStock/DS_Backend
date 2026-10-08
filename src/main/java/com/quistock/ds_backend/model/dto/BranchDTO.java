package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BranchDTO(
    String id,
    @JsonProperty("store_id") long storeId,
    String name,
    String address,
    String city,
    String state,
    Double latitude,
    Double longitude) {}
