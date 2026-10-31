package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.ErpBatchDTO;
import com.quistock.ds_backend.util.ErpValueParser;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** PostgreSQL upserts preserve ERP identity and the existing snapshot deactivation rules. */
@Repository
class ErpSnapshotRepository {
  private static final String ERP_ID_PARAMETER = "erpId";
  private static final String SYNC_ID_PARAMETER = "syncId";
  private static final String NAME_PARAMETER = "name";

  private final EntityManager entityManager;
  private final ErpProjectionLock projectionLock;

  ErpSnapshotRepository(EntityManager entityManager, ErpProjectionLock projectionLock) {
    this.entityManager = entityManager;
    this.projectionLock = projectionLock;
  }

  int activeProductCount() {
    projectionLock.acquire();
    Map<String, Object> row =
        JpaNativeQuery.rows(
                entityManager,
                "SELECT COUNT(*) AS count FROM product_store WHERE active = TRUE",
                Map.of())
            .getFirst();
    return JpaNativeQuery.longValue(row, "count").intValue();
  }

  long upsertRegion(String regionCode, long syncId) {
    return returnedId(
        """
        INSERT INTO region_type (code, name, active)
        VALUES (:code, :name, TRUE)
        ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, active = TRUE
        RETURNING id
        """,
        JpaNativeQuery.parameters("code", regionCode, NAME_PARAMETER, regionCode));
  }

  long upsertStore(String erpId, String name, Long regionId, long syncId) {
    return returnedId(
        """
        INSERT INTO store (erp_id, name, region_id, status, created_by_id, deactivated_at, last_sync_id)
        VALUES (:erpId, :name, :regionId, 'ACTIVE', NULL, NULL, :syncId)
        ON CONFLICT (erp_id) DO UPDATE SET
          name = EXCLUDED.name,
          region_id = COALESCE(EXCLUDED.region_id, store.region_id),
          status = 'ACTIVE', deactivated_at = NULL, last_sync_id = EXCLUDED.last_sync_id
        RETURNING id
        """,
        JpaNativeQuery.parameters(
            ERP_ID_PARAMETER,
            erpId,
            NAME_PARAMETER,
            name,
            "regionId",
            regionId,
            SYNC_ID_PARAMETER,
            syncId));
  }

  long upsertCategory(String erpId, String name, long syncId) {
    return returnedId(
        """
        INSERT INTO category (erp_id, name, active, last_sync_id)
        VALUES (:erpId, :name, TRUE, :syncId)
        ON CONFLICT (erp_id) DO UPDATE SET
          name = EXCLUDED.name, active = TRUE, last_sync_id = EXCLUDED.last_sync_id
        RETURNING id
        """,
        JpaNativeQuery.parameters(
            ERP_ID_PARAMETER, erpId, NAME_PARAMETER, name, SYNC_ID_PARAMETER, syncId));
  }

  long upsertProduct(
      String erpId,
      String name,
      Long categoryId,
      BigDecimal salePrice,
      BigDecimal minimumStock,
      long syncId) {
    return returnedId(
        """
        INSERT INTO product
          (erp_id, sku, category_id, name, sale_price, minimum_stock_quantity, active, last_sync_id)
        VALUES (:erpId, :sku, :categoryId, :name, :salePrice, :minimumStock, TRUE, :syncId)
        ON CONFLICT (erp_id) DO UPDATE SET
          sku = EXCLUDED.sku, category_id = EXCLUDED.category_id, name = EXCLUDED.name,
          sale_price = EXCLUDED.sale_price, minimum_stock_quantity = EXCLUDED.minimum_stock_quantity,
          active = TRUE, last_sync_id = EXCLUDED.last_sync_id
        RETURNING id
        """,
        JpaNativeQuery.parameters(
            ERP_ID_PARAMETER,
            erpId,
            "sku",
            erpId,
            "categoryId",
            categoryId,
            NAME_PARAMETER,
            name,
            "salePrice",
            salePrice,
            "minimumStock",
            minimumStock,
            SYNC_ID_PARAMETER,
            syncId));
  }

  void upsertBatch(String erpId, ErpBatchDTO batch, long productId, long storeId, long syncId) {
    String batchNumber = ErpIdentifiers.clean(batch.batchNumber());
    if (batchNumber == null) {
      batchNumber = erpId.substring(Math.max(0, erpId.length() - 100));
    }
    BigDecimal quantity = ErpValueParser.toBigDecimal(batch.quantity());
    if (quantity == null) {
      quantity = BigDecimal.ZERO;
    }
    BigDecimal salePrice = ErpValueParser.toBigDecimal(batch.price());
    BigDecimal unitCost = ErpValueParser.toBigDecimal(batch.cost());
    String qualityCertificate =
        batch.qualityCertificate() == null ? null : batch.qualityCertificate().toString();

    JpaNativeQuery.update(
        entityManager,
        """
        INSERT INTO batch (
          erp_id, batch_number, product_id, store_id, expiration_date, entry_date,
          sale_price, unit_cost, current_balance, quality_certificate, active, last_sync_id
        ) VALUES (
          :erpId, :batchNumber, :productId, :storeId, :expirationDate, :entryDate,
          :salePrice, :unitCost, :balance, :qualityCertificate, TRUE, :syncId
        )
        ON CONFLICT (erp_id) DO UPDATE SET
          batch_number = EXCLUDED.batch_number, product_id = EXCLUDED.product_id,
          store_id = EXCLUDED.store_id, expiration_date = EXCLUDED.expiration_date,
          entry_date = EXCLUDED.entry_date, sale_price = EXCLUDED.sale_price,
          unit_cost = EXCLUDED.unit_cost, current_balance = EXCLUDED.current_balance,
          quality_certificate = EXCLUDED.quality_certificate, active = TRUE,
          last_sync_id = EXCLUDED.last_sync_id
        """,
        JpaNativeQuery.parameters(
            ERP_ID_PARAMETER,
            erpId,
            "batchNumber",
            batchNumber,
            "productId",
            productId,
            "storeId",
            storeId,
            "expirationDate",
            ErpValueParser.toLocalDate(batch.expirationDate()),
            "entryDate",
            ErpIdentifiers.databaseTimestamp(ErpValueParser.toInstant(batch.entryDate())),
            "salePrice",
            salePrice,
            "unitCost",
            unitCost,
            "balance",
            quantity,
            "qualityCertificate",
            qualityCertificate,
            SYNC_ID_PARAMETER,
            syncId));
  }

  void upsertProductStore(ErpProductStoreSnapshot snapshot) {
    JpaNativeQuery.update(
        entityManager,
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
          sales_7d = EXCLUDED.sales_7d, sales_30d = EXCLUDED.sales_30d,
          sale_price = EXCLUDED.sale_price, unit_cost = EXCLUDED.unit_cost,
          last_restock_at = EXCLUDED.last_restock_at, active = TRUE,
          last_sync_id = EXCLUDED.last_sync_id
        """,
        JpaNativeQuery.parameters(
            "productId",
            snapshot.productId(),
            "storeId",
            snapshot.storeId(),
            "minimumStock",
            snapshot.minimumStock(),
            "leadTime",
            snapshot.supplierLeadTime(),
            "sales7d",
            snapshot.sales7d(),
            "sales30d",
            snapshot.sales30d(),
            "salePrice",
            snapshot.salePrice(),
            "unitCost",
            snapshot.unitCost(),
            "lastRestock",
            ErpIdentifiers.databaseTimestamp(snapshot.lastRestock()),
            SYNC_ID_PARAMETER,
            snapshot.syncId()));
  }

  boolean batchExists(String erpId) {
    return Boolean.TRUE.equals(
        JpaNativeQuery.rows(
                entityManager,
                "SELECT EXISTS (SELECT 1 FROM batch WHERE erp_id = :erpId) AS exists",
                Map.of(ERP_ID_PARAMETER, erpId))
            .getFirst()
            .get("exists"));
  }

  int deactivateMissing(long syncId) {
    int deactivatedBatches =
        JpaNativeQuery.update(
            entityManager,
            "UPDATE batch SET active = FALSE WHERE active = TRUE AND last_sync_id IS DISTINCT FROM :syncId",
            Map.of(SYNC_ID_PARAMETER, syncId));
    int deactivatedProductsByStore =
        JpaNativeQuery.update(
            entityManager,
            "UPDATE product_store SET active = FALSE WHERE active = TRUE AND last_sync_id IS DISTINCT FROM :syncId",
            Map.of(SYNC_ID_PARAMETER, syncId));
    int deactivatedStores =
        JpaNativeQuery.update(
            entityManager,
            """
            UPDATE store SET status = 'INACTIVE', deactivated_at = CURRENT_TIMESTAMP
            WHERE status = 'ACTIVE' AND last_sync_id IS DISTINCT FROM :syncId
            """,
            Map.of(SYNC_ID_PARAMETER, syncId));
    JpaNativeQuery.update(
        entityManager,
        """
        UPDATE product p SET active = EXISTS (
          SELECT 1 FROM product_store ps WHERE ps.product_id = p.id AND ps.active = TRUE
        )
        """,
        Map.of());
    JpaNativeQuery.update(
        entityManager,
        """
        UPDATE category c SET active = EXISTS (
          SELECT 1 FROM product p WHERE p.category_id = c.id AND p.active = TRUE
        )
        """,
        Map.of());
    return deactivatedBatches + deactivatedProductsByStore + deactivatedStores;
  }

  private long returnedId(String sql, Map<String, ?> parameters) {
    return JpaNativeQuery.longValue(
        JpaNativeQuery.rows(entityManager, sql, parameters).getFirst(), "id");
  }
}
