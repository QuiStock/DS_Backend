package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.ErpBatchDTO;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ErpSnapshotProcessor {
  private final ErpSnapshotRepository snapshotRepository;
  private final ErpSyncStateRepository stateRepository;

  ErpSnapshotProcessor(
      ErpSnapshotRepository snapshotRepository, ErpSyncStateRepository stateRepository) {
    this.snapshotRepository = snapshotRepository;
    this.stateRepository = stateRepository;
  }

  @Transactional
  ErpSyncRepository.SyncResult applySnapshot(long syncId, List<ErpBatchDTO> batches) {
    int currentProductCount = snapshotRepository.activeProductCount();
    if (batches.isEmpty() && currentProductCount > 0) {
      throw new IllegalStateException(
          "The ERP returned an empty product snapshot; the existing catalog was preserved.");
    }

    Map<ProductStoreKey, List<ErpBatchDTO>> groups =
        batches.stream()
            .collect(
                Collectors.groupingBy(
                    batch ->
                        new ProductStoreKey(
                            ErpIdentifiers.required(batch.erpProductCode(), "product code"),
                            ErpIdentifiers.storeId(batch)),
                    LinkedHashMap::new,
                    Collectors.toList()));
    SnapshotContext context = new SnapshotContext(syncId);
    groups.forEach((key, productBatches) -> persistProductStore(key, productBatches, context));

    int deactivated = snapshotRepository.deactivateMissing(syncId);
    stateRepository.markCompleted(
        syncId, batches.size(), context.inserted, context.updated, deactivated);
    return new ErpSyncRepository.SyncResult(
        batches.size(), context.inserted, context.updated, deactivated);
  }

  private void persistProductStore(
      ProductStoreKey key, List<ErpBatchDTO> productBatches, SnapshotContext context) {
    ErpBatchDTO firstBatch = productBatches.get(0);
    String storeName = ErpIdentifiers.required(firstBatch.branch(), "store name");
    long storeId =
        context.storeId(
            key.storeErpId(),
            () -> snapshotRepository.upsertStore(key.storeErpId(), storeName, context.syncId));

    String categoryName = ErpIdentifiers.clean(firstBatch.category());
    Long categoryId =
        categoryName == null
            ? null
            : context.categoryId(
                categoryName.toLowerCase(java.util.Locale.ROOT),
                () ->
                    snapshotRepository.upsertCategory(
                        ErpIdentifiers.categoryId(categoryName), categoryName, context.syncId));

    ErpBatchDTO newestBatch = ErpBatchAggregator.mostRecent(productBatches);
    BigDecimal minimumStock =
        ErpBatchAggregator.maximumDecimal(productBatches, ErpBatchDTO::minimumStock);
    BigDecimal unitPrice = ErpBatchAggregator.decimal(newestBatch.price());
    long productId =
        context.productId(
            key.productErpId(),
            () ->
                snapshotRepository.upsertProduct(
                    key.productErpId(),
                    ErpIdentifiers.required(firstBatch.productName(), "product name"),
                    categoryId,
                    unitPrice,
                    minimumStock,
                    context.syncId));

    persistBatches(key, productBatches, productId, storeId, context);
    snapshotRepository.upsertProductStore(
        new ErpProductStoreSnapshot(
            productId,
            storeId,
            minimumStock,
            ErpBatchAggregator.maximumInteger(productBatches, ErpBatchDTO::leadTimeDays),
            ErpBatchAggregator.sum(productBatches, ErpBatchDTO::sales7d),
            ErpBatchAggregator.sum(productBatches, ErpBatchDTO::sales30d),
            unitPrice,
            ErpBatchAggregator.decimal(newestBatch.cost()),
            ErpBatchAggregator.instant(newestBatch.entryDate()),
            context.syncId));
  }

  private void persistBatches(
      ProductStoreKey key,
      List<ErpBatchDTO> productBatches,
      long productId,
      long storeId,
      SnapshotContext context) {
    for (ErpBatchDTO batch : productBatches) {
      String erpBatchId = ErpIdentifiers.batchId(key.productErpId(), key.storeErpId(), batch);
      boolean existed = snapshotRepository.batchExists(erpBatchId);
      snapshotRepository.upsertBatch(erpBatchId, batch, productId, storeId, context.syncId);
      if (existed) {
        context.updated++;
      } else {
        context.inserted++;
      }
    }
  }

  private record ProductStoreKey(String productErpId, String storeErpId) {}

  private static final class SnapshotContext {
    private final long syncId;
    private final Map<String, Long> storeIds = new LinkedHashMap<>();
    private final Map<String, Long> productIds = new LinkedHashMap<>();
    private final Map<String, Long> categoryIds = new LinkedHashMap<>();
    private int inserted;
    private int updated;

    private SnapshotContext(long syncId) {
      this.syncId = syncId;
    }

    private long storeId(String erpId, java.util.function.LongSupplier supplier) {
      return storeIds.computeIfAbsent(erpId, ignored -> supplier.getAsLong());
    }

    private Long categoryId(String name, java.util.function.LongSupplier supplier) {
      return categoryIds.computeIfAbsent(name, ignored -> supplier.getAsLong());
    }

    private long productId(String erpId, java.util.function.LongSupplier supplier) {
      return productIds.computeIfAbsent(erpId, ignored -> supplier.getAsLong());
    }
  }
}
