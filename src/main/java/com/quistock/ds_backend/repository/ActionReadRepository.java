package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.exception.ActionNotFoundException;
import com.quistock.ds_backend.model.dto.ActionDTO;
import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ActionReadRepository {
  private final NamedParameterJdbcTemplate jdbc;

  ActionReadRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  List<ActionListItemDTO> findAll(String status, String actionType, Long flowId) {
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("status", status)
            .addValue("actionType", actionType)
            .addValue("flowId", flowId);
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

  String findTypeById(long id) {
    List<String> types =
        jdbc.query(
            "SELECT \"type\"::text FROM suggestion WHERE id = :id",
            new MapSqlParameterSource("id", id),
            (resultSet, rowNumber) -> resultSet.getString(1));
    return types.isEmpty() ? null : types.get(0);
  }

  ActionDTO findActionById(long suggestionId) {
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

  Long findSuggestionIdByFlow(long flowId) {
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
}
