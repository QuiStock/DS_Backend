package com.quistock.ds_backend.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class DependencyHealthIndicatorTest {
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
