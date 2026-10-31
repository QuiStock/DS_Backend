package com.quistock.ds_backend.repository;

import jakarta.persistence.EntityManager;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountWriteRepository {
  private static final String EMAIL_PARAMETER = "email";

  private final EntityManager entityManager;
  private final UserAccountJpaRepository users;
  private final RoleJpaRepository roles;

  public UserAccountWriteRepository(
      EntityManager entityManager, UserAccountJpaRepository users, RoleJpaRepository roles) {
    this.entityManager = entityManager;
    this.users = users;
    this.roles = roles;
  }

  public long createUser(NewUserAccount account) {
    RoleEntity role =
        roles
            .findByCode(account.roleCode())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "The requested role is not configured in the database."));
    return users
        .saveAndFlush(
            new UserAccountEntity(
                role.getId(),
                account.name(),
                account.email(),
                account.passwordHash(),
                account.createdById()))
        .getId();
  }

  public void updateUser(long id, String name, String email, String status) {
    JpaNativeQuery.update(
        entityManager,
        """
        UPDATE user_account SET
          name = COALESCE(:name, name),
          email = COALESCE(:email, email),
          status = COALESCE(CAST(:status AS user_status), status),
          updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
        """,
        JpaNativeQuery.parameters(
            "id", id, "name", name, EMAIL_PARAMETER, email, "status", status));
  }

  public void updateProfile(long id, String name, String photoUrl) {
    JpaNativeQuery.update(
        entityManager,
        """
        UPDATE user_account SET name = COALESCE(:name, name),
          profile_photo_url = CASE WHEN CAST(:photoUrl AS text) IS NULL THEN profile_photo_url
            ELSE NULLIF(:photoUrl, '') END, updated_at = CURRENT_TIMESTAMP
        WHERE id = :id AND status = 'ACTIVE'
        """,
        JpaNativeQuery.parameters("id", id, "name", name, "photoUrl", photoUrl));
  }

  public void insertAuditEvent(
      long actorId, long targetId, String action, String role, String email) {
    JpaNativeQuery.update(
        entityManager,
        """
        INSERT INTO audit_event (user_id, entity, entity_id, action, data)
        VALUES (:actorId, 'user_account', :targetId, :action,
                jsonb_build_object('role', :role, 'email', :email))
        """,
        Map.of(
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
