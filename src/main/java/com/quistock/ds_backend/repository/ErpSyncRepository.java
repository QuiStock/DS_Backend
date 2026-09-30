package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.ErpBatchDTO;
import com.quistock.ds_backend.util.ErpValueParser;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ErpSyncRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public ErpSyncRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long startSync() {
    List<Long> ids =
        jdbc.query(
            """
            INSERT INTO erp_sync (started_at, status)
            VALUES (CURRENT_TIMESTAMP, 'STARTED')
            RETURNING id
            """,
            new MapSqlParameterSource(),
            (resultSet, rowNumber) -> resultSet.getLong("id"));
    return ids.get(0);
  }

  public void failSync(long syncId, String errorMessage) {
    jdbc.update(
        """
        UPDATE erp_sync
        SET status = 'FAILED', finished_at = CURRENT_TIMESTAMP, error_message = :error
        WHERE id = :id
        """,
        new MapSqlParameterSource("id", syncId).addValue("error", abbreviate(errorMessage, 4000)));
  }

  @Transactional
  public SyncResult applySnapshot(long syncId, List<ErpBatchDTO> batches) {
    jdbc.getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(74192001)");
    int activeProductCount =
        jdbc.getJdbcTemplate()
            .queryForObject("SELECT COUNT(*) FROM product_store WHERE active = TRUE", Integer.class);
    if (batches.isEmpty() && activeProductCount != null && activeProductCount > 0) {
      throw new IllegalStateException(
          "The ERP returned an empty product snapshot; the existing catalog was preserved.");
    }

    Map<ProductStoreKey, List<ErpBatchDTO>> groups =
        batches.stream()
            .collect(
                Collectors.groupingBy(
                    batch -> new ProductStoreKey(required(batch.erpProductCode(), "product code"),
                        required(batch.erpBranchCode(), "store code")),
                    LinkedHashMap::new,
                    Collectors.toList()));

    Map<String, Long> storeIds = new LinkedHashMap<>();
    Map<String, Long> productIds = new LinkedHashMap<>();
    Map<String, Long> categoryIds = new LinkedHashMap<>();
    Set<String> seenStores = new LinkedHashSet<>();
    int inserted = 0;
    int updated = 0;

    for (Map.Entry<ProductStoreKey, List<ErpBatchDTO>> entry : groups.entrySet()) {
      ProductStoreKey key = entry.getKey();
      List<ErpBatchDTO> productBatches = entry.getValue();
      ErpBatchDTO firstBatch = productBatches.get(0);
      String storeName = required(firstBatch.branch(), "store name");
      Long storeId = storeIds.computeIfAbsent(
          key.storeErpId(), ignored -> upsertStore(key.storeErpId(), storeName, syncId));
      seenStores.add(key.storeErpId());

      String categoryName = clean(firstBatch.category());
      Long categoryId =
          categoryName == null
              ? null
              : categoryIds.computeIfAbsent(
                  categoryName.toLowerCase(java.util.Locale.ROOT),
                  ignored -> upsertCategory(categoryName, syncId));

      ErpBatchDTO newestBatch = findMostRecentBatch(productBatches);
      BigDecimal minimumStock = maximumDecimal(productBatches, ErpBatchDTO::minimumStock);
      BigDecimal unitPrice = ErpValueParser.toBigDecimal(newestBatch.price());
      Long productId =
          productIds.computeIfAbsent(
              key.productErpId(),
              ignored ->
                  upsertProduct(
                      key.productErpId(),
                      required(firstBatch.productName(), "product name"),
                      categoryId,
                      unitPrice,
                      minimumStock,
                      syncId));

      for (ErpBatchDTO batch : productBatches) {
        String erpBatchId = batchErpId(key, batch);
        boolean existed = batchExists(erpBatchId);
        upsertBatch(erpBatchId, batch, productId, storeId, syncId);
        if (existed) {
          updated++;
        } else {
          inserted++;
        }
      }

      upsertProductStore(
          new ProductStoreSnapshot(
              productId,
              storeId,
              minimumStock,
              maximumInteger(productBatches, ErpBatchDTO::leadTimeDays),
              sum(productBatches, ErpBatchDTO::sales7d),
              sum(productBatches, ErpBatchDTO::sales30d),
              unitPrice,
              ErpValueParser.toBigDecimal(newestBatch.cost()),
              ErpValueParser.toInstant(newestBatch.entryDate()),
              syncId));
    }

    int deactivated = deactivateMissing(syncId);
    markSyncCompleted(syncId, batches.size(), inserted, updated, deactivated);
    return new SyncResult(batches.size(), inserted, updated, deactivated);
  }

  public Instant latestFinishedAt() {
    List<Timestamp> values =
        jdbc.query(
            """
            SELECT MAX(finished_at) AS finished_at
            FROM erp_sync
            WHERE status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS')
            """,
            new MapSqlParameterSource(),
            (resultSet, rowNumber) -> resultSet.getTimestamp("finished_at"));
    return values.isEmpty() || values.get(0) == null ? null : values.get(0).toInstant();
  }

  public boolean hasCompletedSync() {
    Boolean completed =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM erp_sync WHERE status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS'))",
            new MapSqlParameterSource(),
            Boolean.class);
    return Boolean.TRUE.equals(completed);
  }

  public String latestStatus() {
    List<String> values =
        jdbc.query(
            "SELECT status::text FROM erp_sync ORDER BY started_at DESC, id DESC LIMIT 1",
            new MapSqlParameterSource(),
            (resultSet, rowNumber) -> resultSet.getString(1));
    return values.isEmpty() ? null : values.get(0);
  }

  private long upsertStore(String erpId, String name, long syncId) {
    List<Long> ids =
        jdbc.query(
            """
            INSERT INTO store (erp_id, name, region_id, status, created_by_id, deactivated_at, last_sync_id)
            VALUES (:erpId, :name, NULL, 'ACTIVE', NULL, NULL, :syncId)
            ON CONFLICT (erp_id) DO UPDATE SET
              name = EXCLUDED.name,
              status = 'ACTIVE',
              deactivated_at = NULL,
              last_sync_id = EXCLUDED.last_sync_id
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("erpId", erpId)
                .addValue("name", name)
                .addValue("syncId", syncId),
            (resultSet, rowNumber) -> resultSet.getLong("id"));
    return ids.get(0);
  }

  private long upsertCategory(String name, long syncId) {
    String erpId = categoryErpId(name);
    List<Long> ids =
        jdbc.query(
            """
            INSERT INTO category (erp_id, name, active, last_sync_id)
            VALUES (:erpId, :name, TRUE, :syncId)
            ON CONFLICT (erp_id) DO UPDATE SET
              name = EXCLUDED.name,
              active = TRUE,
              last_sync_id = EXCLUDED.last_sync_id
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("erpId", erpId)
                .addValue("name", name)
                .addValue("syncId", syncId),
            (resultSet, rowNumber) -> resultSet.getLong("id"));
    return ids.get(0);
  }

  private long upsertProduct(
      String erpId,
      String name,
      Long categoryId,
      BigDecimal salePrice,
      BigDecimal minimumStock,
      long syncId) {
    List<Long> ids =
        jdbc.query(
            """
            INSERT INTO product
              (erp_id, sku, category_id, name, sale_price, minimum_stock_quantity, active, last_sync_id)
            VALUES (:erpId, :sku, :categoryId, :name, :salePrice, :minimumStock, TRUE, :syncId)
            ON CONFLICT (erp_id) DO UPDATE SET
              sku = EXCLUDED.sku,
              category_id = EXCLUDED.category_id,
              name = EXCLUDED.name,
              sale_price = EXCLUDED.sale_price,
              minimum_stock_quantity = EXCLUDED.minimum_stock_quantity,
              active = TRUE,
              last_sync_id = EXCLUDED.last_sync_id
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("erpId", erpId)
                .addValue("sku", erpId)
                .addValue("categoryId", categoryId)
                .addValue("name", name)
                .addValue("salePrice", salePrice)
                .addValue("minimumStock", minimumStock)
                .addValue("syncId", syncId),
            (resultSet, rowNumber) -> resultSet.getLong("id"));
    return ids.get(0);
  }

  private void upsertBatch(
      String erpId, ErpBatchDTO batch, long productId, long storeId, long syncId) {
    String batchNumber = clean(batch.batchNumber());
    if (batchNumber == null) {
      batchNumber = erpId.substring(Math.max(0, erpId.length() - 100));
    }
    BigDecimal quantity = ErpValueParser.toBigDecimal(batch.quantity());
    if (quantity == null) {
      quantity = BigDecimal.ZERO;
    }
    LocalDate expirationDate = ErpValueParser.toLocalDate(batch.expirationDate());
    Timestamp entryDate = timestamp(ErpValueParser.toInstant(batch.entryDate()));
    BigDecimal salePrice = ErpValueParser.toBigDecimal(batch.price());
    BigDecimal unitCost = ErpValueParser.toBigDecimal(batch.cost());
    String qualityCertificate =
        batch.qualityCertificate() == null ? null : batch.qualityCertificate().toString();

    jdbc.update(
        """
        INSERT INTO batch (
          erp_id, batch_number, product_id, store_id, expiration_date, entry_date,
          sale_price, unit_cost, current_balance, quality_certificate, active, last_sync_id
        ) VALUES (
          :erpId, :batchNumber, :productId, :storeId, :expirationDate, :entryDate,
          :salePrice, :unitCost, :balance, :qualityCertificate, TRUE, :syncId
        )
        ON CONFLICT (erp_id) DO UPDATE SET
          batch_number = EXCLUDED.batch_number,
          product_id = EXCLUDED.product_id,
          store_id = EXCLUDED.store_id,
          expiration_date = EXCLUDED.expiration_date,
          entry_date = EXCLUDED.entry_date,
          sale_price = EXCLUDED.sale_price,
          unit_cost = EXCLUDED.unit_cost,
          current_balance = EXCLUDED.current_balance,
          quality_certificate = EXCLUDED.quality_certificate,
          active = TRUE,
          last_sync_id = EXCLUDED.last_sync_id
        """,
        new MapSqlParameterSource()
            .addValue("erpId", erpId)
            .addValue("batchNumber", batchNumber)
            .addValue("productId", productId)
            .addValue("storeId", storeId)
            .addValue("expirationDate", expirationDate)
            .addValue("entryDate", entryDate)
            .addValue("salePrice", salePrice)
            .addValue("unitCost", unitCost)
            .addValue("balance", quantity)
            .addValue("qualityCertificate", qualityCertificate)
            .addValue("syncId", syncId));
  }

  private void upsertProductStore(ProductStoreSnapshot snapshot) {
    jdbc.update(
        """
        INSERT INTO product_store (
          product_id, store_id, minimum_stock_quantity, supplier_lead_time_days,
          sales_7d, sales_30d, sale_price, unit_cost, last_restock_at, active, last_sync_id
        ) VALUES (
          :productId, :storeId, :minimumStock, :leadTime, :sales7d, :sales30d,
          :salePrice, :unitCost, :lastRestock, TRUE, :syncId
        )
        ON CONFLICT (product_id, store_id) DO UPDATE SET
          minimum_stock_quantity = EXCLUDED.minimum_stock_quantity,
          supplier_lead_time_days = EXCLUDED.supplier_lead_time_days,
          sales_7d = EXCLUDED.sales_7d,
          sales_30d = EXCLUDED.sales_30d,
          sale_price = EXCLUDED.sale_price,
          unit_cost = EXCLUDED.unit_cost,
          last_restock_at = EXCLUDED.last_restock_at,
          active = TRUE,
          last_sync_id = EXCLUDED.last_sync_id
        """,
        new MapSqlParameterSource()
            .addValue("productId", snapshot.productId())
            .addValue("storeId", snapshot.storeId())
            .addValue("minimumStock", snapshot.minimumStock())
            .addValue("leadTime", snapshot.supplierLeadTime())
            .addValue("sales7d", snapshot.sales7d())
            .addValue("sales30d", snapshot.sales30d())
            .addValue("salePrice", snapshot.salePrice())
            .addValue("unitCost", snapshot.unitCost())
            .addValue("lastRestock", timestamp(snapshot.lastRestock()))
            .addValue("syncId", snapshot.syncId()));
  }

  private int deactivateMissing(long syncId) {
    int deactivatedBatches =
        jdbc.update(
            "UPDATE batch SET active = FALSE WHERE active = TRUE AND last_sync_id IS DISTINCT FROM :syncId",
            new MapSqlParameterSource("syncId", syncId));
    int deactivatedProductsByStore =
        jdbc.update(
            "UPDATE product_store SET active = FALSE WHERE active = TRUE AND last_sync_id IS DISTINCT FROM :syncId",
            new MapSqlParameterSource("syncId", syncId));
    int deactivatedStores =
        jdbc.update(
            """
            UPDATE store SET status = 'INACTIVE', deactivated_at = CURRENT_TIMESTAMP
            WHERE status = 'ACTIVE' AND last_sync_id IS DISTINCT FROM :syncId
            """,
            new MapSqlParameterSource("syncId", syncId));
    jdbc.update(
        """
        UPDATE product p
        SET active = EXISTS (
          SELECT 1 FROM product_store ps WHERE ps.product_id = p.id AND ps.active = TRUE
        )
        """,
        new MapSqlParameterSource());
    jdbc.update(
        """
        UPDATE category c
        SET active = EXISTS (
          SELECT 1 FROM product p WHERE p.category_id = c.id AND p.active = TRUE
        )
        """,
        new MapSqlParameterSource());
    return deactivatedBatches + deactivatedProductsByStore + deactivatedStores;
  }

  private void markSyncCompleted(
      long syncId, int recordsRead, int inserted, int updated, int deactivated) {
    jdbc.update(
        """
        UPDATE erp_sync
        SET status = 'COMPLETED', finished_at = CURRENT_TIMESTAMP,
            records_read = :recordsRead, records_inserted = :inserted,
            records_updated = :updated, records_deactivated = :deactivated,
            error_message = NULL
        WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("id", syncId)
            .addValue("recordsRead", recordsRead)
            .addValue("inserted", inserted)
            .addValue("updated", updated)
            .addValue("deactivated", deactivated));
  }

  private boolean batchExists(String erpId) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM batch WHERE erp_id = :erpId)",
            new MapSqlParameterSource("erpId", erpId),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  private BigDecimal sum(List<ErpBatchDTO> batches, java.util.function.Function<ErpBatchDTO, Object> field) {
    return batches.stream()
        .map(field)
        .map(ErpValueParser::toBigDecimal)
        .filter(value -> value != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal maximumDecimal(
      List<ErpBatchDTO> batches, java.util.function.Function<ErpBatchDTO, Object> field) {
    return batches.stream()
        .map(field)
        .map(ErpValueParser::toBigDecimal)
        .filter(value -> value != null)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }

  private Integer maximumInteger(
      List<ErpBatchDTO> batches, java.util.function.Function<ErpBatchDTO, Object> field) {
    return batches.stream()
        .map(field)
        .map(ErpValueParser::toInteger)
        .filter(value -> value != null)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }

  private ErpBatchDTO findMostRecentBatch(List<ErpBatchDTO> batches) {
    return batches.stream()
        .max(
            Comparator.comparing(
                    (ErpBatchDTO batch) -> ErpValueParser.toInstant(batch.entryDate()),
                    Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(batch -> java.util.Objects.toString(batch.id(), "")))
        .orElseThrow();
  }

  private String batchErpId(ProductStoreKey key, ErpBatchDTO batch) {
    String sourceId = clean(batch.id());
    if (sourceId == null) {
      sourceId = clean(batch.batchNumber());
    }
    if (sourceId == null) {
      throw new IllegalArgumentException(
          "The ERP batch record is missing a stable ID and batch number.");
    }
    String value = key.productErpId() + ":" + key.storeErpId() + ":" + sourceId;
    if (value.length() > 150) {
      throw new IllegalArgumentException("The composite ERP batch identifier exceeds 150 characters.");
    }
    return value;
  }

  private String categoryErpId(String categoryName) {
    String erpId = "category:" + categoryName.trim().toLowerCase(java.util.Locale.ROOT);
    if (erpId.length() > 150) {
      throw new IllegalArgumentException("The ERP category identifier exceeds 150 characters.");
    }
    return erpId;
  }

  private String required(String value, String field) {
    String cleaned = clean(value);
    if (cleaned == null) {
      throw new IllegalArgumentException("The ERP record is missing " + field + ".");
    }
    return cleaned;
  }

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private String abbreviate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }

  public record SyncResult(int recordsRead, int recordsInserted, int recordsUpdated, int recordsDeactivated) {}

  private record ProductStoreSnapshot(
      long productId,
      long storeId,
      BigDecimal minimumStock,
      Integer supplierLeadTime,
      BigDecimal sales7d,
      BigDecimal sales30d,
      BigDecimal salePrice,
      BigDecimal unitCost,
      Instant lastRestock,
      long syncId) {}

  private record ProductStoreKey(String productErpId, String storeErpId) {}
}
