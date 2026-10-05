package com.quistock.ds_backend.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DependencyHealthIndicatorTest {
  private Probe indicator;

  @AfterEach
  void cleanup() {
    if (indicator != null) {
      indicator.shutdown();
    }
  }

  @Test
  void reportsUpOnlyWhenDependenciesSucceed() {
    indicator = new Probe(() -> true, 1000);
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
  }

  @Test
  void reportsDownWhenDependencyReturnsFalse() {
    indicator = new Probe(() -> false, 1000);
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
  }

  @Test
  void hidesDependencyExceptionDetails() {
    indicator =
        new Probe(
            () -> {
              throw new IllegalStateException("sensitive connection details");
            },
            1000);
    assertThat(indicator.health().getDetails()).isEmpty();
  }

  @Test
  void boundsAnUnresponsiveDependency() {
    CountDownLatch release = new CountDownLatch(1);
    indicator = new Probe(() -> await(release), 50);
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
    release.countDown();
  }

  @Test
  void rejectsUnboundedTimeoutConfiguration() {
    assertThatThrownBy(() -> new Probe(() -> true, 30_001))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private boolean await(CountDownLatch release) {
    try {
      release.await();
      return true;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static final class Probe extends DependencyHealthIndicator {
    private final BooleanSupplier check;

    private Probe(BooleanSupplier check, long timeoutMs) {
      super(timeoutMs);
      this.check = check;
    }

    @Override
    protected boolean dependenciesAvailable() {
      return check.getAsBoolean();
    }
  }
}
