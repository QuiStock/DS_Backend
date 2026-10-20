package com.quistock.ds_backend.repository;

import java.sql.Types;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Store restrictions resolved from the active SQL account, never from JWT role claims. */
public record StoreScope(long actorId, Long storeId, Long regionId) {
  public static final String SQL =
      """
       AND s.status = 'ACTIVE'
       AND (:scopeStoreId IS NULL OR s.id = :scopeStoreId)
       AND (:scopeRegionId IS NULL OR s.region_id = :scopeRegionId)
      """;

  public MapSqlParameterSource parameters() {
    return new MapSqlParameterSource()
        .addValue("scopeStoreId", storeId, Types.BIGINT)
        .addValue("scopeRegionId", regionId, Types.BIGINT);
  }
}
