package com.quistock.ds_backend.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AnalysisExecutionRepository {
  public static final String DAILY_MODEL_VERSION = "SALES_PERCENTILES_V1_DAILY";
  private final NamedParameterJdbcTemplate jdbc;

  public AnalysisExecutionRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean tryDailyLock() {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT pg_try_advisory_xact_lock(74192002)",
            new MapSqlParameterSource(),
            Boolean.class));
  }

  public boolean completedDailyRun(LocalDate date) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        SELECT EXISTS (SELECT 1 FROM ml_execution
        WHERE model_version = :model AND cutoff_date = :date AND status = 'COMPLETED')
        """,
            new MapSqlParameterSource("model", DAILY_MODEL_VERSION).addValue("date", date),
            Boolean.class));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public long start(String model, LocalDate date, Instant now) {
    return jdbc.queryForObject(
        """
        INSERT INTO ml_execution
          (model_version, execution_date, cutoff_date, window_start_date, window_end_date, status)
        VALUES (:model, :now, :date, :windowStart, :date, 'STARTED') RETURNING id
        """,
        new MapSqlParameterSource("model", model)
            .addValue("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
            .addValue("date", date)
            .addValue("windowStart", date.minusDays(6)),
        Long.class);
  }

  public void complete(long executionId) {
    jdbc.update(
        "UPDATE ml_execution SET status = 'COMPLETED' WHERE id = :id",
        new MapSqlParameterSource("id", executionId));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void fail(long executionId) {
    jdbc.update(
        "UPDATE ml_execution SET status = 'FAILED' WHERE id = :id",
        new MapSqlParameterSource("id", executionId));
  }
}
