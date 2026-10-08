package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateTeamMemberRequest(
    @Size(max = 150) String name,
    @Email @Size(max = 255) String email,
    String role,
    String status,
    @JsonProperty("store_id") Long storeId,
    @JsonProperty("region_id") Long regionId) {}
