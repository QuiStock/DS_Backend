package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.model.dto.DashboardSummaryDTO;
import com.quistock.ds_backend.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DashboardControllerTest {

  @Test
  void shouldReturnDashboardSummaryOnPublicRoute() throws Exception {
    DashboardService dashboardService = mock(DashboardService.class);
    when(dashboardService.getSummary())
        .thenReturn(new DashboardSummaryDTO(5, 1, 2, 2, 3, 2, 1, 4, 2, 3, 5));

    mockMvc(dashboardService)
        .perform(get("/api/dashboard/summary").contextPath("/api"))
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

  private MockMvc mockMvc(DashboardService dashboardService) {
    return MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService)).build();
  }
}
