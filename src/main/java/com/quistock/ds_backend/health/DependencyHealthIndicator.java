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
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

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
    AtomicReference<String> dependency = new AtomicReference<>("health-check");
    Future<Health> check;
    try {
      check = executor.submit(() -> dependenciesHealth(dependency));
    } catch (RejectedExecutionException exception) {
      LOGGER.warn("Health check capacity exhausted.");
      return Health.down().withDetail("reason", "capacity_exhausted").build();
    }
    try {
      Health result = check.get(timeoutMs, TimeUnit.MILLISECONDS);
      if (Status.DOWN.equals(result.getStatus()) && LOGGER.isWarnEnabled()) {
        LOGGER.warn("Health dependency unavailable: {}", result.getDetails());
      }
      return result;
    } catch (ExecutionException exception) {
      // Exception messages can contain connection URLs or credentials.
      return failure(dependency.get(), "check_failed")
          .withDetail("errorType", exception.getCause().getClass().getSimpleName())
          .build();
    } catch (TimeoutException exception) {
      return failure(dependency.get(), "timeout").withDetail("timeoutMs", timeoutMs).build();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return failure(dependency.get(), "interrupted").build();
    } finally {
      check.cancel(true);
    }
  }

  private Health.Builder failure(String dependency, String reason) {
    LOGGER.warn("Health dependency unavailable: dependency={}, reason={}", dependency, reason);
    return Health.down().withDetail("dependency", dependency).withDetail("reason", reason);
  }

  protected abstract Health dependenciesHealth(AtomicReference<String> dependency)
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
