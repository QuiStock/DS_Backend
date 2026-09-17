package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.model.dto.ErpIntegrationStatusDTO;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ErpIntegrationServiceTest {
  private static final Clock TEST_CLOCK =
      Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC);

  private MockRestServiceServer server;
  private ErpIntegrationService erpIntegrationService;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://erp.test");
    server = MockRestServiceServer.bindTo(builder).build();
    erpIntegrationService = new ErpIntegrationService(builder.build(), "/products", TEST_CLOCK);
  }

  @Test
  void shouldReportConnectedWhenErpRespondsSuccessfully() {
    server
        .expect(requestTo("http://erp.test/products"))
        .andExpect(method(GET))
        .andRespond(withSuccess());

    ErpIntegrationStatusDTO status = erpIntegrationService.getStatus();

    assertThat(status)
        .isEqualTo(
            new ErpIntegrationStatusDTO(
                "MockAPI", "CONNECTED", Instant.parse("2026-08-28T12:00:00Z")));
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
