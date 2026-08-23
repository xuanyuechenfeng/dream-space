package com.dreamspace.worker.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.boot.health.contributor.Health;

import org.junit.jupiter.api.Test;

class WorkerModelHealthIndicatorTest {
  @Test
  void appendsModelsToTheConfiguredSpringAiBasePath() {
    assertThat(WorkerModelHealthIndicator.appendPath("", "/models")).isEqualTo("/models");
    assertThat(WorkerModelHealthIndicator.appendPath("/v1", "/models")).isEqualTo("/v1/models");
    assertThat(WorkerModelHealthIndicator.appendPath("/compatible/v1/", "/models"))
        .isEqualTo("/compatible/v1/models");
  }

  @Test
  void exposesPendingStateUntilTheIndependentProbePublishesAResult() {
    WorkerModelHealthIndicator indicator = new WorkerModelHealthIndicator();

    assertThat(indicator.isReady()).isFalse();
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");

    indicator.update(Health.up().build());

    assertThat(indicator.isReady()).isTrue();
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
  }
}
