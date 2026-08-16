package com.dreamspace.api;

import com.dreamspace.common.ServiceHealth;
import com.dreamspace.common.ReadinessProbe;
import java.time.Clock;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {
    private final Clock clock;
    private final ReadinessProbe readinessProbe;

    public HealthController() {
        this(Clock.systemUTC(), () -> true);
    }

    HealthController(Clock clock) {
        this(clock, () -> true);
    }

    public HealthController(Clock clock, ReadinessProbe readinessProbe) {
        this.clock = clock;
        this.readinessProbe = readinessProbe;
    }

    @GetMapping
    public ServiceHealth health() {
        return response("api");
    }

    @GetMapping("/live")
    public ServiceHealth live() {
        return response("api-live");
    }

    @GetMapping("/ready")
    public ServiceHealth ready() {
        return new ServiceHealth("api-ready", readinessProbe.ready() ? "ok" : "down", Instant.now(clock));
    }

    private ServiceHealth response(String service) {
        return new ServiceHealth(service, "ok", Instant.now(clock));
    }
}
