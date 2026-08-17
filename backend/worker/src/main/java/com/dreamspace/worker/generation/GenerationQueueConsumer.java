package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.queue.GenerationJob;
import com.dreamspace.common.persistence.queue.GenerationQueue;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "dream-space.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GenerationQueueConsumer {
  private static final Logger log = LoggerFactory.getLogger(GenerationQueueConsumer.class);
  private final GenerationQueue queue;
  private final GenerationProcessor processor;
  private final DreamSpaceProperties properties;
  private final String consumerName;

  public GenerationQueueConsumer(GenerationQueue queue, GenerationProcessor processor,
      DreamSpaceProperties properties) {
    this.queue = queue;
    this.processor = processor;
    this.properties = properties;
    this.consumerName = hostName() + "-" + ManagementFactory.getRuntimeMXBean().getName().replace('@', '-');
  }

  @Scheduled(initialDelayString = "${dream-space.worker.initial-delay-ms:1000}",
      fixedDelayString = "${dream-space.worker.poll-delay-ms:500}")
  public void poll() {
    process(queue.reclaim(consumerName, properties.redis().reclaimIdle(), 10));
    process(queue.read(consumerName, 10, Duration.ofMillis(100)));
  }

  private void process(List<GenerationQueue.Delivery> deliveries) {
    for (GenerationQueue.Delivery delivery : deliveries) {
      GenerationJob job = delivery.job();
      int attemptNumber = Math.max(job.attemptNumber(), delivery.deliveryCount());
      int maxAttempts = Math.max(1, Math.min(job.maxAttempts(), properties.queue().maxAttempts()));
      GenerationAttempt attempt = new GenerationAttempt(job.taskId() + ":" + attemptNumber,
          Math.min(attemptNumber, maxAttempts), maxAttempts);
      try {
        if (job.schemaVersion() != 1 || job.attemptNumber() < 1 || job.maxAttempts() < 1
            || attemptNumber > maxAttempts) processor.rejectInvalidMessage(job, attempt);
        else processor.process(job, attempt);
        queue.acknowledge(delivery.messageId());
      } catch (GenerationProviderException retryable) {
        if (!retryable.retryable()) queue.acknowledge(delivery.messageId());
        log.atWarn().addKeyValue("taskId", job.taskId()).addKeyValue("attempt", attemptNumber)
            .addKeyValue("errorCode", retryable.code()).log("generation attempt will be reclaimed");
      } catch (RuntimeException error) {
        log.atError().addKeyValue("taskId", job.taskId()).addKeyValue("attempt", attemptNumber)
            .log("generation delivery was not acknowledged", error);
      }
    }
  }

  private static String hostName() {
    try { return InetAddress.getLocalHost().getHostName(); }
    catch (UnknownHostException ignored) { return "worker"; }
  }
}
