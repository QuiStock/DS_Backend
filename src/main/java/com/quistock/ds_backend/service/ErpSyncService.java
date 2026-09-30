package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.model.dto.ErpBatchDTO;
import com.quistock.ds_backend.repository.ErpSyncRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@ConditionalOnProperty(name = "erp.sync.enabled", havingValue = "true", matchIfMissing = true)
public class ErpSyncService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ErpSyncService.class);
  private static final ParameterizedTypeReference<List<ErpBatchDTO>> BATCHES_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient erpRestClient;
  private final String productsPath;
  private final ErpSyncRepository syncRepository;
  private final AtomicBoolean running = new AtomicBoolean();

  public ErpSyncService(
      RestClient erpRestClient,
      @Value("${erp.api.products-path:/products}") String productsPath,
      ErpSyncRepository syncRepository) {
    this.erpRestClient = erpRestClient;
    this.productsPath = productsPath;
    this.syncRepository = syncRepository;
  }

  @Scheduled(
      fixedDelayString = "${erp.sync.fixed-delay-ms:900000}",
      initialDelayString = "${erp.sync.initial-delay-ms:1000}")
  public void synchronizeOnSchedule() {
    try {
      syncNow();
    } catch (ErpIntegrationException | DataAccessException | IllegalStateException exception) {
      LOGGER.error("Scheduled ERP synchronization failed.", exception);
    }
  }

  public boolean syncNow() {
    if (!running.compareAndSet(false, true)) {
      return false;
    }

    long syncId = -1;
    try {
      syncId = syncRepository.startSync();
      List<ErpBatchDTO> batches =
          erpRestClient.get().uri(productsPath).retrieve().body(BATCHES_TYPE);
      syncRepository.applySnapshot(syncId, batches == null ? List.of() : batches);
      return true;
    } catch (RestClientException | IllegalArgumentException exception) {
      markFailed(syncId, exception);
      throw new ErpIntegrationException("Could not synchronize the external ERP data.", exception);
    } catch (DataAccessException | IllegalStateException exception) {
      markFailed(syncId, exception);
      throw exception;
    } finally {
      running.set(false);
    }
  }

  public boolean hasCompletedSync() {
    return syncRepository.hasCompletedSync();
  }

  private void markFailed(long syncId, RuntimeException exception) {
    if (syncId <= 0) {
      return;
    }
    try {
      syncRepository.failSync(syncId, exception.getMessage());
    } catch (DataAccessException statusException) {
      exception.addSuppressed(statusException);
      LOGGER.error("Could not record the failed ERP synchronization status.", statusException);
    }
  }
}
