package com.dreamspace.worker.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;

class WorkerModelHealthSchedulerTest {
  @Test
  void publishesTheProbeResultToTheReadinessIndicator() {
    WorkerModelHealthProbe probe = mock(WorkerModelHealthProbe.class);
    WorkerModelHealthIndicator indicator = new WorkerModelHealthIndicator();
    when(probe.check()).thenReturn(Health.up().withDetail("planning", "reachable").build());

    new WorkerModelHealthScheduler(probe, indicator).check();

    assertThat(indicator.isReady()).isTrue();
    assertThat(indicator.health().getDetails()).containsEntry("planning", "reachable");
  }
}
