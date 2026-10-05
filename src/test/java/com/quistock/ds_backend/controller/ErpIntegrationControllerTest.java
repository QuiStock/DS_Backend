package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.handler.ApiExceptionHandler;
import com.quistock.ds_backend.model.dto.ErpIntegrationStatusDTO;
import com.quistock.ds_backend.service.ErpIntegrationService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ErpIntegrationControllerTest {

  @Test
  void shouldReturnErpIntegrationStatusOnPublicRoute() throws Exception {
    ErpIntegrationService erpIntegrationService = mock(ErpIntegrationService.class);
    when(erpIntegrationService.getStatus())
        .thenReturn(
            new ErpIntegrationStatusDTO(
                "MockAPI", "CONNECTED", Instant.parse("2026-08-28T12:00:00Z")));

    mockMvc(erpIntegrationService)
        .perform(get("/erp-integration/status"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.source").value("MockAPI"))
        .andExpect(jsonPath("$.status").value("CONNECTED"))
        .andExpect(jsonPath("$.last_synchronization").value("2026-08-28T12:00:00Z"));
  }

  @Test
  void shouldReturn503WhenErpIsUnavailable() throws Exception {
    ErpIntegrationService erpIntegrationService = mock(ErpIntegrationService.class);
    when(erpIntegrationService.getStatus())
        .thenThrow(
            new ErpIntegrationException(
                "Could not connect to the external ERP API.", new RuntimeException()));

    mockMvc(erpIntegrationService)
        .perform(get("/erp-integration/status"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("ERP_UNAVAILABLE"));
  }

  private MockMvc mockMvc(ErpIntegrationService erpIntegrationService) {
    return MockMvcBuilders.standaloneSetup(new ErpIntegrationController(erpIntegrationService))
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }
}
