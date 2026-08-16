package com.dreamspace.api;

import com.dreamspace.common.ServiceHealth;
import java.time.Clock;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {
    private final Clock clock;

    public HealthController() {
        this(Clock.systemUTC());
    }

    HealthController(Clock clock) {
        this.clock = clock;
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
        return response("api-ready");
    }

    private ServiceHealth response(String service) {
        return new ServiceHealth(service, "ok", Instant.now(clock));
    }
}
