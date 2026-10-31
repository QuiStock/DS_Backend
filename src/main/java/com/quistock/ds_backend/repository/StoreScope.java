package com.quistock.ds_backend.repository;

import java.util.HashMap;
import java.util.Map;

/** Store restrictions resolved from the active SQL account, never from JWT role claims. */
public record StoreScope(long actorId, Long storeId, Long regionId) {
  public String sql() {
    return " AND s.status = 'ACTIVE'"
        + (storeId == null ? "" : " AND s.id = :scopeStoreId")
        + (regionId == null ? "" : " AND s.region_id = :scopeRegionId");
  }

  public Map<String, Object> parameters() {
    Map<String, Object> parameters = new HashMap<>();
    if (storeId != null) {
      parameters.put("scopeStoreId", storeId);
    }
    if (regionId != null) {
      parameters.put("scopeRegionId", regionId);
    }
    return parameters;
  }
}
