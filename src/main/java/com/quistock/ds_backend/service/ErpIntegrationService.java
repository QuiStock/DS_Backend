package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.model.dto.ErpIntegrationStatusDTO;
import com.quistock.ds_backend.repository.ErpSyncRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final ErpSyncRepository syncRepository;

  @Autowired
  public ErpIntegrationService(
      RestClient restClient,
      @Value("${erp.api.products-path:/products}") String path,
      Clock applicationClock,
      ErpSyncRepository repository) {
    this.erpRestClient = restClient;
    this.productsPath = path;
    this.clock = applicationClock;
    this.syncRepository = repository;
  }

  // Kept for the existing service-level tests.
  public ErpIntegrationService(
      RestClient restClient,
      @Value("${erp.api.products-path:/products}") String path,
      Clock applicationClock) {
    this.erpRestClient = restClient;
    this.productsPath = path;
    this.clock = applicationClock;
    this.syncRepository = null;
  }

  public ErpIntegrationStatusDTO getStatus() {
    try {
      erpRestClient.get().uri(productsPath).retrieve().toBodilessEntity();
      Instant lastSynchronization =
          syncRepository == null ? Instant.now(clock) : syncRepository.latestFinishedAt();
      return new ErpIntegrationStatusDTO(SOURCE, CONNECTED_STATUS, lastSynchronization);
    } catch (RestClientException | IllegalArgumentException exception) {
      throw new ErpIntegrationException("Could not connect to the external ERP API.", exception);
    }
  }
}
