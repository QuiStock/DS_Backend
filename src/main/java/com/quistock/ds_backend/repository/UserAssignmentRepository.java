package com.quistock.ds_backend.repository;

import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserAssignmentRepository {
  private static final String USER_ID_PARAMETER = "userId";
  private static final String STORE_ID_PARAMETER = "storeId";

  private final EntityManager entityManager;

  public UserAssignmentRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public boolean storeIsActive(long storeId) {
    return Boolean.TRUE.equals(
        entityManager
            .createNativeQuery(
                "SELECT EXISTS (SELECT 1 FROM store WHERE id = :storeId AND status = 'ACTIVE')")
            .setParameter(STORE_ID_PARAMETER, storeId)
            .getSingleResult());
  }

  public Optional<Long> findActiveStoreRegion(long storeId) {
    return JpaNativeQuery.rows(
            entityManager,
            """
            SELECT s.region_id FROM store s
            JOIN region_type rt ON rt.id = s.region_id AND rt.active = TRUE
            WHERE s.id = :storeId AND s.status = 'ACTIVE'
            FOR SHARE OF s, rt
            """,
            Map.of(STORE_ID_PARAMETER, storeId))
        .stream()
        .map(row -> JpaNativeQuery.longValue(row, "region_id"))
        .findFirst();
  }

  public void lockUsers(long... userIds) {
    entityManager
        .createNativeQuery(
            "SELECT id FROM user_account WHERE id IN (:userIds) ORDER BY id FOR UPDATE")
        .setParameter("userIds", Arrays.stream(userIds).boxed().toList())
        .getResultList();
  }

  public boolean storeHasActiveEmployees(long storeId) {
    return Boolean.TRUE.equals(
        entityManager
            .createNativeQuery(
                """
                SELECT EXISTS (
                  SELECT 1 FROM user_store us
                  JOIN user_account u ON u.id = us.user_id
                  JOIN role r ON r.id = u.role_id
                  WHERE us.store_id = :storeId AND us.active = TRUE
                    AND u.status = 'ACTIVE' AND r.code = 'EMPLOYEE'
                )
                """)
            .setParameter(STORE_ID_PARAMETER, storeId)
            .getSingleResult());
  }

  public void insertStoreAssignment(long userId, long storeId) {
    JpaNativeQuery.update(
        entityManager,
        "INSERT INTO user_store (user_id, store_id, active, assigned_at) "
            + "VALUES (:userId, :storeId, TRUE, CURRENT_TIMESTAMP)",
        Map.of(USER_ID_PARAMETER, userId, STORE_ID_PARAMETER, storeId));
  }

  public void closeStoreAssignments(long userId) {
    JpaNativeQuery.update(
        entityManager,
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
