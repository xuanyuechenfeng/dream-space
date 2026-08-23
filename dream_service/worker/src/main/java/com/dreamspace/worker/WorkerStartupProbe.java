package com.dreamspace.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicBoolean;

@Component("workerStartup")
public class WorkerStartupProbe implements ApplicationRunner, HealthIndicator {
    private static final Logger log = LoggerFactory.getLogger(WorkerStartupProbe.class);
    private final AtomicBoolean started = new AtomicBoolean();

    @Override
    public void run(ApplicationArguments args) {
        started.set(true);
        log.info("Dream Space worker profile started");
    }

    @Override
    public Health health() {
        return started.get() ? Health.up().build() : Health.down().withDetail("reason", "worker startup probe has not completed").build();
    }
}
