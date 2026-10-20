package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.handler.ApiExceptionHandler;
import com.quistock.ds_backend.model.dto.DashboardSummaryDTO;
import com.quistock.ds_backend.repository.StoreScope;
import com.quistock.ds_backend.service.DashboardService;
import com.quistock.ds_backend.service.UserAccessGuard;
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

class DashboardControllerTest {
  private static final StoreScope SCOPE = new StoreScope(1001L, null, null);

  @Test
  void shouldReturnDashboardSummaryOnPublicRoute() throws Exception {
    DashboardService dashboardService = mock(DashboardService.class);
    when(dashboardService.getSummary(SCOPE))
        .thenReturn(new DashboardSummaryDTO(5, 1, 2, 2, 3, 2, 1, 4, 2, 3, 5));

    mockMvc(dashboardService)
        .perform(get("/dashboard/summary"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.total_products").value(5))
        .andExpect(jsonPath("$.high_risk_products").value(1))
        .andExpect(jsonPath("$.medium_risk_products").value(2))
        .andExpect(jsonPath("$.low_risk_products").value(2))
        .andExpect(jsonPath("$.suggested_actions").value(3))
        .andExpect(jsonPath("$.suggested_promotions").value(2))
        .andExpect(jsonPath("$.suggested_stock_orders").value(1))
        .andExpect(jsonPath("$.near_expiry_products").value(4))
        .andExpect(jsonPath("$.stockout_products").value(2))
        .andExpect(jsonPath("$.overstock_products").value(3))
        .andExpect(jsonPath("$.active_actions").value(5));
  }

  @Test
  void shouldReturn503WhenErpIsUnavailable() throws Exception {
    DashboardService dashboardService = mock(DashboardService.class);
    when(dashboardService.getSummary(SCOPE))
        .thenThrow(
            new ErpIntegrationException(
                "Could not connect to the external ERP API.", new RuntimeException()));

    mockMvc(dashboardService)
        .perform(get("/dashboard/summary"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("ERP_UNAVAILABLE"));
  }

  private MockMvc mockMvc(DashboardService dashboardService) {
    UserAccessGuard accessGuard = mock(UserAccessGuard.class);
    when(accessGuard.requireStoreScope(1001L, null)).thenReturn(SCOPE);
    return MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService, accessGuard))
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
}
