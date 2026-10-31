package com.quistock.ds_backend.repository;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
class ActionAuditRepository {
  private static final String GENERATED = "GENERATED";
  private static final String EDITED = "EDITED";
  private static final String FORWARDED = "FORWARDED";
  private static final String APPROVED = "APPROVED";
  private static final String REJECTED = "REJECTED";
  private static final String STATUS_KEY = "status";
  private static final String PROMOTION_FROM_KEY = "promotion_valid_from";
  private static final String PROMOTION_UNTIL_KEY = "promotion_valid_until";

  private final EntityManager entityManager;
  private final ObjectMapper objectMapper;

  ActionAuditRepository(EntityManager entityManager, ObjectMapper objectMapper) {
    this.entityManager = entityManager;
    this.objectMapper = objectMapper;
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
                "type",
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
    JpaNativeQuery.update(
        entityManager,
        """
        INSERT INTO suggestion_log (
          suggestion_id, user_id, event, recorded_status, batch_quantity,
          discount_percentage, promotional_price, valid_from, valid_until,
          previous_data, new_data, reason, occurred_at
        ) VALUES (
          :suggestionId, :userId, CAST(:event AS suggestion_log_event), :status,
          (SELECT current_batch_count FROM suggestion WHERE id = :suggestionId),
          (SELECT current_discount_percentage FROM suggestion WHERE id = :suggestionId),
          (SELECT promotional_price FROM suggestion WHERE id = :suggestionId), :validFrom, :validUntil,
          CAST(:previousData AS jsonb), CAST(:newData AS jsonb) ||
            (SELECT jsonb_build_object('quantity', current_batch_count,
               'desired_order_date', desired_order_date, 'discount_percentage', current_discount_percentage,
               'reference_sale_price', reference_sale_price, 'promotional_price', promotional_price)
             FROM suggestion WHERE id = :suggestionId), :reason, CURRENT_TIMESTAMP
        )
        """,
        JpaNativeQuery.parameters(
            "suggestionId",
            suggestionId,
            "userId",
            log.userId(),
            "event",
            log.event(),
            "status",
            log.status(),
            "validFrom",
            log.validFrom(),
            "validUntil",
            log.validUntil(),
            "previousData",
            toJson(log.previousData()),
            "newData",
            toJson(log.newData()),
            "reason",
            log.reason()));
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

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Could not serialize suggestion history JSON.", exception);
    }
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
