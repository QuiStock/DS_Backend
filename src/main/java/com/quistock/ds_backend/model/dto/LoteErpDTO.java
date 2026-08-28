package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoteErpDTO(
    String id,
    @JsonProperty("codigo_produto_erp") String codigoProdutoErp,
    @JsonProperty("nome_produto") String nomeProduto,
    String categoria,
    @JsonProperty("unidade_medida") String unidadeMedida,
    @JsonProperty("num_lote") String numLote,
    @JsonProperty("data_validade") Object dataValidade,
    Object quantidade,
    Object preco,
    Object custo,
    @JsonProperty("codigo_filial_erp") String codigoFilialErp,
    String filial,
    @JsonProperty("certificado_qualidade") Boolean certificadoQualidade,
    @JsonProperty("data_entrada") Object dataEntrada,
    @JsonProperty("vendas_7d") Object vendas7d,
    @JsonProperty("vendas_30d") Object vendas30d,
    @JsonProperty("estoque_minimo") Object estoqueMinimo,
    @JsonProperty("lead_time_dias") Object leadTimeDias) {}
