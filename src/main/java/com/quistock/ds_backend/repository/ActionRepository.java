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
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ActionRepository {
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
  public ActionDTO createGenerated(
      FlowDTO flow,
      String actionType,
      String justification,
      LocalDate promotionValidFrom,
      LocalDate promotionValidUntil,
      long actorId) {
    Long flowId = parseId(flow.id());
    if (flowId == null) {
      throw new IllegalArgumentException("The flow ID must be a persisted analysis ID.");
    }

    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("flowId", flowId)
            .addValue("actionType", actionType)
            .addValue("origin", "EMPLOYEE")
            .addValue(STATUS_PARAMETER, GENERATED)
            .addValue("promotionValidFrom", promotionValidFrom)
            .addValue("promotionValidUntil", promotionValidUntil)
            .addValue("actorId", actorId);
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
                   :actorId, TRUE
            FROM product_analysis pa
            JOIN product_store ps
              ON ps.product_id = pa.product_id AND ps.store_id = pa.store_id
            WHERE pa.id = :flowId
            ON CONFLICT (product_analysis_id) WHERE product_analysis_id IS NOT NULL DO NOTHING
            RETURNING id
            """,
            parameters,
            (resultSet, rowNumber) -> resultSet.getLong("id"));

    Long suggestionId =
        insertedIds.isEmpty() ? readRepository.findSuggestionIdByFlow(flowId) : insertedIds.get(0);
    if (suggestionId == null) {
      throw new IllegalArgumentException("The analyzed flow has no persisted product snapshot.");
    }
    if (!insertedIds.isEmpty()) {
      auditRepository.logGenerated(
          suggestionId,
          actionType,
          flowId,
          actorId,
          promotionValidFrom,
          promotionValidUntil,
          justification);
    }
    return readRepository.findActionById(suggestionId);
  }

  public List<ActionListItemDTO> findAll(String status, String actionType, String flowId) {
    Long analysisId = flowId == null ? null : parseId(flowId);
    if (flowId != null && analysisId == null) {
      return List.of();
    }
    return readRepository.findAll(status, actionType, analysisId);
  }

  public String findTypeById(String actionId) {
    Long id = parseId(actionId);
    return id == null ? null : readRepository.findTypeById(id);
  }

  @Transactional
  public ActionStatusResponse updateStatus(
      String actionId, UpdateActionStatusRequest request, long actorId) {
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
    LocalDate validFrom =
        request.finalPromotionValidFrom() == null
            ? current.validFrom()
            : request.finalPromotionValidFrom();
    LocalDate validUntil =
        request.finalPromotionValidUntil() == null
            ? current.validUntil()
            : request.finalPromotionValidUntil();
    updateStatusRow(id, request.status(), validFrom, validUntil);
    decisionRepository.updateTriageIfNeeded(id, request.status(), actorId);
    decisionRepository.updateDecisionIfNeeded(
        id, request.status(), request.justification(), validFrom, validUntil, actorId);
    auditRepository.logStatusChanged(
        id,
        actorId,
        current.status(),
        request.status(),
        validFrom,
        validUntil,
        request.justification());
    return new ActionStatusResponse(actionId, request.status(), validFrom, validUntil);
  }

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
