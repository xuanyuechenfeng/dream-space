package com.dreamspace.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class WorkerStartupProbe implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WorkerStartupProbe.class);

    @Override
    public void run(ApplicationArguments args) {
        log.info("Dream Space worker profile started");
    }
}
