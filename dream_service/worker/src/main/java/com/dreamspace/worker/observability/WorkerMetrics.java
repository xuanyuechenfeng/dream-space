package com.dreamspace.worker.observability;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public final class WorkerMetrics {
  private final MeterRegistry registry;
  private final AtomicLong pending = new AtomicLong();
  private final AtomicLong moderationPending = new AtomicLong();
  private final AtomicLong reconciliationBlocked = new AtomicLong();
  private final String storageMode;

  public WorkerMetrics(MeterRegistry registry, DreamSpaceProperties properties) {
    this.registry = registry;
    this.storageMode = safe(properties.storage().mode());
    Gauge.builder("dreamspace_worker_queue_pending", pending, AtomicLong::get)
        .description("Redis Stream messages currently pending for the generation consumer group")
        .tag("stream", safe(properties.redis().stream()))
        .tag("group", safe(properties.redis().consumerGroup())).register(registry);
    Gauge.builder("dreamspace_moderation_pending", moderationPending, AtomicLong::get)
        .description("Generation tasks waiting for moderation operations")
        .tag("stage", "worker").register(registry);
    Gauge.builder("dreamspace_quota_reconciliation_blocked_total", reconciliationBlocked, AtomicLong::get)
        .description("Quota reconciliation findings that remain BLOCKED").tag("reason", "any").register(registry);
  }

  public void pending(long value) { pending.set(Math.max(0, value)); }
  public void moderationPending(long value) { moderationPending.set(Math.max(0, value)); }
  public void reconciliationBlocked(long value) { reconciliationBlocked.set(Math.max(0, value)); }
  public void recordAttempt(String stage, String outcome, String errorCode) {
    Counter.builder("dreamspace_generation_attempt_total")
        .tag("stage", safe(stage)).tag("outcome", safe(outcome)).tag("error_code", safe(errorCode))
        .register(registry).increment();
  }
  public void recordDeadLetter(String errorCode) {
    Counter.builder("dreamspace_generation_dead_letter_total")
        .description("Generation attempts placed in the dead-letter table")
        .tag("error_code", safe(errorCode)).register(registry).increment();
  }
  public void recordCleanupFailure() {
    Counter.builder("dreamspace_object_cleanup_failure_total")
        .description("Object cleanup failures")
        .tag("storage", storageMode).tag("operation", "delete").register(registry).increment();
  }
  public long startModel() { return System.nanoTime(); }
  public void stopModel(long startedNanos, String provider, String model, String stage) {
    Timer.builder("dreamspace_model_request_duration_seconds")
        .tag("provider", safe(provider)).tag("model", safe(model)).tag("stage", safe(stage))
        .publishPercentileHistogram().register(registry)
        .record(System.nanoTime() - startedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
  }
  public long startImageProcessing() { return System.nanoTime(); }
  public void stopImageProcessing(long startedNanos, String operation) {
    Timer.builder("dreamspace_image_processing_duration_seconds")
        .tag("operation", safe(operation)).publishPercentileHistogram().register(registry)
        .record(System.nanoTime() - startedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^A-Za-z0-9_.-]", "_");
  }
}
