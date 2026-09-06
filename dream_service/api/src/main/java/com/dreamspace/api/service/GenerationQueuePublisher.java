package com.dreamspace.api.service;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.dreamspace.common.persistence.queue.GenerationJob;
import com.dreamspace.common.persistence.queue.GenerationQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GenerationQueuePublisher {
  private static final int SCHEMA_VERSION = 1;
  private final GenerationMapper mapper;
  private final GenerationQueue queue;
  private final DreamSpaceProperties properties;
  private final TransactionTemplate transactions;
  private final GenerationV2Mapper v2;

  public GenerationQueuePublisher(GenerationMapper mapper, GenerationQueue queue, DreamSpaceProperties properties,
      PlatformTransactionManager transactionManager) {
    this(mapper, queue, properties, transactionManager, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public GenerationQueuePublisher(GenerationMapper mapper, GenerationQueue queue, DreamSpaceProperties properties,
      PlatformTransactionManager transactionManager, GenerationV2Mapper v2) {
    this.mapper = mapper;
    this.queue = queue;
    this.properties = properties;
    this.transactions = new TransactionTemplate(transactionManager);
    this.v2 = v2;
  }

  public boolean publish(GenerationTaskRecord task) {
    if (task == null || task.queueJobId() != null) return true;
    int attemptNumber = Math.max(1, task.attempts() + 1);
    String attemptKey = task.id() + ":" + attemptNumber;
    try {
      String messageId = queue.publish(new GenerationJob(task.id(), attemptKey, attemptNumber,
          properties.queue().maxAttempts(), SCHEMA_VERSION));
      transactions.executeWithoutResult(status -> mapper.setQueueMessageId(task.id(), messageId));
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  public boolean publishPreflight(String preflightId, String idempotencyKey) {
    try {
      queue.publish(GenerationJob.preflight(preflightId, idempotencyKey, properties.queue().maxAttempts()));
      return true;
    } catch (RuntimeException ignored) { return false; }
  }

  public boolean publishExecution(String taskId, String executionId, String idempotencyKey) {
    try {
      String messageId = queue.publish(new GenerationJob(taskId, executionId + ":1", 1,
          properties.queue().maxAttempts(), 2, "EXECUTION", executionId, idempotencyKey));
      transactions.executeWithoutResult(status -> { if (v2 != null) v2.setExecutionQueueMessage(executionId, messageId); });
      return messageId != null;
    } catch (RuntimeException ignored) { return false; }
  }

  @Scheduled(fixedDelayString = "${dream-space.queue.publish-retry-delay-ms:5000}",
      initialDelayString = "${dream-space.queue.publish-retry-initial-delay-ms:5000}")
  public void retryPending() {
    try {
      for (GenerationTaskRecord task : mapper.listPendingQueuePublish(100)) publish(task);
      if (v2 != null) {
        for (var preflight : v2.listPendingPreflights(100)) publishPreflight(preflight.id(), preflight.idempotencyKey());
        for (var execution : v2.listPendingExecutions(100)) publishExecution(execution.taskId(), execution.id(), "execution:" + execution.id());
      }
    } catch (RuntimeException ignored) {
      // Readiness exposes dependency outages; the next scheduled run retries publication.
    }
  }
}
