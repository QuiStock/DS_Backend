package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.handler.ApiExceptionHandler;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.repository.StoreScope;
import com.quistock.ds_backend.service.FlowService;
import com.quistock.ds_backend.service.UserAccessGuard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class FlowControllerTest {
  private static final StoreScope SCOPE = new StoreScope(1001L, null, null);

  @Test
  void shouldAnalyzeProductOnPublicRoute() throws Exception {
    FlowService flowService = mock(FlowService.class);
    when(flowService.analyzeProduct("PROD001:FIL001", SCOPE)).thenReturn(flow());

    mockMvc(flowService)
        .perform(
            post("/flows/analyze")
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
  void shouldReturnContractErrorWhenProductIdIsMissing() throws Exception {
    FlowService flowService = mock(FlowService.class);

    mockMvc(flowService)
        .perform(post("/flows/analyze").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Required fields are missing or invalid."));
  }

  @Test
  void shouldReturnContractErrorForMalformedAnalyzeBody() throws Exception {
    FlowService flowService = mock(FlowService.class);

    mockMvc(flowService)
        .perform(
            post("/flows/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Required fields are missing or invalid."));
  }

  @Test
  void shouldListFlowsUsingContractFilters() throws Exception {
    FlowService flowService = mock(FlowService.class);
    when(flowService.listFlows("LOW", "PROD001:FIL001", "ANALYZED", SCOPE))
        .thenReturn(List.of(flow()));

    mockMvc(flowService)
        .perform(
            get("/flows")
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
  void shouldReturn400ForUnsupportedFlowTypeFilter() throws Exception {
    FlowService flowService = mock(FlowService.class);
    when(flowService.listFlows("URGENT", null, null, SCOPE))
        .thenThrow(new InvalidRequestException());

    mockMvc(flowService)
        .perform(get("/flows").queryParam("flow_type", "URGENT"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Required fields are missing or invalid."));
  }

  @Test
  void shouldReturn400ForUnsupportedFlowStatusFilter() throws Exception {
    FlowService flowService = mock(FlowService.class);
    when(flowService.listFlows(null, null, "PENDING", SCOPE))
        .thenThrow(new InvalidRequestException());

    mockMvc(flowService)
        .perform(get("/flows").queryParam("status", "PENDING"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Required fields are missing or invalid."));
  }

  @Test
  void shouldReturn404WhenProductIsNotFound() throws Exception {
    FlowService flowService = mock(FlowService.class);
    when(flowService.analyzeProduct("UNKNOWN", SCOPE))
        .thenThrow(new ProductNotFoundException("UNKNOWN"));

    mockMvc(flowService)
        .perform(
            post("/flows/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":\"UNKNOWN\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void shouldReturn503WhenErpIsUnavailable() throws Exception {
    FlowService flowService = mock(FlowService.class);
    when(flowService.analyzeProduct("PROD001:FIL001", SCOPE))
        .thenThrow(
            new ErpIntegrationException(
                "Could not connect to the external ERP API.", new RuntimeException()));

    mockMvc(flowService)
        .perform(
            post("/flows/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_id\":\"PROD001:FIL001\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("ERP_UNAVAILABLE"));
  }

  private MockMvc mockMvc(FlowService flowService) {
    UserAccessGuard accessGuard = mock(UserAccessGuard.class);
    when(accessGuard.requireStoreScope(1001L, null)).thenReturn(SCOPE);
    return MockMvcBuilders.standaloneSetup(
            new FlowController(
                flowService,
                accessGuard,
                mock(com.quistock.ds_backend.service.LoginAnalysisService.class)))
        .setCustomArgumentResolvers(
            new HandlerMethodArgumentResolver() {
              @Override
              public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && Jwt.class.isAssignableFrom(parameter.getParameterType());
              }

              @Override
              public Object resolveArgument(
                  MethodParameter parameter,
                  ModelAndViewContainer container,
                  NativeWebRequest request,
                  WebDataBinderFactory factory) {
                return Jwt.withTokenValue("test-token")
                    .header("alg", "RS256")
                    .subject("1001")
                    .build();
              }
            })
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
        Instant.parse("2026-08-28T12:00:00Z"),
        java.util.List.of(),
        "SALES_PERCENTILES_V1",
        true,
        1L);
  }
}
