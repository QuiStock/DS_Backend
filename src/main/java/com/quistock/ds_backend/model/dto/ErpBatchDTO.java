package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErpBatchDTO(
    String id,
    @JsonProperty("codigo_produto_erp") String erpProductCode,
    @JsonProperty("nome_produto") String productName,
    @JsonProperty("categoria") String category,
    @JsonProperty("unidade_medida") String unitOfMeasure,
    @JsonProperty("num_lote") String batchNumber,
    @JsonProperty("data_validade") Object expirationDate,
    @JsonProperty("quantidade") Object quantity,
    @JsonProperty("preco") Object price,
    @JsonProperty("custo") Object cost,
    @JsonProperty("codigo_filial_erp") String erpBranchCode,
    @JsonProperty("filial") String branch,
    @JsonProperty("certificado_qualidade") Boolean qualityCertificate,
    @JsonProperty("data_entrada") Object entryDate,
    @JsonProperty("vendas_7d") Object sales7d,
    @JsonProperty("vendas_30d") Object sales30d,
    @JsonProperty("estoque_minimo") Object minimumStock,
    @JsonProperty("lead_time_dias") Object leadTimeDays) {}
