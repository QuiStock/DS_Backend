package com.quistock.ds_backend.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ErpSyncStateRepository {
  private static final int MAX_ERROR_LENGTH = 4000;
  private static final String SYNC_ID_PARAMETER = "syncId";

  private final NamedParameterJdbcTemplate jdbc;

  ErpSyncStateRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  long startSync() {
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

  void failSync(long syncId, String errorMessage) {
    jdbc.update(
        """
        UPDATE erp_sync
        SET status = 'FAILED', finished_at = CURRENT_TIMESTAMP, error_message = :error
        WHERE id = :syncId
        """,
        new MapSqlParameterSource(SYNC_ID_PARAMETER, syncId)
            .addValue("error", abbreviate(errorMessage)));
  }

  Instant latestFinishedAt() {
    List<OffsetDateTime> values =
        jdbc.query(
            """
            SELECT MAX(finished_at) AS finished_at
            FROM erp_sync
            WHERE status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS')
            """,
            new MapSqlParameterSource(),
            (resultSet, rowNumber) -> resultSet.getObject("finished_at", OffsetDateTime.class));
    return values.isEmpty() || values.get(0) == null ? null : values.get(0).toInstant();
  }

  boolean hasCompletedSync() {
    Boolean completed =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM erp_sync WHERE status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS'))",
            new MapSqlParameterSource(),
            Boolean.class);
    return Boolean.TRUE.equals(completed);
  }

  String latestStatus() {
    List<String> values =
        jdbc.query(
            "SELECT status::text FROM erp_sync ORDER BY started_at DESC, id DESC LIMIT 1",
            new MapSqlParameterSource(),
            (resultSet, rowNumber) -> resultSet.getString(1));
    return values.isEmpty() ? null : values.get(0);
  }

  void markCompleted(long syncId, int recordsRead, int inserted, int updated, int deactivated) {
    jdbc.update(
        """
        UPDATE erp_sync
        SET status = 'COMPLETED', finished_at = CURRENT_TIMESTAMP,
            records_read = :recordsRead, records_inserted = :inserted,
            records_updated = :updated, records_deactivated = :deactivated,
            error_message = NULL
        WHERE id = :syncId
        """,
        new MapSqlParameterSource()
            .addValue(SYNC_ID_PARAMETER, syncId)
            .addValue("recordsRead", recordsRead)
            .addValue("inserted", inserted)
            .addValue("updated", updated)
            .addValue("deactivated", deactivated));
  }

  private String abbreviate(String value) {
    if (value == null || value.length() <= MAX_ERROR_LENGTH) {
      return value;
    }
    return value.substring(0, MAX_ERROR_LENGTH);
  }
}
