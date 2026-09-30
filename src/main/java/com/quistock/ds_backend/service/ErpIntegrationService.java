package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.model.dto.ErpIntegrationStatusDTO;
import com.quistock.ds_backend.repository.ErpSyncRepository;
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
  private final ErpSyncRepository syncRepository;

  public ErpIntegrationService(
      RestClient restClient,
      @Value("${erp.api.products-path:/products}") String path,
      ErpSyncRepository repository) {
    this.erpRestClient = restClient;
    this.productsPath = path;
    this.syncRepository = repository;
  }

  public ErpIntegrationStatusDTO getStatus() {
    try {
      erpRestClient.get().uri(productsPath).retrieve().toBodilessEntity();
      Instant lastSynchronization = syncRepository.latestFinishedAt();
      return new ErpIntegrationStatusDTO(SOURCE, CONNECTED_STATUS, lastSynchronization);
    } catch (RestClientException | IllegalArgumentException exception) {
      throw new ErpIntegrationException("Could not connect to the external ERP API.", exception);
    }
  }
}
