package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.ErpBatchDTO;
import com.quistock.ds_backend.util.ErpValueParser;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ErpSnapshotProcessor {
  private static final int MAX_REGION_CODE_LENGTH = 50;
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

    batches.forEach(this::validateRequiredErpFields);
    Map<String, String> regionCodesByStore = validateStoreRegionConsistency(batches);

    Map<ProductStoreKey, List<ErpBatchDTO>> groups =
        batches.stream()
            .collect(
                Collectors.groupingBy(
                    batch ->
                        new ProductStoreKey(
                            ErpIdentifiers.productSku(batch.erpProductCode()),
                            ErpIdentifiers.storeId(batch)),
                    LinkedHashMap::new,
                    Collectors.toList()));
    SnapshotContext context = new SnapshotContext(syncId, regionCodesByStore);
    groups.forEach((key, productBatches) -> persistProductStore(key, productBatches, context));

    int deactivated = snapshotRepository.deactivateMissing(syncId);
    stateRepository.markCompleted(
        syncId, batches.size(), context.inserted, context.updated, deactivated);
    return new ErpSyncRepository.SyncResult(
        batches.size(), context.inserted, context.updated, deactivated);
  }

  private Map<String, String> validateStoreRegionConsistency(List<ErpBatchDTO> batches) {
    Map<String, String> regionCodesByStore = new HashMap<>();
    for (ErpBatchDTO batch : batches) {
      String regionCode = ErpIdentifiers.clean(batch.regionId());
      if (regionCode == null) {
        continue;
      }
      if (regionCode.length() > MAX_REGION_CODE_LENGTH) {
        throw new IllegalArgumentException("The ERP region_id exceeds 50 characters.");
      }
      String storeCode = ErpIdentifiers.storeId(batch);
      String existing = regionCodesByStore.putIfAbsent(storeCode, regionCode);
      if (existing != null && !existing.equals(regionCode)) {
        throw new IllegalArgumentException(
            "The ERP records contain conflicting region_id values for one store.");
      }
    }
    return regionCodesByStore;
  }

  private void validateRequiredErpFields(ErpBatchDTO batch) {
    if (ErpValueParser.toLocalDate(batch.expirationDate()) == null) {
      throw new IllegalArgumentException("The ERP record is missing expiration date.");
    }
    requireNonNegativeDecimal(batch.price(), "price");
    requireNonNegativeDecimal(batch.cost(), "cost");
  }

  private void requireNonNegativeDecimal(Object value, String field) {
    BigDecimal amount = ErpValueParser.toBigDecimal(value);
    if (amount == null) {
      throw new IllegalArgumentException("The ERP record is missing " + field + ".");
    }
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("The ERP record has a negative " + field + ".");
    }
  }

  private void persistProductStore(
      ProductStoreKey key, List<ErpBatchDTO> productBatches, SnapshotContext context) {
    ErpBatchDTO firstBatch = productBatches.get(0);
    String storeName = ErpIdentifiers.required(firstBatch.branch(), "store name");
    String regionCode = context.regionCodeForStore(key.storeErpId());
    Long regionId =
        regionCode == null
            ? null
            : context.regionId(
                regionCode, () -> snapshotRepository.upsertRegion(regionCode, context.syncId));
    long storeId =
        context.storeId(
            key.storeErpId(),
            () ->
                snapshotRepository.upsertStore(
                    key.storeErpId(), storeName, regionId, context.syncId));

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
    private final Map<String, String> regionCodesByStore;
    private final Map<String, Long> storeIds = new LinkedHashMap<>();
    private final Map<String, Long> regionIds = new LinkedHashMap<>();
    private final Map<String, Long> productIds = new LinkedHashMap<>();
    private final Map<String, Long> categoryIds = new LinkedHashMap<>();
    private int inserted;
    private int updated;

    private SnapshotContext(long syncId, Map<String, String> regionCodesByStore) {
      this.syncId = syncId;
      this.regionCodesByStore = regionCodesByStore;
    }

    private long storeId(String erpId, java.util.function.LongSupplier supplier) {
      return storeIds.computeIfAbsent(erpId, ignored -> supplier.getAsLong());
    }

    private long regionId(String regionCode, java.util.function.LongSupplier supplier) {
      return regionIds.computeIfAbsent(regionCode, ignored -> supplier.getAsLong());
    }

    private Long categoryId(String name, java.util.function.LongSupplier supplier) {
      return categoryIds.computeIfAbsent(name, ignored -> supplier.getAsLong());
    }

    private long productId(String erpId, java.util.function.LongSupplier supplier) {
      return productIds.computeIfAbsent(erpId, ignored -> supplier.getAsLong());
    }

    private String regionCodeForStore(String storeErpId) {
      return regionCodesByStore.get(storeErpId);
    }
  }
}
