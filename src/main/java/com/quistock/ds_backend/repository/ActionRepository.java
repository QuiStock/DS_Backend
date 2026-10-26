package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.exception.ActionNotFoundException;
import com.quistock.ds_backend.exception.ConflictException;
import com.quistock.ds_backend.exception.FlowNotFoundException;
import com.quistock.ds_backend.model.dto.ActionDTO;
import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.ActionStatusResponse;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.UpdateActionStatusRequest;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ActionRepository {
  private static final String FLOW_ID = "flowId";
  private static final String GENERATED = "GENERATED";
  private static final String STATUS_PARAMETER = "status";

  private final NamedParameterJdbcTemplate jdbc;
  private final ActionReadRepository readRepository;
  private final ActionDecisionRepository decisionRepository;
  private final ActionAuditRepository auditRepository;

  public ActionRepository(
      NamedParameterJdbcTemplate jdbc,
      ActionReadRepository readRepository,
      ActionDecisionRepository decisionRepository,
      ActionAuditRepository auditRepository) {
    this.jdbc = jdbc;
    this.readRepository = readRepository;
    this.decisionRepository = decisionRepository;
    this.auditRepository = auditRepository;
  }

  @Transactional
  public ActionDTO createGenerated(FlowDTO flow, CreationDetails details, StoreScope scope) {
    String actionType = details.type();
    String justification = details.justification();
    LocalDate promotionValidFrom = details.validFrom();
    LocalDate promotionValidUntil = details.validUntil();
    Long flowId = parseId(flow.id());
    if (flowId == null) {
      throw new IllegalArgumentException("The flow ID must be a persisted analysis ID.");
    }

    readRepository.lockProductAndCheckPending(flowId, scope);
    MapSqlParameterSource parameters =
        scope
            .parameters()
            .addValue(FLOW_ID, flowId)
            .addValue("actionType", actionType)
            .addValue("origin", "EMPLOYEE")
            .addValue(STATUS_PARAMETER, GENERATED)
            .addValue("promotionValidFrom", promotionValidFrom)
            .addValue("promotionValidUntil", promotionValidUntil)
            .addValue("actorId", scope.actorId())
            .addValue("quantity", details.quantity())
            .addValue("desiredDate", details.desiredDate())
            .addValue("discount", details.discount());
    if (readRepository.findSuggestionIdByFlow(flowId, actionType, scope) == null) {
      readRepository.rejectOtherPending(flowId, actionType, scope);
    }
    readRepository.requireCurrentAnalysis(flowId, scope);
    List<Long> insertedIds =
        jdbc.query(
            """
            INSERT INTO suggestion (
              product_analysis_id, store_id, product_id, "type", origin, status,
              promotion_valid_from, promotion_valid_until, reference_sale_price,
              created_by_id, available_for_triage, current_batch_count, desired_order_date,
              current_discount_percentage, promotional_price
            )
            SELECT pa.id, pa.store_id, pa.product_id,
                   CAST(:actionType AS suggestion_type),
                   CAST(:origin AS suggestion_origin),
                   CAST(:status AS suggestion_status),
                   :promotionValidFrom, :promotionValidUntil,
                   CASE WHEN CAST(:actionType AS suggestion_type) = 'PROMOTION'
                     THEN ps.sale_price ELSE NULL END,
                   :actorId, TRUE, :quantity, :desiredDate, :discount,
                   CASE WHEN CAST(:actionType AS suggestion_type) = 'PROMOTION'
                     THEN ROUND(ps.sale_price * (1 - CAST(:discount AS numeric) / 100), 2) ELSE NULL END
            FROM product_analysis pa
            JOIN store s ON s.id = pa.store_id
            JOIN product_store ps
              ON ps.product_id = pa.product_id AND ps.store_id = pa.store_id
            WHERE pa.id = :flowId AND ps.active = TRUE
            """
                + StoreScope.SQL
                + " ON CONFLICT (product_analysis_id, type) WHERE product_analysis_id IS NOT NULL DO NOTHING"
                + " RETURNING id",
            parameters,
            (resultSet, rowNumber) -> resultSet.getLong("id"));

    Long suggestionId =
        insertedIds.isEmpty()
            ? readRepository.findSuggestionIdByFlow(flowId, actionType, scope)
            : insertedIds.get(0);
    if (suggestionId == null) {
      throw new FlowNotFoundException(flow.id());
    }
    if (!insertedIds.isEmpty()) {
      auditRepository.logGenerated(
          new ActionAuditRepository.GeneratedLog(
              suggestionId,
              actionType,
              flowId,
              scope.actorId(),
              promotionValidFrom,
              promotionValidUntil,
              justification));
    }
    return readRepository.findActionById(suggestionId, scope);
  }

  public List<ActionListItemDTO> findAll(
      String status, String actionType, String flowId, StoreScope scope) {
    Long analysisId = flowId == null ? null : parseId(flowId);
    if (flowId != null && analysisId == null) {
      return List.of();
    }
    return readRepository.findAll(status, actionType, analysisId, scope);
  }

  public String findTypeById(String actionId, StoreScope scope) {
    Long id = parseId(actionId);
    return id == null ? null : readRepository.findTypeById(id, scope);
  }

  @Transactional
  public ActionStatusResponse updateStatus(
      String actionId, UpdateActionStatusRequest request, StoreScope scope) {
    Long id = parseId(actionId);
    if (id == null) {
      throw new ActionNotFoundException(actionId);
    }

    List<StoredSuggestion> rows =
        jdbc.query(
            """
            SELECT sg.id, sg.status::text AS status, sg."type"::text AS type,
                   sg.promotion_valid_from, sg.promotion_valid_until
            FROM suggestion sg JOIN store s ON s.id = sg.store_id WHERE sg.id = :id
            """
                + StoreScope.SQL
                + " FOR UPDATE OF sg",
            scope.parameters().addValue("id", id),
            (resultSet, rowNumber) -> toStoredSuggestion(resultSet));
    if (rows.isEmpty()) {
      throw new ActionNotFoundException(actionId);
    }

    StoredSuggestion current = rows.get(0);
    if (current.status().equals(request.status())) {
      requireUnchangedRetry(request);
      return new ActionStatusResponse(
          actionId, current.status(), current.validFrom(), current.validUntil());
    }
    requireTransition(current.status(), request.status());
    LocalDate validFrom =
        request.finalPromotionValidFrom() == null
            ? current.validFrom()
            : request.finalPromotionValidFrom();
    LocalDate validUntil =
        request.finalPromotionValidUntil() == null
            ? current.validUntil()
            : request.finalPromotionValidUntil();
    updateStatusRow(id, request.status(), validFrom, validUntil);
    decisionRepository.updateTriageIfNeeded(id, request.status(), scope.actorId());
    decisionRepository.updateDecisionIfNeeded(
        id, request.status(), request.justification(), validFrom, validUntil, scope.actorId());
    auditRepository.logStatusChanged(
        new ActionAuditRepository.StatusChangedLog(
            id,
            scope.actorId(),
            current.status(),
            request.status(),
            validFrom,
            validUntil,
            request.justification()));
    return new ActionStatusResponse(actionId, request.status(), validFrom, validUntil);
  }

  private void requireUnchangedRetry(UpdateActionStatusRequest request) {
    if (request.finalPromotionValidFrom() != null
        || request.finalPromotionValidUntil() != null
        || request.justification() != null) {
      throw new ConflictException();
    }
  }

  private void requireTransition(String from, String to) {
    Set<String> allowed =
        switch (from) {
          case "GENERATED" -> Set.of("IN_EMPLOYEE_TRIAGE", "SENT_TO_MANAGER");
          case "IN_EMPLOYEE_TRIAGE" -> Set.of("SENT_TO_MANAGER");
          case "SENT_TO_MANAGER" -> Set.of("APPROVED", "REJECTED");
          default -> Set.of();
        };
    if (!allowed.contains(to)) {
      throw new ConflictException();
    }
  }

  public record CreationDetails(
      String type,
      String justification,
      LocalDate validFrom,
      LocalDate validUntil,
      Integer quantity,
      LocalDate desiredDate,
      BigDecimal discount) {}

  private void updateStatusRow(long id, String status, LocalDate validFrom, LocalDate validUntil) {
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
            .addValue(STATUS_PARAMETER, status)
            .addValue("validFrom", validFrom)
            .addValue("validUntil", validUntil)
            .addValue("id", id));
  }

  private StoredSuggestion toStoredSuggestion(ResultSet resultSet) throws SQLException {
    return new StoredSuggestion(
        resultSet.getLong("id"),
        resultSet.getString(STATUS_PARAMETER),
        resultSet.getString("type"),
        resultSet.getObject("promotion_valid_from", LocalDate.class),
        resultSet.getObject("promotion_valid_until", LocalDate.class));
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
}
