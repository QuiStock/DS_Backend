package com.quistock.ds_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountWriteRepository {
  private static final String EMAIL_PARAMETER = "email";

  private final NamedParameterJdbcTemplate jdbc;

  public UserAccountWriteRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long createUser(NewUserAccount account) {
    Long id =
        jdbc.queryForObject(
            """
            INSERT INTO user_account (role_id, name, email, password_hash, created_by_id)
            SELECT id, :name, :email, :passwordHash, :createdById
            FROM role WHERE code = :roleCode
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("roleCode", account.roleCode())
                .addValue("name", account.name())
                .addValue(EMAIL_PARAMETER, account.email())
                .addValue("passwordHash", account.passwordHash())
                .addValue("createdById", account.createdById()),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("The requested role is not configured in the database.");
    }
    return id;
  }

  public void updateUser(long id, String name, String email, String status) {
    jdbc.update(
        """
        UPDATE user_account SET
          name = COALESCE(:name, name),
          email = COALESCE(:email, email),
          status = COALESCE(CAST(:status AS user_status), status),
          updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("name", name)
            .addValue(EMAIL_PARAMETER, email)
            .addValue("status", status));
  }

  public void updateProfile(long id, String name, String photoUrl) {
    jdbc.update(
        """
        UPDATE user_account SET name = COALESCE(:name, name),
          profile_photo_url = CASE WHEN CAST(:photoUrl AS text) IS NULL THEN profile_photo_url
            ELSE NULLIF(:photoUrl, '') END, updated_at = CURRENT_TIMESTAMP
        WHERE id = :id AND status = 'ACTIVE'
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("name", name)
            .addValue("photoUrl", photoUrl));
  }

  public void insertAuditEvent(
      long actorId, long targetId, String action, String role, String email) {
    jdbc.update(
        """
        INSERT INTO audit_event (user_id, entity, entity_id, action, data)
        VALUES (:actorId, 'user_account', :targetId, :action,
                jsonb_build_object('role', :role, 'email', :email))
        """,
        java.util.Map.of(
            "actorId",
            actorId,
            "targetId",
            targetId,
            "action",
            action,
            "role",
            role,
            EMAIL_PARAMETER,
            email));
  }
}
