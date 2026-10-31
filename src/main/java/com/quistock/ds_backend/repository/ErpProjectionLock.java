package com.quistock.ds_backend.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/** Transaction-scoped coordination shared by ERP snapshot writes and sales analysis. */
@Component
public class ErpProjectionLock {
  private final EntityManager entityManager;

  public ErpProjectionLock(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public void acquire() {
    entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(74192001)").getSingleResult();
  }
}
