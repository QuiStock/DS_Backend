package com.quistock.ds_backend.repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  ActionAuditRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
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
    jdbc.update(
        """
        INSERT INTO suggestion_log (
          suggestion_id, user_id, event, recorded_status, batch_quantity,
          discount_percentage, promotional_price, valid_from, valid_until,
          previous_data, new_data, reason, occurred_at
        ) VALUES (
          :suggestionId, :userId, CAST(:event AS suggestion_log_event), :status,
          NULL, NULL, NULL, :validFrom, :validUntil,
          CAST(:previousData AS jsonb), CAST(:newData AS jsonb), :reason, CURRENT_TIMESTAMP
        )
        """,
        new MapSqlParameterSource()
            .addValue("suggestionId", suggestionId)
            .addValue("userId", log.userId())
            .addValue("event", log.event())
            .addValue("status", log.status())
            .addValue("validFrom", log.validFrom())
            .addValue("validUntil", log.validUntil())
            .addValue("previousData", toJson(log.previousData()))
            .addValue("newData", toJson(log.newData()))
            .addValue("reason", log.reason()));
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
