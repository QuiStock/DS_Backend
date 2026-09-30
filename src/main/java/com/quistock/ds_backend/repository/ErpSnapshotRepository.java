package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.ErpBatchDTO;
import com.quistock.ds_backend.util.ErpValueParser;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ErpSnapshotRepository {
  private static final String ERP_ID_PARAMETER = "erpId";
  private static final String SYNC_ID_PARAMETER = "syncId";

  private final NamedParameterJdbcTemplate jdbc;
  private final JdbcTemplate jdbcTemplate;

  ErpSnapshotRepository(NamedParameterJdbcTemplate jdbc, JdbcTemplate jdbcTemplate) {
    this.jdbc = jdbc;
    this.jdbcTemplate = jdbcTemplate;
  }

  int activeProductCount() {
    jdbcTemplate.execute("SELECT pg_advisory_xact_lock(74192001)");
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_store WHERE active = TRUE", Integer.class);
    return count == null ? 0 : count;
  }

  long upsertStore(String erpId, String name, long syncId) {
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
                .addValue(ERP_ID_PARAMETER, erpId)
                .addValue("name", name)
                .addValue(SYNC_ID_PARAMETER, syncId),
            (resultSet, rowNumber) -> resultSet.getLong("id"));
    return ids.get(0);
  }

  long upsertCategory(String erpId, String name, long syncId) {
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
                .addValue(ERP_ID_PARAMETER, erpId)
                .addValue("name", name)
                .addValue(SYNC_ID_PARAMETER, syncId),
            (resultSet, rowNumber) -> resultSet.getLong("id"));
    return ids.get(0);
  }

  long upsertProduct(
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
                .addValue(ERP_ID_PARAMETER, erpId)
                .addValue("sku", erpId)
                .addValue("categoryId", categoryId)
                .addValue("name", name)
                .addValue("salePrice", salePrice)
                .addValue("minimumStock", minimumStock)
                .addValue(SYNC_ID_PARAMETER, syncId),
            (resultSet, rowNumber) -> resultSet.getLong("id"));
    return ids.get(0);
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
            .addValue(ERP_ID_PARAMETER, erpId)
            .addValue("batchNumber", batchNumber)
            .addValue("productId", productId)
            .addValue("storeId", storeId)
            .addValue("expirationDate", ErpValueParser.toLocalDate(batch.expirationDate()))
            .addValue(
                "entryDate",
                ErpIdentifiers.databaseTimestamp(ErpValueParser.toInstant(batch.entryDate())))
            .addValue("salePrice", salePrice)
            .addValue("unitCost", unitCost)
            .addValue("balance", quantity)
            .addValue("qualityCertificate", qualityCertificate)
            .addValue(SYNC_ID_PARAMETER, syncId));
  }

  void upsertProductStore(ErpProductStoreSnapshot snapshot) {
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
            .addValue("lastRestock", ErpIdentifiers.databaseTimestamp(snapshot.lastRestock()))
            .addValue(SYNC_ID_PARAMETER, snapshot.syncId()));
  }

  boolean batchExists(String erpId) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM batch WHERE erp_id = :erpId)",
            new MapSqlParameterSource(ERP_ID_PARAMETER, erpId),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  int deactivateMissing(long syncId) {
    int deactivatedBatches =
        jdbc.update(
            "UPDATE batch SET active = FALSE WHERE active = TRUE AND last_sync_id IS DISTINCT FROM :syncId",
            new MapSqlParameterSource(SYNC_ID_PARAMETER, syncId));
    int deactivatedProductsByStore =
        jdbc.update(
            "UPDATE product_store SET active = FALSE WHERE active = TRUE AND last_sync_id IS DISTINCT FROM :syncId",
            new MapSqlParameterSource(SYNC_ID_PARAMETER, syncId));
    int deactivatedStores =
        jdbc.update(
            """
            UPDATE store SET status = 'INACTIVE', deactivated_at = CURRENT_TIMESTAMP
            WHERE status = 'ACTIVE' AND last_sync_id IS DISTINCT FROM :syncId
            """,
            new MapSqlParameterSource(SYNC_ID_PARAMETER, syncId));
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
}
