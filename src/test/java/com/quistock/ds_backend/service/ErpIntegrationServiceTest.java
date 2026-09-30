package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.model.dto.ErpIntegrationStatusDTO;
import com.quistock.ds_backend.repository.ErpSyncRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ErpIntegrationServiceTest {
  private MockRestServiceServer server;
  private ErpIntegrationService erpIntegrationService;
  private ErpSyncRepository syncRepository;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://erp.test");
    server = MockRestServiceServer.bindTo(builder).build();
    syncRepository = mock(ErpSyncRepository.class);
    erpIntegrationService = new ErpIntegrationService(builder.build(), "/products", syncRepository);
  }

  @Test
  void shouldReportConnectedWhenErpRespondsSuccessfully() {
    server
        .expect(requestTo("http://erp.test/products"))
        .andExpect(method(GET))
        .andRespond(withSuccess());
    Instant lastFinished = Instant.parse("2026-08-28T12:00:00Z");
    when(syncRepository.latestFinishedAt()).thenReturn(lastFinished);

    ErpIntegrationStatusDTO status = erpIntegrationService.getStatus();

    assertThat(status).isEqualTo(new ErpIntegrationStatusDTO("MockAPI", "CONNECTED", lastFinished));
    server.verify();
  }

  @Test
  void shouldRaiseErpIntegrationExceptionWhenErpDoesNotRespond() {
    server
        .expect(requestTo("http://erp.test/products"))
        .andExpect(method(GET))
        .andRespond(withServerError());

    assertThatThrownBy(() -> erpIntegrationService.getStatus())
        .isInstanceOf(ErpIntegrationException.class)
        .hasMessage("Could not connect to the external ERP API.");
    server.verify();
  }
}
