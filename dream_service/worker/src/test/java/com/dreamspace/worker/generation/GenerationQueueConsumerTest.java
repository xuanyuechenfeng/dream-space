package com.dreamspace.worker.generation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.queue.GenerationJob;
import com.dreamspace.common.persistence.queue.GenerationQueue;
import com.dreamspace.worker.observability.WorkerMetrics;
import com.dreamspace.worker.observability.WorkerModelHealthIndicator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationQueueConsumerTest {
  private final GenerationQueue queue = mock(GenerationQueue.class);
  private final GenerationProcessor processor = mock(GenerationProcessor.class);
  private final GenerationQueueConsumer consumer = new GenerationQueueConsumer(queue, processor,
      properties(), new WorkerMetrics(new SimpleMeterRegistry(), properties()));

  @Test
  void doesNotReadOrReclaimTasksWhileModelsAreUnavailable() {
    WorkerModelHealthIndicator modelHealth = mock(WorkerModelHealthIndicator.class);
    when(modelHealth.isReady()).thenReturn(false);
    GenerationQueueConsumer guarded = new GenerationQueueConsumer(queue, processor, properties(),
        new WorkerMetrics(new SimpleMeterRegistry(), properties()), modelHealth);

    guarded.poll();

    verify(queue, never()).pendingCount();
    verify(queue, never()).read(any(), anyInt(), any(Duration.class));
    verify(queue, never()).reclaim(any(), any(), anyInt());
    verify(processor, never()).process(any(), any());
  }

  @Test
  void acknowledgesDeliveryAfterProcessorReturns() {
    GenerationQueue.Delivery delivery = delivery(1);
    when(queue.reclaim(any(), any(), anyInt())).thenReturn(List.of());
    when(queue.read(any(), anyInt(), any(Duration.class))).thenReturn(List.of(delivery));
    when(processor.process(any(), any())).thenReturn(new GenerationProcessor.Outcome(
        "task-1", GenerationProcessor.Status.SUCCEEDED));

    consumer.poll();

    verify(queue).acknowledge("message-1");
  }

  @Test
  void keepsRetryableDeliveryPending() {
    GenerationQueue.Delivery delivery = delivery(1);
    when(queue.reclaim(any(), any(), anyInt())).thenReturn(List.of());
    when(queue.read(any(), anyInt(), any(Duration.class))).thenReturn(List.of(delivery));
    doThrow(new GenerationProviderException("TEMPORARY", "temporary", true))
        .when(processor).process(any(), any());

    consumer.poll();

    verify(queue, never()).acknowledge(any());
  }

  @Test
  void acknowledgesNonRetryableDeliveryThrownByProcessor() {
    GenerationQueue.Delivery delivery = delivery(1);
    when(queue.reclaim(any(), any(), anyInt())).thenReturn(List.of());
    when(queue.read(any(), anyInt(), any(Duration.class))).thenReturn(List.of(delivery));
    doThrow(new GenerationProviderException("INVALID", "invalid", false))
        .when(processor).process(any(), any());

    consumer.poll();

    verify(queue).acknowledge("message-1");
  }

  private static GenerationQueue.Delivery delivery(int count) {
    return new GenerationQueue.Delivery("message-1",
        new GenerationJob("task-1", "task-1:1", 1, 3, 1), count);
  }

  private static DreamSpaceProperties properties() {
    return new DreamSpaceProperties(
        new DreamSpaceProperties.Redis("redis://localhost:6379", "generation", "generation-workers",
            Duration.ofSeconds(30)), null, null, null, null);
  }
}
