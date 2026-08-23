package com.dreamspace.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class HealthControllerTest {
    @Test
    void exposesStableHealthContracts() {
        var controller = new HealthController(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        assertThat(controller.health()).isEqualTo(new com.dreamspace.common.ServiceHealth("api", "ok", Instant.EPOCH));
        assertThat(controller.live().service()).isEqualTo("api-live");
        assertThat(controller.ready().status()).isEqualTo("ok");
    }

    @Test
    void reportsDependencyFailureOnReadiness() {
        var controller = new HealthController(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), () -> false);

        assertThat(controller.ready().status()).isEqualTo("down");
    }
}
