package com.quistock.ds_backend.health;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

/** Limits readiness latency and the number of blocked dependency checks. */
public abstract class DependencyHealthIndicator implements HealthIndicator {
  private static final Logger LOGGER = LoggerFactory.getLogger(DependencyHealthIndicator.class);
  private final long timeoutMs;
  private Check inFlight;
  private long cacheNanos;
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

  @Autowired
  final void configureCache(
      @Value("${management.endpoint.health.cache.time-to-live:5s}") Duration cacheTtl) {
    if (cacheTtl.isNegative()) {
      throw new IllegalArgumentException("Health cache TTL must not be negative.");
    }
    cacheNanos = cacheTtl.toNanos();
  }

  @Override
  public Health health() {
    Check check;
    try {
      check = currentCheck();
    } catch (RejectedExecutionException exception) {
      LOGGER.warn("Health check capacity exhausted.");
      return Health.down().withDetail("reason", "capacity_exhausted").build();
    }
    try {
      Health result = check.task().get(timeoutMs, TimeUnit.MILLISECONDS);
      if (Status.DOWN.equals(result.getStatus()) && LOGGER.isWarnEnabled()) {
        LOGGER.warn("Health dependency unavailable: {}", result.getDetails());
      }
      return result;
    } catch (ExecutionException exception) {
      // Exception messages can contain connection URLs or credentials.
      return failure(check.dependency().get(), "check_failed")
          .withDetail("errorType", exception.getCause().getClass().getSimpleName())
          .build();
    } catch (TimeoutException exception) {
      return failure(check.dependency().get(), "timeout")
          .withDetail("timeoutMs", timeoutMs)
          .build();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return failure(check.dependency().get(), "interrupted").build();
    }
  }

  private synchronized Check currentCheck() {
    if (inFlight == null
        || (inFlight.task().isDone()
            && System.nanoTime() - inFlight.completedAt().get() >= cacheNanos)) {
      AtomicReference<String> dependency = new AtomicReference<>("health-check");
      AtomicLong completedAt = new AtomicLong();
      FutureTask<Health> task =
          new FutureTask<>(
              () -> {
                try {
                  return dependenciesHealth(dependency);
                } finally {
                  completedAt.set(System.nanoTime());
                }
              });
      executor.execute(task);
      inFlight = new Check(dependency, task, completedAt);
    }
    // A caller timing out must not cancel work shared with other callers. Retain
    // blocked work until it actually finishes, preventing duplicate SQL checks.
    return inFlight;
  }

  private record Check(
      AtomicReference<String> dependency, FutureTask<Health> task, AtomicLong completedAt) {}

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
