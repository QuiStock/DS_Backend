package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.exception.ActionNotFoundException;
import com.quistock.ds_backend.model.dto.ActionDTO;
import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.ActionStatusResponse;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.UpdateActionStatusRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ActionRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public ActionRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public ActionDTO createGenerated(
      FlowDTO flow,
      String actionType,
      String justification,
      LocalDate promotionValidFrom,
      LocalDate promotionValidUntil) {
    Long flowId = parseId(flow.id());
    if (flowId == null) {
      throw new IllegalArgumentException("The flow ID must be a persisted analysis ID.");
    }

    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("flowId", flowId)
            .addValue("actionType", actionType)
            .addValue("origin", "EMPLOYEE")
            .addValue("status", "GENERATED")
            .addValue("promotionValidFrom", promotionValidFrom)
            .addValue("promotionValidUntil", promotionValidUntil);

    List<Long> insertedIds =
        jdbc.query(
            """
            INSERT INTO suggestion (
              product_analysis_id, store_id, product_id, "type", origin, status,
              promotion_valid_from, promotion_valid_until, reference_sale_price,
              created_by_id, available_for_triage
            )
            SELECT pa.id, pa.store_id, pa.product_id,
                   CAST(:actionType AS suggestion_type),
                   CAST(:origin AS suggestion_origin),
                   CAST(:status AS suggestion_status),
                   :promotionValidFrom, :promotionValidUntil,
                   CASE WHEN CAST(:actionType AS suggestion_type) = 'PROMOTION'
                     THEN ps.sale_price ELSE NULL END,
                   NULL, TRUE
            FROM product_analysis pa
            JOIN product_store ps
              ON ps.product_id = pa.product_id AND ps.store_id = pa.store_id
            WHERE pa.id = :flowId
            ON CONFLICT (product_analysis_id) WHERE product_analysis_id IS NOT NULL DO NOTHING
            RETURNING id
            """,
            parameters,
            (resultSet, rowNumber) -> resultSet.getLong("id"));

    Long suggestionId = insertedIds.isEmpty() ? findSuggestionIdByFlow(flowId) : insertedIds.get(0);
    if (suggestionId == null) {
      throw new IllegalArgumentException("The analyzed flow has no persisted product snapshot.");
    }

    if (!insertedIds.isEmpty()) {
      writeLog(
          suggestionId,
          new LogData(
              "GENERATED",
              "GENERATED",
              null,
              null,
              null,
              promotionValidFrom,
              promotionValidUntil,
              justification,
              Map.of(),
              data(
                  "status", "GENERATED",
                  "type", actionType,
                  "flow_id", flowId,
                  "promotion_valid_from", promotionValidFrom,
                  "promotion_valid_until", promotionValidUntil)));
    }
    return findActionById(suggestionId);
  }

  public List<ActionListItemDTO> findAll(String status, String actionType, String flowId) {
    Long analysisId = flowId == null ? null : parseId(flowId);
    if (flowId != null && analysisId == null) {
      return List.of();
    }
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("status", status)
            .addValue("actionType", actionType)
            .addValue("flowId", analysisId);
    return jdbc.query(
        """
        SELECT sg.id,
               pa.id AS flow_id,
               p.name AS product_name,
               sg."type"::text AS action_type,
               sg.status::text AS status,
               COALESCE((
                 SELECT sl.reason FROM suggestion_log sl
                 WHERE sl.suggestion_id = sg.id AND sl.event = 'GENERATED'
                 ORDER BY sl.occurred_at, sl.id LIMIT 1
               ), '') AS justification,
               sg.promotion_valid_from,
               sg.promotion_valid_until,
               sd.justification AS decision_justification
        FROM suggestion sg
        JOIN product p ON p.id = sg.product_id
        LEFT JOIN product_analysis pa ON pa.id = sg.product_analysis_id
        LEFT JOIN suggestion_decision sd ON sd.suggestion_id = sg.id
        WHERE (CAST(:status AS text) IS NULL OR sg.status = CAST(:status AS suggestion_status))
          AND (CAST(:actionType AS text) IS NULL OR sg."type" = CAST(:actionType AS suggestion_type))
          AND (CAST(:flowId AS bigint) IS NULL OR sg.product_analysis_id = :flowId)
        ORDER BY sg.created_at DESC, sg.id DESC
        """,
        parameters,
        (resultSet, rowNumber) -> toListItem(resultSet));
  }

  public String findTypeById(String actionId) {
    Long id = parseId(actionId);
    if (id == null) {
      return null;
    }
    List<String> types =
        jdbc.query(
            "SELECT \"type\"::text FROM suggestion WHERE id = :id",
            new MapSqlParameterSource("id", id),
            (resultSet, rowNumber) -> resultSet.getString(1));
    return types.isEmpty() ? null : types.get(0);
  }

  @Transactional
  public ActionStatusResponse updateStatus(String actionId, UpdateActionStatusRequest request) {
    Long id = parseId(actionId);
    if (id == null) {
      throw new ActionNotFoundException(actionId);
    }

    List<StoredSuggestion> rows =
        jdbc.query(
            """
            SELECT id, status::text AS status, "type"::text AS type,
                   promotion_valid_from, promotion_valid_until
            FROM suggestion WHERE id = :id FOR UPDATE
            """,
            new MapSqlParameterSource("id", id),
            (resultSet, rowNumber) -> toStoredSuggestion(resultSet));
    if (rows.isEmpty()) {
      throw new ActionNotFoundException(actionId);
    }

    StoredSuggestion current = rows.get(0);
    LocalDate validFrom = current.validFrom();
    LocalDate validUntil = current.validUntil();
    if (request.finalPromotionValidFrom() != null || request.finalPromotionValidUntil() != null) {
      validFrom = request.finalPromotionValidFrom();
      validUntil = request.finalPromotionValidUntil();
    }

    jdbc.update(
        """
        UPDATE suggestion
        SET status = CAST(:status AS suggestion_status),
            promotion_valid_from = :validFrom,
            promotion_valid_until = :validUntil,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("status", request.status())
            .addValue("validFrom", validFrom)
            .addValue("validUntil", validUntil)
            .addValue("id", id));

    if ("IN_EMPLOYEE_TRIAGE".equals(request.status())) {
      upsertTriage(id, "EDIT", null);
    } else if ("SENT_TO_MANAGER".equals(request.status())) {
      upsertTriage(id, "FORWARD", null);
    }

    if ("APPROVED".equals(request.status()) || "REJECTED".equals(request.status())) {
      upsertDecision(id, request.status(), request.justification(), validFrom, validUntil);
    }

    String event = eventFor(request.status());
    writeLog(
        id,
        new LogData(
            event,
            request.status(),
            null,
            null,
            null,
            validFrom,
            validUntil,
            request.justification(),
            data("status", current.status()),
            data(
                "status", request.status(),
                "promotion_valid_from", validFrom,
                "promotion_valid_until", validUntil)));
    return new ActionStatusResponse(actionId, request.status(), validFrom, validUntil);
  }

  private void upsertTriage(long suggestionId, String action, Long actorId) {
    jdbc.update(
        """
        INSERT INTO suggestion_triage
          (suggestion_id, employee_id, last_action, forwarded_at, updated_at)
        VALUES
          (:suggestionId, :actorId, CAST(:action AS triage_action),
           CASE WHEN :action = 'FORWARD' THEN CURRENT_TIMESTAMP ELSE NULL END,
           CURRENT_TIMESTAMP)
        ON CONFLICT (suggestion_id) DO UPDATE SET
          employee_id = EXCLUDED.employee_id,
          last_action = EXCLUDED.last_action,
          forwarded_at = EXCLUDED.forwarded_at,
          updated_at = CURRENT_TIMESTAMP
        """,
        new MapSqlParameterSource()
            .addValue("suggestionId", suggestionId)
            .addValue("actorId", actorId)
            .addValue("action", action));
  }

  private void upsertDecision(
      long suggestionId,
      String status,
      String justification,
      LocalDate validFrom,
      LocalDate validUntil) {
    String decision = "APPROVED".equals(status) ? "APPROVE" : "REJECT";
    jdbc.update(
        """
        INSERT INTO suggestion_decision (
          suggestion_id, manager_id, decision, final_promotion_valid_from,
          final_promotion_valid_until, justification, decided_at
        ) VALUES (
          :suggestionId, NULL, CAST(:decision AS decision_type),
          :validFrom, :validUntil, :justification, CURRENT_TIMESTAMP
        )
        ON CONFLICT (suggestion_id) DO UPDATE SET
          manager_id = NULL,
          decision = EXCLUDED.decision,
          final_promotion_valid_from = EXCLUDED.final_promotion_valid_from,
          final_promotion_valid_until = EXCLUDED.final_promotion_valid_until,
          justification = EXCLUDED.justification,
          decided_at = CURRENT_TIMESTAMP
        """,
        new MapSqlParameterSource()
            .addValue("suggestionId", suggestionId)
            .addValue("decision", decision)
            .addValue("validFrom", "REJECTED".equals(status) ? null : validFrom)
            .addValue("validUntil", "REJECTED".equals(status) ? null : validUntil)
            .addValue("justification", justification));
  }

  private void writeLog(long suggestionId, LogData log) {
    jdbc.update(
        """
        INSERT INTO suggestion_log (
          suggestion_id, user_id, event, recorded_status, batch_quantity,
          discount_percentage, promotional_price, valid_from, valid_until,
          previous_data, new_data, reason, occurred_at
        ) VALUES (
          :suggestionId, NULL, CAST(:event AS suggestion_log_event), :status,
          :batchQuantity, :discountPercentage, :promotionalPrice, :validFrom, :validUntil,
          CAST(:previousData AS jsonb), CAST(:newData AS jsonb), :reason, CURRENT_TIMESTAMP
        )
        """,
        new MapSqlParameterSource()
            .addValue("suggestionId", suggestionId)
            .addValue("event", log.event())
            .addValue("status", log.status())
            .addValue("batchQuantity", log.batchQuantity())
            .addValue("discountPercentage", log.discountPercentage())
            .addValue("promotionalPrice", log.promotionalPrice())
            .addValue("validFrom", log.validFrom())
            .addValue("validUntil", log.validUntil())
            .addValue("previousData", toJson(log.previousData()))
            .addValue("newData", toJson(log.newData()))
            .addValue("reason", log.reason()));
  }

  private ActionDTO findActionById(long suggestionId) {
    List<ActionDTO> actions =
        jdbc.query(
            """
            SELECT sg.id, sg."type"::text AS action_type, sg.status::text AS status,
                   COALESCE((SELECT sl.reason FROM suggestion_log sl
                     WHERE sl.suggestion_id = sg.id AND sl.event = 'GENERATED'
                     ORDER BY sl.occurred_at, sl.id LIMIT 1), '') AS justification,
                   sg.promotion_valid_from, sg.promotion_valid_until
            FROM suggestion sg WHERE sg.id = :id
            """,
            new MapSqlParameterSource("id", suggestionId),
            (resultSet, rowNumber) -> toAction(resultSet));
    if (actions.isEmpty()) {
      throw new ActionNotFoundException(Long.toString(suggestionId));
    }
    return actions.get(0);
  }

  private Long findSuggestionIdByFlow(long flowId) {
    List<Long> ids =
        jdbc.query(
            "SELECT id FROM suggestion WHERE product_analysis_id = :flowId",
            new MapSqlParameterSource("flowId", flowId),
            (resultSet, rowNumber) -> resultSet.getLong("id"));
    return ids.isEmpty() ? null : ids.get(0);
  }

  private ActionDTO toAction(ResultSet resultSet) throws SQLException {
    return new ActionDTO(
        resultSet.getString("id"),
        resultSet.getString("action_type"),
        resultSet.getString("status"),
        resultSet.getString("justification"),
        resultSet.getObject("promotion_valid_from", LocalDate.class),
        resultSet.getObject("promotion_valid_until", LocalDate.class));
  }

  private ActionListItemDTO toListItem(ResultSet resultSet) throws SQLException {
    return new ActionListItemDTO(
        resultSet.getString("id"),
        resultSet.getString("flow_id"),
        resultSet.getString("product_name"),
        resultSet.getString("action_type"),
        resultSet.getString("status"),
        resultSet.getString("justification"),
        resultSet.getObject("promotion_valid_from", LocalDate.class),
        resultSet.getObject("promotion_valid_until", LocalDate.class),
        resultSet.getString("decision_justification"));
  }

  private StoredSuggestion toStoredSuggestion(ResultSet resultSet) throws SQLException {
    return new StoredSuggestion(
        resultSet.getLong("id"),
        resultSet.getString("status"),
        resultSet.getString("type"),
        resultSet.getObject("promotion_valid_from", LocalDate.class),
        resultSet.getObject("promotion_valid_until", LocalDate.class));
  }

  private String eventFor(String status) {
    return switch (status) {
      case "IN_EMPLOYEE_TRIAGE" -> "EDITED";
      case "SENT_TO_MANAGER" -> "FORWARDED";
      case "APPROVED" -> "APPROVED";
      case "REJECTED" -> "REJECTED";
      default -> "GENERATED";
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

  private Long parseId(String id) {
    try {
      return id == null ? null : Long.parseLong(id);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private record StoredSuggestion(
      long id, String status, String type, LocalDate validFrom, LocalDate validUntil) {}

  private record LogData(
      String event,
      String status,
      Integer batchQuantity,
      java.math.BigDecimal discountPercentage,
      java.math.BigDecimal promotionalPrice,
      LocalDate validFrom,
      LocalDate validUntil,
      String reason,
      Map<String, Object> previousData,
      Map<String, Object> newData) {}
}
