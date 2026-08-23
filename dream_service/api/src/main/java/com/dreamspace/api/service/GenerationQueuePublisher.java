package com.dreamspace.api.service;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
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

  public GenerationQueuePublisher(GenerationMapper mapper, GenerationQueue queue, DreamSpaceProperties properties,
      PlatformTransactionManager transactionManager) {
    this.mapper = mapper;
    this.queue = queue;
    this.properties = properties;
    this.transactions = new TransactionTemplate(transactionManager);
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

  @Scheduled(fixedDelayString = "${dream-space.queue.publish-retry-delay-ms:5000}",
      initialDelayString = "${dream-space.queue.publish-retry-initial-delay-ms:5000}")
  public void retryPending() {
    try {
      for (GenerationTaskRecord task : mapper.listPendingQueuePublish(100)) publish(task);
    } catch (RuntimeException ignored) {
      // Readiness exposes dependency outages; the next scheduled run retries publication.
    }
  }
}
