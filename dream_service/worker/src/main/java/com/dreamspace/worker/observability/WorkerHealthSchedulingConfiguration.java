package com.dreamspace.worker.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class WorkerHealthSchedulingConfiguration {
  @Bean(name = "workerModelHealthTaskScheduler")
  ThreadPoolTaskScheduler workerModelHealthTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("worker-model-health-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(15);
    return scheduler;
  }
}
