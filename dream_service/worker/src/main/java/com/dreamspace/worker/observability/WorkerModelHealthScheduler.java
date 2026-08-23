package com.dreamspace.worker.observability;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class WorkerModelHealthScheduler {
  private final WorkerModelHealthProbe probe;
  private final WorkerModelHealthIndicator indicator;

  public WorkerModelHealthScheduler(WorkerModelHealthProbe probe, WorkerModelHealthIndicator indicator) {
    this.probe = probe;
    this.indicator = indicator;
  }

  @Scheduled(initialDelayString = "${dream-space.worker.model-health.initial-delay-ms:0}",
      fixedDelayString = "${dream-space.worker.model-health.fixed-delay-ms:1800000}",
      scheduler = "workerModelHealthTaskScheduler")
  public void check() {
    indicator.update(probe.check());
  }
}
