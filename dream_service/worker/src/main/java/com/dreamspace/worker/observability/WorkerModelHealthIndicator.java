package com.dreamspace.worker.observability;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("workerModel")
public final class WorkerModelHealthIndicator implements HealthIndicator {
  private static final Health PENDING = Health.down()
      .withDetail("reason", "model health check has not completed").build();
  private final AtomicReference<Health> current = new AtomicReference<>(PENDING);

  public boolean isReady() {
    return current.get().getStatus().equals(Status.UP);
  }

  public void update(Health health) {
    current.set(health == null ? PENDING : health);
  }

  @Override
  public Health health() {
    return current.get();
  }

  static String appendPath(String basePath, String suffix) {
    return WorkerModelHealthProbe.appendPath(basePath, suffix);
  }

  static String preview(String body) {
    return WorkerModelHealthProbe.preview(body);
  }
}
