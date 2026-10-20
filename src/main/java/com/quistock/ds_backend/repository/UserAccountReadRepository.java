package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.UserDTO;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
        WHEN r.code = 'GERENTE_REGIONAL' THEN rma.region_id ELSE s.region_id END
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public UserAccountReadRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Actor> findActor(long id) {
    List<Actor> result =
        jdbc.query(
            """
            SELECT u.id, u.status::text AS status, r.code AS role
            FROM user_account u JOIN role r ON r.id = u.role_id
            WHERE u.id = :id
            """,
            Map.of("id", id),
            (rs, row) -> new Actor(rs.getLong("id"), rs.getString("role"), rs.getString("status")));
    return result.stream().findFirst();
  }

  public Optional<Long> findActiveStoreAssignment(long userId) {
    return jdbc
        .queryForList(
            """
        SELECT us.store_id FROM user_store us
        JOIN store s ON s.id = us.store_id AND s.status = 'ACTIVE'
        WHERE us.user_id = :userId AND us.active = TRUE
        """,
            Map.of("userId", userId),
            Long.class)
        .stream()
        .findFirst();
  }

  public Optional<UserDTO> findUser(long id) {
    return findOne(USER_PROJECTION + " WHERE u.id = :id", Map.of("id", id));
  }

  public Optional<UserDTO> findManager(long id) {
    return findOne(USER_PROJECTION + " WHERE u.id = :id AND r.code = 'GERENTE'", Map.of("id", id));
  }

  public Optional<UserDTO> findTeamMember(long id, long managerId) {
    Long storeId = findUser(managerId).map(UserDTO::storeId).orElse(null);
    if (storeId == null) {
      return Optional.empty();
    }
    return findOne(
        USER_PROJECTION + " WHERE u.id = :id AND r.code = 'FUNCIONARIO' AND s.id = :storeId",
        Map.of("id", id, "storeId", storeId));
  }

  public List<UserDTO> findManagers(StoreScope scope) {
    return jdbc.query(
        USER_PROJECTION
            + " WHERE r.code = 'GERENTE'"
            + StoreScope.SQL
            + " ORDER BY LOWER(u.name), u.id",
        scope.parameters(),
        UserAccountReadRepository::mapUser);
  }

  public Optional<Long> findActiveRegionalAssignment(long userId) {
    return jdbc
        .queryForList(
            """
        SELECT rma.region_id FROM region_manager_assignment rma
        JOIN region_type rt ON rt.id = rma.region_id AND rt.active = TRUE
        WHERE rma.user_id = :userId AND rma.active = TRUE
        """,
            Map.of("userId", userId),
            Long.class)
        .stream()
        .findFirst();
  }

  public List<UserDTO> findTeamMembers(long managerId) {
    Long storeId = findUser(managerId).map(UserDTO::storeId).orElse(null);
    if (storeId == null) {
      return List.of();
    }
    return jdbc.query(
        USER_PROJECTION
            + " WHERE r.code = 'FUNCIONARIO' AND s.id = :storeId ORDER BY LOWER(u.name), u.id",
        Map.of("storeId", storeId),
        UserAccountReadRepository::mapUser);
  }

  private Optional<UserDTO> findOne(String sql, Map<String, ?> parameters) {
    return jdbc.query(sql, parameters, UserAccountReadRepository::mapUser).stream().findFirst();
  }

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private static UserDTO mapUser(java.sql.ResultSet rs, int rowNumber)
      throws java.sql.SQLException {
    long storeIdValue = rs.getLong("store_id");
    Long storeId = rs.wasNull() ? null : storeIdValue;
    long regionIdValue = rs.getLong("region_id");
    Long regionId = rs.wasNull() ? null : regionIdValue;
    return new UserDTO(
        Long.toString(rs.getLong("id")),
        rs.getString("name"),
        rs.getString("email"),
        rs.getString("role"),
        rs.getString("status"),
        rs.getString("profile_photo_url"),
        storeId,
        rs.getString("store_code"),
        rs.getString("store_name"),
        regionId,
        rs.getString("region_code"),
        rs.getString("region_name"));
  }

  public record Actor(long id, String role, String status) {}
}
