package com.dreamspace.common.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class DreamSpacePropertiesTest {
  @Test
  void appliesSharedDefaults() {
    var properties = new DreamSpaceProperties(null, null, null, null, null);

    assertThat(properties.redis().stream()).isEqualTo("generation");
    assertThat(properties.redis().reclaimIdle()).isEqualTo(Duration.ofSeconds(30));
    assertThat(properties.storage().isLocal()).isTrue();
    assertThat(properties.email().from()).isNull();
    assertThat(properties.email().codeTtlSeconds()).isEqualTo(600);
    assertThat(properties.email().codeMaxAttempts()).isEqualTo(5);
    assertThat(properties.email().codeIssueLimitPerMinute()).isEqualTo(5);
    assertThat(properties.ai().planning().enabled()).isFalse();
    assertThat(properties.ai().image().enabled()).isFalse();
  }

  @Test
  void secureCookiesAreExplicitlyConfigured() {
    var properties = new DreamSpaceProperties(null, null, null, null, null, null,
        new DreamSpaceProperties.Security(true));

    assertThat(properties.security().secureCookies()).isTrue();
  }

  @Test
  void bindsDedicatedEmailConfiguration() {
    var source = new MapConfigurationPropertySource(Map.of(
        "dream-space.email.from", "sender@qq.com",
        "dream-space.email.code-ttl-seconds", "900",
        "dream-space.email.code-max-attempts", "4",
        "dream-space.email.code-issue-limit-per-minute", "3"));

    var properties = new Binder(source)
        .bind("dream-space", Bindable.of(DreamSpaceProperties.class))
        .orElseThrow(IllegalStateException::new);

    assertThat(properties.email()).isEqualTo(
        new DreamSpaceProperties.Email("sender@qq.com", 900, 4, 3));
  }
}
