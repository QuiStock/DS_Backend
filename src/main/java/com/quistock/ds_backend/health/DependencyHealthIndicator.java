package com.quistock.ds_backend.health;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/** Limits readiness latency and the number of blocked dependency checks. */
public abstract class DependencyHealthIndicator implements HealthIndicator {
  private static final Logger LOGGER = LoggerFactory.getLogger(DependencyHealthIndicator.class);
  private final long timeoutMs;
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(
          0,
          2,
          30,
          TimeUnit.SECONDS,
          new SynchronousQueue<>(),
          Thread.ofPlatform().daemon().name("health-check-", 0).factory());

  protected DependencyHealthIndicator(long timeoutMs) {
    if (timeoutMs < 1 || timeoutMs > 30_000) {
      throw new IllegalArgumentException("HEALTH_TIMEOUT_MS must be between 1 and 30000.");
    }
    this.timeoutMs = timeoutMs;
  }

  @Override
  public Health health() {
    Future<Boolean> check;
    try {
      check = executor.submit(this::dependenciesAvailable);
    } catch (RejectedExecutionException exception) {
      LOGGER.warn("Health check capacity exhausted.");
      return Health.down().build();
    }
    try {
      return Boolean.TRUE.equals(check.get(timeoutMs, TimeUnit.MILLISECONDS))
          ? Health.up().build()
          : Health.down().build();
    } catch (ExecutionException | TimeoutException exception) {
      // Do not log driver messages that might contain connection URLs or credentials.
      LOGGER.warn("Health dependency check failed or timed out.");
      return Health.down().build();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return Health.down().build();
    } finally {
      check.cancel(true);
    }
  }

  protected abstract boolean dependenciesAvailable()
      throws SQLException, IOException, InterruptedException, ParseException;

  @PreDestroy
  public final void shutdown() {
    executor.shutdownNow();
    releaseResources();
  }

  protected void releaseResources() {
    // Subclasses may release their dependency clients here.
  }
}
