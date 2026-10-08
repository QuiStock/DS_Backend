package com.quistock.ds_backend.repository;

import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAssignmentRepository {
  private static final String USER_ID_PARAMETER = "userId";

  private final NamedParameterJdbcTemplate jdbc;

  public UserAssignmentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean storeIsActive(long storeId) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM store WHERE id = :storeId AND status = 'ACTIVE')",
            Map.of("storeId", storeId),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  public boolean regionIsActive(long regionId) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM region_type WHERE id = :regionId AND active = TRUE)",
            Map.of("regionId", regionId),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  public void insertStoreAssignment(long userId, long storeId) {
    jdbc.update(
        "INSERT INTO user_store (user_id, store_id, active, assigned_at) "
            + "VALUES (:userId, :storeId, TRUE, CURRENT_TIMESTAMP)",
        Map.of(USER_ID_PARAMETER, userId, "storeId", storeId));
  }

  public void insertRegionAssignment(long userId, long regionId) {
    jdbc.update(
        "INSERT INTO region_manager_assignment (user_id, region_id, active) "
            + "VALUES (:userId, :regionId, TRUE)",
        Map.of(USER_ID_PARAMETER, userId, "regionId", regionId));
  }

  public void closeStoreAssignments(long userId) {
    jdbc.update(
        """
        UPDATE user_store SET active = FALSE, unassigned_at = CURRENT_TIMESTAMP
        WHERE user_id = :userId AND active = TRUE
        """,
        Map.of(USER_ID_PARAMETER, userId));
  }

  public void closeRegionAssignments(long userId) {
    jdbc.update(
        "UPDATE region_manager_assignment SET active = FALSE "
            + "WHERE user_id = :userId AND active = TRUE",
        Map.of(USER_ID_PARAMETER, userId));
  }

  public void reassignStore(long userId, long storeId) {
    closeStoreAssignments(userId);
    insertStoreAssignment(userId, storeId);
  }

  public void reassignRegion(long userId, long regionId) {
    closeRegionAssignments(userId);
    insertRegionAssignment(userId, regionId);
  }
}
