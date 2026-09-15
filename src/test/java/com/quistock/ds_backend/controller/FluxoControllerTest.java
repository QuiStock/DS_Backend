package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.exception.ProdutoNotFoundException;
import com.quistock.ds_backend.handler.ApiExceptionHandler;
import com.quistock.ds_backend.model.dto.FluxoDTO;
import com.quistock.ds_backend.service.FluxoService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FluxoControllerTest {

  @Test
  void deveAnalisarProdutoNaRotaPublica() throws Exception {
    FluxoService fluxoService = mock(FluxoService.class);
    when(fluxoService.analisarProduto("PROD001:FIL001")).thenReturn(fluxo());

    mockMvc(fluxoService)
        .perform(
            post("/api/fluxos/analisar")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"produto_id\":\"PROD001:FIL001\"}"))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("101"))
        .andExpect(jsonPath("$.produto_id").value("PROD001:FIL001"))
        .andExpect(jsonPath("$.tipo_fluxo").value("BAIXO"))
        .andExpect(jsonPath("$.status").value("ANALISADO"))
        .andExpect(jsonPath("$.media_vendas_diaria").value(5.14))
        .andExpect(jsonPath("$.cobertura_estoque_dias").value(9.92));
  }

  @Test
  void deveRetornar404QuandoProdutoNaoForEncontrado() throws Exception {
    FluxoService fluxoService = mock(FluxoService.class);
    when(fluxoService.analisarProduto("INEXISTENTE"))
        .thenThrow(new ProdutoNotFoundException("INEXISTENTE"));

    mockMvc(fluxoService)
        .perform(
            post("/api/fluxos/analisar")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"produto_id\":\"INEXISTENTE\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.erro").value("PRODUTO_NAO_ENCONTRADO"));
  }

  @Test
  void deveRetornar503QuandoOErpEstiverIndisponivel() throws Exception {
    FluxoService fluxoService = mock(FluxoService.class);
    when(fluxoService.analisarProduto("PROD001:FIL001"))
        .thenThrow(
            new ErpIntegrationException(
                "Não foi possível conectar com a API externa do ERP.", new RuntimeException()));

    mockMvc(fluxoService)
        .perform(
            post("/api/fluxos/analisar")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"produto_id\":\"PROD001:FIL001\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.erro").value("ERP_INDISPONIVEL"));
  }

  private MockMvc mockMvc(FluxoService fluxoService) {
    return MockMvcBuilders.standaloneSetup(new FluxoController(fluxoService))
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }

  private FluxoDTO fluxo() {
    return new FluxoDTO(
        "101",
        "PROD001:FIL001",
        "Leite Integral 1L",
        "BAIXO",
        "ANALISADO",
        "Produto vencido ou próximo do vencimento.",
        new BigDecimal("5.14"),
        new BigDecimal("9.92"),
        0,
        3,
        Instant.parse("2026-08-28T12:00:00Z"));
  }
}
