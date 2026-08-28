package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record ProdutoDTO(
    String id,
    String sku,
    String nome,
    String categoria,
    @JsonProperty("estoque_atual") Integer estoqueAtual,
    @JsonProperty("estoque_minimo") Integer estoqueMinimo,
    @JsonProperty("vendas_7d") Integer vendas7d,
    @JsonProperty("vendas_30d") Integer vendas30d,
    @JsonProperty("dias_validade") Integer diasValidade,
    @JsonProperty("lead_time_fornecedor") Integer leadTimeFornecedor,
    BigDecimal preco,
    BigDecimal custo,
    @JsonProperty("ultima_reposicao") Instant ultimaReposicao,
    Boolean status,
    String filial) {}
