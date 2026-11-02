package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.exception.ActionNotFoundException;
import com.quistock.ds_backend.exception.ConflictException;
import com.quistock.ds_backend.model.dto.ActionDTO;
import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
class ActionReadRepository {
  private static final String FLOW_ID = "flowId";
  private final EntityManager entityManager;

  ActionReadRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  List<ActionListItemDTO> findAll(String status, String actionType, Long flowId, StoreScope scope) {
    Map<String, Object> parameters = new HashMap<>(scope.parameters());
    parameters.put("status", status);
    parameters.put("actionType", actionType);
    parameters.put(FLOW_ID, flowId);
    String sql =
        """
        SELECT sg.id, sg.current_batch_count, sg.desired_order_date,
               sg.current_discount_percentage, sg.reference_sale_price, sg.promotional_price,
               COALESCE(pa.store_id, ps.store_id) AS store_id,
               p.erp_id || ':' || s.erp_id AS public_product_id,
               pa.id AS flow_id, p.name AS product_name,
               sg."type"::text AS action_type, sg.status::text AS status,
               COALESCE((SELECT sl.reason FROM suggestion_log sl
                 WHERE sl.suggestion_id = sg.id AND sl.event = 'GENERATED'
                 ORDER BY sl.occurred_at, sl.id LIMIT 1), '') AS justification,
               sg.promotion_valid_from, sg.promotion_valid_until,
               sd.justification AS decision_justification
        FROM suggestion sg
        LEFT JOIN product_analysis pa ON pa.id = sg.product_analysis_id
        LEFT JOIN product_store ps ON ps.id = sg.product_store_id
        JOIN store s ON s.id = COALESCE(pa.store_id, ps.store_id)
        JOIN product p ON p.id = COALESCE(pa.product_id, ps.product_id)
        LEFT JOIN suggestion_decision sd ON sd.suggestion_id = sg.id
        WHERE (CAST(:status AS text) IS NULL OR sg.status = CAST(:status AS suggestion_status))
          AND (CAST(:actionType AS text) IS NULL OR sg."type" = CAST(:actionType AS suggestion_type))
          AND (CAST(:flowId AS bigint) IS NULL OR sg.product_analysis_id = :flowId)
        """
            + scope.sql()
            + " ORDER BY sg.created_at DESC, sg.id DESC";
    return JpaNativeQuery.rows(entityManager, sql, parameters).stream()
        .map(this::toListItem)
        .toList();
  }

  String findTypeById(long id, StoreScope scope) {
    List<Map<String, Object>> types =
        JpaNativeQuery.rows(
            entityManager,
            "SELECT sg.\"type\"::text AS action_type FROM suggestion sg"
                + " LEFT JOIN product_analysis pa ON pa.id = sg.product_analysis_id"
                + " LEFT JOIN product_store ps ON ps.id = sg.product_store_id"
                + " JOIN store s ON s.id = COALESCE(pa.store_id, ps.store_id)"
                + " WHERE sg.id = :id"
                + scope.sql(),
            parameters(scope, "id", id));
    return types.isEmpty() ? null : JpaNativeQuery.string(types.getFirst(), "action_type");
  }

  ActionDTO findActionById(long suggestionId, StoreScope scope) {
    List<ActionDTO> actions =
        JpaNativeQuery.rows(
                entityManager,
                """
                SELECT sg.id, sg.current_batch_count, sg.desired_order_date,
                       sg.current_discount_percentage, sg.reference_sale_price, sg.promotional_price,
                       COALESCE(pa.store_id, ps.store_id) AS store_id,
                       p.erp_id || ':' || s.erp_id AS public_product_id,
                       sg."type"::text AS action_type, sg.status::text AS status,
                       COALESCE((SELECT sl.reason FROM suggestion_log sl
                         WHERE sl.suggestion_id = sg.id AND sl.event = 'GENERATED'
                         ORDER BY sl.occurred_at, sl.id LIMIT 1), '') AS justification,
                       sg.promotion_valid_from, sg.promotion_valid_until
                FROM suggestion sg
                LEFT JOIN product_analysis pa ON pa.id = sg.product_analysis_id
                LEFT JOIN product_store ps ON ps.id = sg.product_store_id
                JOIN store s ON s.id = COALESCE(pa.store_id, ps.store_id)
                JOIN product p ON p.id = COALESCE(pa.product_id, ps.product_id)
                WHERE sg.id = :id
                """
                    + scope.sql(),
                parameters(scope, "id", suggestionId))
            .stream()
            .map(this::toAction)
            .toList();
    if (actions.isEmpty()) {
      throw new ActionNotFoundException(Long.toString(suggestionId));
    }
    return actions.getFirst();
  }

  Long findSuggestionIdByFlow(long flowId, String type, StoreScope scope) {
    return JpaNativeQuery.rows(
            entityManager,
            "SELECT sg.id FROM suggestion sg"
                + " JOIN product_analysis pa ON pa.id = sg.product_analysis_id"
                + " JOIN store s ON s.id = pa.store_id"
                + " WHERE sg.product_analysis_id = :flowId"
                + " AND sg.type = CAST(:type AS suggestion_type)"
                + scope.sql(),
            parameters(scope, FLOW_ID, flowId, "type", type))
        .stream()
        .map(row -> JpaNativeQuery.longValue(row, "id"))
        .findFirst()
        .orElse(null);
  }

  void lockProductAndCheckPending(long flowId, StoreScope scope) {
    JpaNativeQuery.rows(
        entityManager,
        """
        SELECT ps.id FROM product_analysis pa
        JOIN store s ON s.id = pa.store_id
        JOIN product_store ps ON ps.product_id = pa.product_id AND ps.store_id = pa.store_id
        WHERE pa.id = :flowId
        """
            + scope.sql()
            + " FOR UPDATE OF ps",
        parameters(scope, FLOW_ID, flowId));
  }

  void requireCurrentAnalysis(long flowId, StoreScope scope) {
    boolean current =
        Boolean.TRUE.equals(
            JpaNativeQuery.rows(
                    entityManager,
                    """
                    SELECT EXISTS (SELECT 1 FROM product_analysis pa JOIN store s ON s.id = pa.store_id
                    WHERE pa.id = :flowId AND pa.classifier = 'SALES_PERCENTILES_V1'
                      AND pa.analysis_status = 'ANALYZED'
                      AND NOT EXISTS (SELECT 1 FROM product_analysis newer
                        WHERE newer.store_id = pa.store_id AND newer.product_id = pa.product_id
                          AND (newer.created_at, newer.id) > (pa.created_at, pa.id))
                    """
                        + scope.sql()
                        + ") AS current",
                    parameters(scope, FLOW_ID, flowId))
                .getFirst()
                .get("current"));
    if (!current) {
      throw new ConflictException();
    }
  }

  void rejectOtherPending(long flowId, String type, StoreScope scope) {
    boolean pending =
        Boolean.TRUE.equals(
            JpaNativeQuery.rows(
                    entityManager,
                    """
                    SELECT EXISTS (SELECT 1 FROM suggestion sg
                    LEFT JOIN product_analysis source_pa ON source_pa.id = sg.product_analysis_id
                    LEFT JOIN product_store source_ps ON source_ps.id = sg.product_store_id
                    JOIN product_analysis target_pa ON target_pa.id = :flowId
                    JOIN store s ON s.id = COALESCE(source_pa.store_id, source_ps.store_id)
                    WHERE COALESCE(source_pa.store_id, source_ps.store_id) = target_pa.store_id
                      AND COALESCE(source_pa.product_id, source_ps.product_id) = target_pa.product_id
                      AND sg.type = CAST(:type AS suggestion_type)
                      AND sg.product_analysis_id IS DISTINCT FROM :flowId
                      AND sg.status IN ('GENERATED', 'IN_EMPLOYEE_TRIAGE', 'SENT_TO_MANAGER')
                    """
                        + scope.sql()
                        + ") AS pending",
                    parameters(scope, FLOW_ID, flowId, "type", type))
                .getFirst()
                .get("pending"));
    if (pending) {
      throw new ConflictException();
    }
  }

  private ActionDTO toAction(Map<String, Object> row) {
    return new ActionDTO(
        Objects.toString(row.get("id")),
        JpaNativeQuery.string(row, "action_type"),
        JpaNativeQuery.string(row, "status"),
        JpaNativeQuery.string(row, "justification"),
        JpaNativeQuery.date(row, "promotion_valid_from"),
        JpaNativeQuery.date(row, "promotion_valid_until"),
        JpaNativeQuery.intValue(row, "current_batch_count"),
        JpaNativeQuery.date(row, "desired_order_date"),
        JpaNativeQuery.decimal(row, "current_discount_percentage"),
        JpaNativeQuery.decimal(row, "reference_sale_price"),
        JpaNativeQuery.decimal(row, "promotional_price"),
        JpaNativeQuery.string(row, "public_product_id"),
        JpaNativeQuery.longValue(row, "store_id"));
  }

  private ActionListItemDTO toListItem(Map<String, Object> row) {
    return new ActionListItemDTO(
        Objects.toString(row.get("id")),
        Objects.toString(row.get("flow_id"), null),
        JpaNativeQuery.string(row, "product_name"),
        JpaNativeQuery.string(row, "action_type"),
        JpaNativeQuery.string(row, "status"),
        JpaNativeQuery.string(row, "justification"),
        JpaNativeQuery.date(row, "promotion_valid_from"),
        JpaNativeQuery.date(row, "promotion_valid_until"),
        JpaNativeQuery.string(row, "decision_justification"),
        JpaNativeQuery.intValue(row, "current_batch_count"),
        JpaNativeQuery.date(row, "desired_order_date"),
        JpaNativeQuery.decimal(row, "current_discount_percentage"),
        JpaNativeQuery.decimal(row, "reference_sale_price"),
        JpaNativeQuery.decimal(row, "promotional_price"),
        JpaNativeQuery.string(row, "public_product_id"),
        JpaNativeQuery.longValue(row, "store_id"));
  }

  private Map<String, Object> parameters(StoreScope scope, Object... keyValues) {
    Map<String, Object> parameters = new HashMap<>(scope.parameters());
    for (int index = 0; index < keyValues.length; index += 2) {
      parameters.put((String) keyValues[index], keyValues[index + 1]);
    }
    return parameters;
  }
}
