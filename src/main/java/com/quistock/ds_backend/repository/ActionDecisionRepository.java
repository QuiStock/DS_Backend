package com.quistock.ds_backend.repository;

import java.time.LocalDate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ActionDecisionRepository {
  private static final String TRIAGE = "IN_EMPLOYEE_TRIAGE";
  private static final String FORWARD = "SENT_TO_MANAGER";
  private static final String APPROVED = "APPROVED";
  private static final String REJECTED = "REJECTED";

  private final NamedParameterJdbcTemplate jdbc;

  ActionDecisionRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  void updateTriageIfNeeded(long suggestionId, String status) {
    if (TRIAGE.equals(status)) {
      upsertTriage(suggestionId, "EDIT");
    } else if (FORWARD.equals(status)) {
      upsertTriage(suggestionId, "FORWARD");
    }
  }

  void updateDecisionIfNeeded(
      long suggestionId,
      String status,
      String justification,
      LocalDate validFrom,
      LocalDate validUntil) {
    if (APPROVED.equals(status) || REJECTED.equals(status)) {
      upsertDecision(suggestionId, status, justification, validFrom, validUntil);
    }
  }

  private void upsertTriage(long suggestionId, String action) {
    jdbc.update(
        """
        INSERT INTO suggestion_triage
          (suggestion_id, employee_id, last_action, forwarded_at, updated_at)
        VALUES
          (:suggestionId, NULL, CAST(:action AS triage_action),
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
            .addValue("action", action));
  }

  private void upsertDecision(
      long suggestionId,
      String status,
      String justification,
      LocalDate validFrom,
      LocalDate validUntil) {
    String decision = APPROVED.equals(status) ? "APPROVE" : "REJECT";
    boolean rejected = REJECTED.equals(status);
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
            .addValue("validFrom", rejected ? null : validFrom)
            .addValue("validUntil", rejected ? null : validUntil)
            .addValue("justification", justification));
  }
}
