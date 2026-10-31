package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.UserDTO;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountReadRepository {
  private static final String USER_PROJECTION =
      """
      SELECT u.id, u.name, u.email, r.code AS role, u.status::text AS status,
             u.profile_photo_url, s.id AS store_id, s.erp_id AS store_code,
             s.name AS store_name, rt.id AS region_id, rt.code AS region_code,
             rt.name AS region_name
      FROM user_account u
      JOIN role r ON r.id = u.role_id
      LEFT JOIN LATERAL (
        SELECT assignment.store_id
        FROM user_store assignment
        WHERE assignment.user_id = u.id
          AND (assignment.active = TRUE OR u.status = 'INACTIVE')
        ORDER BY assignment.active DESC, assignment.assigned_at DESC, assignment.id DESC
        LIMIT 1
      ) us ON TRUE
      LEFT JOIN store s ON s.id = us.store_id
      LEFT JOIN region_manager_assignment rma ON rma.user_id = u.id AND rma.active = TRUE
      LEFT JOIN region_type rt ON rt.id = CASE
        WHEN r.code = 'REGIONAL_MANAGER' THEN rma.region_id ELSE s.region_id END
      """;

  private final EntityManager entityManager;

  public UserAccountReadRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public Optional<Actor> findActor(long id) {
    return JpaNativeQuery.rows(
            entityManager,
            """
            SELECT u.id, u.status::text AS status, r.code AS role
            FROM user_account u JOIN role r ON r.id = u.role_id
            WHERE u.id = :id
            """,
            Map.of("id", id))
        .stream()
        .map(
            row ->
                new Actor(
                    JpaNativeQuery.longValue(row, "id"),
                    JpaNativeQuery.string(row, "role"),
                    JpaNativeQuery.string(row, "status")))
        .findFirst();
  }

  public Optional<Long> findActiveStoreAssignment(long userId) {
    return JpaNativeQuery.rows(
            entityManager,
            """
            SELECT us.store_id FROM user_store us
            JOIN store s ON s.id = us.store_id AND s.status = 'ACTIVE'
            WHERE us.user_id = :userId AND us.active = TRUE
            """,
            Map.of("userId", userId))
        .stream()
        .map(row -> JpaNativeQuery.longValue(row, "store_id"))
        .findFirst();
  }

  public Optional<UserDTO> findUser(long id) {
    return queryUsers(USER_PROJECTION + " WHERE u.id = :id", Map.of("id", id)).stream().findFirst();
  }

  public Optional<UserDTO> findManager(long id) {
    return queryUsers(
            USER_PROJECTION + " WHERE u.id = :id AND r.code = 'MANAGER'", Map.of("id", id))
        .stream()
        .findFirst();
  }

  public Optional<UserDTO> findTeamMember(long id, long managerId) {
    Long storeId = findUser(managerId).map(UserDTO::storeId).orElse(null);
    if (storeId == null) {
      return Optional.empty();
    }
    return queryUsers(
            USER_PROJECTION + " WHERE u.id = :id AND r.code = 'EMPLOYEE' AND s.id = :storeId",
            Map.of("id", id, "storeId", storeId))
        .stream()
        .findFirst();
  }

  public List<UserDTO> findManagers(StoreScope scope) {
    return queryUsers(
        USER_PROJECTION
            + " WHERE r.code = 'MANAGER'"
            + scope.sql()
            + " ORDER BY LOWER(u.name), u.id",
        scope.parameters());
  }

  public Optional<Long> findActiveRegionalAssignment(long userId) {
    return JpaNativeQuery.rows(
            entityManager,
            """
            SELECT rma.region_id FROM region_manager_assignment rma
            JOIN region_type rt ON rt.id = rma.region_id AND rt.active = TRUE
            WHERE rma.user_id = :userId AND rma.active = TRUE
            """,
            Map.of("userId", userId))
        .stream()
        .map(row -> JpaNativeQuery.longValue(row, "region_id"))
        .findFirst();
  }

  public List<UserDTO> findTeamMembers(long managerId) {
    Long storeId = findUser(managerId).map(UserDTO::storeId).orElse(null);
    if (storeId == null) {
      return List.of();
    }
    return queryUsers(
        USER_PROJECTION
            + " WHERE r.code = 'EMPLOYEE' AND s.id = :storeId ORDER BY LOWER(u.name), u.id",
        Map.of("storeId", storeId));
  }

  private List<UserDTO> queryUsers(String sql, Map<String, ?> parameters) {
    return JpaNativeQuery.rows(entityManager, sql, parameters).stream()
        .map(UserDTOMapper::map)
        .toList();
  }

  public record Actor(long id, String role, String status) {}
}
