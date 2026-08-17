package com.dreamspace.common.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DreamSpacePropertiesTest {
  @Test
  void defaultsToMockAndLocalAdapters() {
    var properties = new DreamSpaceProperties(null, null, null, null, null, null, null);

    assertThat(properties.externalServicesEnabled()).isFalse();
    assertThat(properties.redis().stream()).isEqualTo("generation");
    assertThat(properties.redis().reclaimIdle()).isEqualTo(Duration.ofSeconds(30));
    assertThat(properties.storage().isS3()).isFalse();
  }

  @Test
  void treatsRealAndProductionAsLive() {
    var real = new DreamSpaceProperties("real", null, null, null, null, null, null);
    var production = new DreamSpaceProperties("production", null, null, null, null, null, null);

    assertThat(real.externalServicesEnabled()).isTrue();
    assertThat(production.externalServicesEnabled()).isTrue();
  }
}
