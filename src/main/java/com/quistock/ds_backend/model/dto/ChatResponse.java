package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ChatResponse(
    String answer,
    @JsonProperty("responsible_agent") String responsibleAgent,
    @JsonProperty("referenced_data") List<ChatReferencedDataDTO> referencedData) {}
