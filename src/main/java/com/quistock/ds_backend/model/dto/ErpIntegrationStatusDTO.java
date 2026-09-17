package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ErpIntegrationStatusDTO(
    String source,
    String status,
    @JsonProperty("last_synchronization") Instant lastSynchronization) {}
