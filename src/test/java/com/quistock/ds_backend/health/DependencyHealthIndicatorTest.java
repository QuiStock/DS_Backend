package com.quistock.ds_backend.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class DependencyHealthIndicatorTest {
  @Test
  @SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
  void cachesSuccessAndFailureUntilExpiryAndSupportsDisablingCache() throws InterruptedException {
    AtomicInteger calls = new AtomicInteger();
    AtomicReference<Health> result = new AtomicReference<>(Health.up().build());
    DependencyHealthIndicator indicator =
        new DependencyHealthIndicator(1000) {
          @Override
          protected Health dependenciesHealth(AtomicReference<String> dependency) {
            calls.incrementAndGet();
            return result.get();
          }
        };
    indicator.configureCache(Duration.ofSeconds(1));
    try {
      assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
      result.set(Health.down().build());
      assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
      assertThat(calls.get()).isEqualTo(1);
      Thread.sleep(1100);
      assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
      result.set(Health.up().build());
      assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
      assertThat(calls.get()).isEqualTo(2);
      indicator.configureCache(Duration.ZERO);
      assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
      assertThat(calls.get()).isEqualTo(3);
    } finally {
      indicator.shutdown();
    }
  }

  @Test
  @SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
  void concurrentCallersShareWorkEvenAfterOneTimesOut() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    DependencyHealthIndicator indicator =
        new DependencyHealthIndicator(100) {
          @Override
          protected Health dependenciesHealth(AtomicReference<String> dependency)
              throws InterruptedException {
            calls.incrementAndGet();
            dependency.set("database");
            started.countDown();
            release.await();
            return Health.up().build();
          }
        };
    try (var callers = Executors.newFixedThreadPool(2)) {
      var first = callers.submit(() -> indicator.health());
      assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
      var second = callers.submit(() -> indicator.health());
      assertThat(first.get(1, TimeUnit.SECONDS).getStatus()).isEqualTo(Status.DOWN);
      assertThat(second.get(1, TimeUnit.SECONDS).getStatus()).isEqualTo(Status.DOWN);
      assertThat(indicator.health().getDetails()).containsEntry("reason", "timeout");
      assertThat(calls.get()).isEqualTo(1);
      release.countDown();
      assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    } finally {
      release.countDown();
      indicator.shutdown();
    }
  }

  @Test
  void identifiesFailureWithoutExposingExceptionMessage() {
    DependencyHealthIndicator indicator =
        new DependencyHealthIndicator(1000) {
          @Override
          protected Health dependenciesHealth(AtomicReference<String> dependency)
              throws IOException {
            dependency.set("database");
            throw new IOException("password=secret");
          }
        };
    try {
      Health health = indicator.health();
      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails())
          .containsEntry("dependency", "database")
          .containsEntry("reason", "check_failed")
          .containsEntry("errorType", "IOException");
      assertThat(health.getDetails().toString()).doesNotContain("secret");
    } finally {
      indicator.shutdown();
    }
  }

  @Test
  void timeoutIdentifiesDependencyInProgress() {
    DependencyHealthIndicator indicator =
        new DependencyHealthIndicator(100) {
          @Override
          protected Health dependenciesHealth(AtomicReference<String> dependency)
              throws InterruptedException {
            dependency.set("erp");
            Thread.sleep(10000);
            return Health.up().build();
          }
        };
    try {
      assertThat(indicator.health().getDetails())
          .containsEntry("dependency", "erp")
          .containsEntry("reason", "timeout")
          .containsEntry("timeoutMs", 100L);
    } finally {
      indicator.shutdown();
    }
  }
}
