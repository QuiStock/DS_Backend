package com.quistock.ds_backend.repository;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ErpSyncStateRepository {
  private static final int MAX_ERROR_LENGTH = 4000;
  private static final String SYNC_ID_PARAMETER = "syncId";

  private final EntityManager entityManager;

  ErpSyncStateRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional
  long startSync() {
    return JpaNativeQuery.longValue(
        JpaNativeQuery.rows(
                entityManager,
                """
            INSERT INTO erp_sync (started_at, status)
            VALUES (CURRENT_TIMESTAMP, 'STARTED')
            RETURNING id
            """,
                Map.of())
            .getFirst(),
        "id");
  }

  @Transactional
  void failSync(long syncId, String errorMessage) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put(SYNC_ID_PARAMETER, syncId);
    parameters.put("error", abbreviate(errorMessage));
    JpaNativeQuery.update(
        entityManager,
        """
        UPDATE erp_sync
        SET status = 'FAILED', finished_at = CURRENT_TIMESTAMP, error_message = :error
        WHERE id = :syncId
        """,
        parameters);
  }

  Instant latestFinishedAt() {
    Object value =
        JpaNativeQuery.rows(
                entityManager,
                """
            SELECT MAX(finished_at) AS finished_at
            FROM erp_sync
            WHERE status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS')
            """,
                Map.of())
            .getFirst()
            .get("finished_at");
    return value == null
        ? null
        : JpaNativeQuery.instant(Map.of("finished_at", value), "finished_at");
  }

  boolean hasCompletedSync() {
    Boolean completed =
        (Boolean)
            JpaNativeQuery.rows(
                    entityManager,
                    "SELECT EXISTS (SELECT 1 FROM erp_sync WHERE status IN ('COMPLETED',"
                        + " 'COMPLETED_WITH_ERRORS'))",
                    Map.of())
                .getFirst()
                .get("exists");
    return Boolean.TRUE.equals(completed);
  }

  String latestStatus() {
    return JpaNativeQuery.string(
        JpaNativeQuery.rows(
                entityManager,
                "SELECT status::text AS status FROM erp_sync ORDER BY started_at DESC, id DESC LIMIT 1",
                Map.of())
            .stream()
            .findFirst()
            .orElse(Map.of()),
        "status");
  }

  void markCompleted(long syncId, int recordsRead, int inserted, int updated, int deactivated) {
    JpaNativeQuery.update(
        entityManager,
        """
        UPDATE erp_sync
        SET status = 'COMPLETED', finished_at = CURRENT_TIMESTAMP,
            records_read = :recordsRead, records_inserted = :inserted,
            records_updated = :updated, records_deactivated = :deactivated,
            error_message = NULL
        WHERE id = :syncId
        """,
        Map.of(
            SYNC_ID_PARAMETER,
            syncId,
            "recordsRead",
            recordsRead,
            "inserted",
            inserted,
            "updated",
            updated,
            "deactivated",
            deactivated));
  }

  private String abbreviate(String value) {
    if (value == null || value.length() <= MAX_ERROR_LENGTH) {
      return value;
    }
    return value.substring(0, MAX_ERROR_LENGTH);
  }
}
