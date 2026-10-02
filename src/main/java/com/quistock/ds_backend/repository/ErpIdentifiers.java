package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.ErpBatchDTO;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;

final class ErpIdentifiers {
  private static final int MAX_ID_LENGTH = 150;
  private static final int MAX_SKU_LENGTH = 100;
  private static final String CATEGORY_PREFIX = "category:";

  private ErpIdentifiers() {}

  static String batchId(String productErpId, String storeErpId, ErpBatchDTO batch) {
    String sourceId = clean(batch.id());
    if (sourceId == null) {
      sourceId = clean(batch.batchNumber());
    }
    if (sourceId == null) {
      throw new IllegalArgumentException(
          "The ERP batch record is missing a stable ID and batch number.");
    }
    String value = productErpId + ":" + storeErpId + ":" + sourceId;
    if (value.length() > MAX_ID_LENGTH) {
      throw new IllegalArgumentException(
          "The composite ERP batch identifier exceeds " + MAX_ID_LENGTH + " characters.");
    }
    return value;
  }

  static String categoryId(String categoryName) {
    String value = CATEGORY_PREFIX + categoryName.trim().toLowerCase(Locale.ROOT);
    if (value.length() > MAX_ID_LENGTH) {
      throw new IllegalArgumentException(
          "The ERP category identifier exceeds " + MAX_ID_LENGTH + " characters.");
    }
    return value;
  }

  static String required(String value, String field) {
    String cleaned = clean(value);
    if (cleaned == null) {
      throw new IllegalArgumentException("The ERP record is missing " + field + ".");
    }
    return cleaned;
  }

  static String productSku(String productErpId) {
    String sku = required(productErpId, "product code");
    if (sku.length() > MAX_SKU_LENGTH) {
      throw new IllegalArgumentException(
          "The ERP product code used as SKU exceeds " + MAX_SKU_LENGTH + " characters.");
    }
    return sku;
  }

  static String storeId(ErpBatchDTO batch) {
    String branchCode = clean(batch.erpBranchCode());
    return branchCode == null ? required(batch.branch(), "store code or branch name") : branchCode;
  }

  static String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  static String publicProductId(String productErpId, String storeErpId) {
    return "%s:%s".formatted(Objects.toString(productErpId, ""), Objects.toString(storeErpId, ""));
  }

  static OffsetDateTime databaseTimestamp(Instant instant) {
    return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
