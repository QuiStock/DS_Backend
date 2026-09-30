package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.ErpBatchDTO;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ErpSyncRepository {
  private final ErpSyncStateRepository stateRepository;
  private final ErpSnapshotProcessor snapshotProcessor;

  public ErpSyncRepository(
      ErpSyncStateRepository stateRepository, ErpSnapshotProcessor snapshotProcessor) {
    this.stateRepository = stateRepository;
    this.snapshotProcessor = snapshotProcessor;
  }

  public long startSync() {
    return stateRepository.startSync();
  }

  public void failSync(long syncId, String errorMessage) {
    stateRepository.failSync(syncId, errorMessage);
  }

  @Transactional
  public SyncResult applySnapshot(long syncId, List<ErpBatchDTO> batches) {
    return snapshotProcessor.applySnapshot(syncId, batches);
  }

  public Instant latestFinishedAt() {
    return stateRepository.latestFinishedAt();
  }

  public boolean hasCompletedSync() {
    return stateRepository.hasCompletedSync();
  }

  public String latestStatus() {
    return stateRepository.latestStatus();
  }

  public record SyncResult(
      int recordsRead, int recordsInserted, int recordsUpdated, int recordsDeactivated) {}
}
