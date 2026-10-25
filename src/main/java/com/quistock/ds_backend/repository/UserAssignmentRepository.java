package com.quistock.ds_backend.repository;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAssignmentRepository {
  private static final String USER_ID_PARAMETER = "userId";
  private static final String STORE_ID_PARAMETER = "storeId";

  private final NamedParameterJdbcTemplate jdbc;

  public UserAssignmentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean storeIsActive(long storeId) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM store WHERE id = :storeId AND status = 'ACTIVE')",
            Map.of(STORE_ID_PARAMETER, storeId),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  public Optional<Long> findActiveStoreRegion(long storeId) {
    return jdbc
        .queryForList(
            """
            SELECT s.region_id FROM store s
            JOIN region_type rt ON rt.id = s.region_id AND rt.active = TRUE
            WHERE s.id = :storeId AND s.status = 'ACTIVE'
            FOR SHARE OF s, rt
            """,
            Map.of(STORE_ID_PARAMETER, storeId),
            Long.class)
        .stream()
        .findFirst();
  }

  public void lockUsers(long... userIds) {
    jdbc.queryForList(
        "SELECT id FROM user_account WHERE id IN (:userIds) ORDER BY id FOR UPDATE",
        Map.of("userIds", Arrays.stream(userIds).boxed().toList()),
        Long.class);
  }

  public boolean storeHasActiveEmployees(long storeId) {
    Boolean exists =
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM user_store us
              JOIN user_account u ON u.id = us.user_id
              JOIN role r ON r.id = u.role_id
              WHERE us.store_id = :storeId AND us.active = TRUE
                AND u.status = 'ACTIVE' AND r.code = 'EMPLOYEE'
            )
            """,
            Map.of(STORE_ID_PARAMETER, storeId),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  public void insertStoreAssignment(long userId, long storeId) {
    jdbc.update(
        "INSERT INTO user_store (user_id, store_id, active, assigned_at) "
            + "VALUES (:userId, :storeId, TRUE, CURRENT_TIMESTAMP)",
        Map.of(USER_ID_PARAMETER, userId, STORE_ID_PARAMETER, storeId));
  }

  public void closeStoreAssignments(long userId) {
    jdbc.update(
        """
        UPDATE user_store SET active = FALSE, unassigned_at = CURRENT_TIMESTAMP
        WHERE user_id = :userId AND active = TRUE
        """,
        Map.of(USER_ID_PARAMETER, userId));
  }

  public void reassignStore(long userId, long storeId) {
    closeStoreAssignments(userId);
    insertStoreAssignment(userId, storeId);
  }
}
