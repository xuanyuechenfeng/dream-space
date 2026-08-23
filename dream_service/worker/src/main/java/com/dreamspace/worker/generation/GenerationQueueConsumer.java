package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.queue.GenerationJob;
import com.dreamspace.common.persistence.queue.GenerationQueue;
import com.dreamspace.worker.observability.WorkerMetrics;
import com.dreamspace.worker.observability.WorkerModelHealthIndicator;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private final WorkerMetrics metrics;
  private final WorkerModelHealthIndicator modelHealth;
  private final String consumerName;
  private final AtomicBoolean queueFailureReported = new AtomicBoolean();
  private final AtomicBoolean modelUnavailableReported = new AtomicBoolean();

  @Deprecated
  public GenerationQueueConsumer(GenerationQueue queue, GenerationProcessor processor,
      DreamSpaceProperties properties, WorkerMetrics metrics) {
    this(queue, processor, properties, metrics, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public GenerationQueueConsumer(GenerationQueue queue, GenerationProcessor processor,
      DreamSpaceProperties properties, WorkerMetrics metrics, WorkerModelHealthIndicator modelHealth) {
    this.queue = queue;
    this.processor = processor;
    this.properties = properties;
    this.metrics = metrics;
    this.modelHealth = modelHealth;
    this.consumerName = hostName() + "-" + ManagementFactory.getRuntimeMXBean().getName().replace('@', '-');
    log.atInfo().addKeyValue("consumer", consumerName).addKeyValue("stream", properties.redis().stream())
        .addKeyValue("consumerGroup", properties.redis().consumerGroup())
        .addKeyValue("reclaimIdle", properties.redis().reclaimIdle())
        .addKeyValue("maxAttempts", properties.queue().maxAttempts())
        .log("generation queue consumer initialized");
  }

  @Scheduled(initialDelayString = "${dream-space.worker.initial-delay-ms:1000}",
      fixedDelayString = "${dream-space.worker.poll-delay-ms:500}")
  public void poll() {
     if (modelHealth != null && !modelHealth.isReady()) {
      if (modelUnavailableReported.compareAndSet(false, true)) {
        log.warn("generation queue polling paused because model services are not ready");
      }
      return;
    }
    if (modelUnavailableReported.compareAndSet(true, false)) {
      log.info("generation queue polling resumed after model services recovered");
    }
   try {
      long pendingBefore = queue.pendingCount();
      metrics.pending(pendingBefore);
      List<GenerationQueue.Delivery> reclaimed = queue.reclaim(consumerName, properties.redis().reclaimIdle(), 10);
      if (!reclaimed.isEmpty()) log.atInfo().addKeyValue("consumer", consumerName)
          .addKeyValue("count", reclaimed.size()).addKeyValue("pendingBefore", pendingBefore)
          .log("generation deliveries reclaimed");
      process(reclaimed, true);
      List<GenerationQueue.Delivery> fresh = queue.read(consumerName, 10, Duration.ofMillis(100));
      if (!fresh.isEmpty()) log.atInfo().addKeyValue("consumer", consumerName)
          .addKeyValue("count", fresh.size()).log("generation deliveries received");
      process(fresh, false);
      metrics.pending(queue.pendingCount());
      if (queueFailureReported.compareAndSet(true, false)) log.info("generation queue poll recovered");
    } catch (RuntimeException error) {
      if (queueFailureReported.compareAndSet(false, true)) log.error("generation queue poll failed; scheduler will retry", error);
      else log.debug("generation queue poll still failing", error);
    }
  }

  private void process(List<GenerationQueue.Delivery> deliveries, boolean reclaimed) {
    for (GenerationQueue.Delivery delivery : deliveries) {
      GenerationJob job = delivery.job();
      int attemptNumber = Math.max(job.attemptNumber(), delivery.deliveryCount());
      int maxAttempts = Math.max(1, Math.min(job.maxAttempts(), properties.queue().maxAttempts()));
      GenerationAttempt attempt = new GenerationAttempt(job.taskId() + ":" + attemptNumber,
          Math.min(attemptNumber, maxAttempts), maxAttempts);
      long started = System.nanoTime();
      log.atInfo().addKeyValue("taskId", job.taskId()).addKeyValue("messageId", delivery.messageId())
          .addKeyValue("attempt", attempt.number()).addKeyValue("maxAttempts", maxAttempts)
          .addKeyValue("deliveryCount", delivery.deliveryCount()).addKeyValue("reclaimed", reclaimed)
          .log("generation delivery processing started");
      try {
        if (job.schemaVersion() != 1 || job.attemptNumber() < 1 || job.maxAttempts() < 1
            || attemptNumber > maxAttempts) processor.rejectInvalidMessage(job, attempt);
        else processor.process(job, attempt);
        queue.acknowledge(delivery.messageId());
        log.atInfo().addKeyValue("taskId", job.taskId()).addKeyValue("messageId", delivery.messageId())
            .addKeyValue("attempt", attempt.number()).addKeyValue("durationMs", elapsedMillis(started))
            .log("generation delivery acknowledged");
      } catch (GenerationProviderException retryable) {
        if (!retryable.retryable()) {
          queue.acknowledge(delivery.messageId());
          log.atError().addKeyValue("taskId", job.taskId()).addKeyValue("messageId", delivery.messageId())
              .addKeyValue("attempt", attempt.number()).addKeyValue("errorCode", retryable.code())
              .addKeyValue("durationMs", elapsedMillis(started))
              .log("generation delivery failed permanently and was acknowledged", retryable);
        } else {
          log.atWarn().addKeyValue("taskId", job.taskId()).addKeyValue("messageId", delivery.messageId())
              .addKeyValue("attempt", attempt.number()).addKeyValue("maxAttempts", maxAttempts)
              .addKeyValue("errorCode", retryable.code()).addKeyValue("durationMs", elapsedMillis(started))
              .log("generation attempt failed and will be reclaimed");
        }
      } catch (RuntimeException error) {
        log.atError().addKeyValue("taskId", job.taskId()).addKeyValue("attempt", attemptNumber)
            .addKeyValue("messageId", delivery.messageId()).addKeyValue("durationMs", elapsedMillis(started))
            .log("generation delivery was not acknowledged", error);
      }
    }
  }

  private static long elapsedMillis(long started) { return Duration.ofNanos(System.nanoTime() - started).toMillis(); }

  private static String hostName() {
    try { return InetAddress.getLocalHost().getHostName(); }
    catch (UnknownHostException ignored) { return "worker"; }
  }
}
