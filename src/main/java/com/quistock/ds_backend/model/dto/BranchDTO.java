package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record BranchDTO(
    String id,
    @JsonProperty("store_id") long storeId,
    String name,
    String address,
    String city,
    String state,
    Double latitude,
    Double longitude,
    @Schema(
            description =
                "ID SQL da região da filial; nulo quando ainda não associada a uma região.")
        @JsonProperty("region_id")
        Long regionId) {}
