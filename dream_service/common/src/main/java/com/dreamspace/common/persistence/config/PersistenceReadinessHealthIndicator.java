package com.dreamspace.common.persistence.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

public final class PersistenceReadinessHealthIndicator implements HealthIndicator {
  private final PersistenceReadinessProbe probe;

  public PersistenceReadinessHealthIndicator(PersistenceReadinessProbe probe) {
    this.probe = probe;
  }

  @Override
  public Health health() {
    return probe.ready() ? Health.up().build() : Health.down().withDetail("reason", "required persistence dependency unavailable").build();
  }
}
