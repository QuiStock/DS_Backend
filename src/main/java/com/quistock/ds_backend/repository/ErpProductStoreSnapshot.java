package com.quistock.ds_backend.repository;

import java.math.BigDecimal;
import java.time.Instant;

record ErpProductStoreSnapshot(
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
