package com.dreamspace.common.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DreamSpacePropertiesTest {
  @Test
  void appliesSharedDefaults() {
    var properties = new DreamSpaceProperties(null, null, null, null, null);

    assertThat(properties.redis().stream()).isEqualTo("generation");
    assertThat(properties.redis().reclaimIdle()).isEqualTo(Duration.ofSeconds(30));
    assertThat(properties.storage().isLocal()).isTrue();
    assertThat(properties.ai().planning().enabled()).isFalse();
    assertThat(properties.ai().image().enabled()).isFalse();
  }

  @Test
  void secureCookiesAreExplicitlyConfigured() {
    var properties = new DreamSpaceProperties(null, null, null, null, null, null,
        new DreamSpaceProperties.Security(true));

    assertThat(properties.security().secureCookies()).isTrue();
  }
}
