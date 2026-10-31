package com.quistock.ds_backend.repository;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AnalysisExecutionRepository {
  public static final String DAILY_MODEL_VERSION = "SALES_PERCENTILES_V1_DAILY";
  private final EntityManager entityManager;

  public AnalysisExecutionRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public boolean tryDailyLock() {
    return Boolean.TRUE.equals(
        entityManager
            .createNativeQuery("SELECT pg_try_advisory_xact_lock(74192002)")
            .getSingleResult());
  }

  public boolean completedDailyRun(LocalDate date) {
    return Boolean.TRUE.equals(
        JpaNativeQuery.rows(
                entityManager,
                """
        SELECT EXISTS (SELECT 1 FROM ml_execution
        WHERE model_version = :model AND cutoff_date = :date AND status = 'COMPLETED')
        """,
                Map.of("model", DAILY_MODEL_VERSION, "date", date))
            .getFirst()
            .get("exists"));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public long start(String model, LocalDate date, Instant now) {
    return JpaNativeQuery.longValue(
        JpaNativeQuery.rows(
                entityManager,
                """
        INSERT INTO ml_execution
          (model_version, execution_date, cutoff_date, window_start_date, window_end_date, status)
        VALUES (:model, :now, :date, :windowStart, :date, 'STARTED') RETURNING id
        """,
                Map.of(
                    "model",
                    model,
                    "now",
                    OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                    "date",
                    date,
                    "windowStart",
                    date.minusDays(6)))
            .getFirst(),
        "id");
  }

  public void complete(long executionId) {
    JpaNativeQuery.update(
        entityManager,
        "UPDATE ml_execution SET status = 'COMPLETED' WHERE id = :id",
        Map.of("id", executionId));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void fail(long executionId) {
    JpaNativeQuery.update(
        entityManager,
        "UPDATE ml_execution SET status = 'FAILED' WHERE id = :id",
        Map.of("id", executionId));
  }
}
