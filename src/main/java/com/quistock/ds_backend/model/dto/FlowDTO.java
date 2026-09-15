package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record FlowDTO(
    String id,
    @JsonProperty("product_id") String productId,
    @JsonProperty("product_name") String productName,
    @JsonProperty("flow_type") String flowType,
    String status,
    String reason,
    @JsonProperty("daily_sales_average") BigDecimal dailySalesAverage,
    @JsonProperty("stock_coverage_days") BigDecimal stockCoverageDays,
    @JsonProperty("expiration_days") Integer expirationDays,
    @JsonProperty("supplier_lead_time") Integer supplierLeadTime,
    @JsonProperty("analysis_date") Instant analysisDate) {}
