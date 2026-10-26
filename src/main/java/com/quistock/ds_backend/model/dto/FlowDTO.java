package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FlowDTO(
    String id,
    @JsonProperty("product_id") String productId,
    @JsonProperty("product_name") String productName,
    @Schema(
            description = "Fluxo de vendas; nulo com dados insuficientes.",
            allowableValues = {"HIGH", "MEDIUM", "LOW"},
            nullable = true)
        @JsonProperty("flow_type")
        String flowType,
    @Schema(allowableValues = {"ANALYZED", "INSUFFICIENT_DATA"}) String status,
    String reason,
    @JsonProperty("daily_sales_average") BigDecimal dailySalesAverage,
    @JsonProperty("stock_coverage_days") BigDecimal stockCoverageDays,
    @JsonProperty("expiration_days") Integer expirationDays,
    @JsonProperty("supplier_lead_time") Integer supplierLeadTime,
    @JsonProperty("analysis_date") Instant analysisDate,
    @ArraySchema(
            schema =
                @Schema(allowableValues = {"STOCKOUT", "NEAR_EXPIRY", "EXPIRATION", "EXPIRED"}))
        List<String> risks,
    String classifier,
    @Schema(description = "Último processamento do produto/loja, não atualização contínua do ERP.")
        @JsonProperty("is_current")
        boolean current,
    @JsonProperty("store_id") Long storeId) {}
