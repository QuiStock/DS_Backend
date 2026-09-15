package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record FluxoDTO(
    String id,
    @JsonProperty("produto_id") String produtoId,
    @JsonProperty("produto_nome") String produtoNome,
    @JsonProperty("tipo_fluxo") String tipoFluxo,
    String status,
    String motivo,
    @JsonProperty("media_vendas_diaria") BigDecimal mediaVendasDiaria,
    @JsonProperty("cobertura_estoque_dias") BigDecimal coberturaEstoqueDias,
    @JsonProperty("dias_validade") Integer diasValidade,
    @JsonProperty("lead_time_fornecedor") Integer leadTimeFornecedor,
    @JsonProperty("data_analise") Instant dataAnalise) {}
