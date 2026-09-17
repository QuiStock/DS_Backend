package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActionDTO(
    String id,
    @JsonProperty("action_type") String actionType,
    String status,
    String justification) {}
