package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.handler.ApiExceptionHandler;
import com.quistock.ds_backend.model.dto.ProdutoDTO;
import com.quistock.ds_backend.service.ProdutoService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProdutoControllerTest {

  @Test
  void deveListarProdutosNaRotaPublica() throws Exception {
    ProdutoService produtoService = mock(ProdutoService.class);
    when(produtoService.listarProdutos(null, null, null)).thenReturn(List.of(produto()));

    MockMvc mockMvc = criarMockMvc(produtoService);

    mockMvc
        .perform(get("/api/produtos").contextPath("/api"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("PROD001:FIL001"))
        .andExpect(jsonPath("$[0].estoque_atual").value(71))
        .andExpect(jsonPath("$[0].num_lote").doesNotExist());
  }

  @Test
  void deveRetornar503QuandoOErpEstiverIndisponivel() throws Exception {
    ProdutoService produtoService = mock(ProdutoService.class);
    when(produtoService.listarProdutos(null, null, null))
        .thenThrow(
            new ErpIntegrationException(
                "Não foi possível conectar com a API externa do ERP.", new RuntimeException()));

    MockMvc mockMvc = criarMockMvc(produtoService);

    mockMvc
        .perform(get("/api/produtos").contextPath("/api"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.erro").value("ERP_INDISPONIVEL"))
        .andExpect(
            jsonPath("$.mensagem")
                .value("Não foi possível conectar com a API externa do ERP."));
  }

  private MockMvc criarMockMvc(ProdutoService produtoService) {
    return MockMvcBuilders.standaloneSetup(new ProdutoController(produtoService))
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }

  private ProdutoDTO produto() {
    return new ProdutoDTO(
        "PROD001:FIL001",
        "PROD001",
        "Leite Integral 1L",
        "Laticinios",
        71,
        40,
        36,
        150,
        33,
        5,
        new BigDecimal("7.99"),
        new BigDecimal("5.20"),
        Instant.parse("2026-08-20T00:00:00Z"),
        true,
        "Loja Santana");
  }
}
