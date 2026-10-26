package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.exception.ActionNotFoundException;
import com.quistock.ds_backend.exception.ConflictException;
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
  private static final String FLOW_ID = "flowId";
  private final NamedParameterJdbcTemplate jdbc;

  ActionReadRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  List<ActionListItemDTO> findAll(String status, String actionType, Long flowId, StoreScope scope) {
    MapSqlParameterSource parameters =
        scope
            .parameters()
            .addValue("status", status)
            .addValue("actionType", actionType)
            .addValue(FLOW_ID, flowId);
    return jdbc.query(
        """
        SELECT sg.id, sg.current_batch_count, sg.desired_order_date,
               sg.current_discount_percentage, sg.reference_sale_price, sg.promotional_price,
               sg.store_id, p.erp_id || ':' || s.erp_id AS public_product_id,
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
        JOIN store s ON s.id = sg.store_id
        JOIN product p ON p.id = sg.product_id
        LEFT JOIN product_analysis pa ON pa.id = sg.product_analysis_id
        LEFT JOIN suggestion_decision sd ON sd.suggestion_id = sg.id
        WHERE (CAST(:status AS text) IS NULL OR sg.status = CAST(:status AS suggestion_status))
          AND (CAST(:actionType AS text) IS NULL OR sg."type" = CAST(:actionType AS suggestion_type))
          AND (CAST(:flowId AS bigint) IS NULL OR sg.product_analysis_id = :flowId)
        """
            + StoreScope.SQL
            + " ORDER BY sg.created_at DESC, sg.id DESC",
        parameters,
        (resultSet, rowNumber) -> toListItem(resultSet));
  }

  String findTypeById(long id, StoreScope scope) {
    List<String> types =
        jdbc.query(
            "SELECT sg.\"type\"::text FROM suggestion sg JOIN store s ON s.id = sg.store_id"
                + " WHERE sg.id = :id"
                + StoreScope.SQL,
            scope.parameters().addValue("id", id),
            (resultSet, rowNumber) -> resultSet.getString(1));
    return types.isEmpty() ? null : types.get(0);
  }

  ActionDTO findActionById(long suggestionId, StoreScope scope) {
    List<ActionDTO> actions =
        jdbc.query(
            """
            SELECT sg.id, sg.current_batch_count, sg.desired_order_date,
                   sg.current_discount_percentage, sg.reference_sale_price, sg.promotional_price,
                   sg.store_id, p.erp_id || ':' || s.erp_id AS public_product_id,
                   sg."type"::text AS action_type, sg.status::text AS status,
                   COALESCE((SELECT sl.reason FROM suggestion_log sl
                     WHERE sl.suggestion_id = sg.id AND sl.event = 'GENERATED'
                     ORDER BY sl.occurred_at, sl.id LIMIT 1), '') AS justification,
                   sg.promotion_valid_from, sg.promotion_valid_until
            FROM suggestion sg JOIN store s ON s.id = sg.store_id
            JOIN product p ON p.id = sg.product_id WHERE sg.id = :id
            """
                + StoreScope.SQL,
            scope.parameters().addValue("id", suggestionId),
            (resultSet, rowNumber) -> toAction(resultSet));
    if (actions.isEmpty()) {
      throw new ActionNotFoundException(Long.toString(suggestionId));
    }
    return actions.get(0);
  }

  Long findSuggestionIdByFlow(long flowId, String type, StoreScope scope) {
    List<Long> ids =
        jdbc.query(
            "SELECT sg.id FROM suggestion sg JOIN store s ON s.id = sg.store_id"
                + " WHERE sg.product_analysis_id = :flowId AND sg.type = CAST(:type AS suggestion_type)"
                + StoreScope.SQL,
            scope.parameters().addValue(FLOW_ID, flowId).addValue("type", type),
            (resultSet, rowNumber) -> resultSet.getLong("id"));
    return ids.isEmpty() ? null : ids.get(0);
  }

  void lockProductAndCheckPending(long flowId, StoreScope scope) {
    jdbc.query(
        """
        SELECT ps.id FROM product_analysis pa
        JOIN store s ON s.id = pa.store_id
        JOIN product_store ps ON ps.product_id = pa.product_id AND ps.store_id = pa.store_id
        WHERE pa.id = :flowId
        """
            + StoreScope.SQL
            + " FOR UPDATE OF ps",
        scope.parameters().addValue(FLOW_ID, flowId),
        (row, index) -> row.getLong(1));
  }

  void requireCurrentAnalysis(long flowId, StoreScope scope) {
    Boolean current =
        jdbc.queryForObject(
            """
        SELECT EXISTS (SELECT 1 FROM product_analysis pa JOIN store s ON s.id = pa.store_id
        WHERE pa.id = :flowId AND pa.context ->> 'classifier' = 'SALES_PERCENTILES_V1'
          AND pa.context ->> 'analysis_status' = 'ANALYZED'
          AND NOT EXISTS (SELECT 1 FROM product_analysis newer
            WHERE newer.store_id = pa.store_id AND newer.product_id = pa.product_id
              AND (newer.created_at, newer.id) > (pa.created_at, pa.id))
        """
                + StoreScope.SQL
                + ")",
            scope.parameters().addValue(FLOW_ID, flowId),
            Boolean.class);
    if (!Boolean.TRUE.equals(current)) {
      throw new ConflictException();
    }
  }

  void rejectOtherPending(long flowId, String type, StoreScope scope) {
    Boolean pending =
        jdbc.queryForObject(
            """
        SELECT EXISTS (SELECT 1 FROM suggestion sg
        JOIN product_analysis pa ON pa.store_id = sg.store_id AND pa.product_id = sg.product_id
        JOIN store s ON s.id = sg.store_id
        WHERE pa.id = :flowId AND sg.type = CAST(:type AS suggestion_type)
          AND sg.product_analysis_id IS DISTINCT FROM :flowId
          AND sg.status IN ('GENERATED', 'IN_EMPLOYEE_TRIAGE', 'SENT_TO_MANAGER')
        """
                + StoreScope.SQL
                + ")",
            scope.parameters().addValue(FLOW_ID, flowId).addValue("type", type),
            Boolean.class);
    if (Boolean.TRUE.equals(pending)) {
      throw new ConflictException();
    }
  }

  private ActionDTO toAction(ResultSet resultSet) throws SQLException {
    return new ActionDTO(
        resultSet.getString("id"),
        resultSet.getString("action_type"),
        resultSet.getString("status"),
        resultSet.getString("justification"),
        resultSet.getObject("promotion_valid_from", LocalDate.class),
        resultSet.getObject("promotion_valid_until", LocalDate.class),
        resultSet.getObject("current_batch_count", Integer.class),
        resultSet.getObject("desired_order_date", LocalDate.class),
        resultSet.getBigDecimal("current_discount_percentage"),
        resultSet.getBigDecimal("reference_sale_price"),
        resultSet.getBigDecimal("promotional_price"),
        resultSet.getString("public_product_id"),
        resultSet.getLong("store_id"));
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
        resultSet.getString("decision_justification"),
        resultSet.getObject("current_batch_count", Integer.class),
        resultSet.getObject("desired_order_date", LocalDate.class),
        resultSet.getBigDecimal("current_discount_percentage"),
        resultSet.getBigDecimal("reference_sale_price"),
        resultSet.getBigDecimal("promotional_price"),
        resultSet.getString("public_product_id"),
        resultSet.getLong("store_id"));
  }
}
