package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardSummaryDTO(
    @JsonProperty("total_products") int totalProducts,
    @Schema(
            description =
                "Nome legado: quantidade de produtos com fluxo de vendas HIGH, não severidade de risco.")
        @JsonProperty("high_risk_products")
        int highRiskProducts,
    @Schema(description = "Nome legado: quantidade de produtos com fluxo de vendas MEDIUM.")
        @JsonProperty("medium_risk_products")
        int mediumRiskProducts,
    @Schema(description = "Nome legado: quantidade de produtos com fluxo de vendas LOW.")
        @JsonProperty("low_risk_products")
        int lowRiskProducts,
    @JsonProperty("suggested_actions") int suggestedActions,
    @JsonProperty("suggested_promotions") int suggestedPromotions,
    @JsonProperty("suggested_stock_orders") int suggestedStockOrders,
    @JsonProperty("near_expiry_products") long nearExpiryProducts,
    @JsonProperty("stockout_products") long stockoutProducts,
    @JsonProperty("overstock_products") long overstockProducts,
    @JsonProperty("active_actions") long activeActions) {}
