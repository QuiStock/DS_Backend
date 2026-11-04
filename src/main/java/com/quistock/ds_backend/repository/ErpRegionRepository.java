package com.quistock.ds_backend.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** Persists ERP region identifiers without violating the region code/name unique constraints. */
@Repository
class ErpRegionRepository {
  private static final int SINGLE_MATCH = 1;
  private static final String NAME_PARAMETER = "name";

  private final EntityManager entityManager;

  ErpRegionRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  long upsertRegion(String regionCode) {
    List<Map<String, Object>> matches = findRegionsByIdentity(regionCode);
    if (matches.size() > SINGLE_MATCH) {
      throw new IllegalStateException(
          "The ERP region code and name resolve to different stored regions.");
    }
    if (matches.size() == SINGLE_MATCH) {
      return updateRegion(matches.getFirst(), regionCode);
    }

    List<Map<String, Object>> inserted =
        JpaNativeQuery.rows(
            entityManager,
            """
            INSERT INTO region_type (code, name, active)
            VALUES (:code, :name, TRUE)
            ON CONFLICT DO NOTHING
            RETURNING id
            """,
            JpaNativeQuery.parameters("code", regionCode, NAME_PARAMETER, regionCode));
    if (!inserted.isEmpty()) {
      return JpaNativeQuery.longValue(inserted.getFirst(), "id");
    }

    matches = findRegionsByIdentity(regionCode);
    if (matches.size() == SINGLE_MATCH) {
      return updateRegion(matches.getFirst(), regionCode);
    }
    throw new IllegalStateException("The ERP region could not be resolved to one stored region.");
  }

  private List<Map<String, Object>> findRegionsByIdentity(String regionCode) {
    return JpaNativeQuery.rows(
        entityManager,
        """
        SELECT id, code, name FROM region_type
        WHERE code = :code OR name = :name
        ORDER BY id
        FOR UPDATE
        """,
        JpaNativeQuery.parameters("code", regionCode, NAME_PARAMETER, regionCode));
  }

  private long updateRegion(Map<String, Object> match, String regionCode) {
    return JpaNativeQuery.longValue(
        JpaNativeQuery.rows(
                entityManager,
                """
                UPDATE region_type
                SET code = :code, name = :name, active = TRUE
                WHERE id = :id
                RETURNING id
                """,
                JpaNativeQuery.parameters(
                    "id",
                    JpaNativeQuery.longValue(match, "id"),
                    "code",
                    regionCode,
                    NAME_PARAMETER,
                    regionCode))
            .getFirst(),
        "id");
  }
}
