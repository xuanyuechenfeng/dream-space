package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class JdbcGenerationWorkerStore implements GenerationWorkerStore {
  private final GenerationMapper mapper;
  private final QuotaTransactionService quota;
  private final ObjectMapper json;
  private final TransactionTemplate transactions;

  public JdbcGenerationWorkerStore(GenerationMapper mapper, QuotaTransactionService quota, ObjectMapper json,
      PlatformTransactionManager transactionManager) {
    this.mapper = mapper; this.quota = quota; this.json = json; this.transactions = new TransactionTemplate(transactionManager);
  }

  @Override public Optional<WorkerTaskSnapshot> start(String taskId, GenerationAttempt attempt) {
    return transactions.execute(status -> {
      GenerationTaskRecord task = mapper.findTask(taskId);
      if (task == null
          || (task.status() != GenerationTaskStatus.QUEUED && task.status() != GenerationTaskStatus.GENERATING)
          || (task.lastAttemptKey() != null && task.lastAttemptKey().equals(attempt.key()))
          || attempt.number() > attempt.maxAttempts()) return Optional.empty();
      int changed = mapper.claimQueuedTask(taskId, attempt.key());
      if (changed != 1) return Optional.empty();
      GenerationTaskRecord claimed = mapper.findTask(taskId);
      String eventType = task.status() == GenerationTaskStatus.QUEUED ? "task.generating" : "task.retrying";
      insertEvent(taskId, eventType, GenerationTaskStatus.GENERATING.name(), Map.of("attempt", attempt.number(), "maxAttempts", attempt.maxAttempts()));
      return Optional.of(snapshot(claimed));
    });
  }

  @Override public boolean recordModeration(String taskId, String stage, ContentModerator.Decision decision) {
    return transactions.execute(status -> {
      int changed = "input".equals(stage) ? mapper.updateInputModeration(taskId, decision.approved() ? "APPROVED" : "REJECTED")
          : mapper.updateOutputModeration(taskId, decision.approved() ? "APPROVED" : "REJECTED");
      if (changed != 1) return false;
      insertEvent(taskId, "task." + stage + ".moderated", GenerationTaskStatus.GENERATING.name(), Map.of("approved", decision.approved(), "code", decision.code() == null ? "" : decision.code()));
      return true;
    });
  }

  @Override public boolean succeed(String taskId, List<StoredGenerationResult> results) {
    return transactions.execute(status -> {
      GenerationTaskRecord task = mapper.findTask(taskId);
      if (task == null || task.status() != GenerationTaskStatus.GENERATING) return false;
      if (results.isEmpty() || results.size() > task.imageCount()) return false;
      String terminalStatus = results.size() == task.imageCount() ? "SUCCEEDED" : "PARTIALLY_SUCCEEDED";
      if (mapper.transition(taskId, "GENERATING", terminalStatus, null, null, true) != 1) return false;
      for (StoredGenerationResult result : results) mapper.insertResult(result.id(), taskId, result.index(), result.imagePath(), result.objectKey(), result.thumbnailObjectKey(), result.checksumSha256(), result.width(), result.height(), result.mimeType(), result.byteSize(), result.thumbnailWidth(), result.thumbnailHeight(), result.thumbnailByteSize(), true);
      if (!quota.settle(task.userId(), task.id(), task.totalCost(), "CONSUME", "consume:" + task.id())) throw new IllegalStateException("quota consume failed");
      insertEvent(taskId, results.size() == task.imageCount() ? "task.succeeded" : "task.partially_succeeded", terminalStatus, Map.of("resultCount", results.size()));
      mapper.touchSession(task.sessionId());
      return true;
    });
  }

  @Override public boolean fail(String taskId, String code, String message, GenerationAttempt attempt, Map<String, Object> deadLetterPayload) {
    return transactions.execute(status -> {
      GenerationTaskRecord task = mapper.findTask(taskId);
      if (task == null || (task.status() != GenerationTaskStatus.GENERATING && task.status() != GenerationTaskStatus.QUEUED)) return false;
      if (mapper.transition(taskId, task.status().name(), "FAILED", code, message, true) != 1) return false;
      if (!quota.settle(task.userId(), task.id(), task.totalCost(), "RELEASE", "failure-release:" + task.id())) throw new IllegalStateException("quota release failed");
      if (deadLetterPayload != null) mapper.upsertDeadLetter(UUID.randomUUID().toString(), task.id(), code, message, attempt.number(), json(deadLetterPayload));
      if (deadLetterPayload != null) insertEvent(taskId, "task.dead_lettered", "FAILED", Map.of("errorCode", code, "attempts", attempt.number()));
      insertEvent(taskId, "task.failed", "FAILED", Map.of("errorCode", code));
      mapper.touchSession(task.sessionId());
      return true;
    });
  }

  private WorkerTaskSnapshot snapshot(GenerationTaskRecord task) { return new WorkerTaskSnapshot(task.id(), task.userId(), task.sessionId(), task.prompt(), task.model(), task.ratio(), task.resolution(), task.imageCount(), task.totalCost(), task.attempts()); }
  private void insertEvent(String taskId, String type, String status, Map<String, ?> payload) { mapper.insertEvent(taskId, type, status, json(payload)); }
  private String json(Object value) { try { return json.writeValueAsString(value); } catch (IOException e) { throw new IllegalStateException(e); } }
}
