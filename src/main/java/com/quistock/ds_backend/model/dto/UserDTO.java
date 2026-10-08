package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserDTO(
    String id,
    String name,
    String email,
    String role,
    String status,
    @JsonProperty("profile_photo_url") String profilePhotoUrl,
    @JsonProperty("store_id") Long storeId,
    @JsonProperty("store_code") String storeCode,
    @JsonProperty("store_name") String storeName,
    @JsonProperty("region_id") Long regionId,
    @JsonProperty("region_code") String regionCode,
    @JsonProperty("region_name") String regionName) {}
