package com.quistock.ds_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Transaction-scoped coordination shared by ERP snapshot writes and sales analysis. */
@Component
public class ErpProjectionLock {
  private final JdbcTemplate jdbc;

  public ErpProjectionLock(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void acquire() {
    jdbc.execute("SELECT pg_advisory_xact_lock(74192001)");
  }
}
