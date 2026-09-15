package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductDTO(
    String id,
    String sku,
    String name,
    String category,
    @JsonProperty("current_stock") Integer currentStock,
    @JsonProperty("minimum_stock") Integer minimumStock,
    @JsonProperty("sales_7d") Integer sales7d,
    @JsonProperty("sales_30d") Integer sales30d,
    @JsonProperty("expiration_days") Integer expirationDays,
    @JsonProperty("supplier_lead_time") Integer supplierLeadTime,
    BigDecimal price,
    BigDecimal cost,
    @JsonProperty("last_restock") Instant lastRestock,
    Boolean status,
    String branch) {}
