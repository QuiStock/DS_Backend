package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.model.dto.ErpIntegrationStatusDTO;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ErpIntegrationService {
  private static final String SOURCE = "MockAPI";
  private static final String CONNECTED_STATUS = "CONNECTED";

  private final RestClient erpRestClient;
  private final String productsPath;
  private final Clock clock;

  public ErpIntegrationService(
      RestClient restClient,
      @Value("${erp.api.products-path:/products}") String path,
      Clock applicationClock) {
    this.erpRestClient = restClient;
    this.productsPath = path;
    this.clock = applicationClock;
  }

  public ErpIntegrationStatusDTO getStatus() {
    try {
      erpRestClient.get().uri(productsPath).retrieve().toBodilessEntity();
      return new ErpIntegrationStatusDTO(SOURCE, CONNECTED_STATUS, Instant.now(clock));
    } catch (RestClientException | IllegalArgumentException exception) {
      throw new ErpIntegrationException("Could not connect to the external ERP API.", exception);
    }
  }
}
