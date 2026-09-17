package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.handler.ApiExceptionHandler;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.service.FlowService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FlowControllerTest {

  @Test
  void shouldAnalyzeProductOnPublicRoute() throws Exception {
    FlowService flowService = mock(FlowService.class);
    when(flowService.analyzeProduct("PROD001:FIL001")).thenReturn(flow());

    mockMvc(flowService)
        .perform(
            post("/api/flows/analyze")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":\"PROD001:FIL001\"}"))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("101"))
        .andExpect(jsonPath("$.product_id").value("PROD001:FIL001"))
        .andExpect(jsonPath("$.flow_type").value("LOW"))
        .andExpect(jsonPath("$.status").value("ANALYZED"))
        .andExpect(jsonPath("$.daily_sales_average").value(5.14))
        .andExpect(jsonPath("$.stock_coverage_days").value(9.92));
  }

  @Test
  void shouldListFlowsUsingContractFilters() throws Exception {
    FlowService flowService = mock(FlowService.class);
    when(flowService.listFlows("LOW", "PROD001:FIL001", "ANALYZED")).thenReturn(List.of(flow()));

    mockMvc(flowService)
        .perform(
            get("/api/flows")
                .contextPath("/api")
                .queryParam("flow_type", "LOW")
                .queryParam("product_id", "PROD001:FIL001")
                .queryParam("status", "ANALYZED"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("101"))
        .andExpect(jsonPath("$[0].product_id").value("PROD001:FIL001"))
        .andExpect(jsonPath("$[0].flow_type").value("LOW"))
        .andExpect(jsonPath("$[0].status").value("ANALYZED"));
  }

  @Test
  void shouldReturn404WhenProductIsNotFound() throws Exception {
    FlowService flowService = mock(FlowService.class);
    when(flowService.analyzeProduct("UNKNOWN")).thenThrow(new ProductNotFoundException("UNKNOWN"));

    mockMvc(flowService)
        .perform(
            post("/api/flows/analyze")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":\"UNKNOWN\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void shouldReturn503WhenErpIsUnavailable() throws Exception {
    FlowService flowService = mock(FlowService.class);
    when(flowService.analyzeProduct("PROD001:FIL001"))
        .thenThrow(
            new ErpIntegrationException(
                "Could not connect to the external ERP API.", new RuntimeException()));

    mockMvc(flowService)
        .perform(
            post("/api/flows/analyze")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":\"PROD001:FIL001\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("ERP_UNAVAILABLE"));
  }

  private MockMvc mockMvc(FlowService flowService) {
    return MockMvcBuilders.standaloneSetup(new FlowController(flowService))
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }

  private FlowDTO flow() {
    return new FlowDTO(
        "101",
        "PROD001:FIL001",
        "Whole Milk 1L",
        "LOW",
        "ANALYZED",
        "Product is expired or close to expiration.",
        new BigDecimal("5.14"),
        new BigDecimal("9.92"),
        0,
        3,
        Instant.parse("2026-08-28T12:00:00Z"));
  }
}
