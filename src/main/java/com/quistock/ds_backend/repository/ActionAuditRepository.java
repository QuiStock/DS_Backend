package com.quistock.ds_backend.repository;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
class ActionAuditRepository {
  private static final String GENERATED = "GENERATED";
  private static final String EDITED = "EDITED";
  private static final String FORWARDED = "FORWARDED";
  private static final String APPROVED = "APPROVED";
  private static final String REJECTED = "REJECTED";
  private static final String STATUS_KEY = "status";
  private static final String TYPE_KEY = "type";
  private static final String PROMOTION_FROM_KEY = "promotion_valid_from";
  private static final String PROMOTION_UNTIL_KEY = "promotion_valid_until";

  private final EntityManager entityManager;

  ActionAuditRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  void logGenerated(GeneratedLog log) {
    writeLog(
        log.suggestionId(),
        new LogData(
            GENERATED,
            GENERATED,
            log.userId(),
            log.validFrom(),
            log.validUntil(),
            log.justification(),
            Map.of(),
            data(
                STATUS_KEY,
                GENERATED,
                TYPE_KEY,
                log.actionType(),
                "flow_id",
                log.flowId(),
                PROMOTION_FROM_KEY,
                log.validFrom(),
                PROMOTION_UNTIL_KEY,
                log.validUntil())));
  }

  void logStatusChanged(StatusChangedLog log) {
    writeLog(
        log.suggestionId(),
        new LogData(
            eventFor(log.status()),
            log.status(),
            log.userId(),
            log.validFrom(),
            log.validUntil(),
            log.justification(),
            data(STATUS_KEY, log.currentStatus()),
            data(
                STATUS_KEY, log.status(),
                PROMOTION_FROM_KEY, log.validFrom(),
                PROMOTION_UNTIL_KEY, log.validUntil())));
  }

  private void writeLog(long suggestionId, LogData log) {
    Map<String, Object> parameters =
        JpaNativeQuery.parameters(
            "suggestionId", suggestionId,
            "userId", log.userId(),
            "event", log.event(),
            "reason", log.reason());

    long logId =
        JpaNativeQuery.longValue(
            JpaNativeQuery.rows(
                    entityManager,
                    """
                    INSERT INTO suggestion_log (suggestion_id, user_id, event, reason, occurred_at)
                    VALUES (:suggestionId, :userId, CAST(:event AS suggestion_log_event),
                            :reason, CURRENT_TIMESTAMP)
                    RETURNING id
                    """,
                    parameters)
                .getFirst(),
            "id");

    if (!log.previousData().isEmpty()) {
      insertPreviousSnapshot(logId, log.previousData());
    }
    insertAfterSnapshot(suggestionId, logId, log);
  }

  private void insertPreviousSnapshot(long logId, Map<String, Object> snapshot) {
    JpaNativeQuery.update(
        entityManager,
        """
        INSERT INTO suggestion_log_snapshot (
          suggestion_log_id, snapshot_side, status, type, flow_id, quantity,
          desired_order_date, discount_percentage, reference_sale_price,
          promotional_price, promotion_valid_from, promotion_valid_until
        ) VALUES (
          :logId, 'BEFORE', CAST(:status AS text),
          CAST(:type AS suggestion_type), CAST(:flowId AS bigint),
          CAST(:quantity AS integer), CAST(:desiredOrderDate AS date),
          CAST(:discount AS numeric(5,2)), CAST(:referencePrice AS numeric(12,2)),
          CAST(:promotionalPrice AS numeric(12,2)),
          CAST(:validFrom AS date), CAST(:validUntil AS date)
        )
        """,
        JpaNativeQuery.parameters(
            "logId",
            logId,
            "status",
            snapshot.get(STATUS_KEY),
            TYPE_KEY,
            snapshot.get(TYPE_KEY),
            "flowId",
            snapshot.get("flow_id"),
            "quantity",
            snapshot.get("quantity"),
            "desiredOrderDate",
            snapshot.get("desired_order_date"),
            "discount",
            snapshot.get("discount_percentage"),
            "referencePrice",
            snapshot.get("reference_sale_price"),
            "promotionalPrice",
            snapshot.get("promotional_price"),
            "validFrom",
            snapshot.get(PROMOTION_FROM_KEY),
            "validUntil",
            snapshot.get(PROMOTION_UNTIL_KEY)));
  }

  private void insertAfterSnapshot(long suggestionId, long logId, LogData log) {
    JpaNativeQuery.update(
        entityManager,
        """
        INSERT INTO suggestion_log_snapshot (
          suggestion_log_id, snapshot_side, status, type, flow_id, quantity,
          desired_order_date, discount_percentage, reference_sale_price,
          promotional_price, promotion_valid_from, promotion_valid_until
        )
        SELECT :logId, 'AFTER',
               COALESCE(CAST(:status AS text), sg.status::text),
               COALESCE(CAST(:type AS suggestion_type), sg.type),
               COALESCE(CAST(:flowId AS bigint), sg.product_analysis_id),
               sg.current_batch_count, sg.desired_order_date,
               sg.current_discount_percentage, sg.reference_sale_price,
               sg.promotional_price, CAST(:validFrom AS date), CAST(:validUntil AS date)
        FROM suggestion sg
        WHERE sg.id = :suggestionId
        """,
        JpaNativeQuery.parameters(
            "suggestionId", suggestionId,
            "logId", logId,
            "status", log.newData().get(STATUS_KEY),
            TYPE_KEY, log.newData().get(TYPE_KEY),
            "flowId", log.newData().get("flow_id"),
            "validFrom", log.validFrom(),
            "validUntil", log.validUntil()));
  }

  private String eventFor(String status) {
    return switch (status) {
      case "IN_EMPLOYEE_TRIAGE" -> EDITED;
      case "SENT_TO_MANAGER" -> FORWARDED;
      case APPROVED -> APPROVED;
      case REJECTED -> REJECTED;
      default -> GENERATED;
    };
  }

  private Map<String, Object> data(Object... keyValues) {
    Map<String, Object> values = new HashMap<>();
    for (int index = 0; index < keyValues.length; index += 2) {
      values.put((String) keyValues[index], keyValues[index + 1]);
    }
    return values;
  }

  private record LogData(
      String event,
      String status,
      long userId,
      LocalDate validFrom,
      LocalDate validUntil,
      String reason,
      Map<String, Object> previousData,
      Map<String, Object> newData) {}

  record GeneratedLog(
      long suggestionId,
      String actionType,
      long flowId,
      long userId,
      LocalDate validFrom,
      LocalDate validUntil,
      String justification) {}

  record StatusChangedLog(
      long suggestionId,
      long userId,
      String currentStatus,
      String status,
      LocalDate validFrom,
      LocalDate validUntil,
      String justification) {}
}
