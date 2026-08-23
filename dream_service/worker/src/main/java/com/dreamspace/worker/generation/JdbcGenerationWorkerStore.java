package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.moderation.ModerationMapper;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.dreamspace.worker.observability.WorkerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class JdbcGenerationWorkerStore implements GenerationWorkerStore {
  private static final Logger log = LoggerFactory.getLogger(JdbcGenerationWorkerStore.class);
  private final GenerationMapper mapper;
  private final QuotaTransactionService quota;
  private final ObjectMapper json;
  private final TransactionTemplate transactions;
  private final ModerationMapper moderation;
  private final WorkerMetrics metrics;
  private final String moderationModel;

  public JdbcGenerationWorkerStore(GenerationMapper mapper, QuotaTransactionService quota, ObjectMapper json,
      PlatformTransactionManager transactionManager, ModerationMapper moderation, WorkerMetrics metrics,
      OpenAiChatProperties planningChat) {
    this.mapper = mapper; this.quota = quota; this.json = json; this.transactions = new TransactionTemplate(transactionManager);
    this.moderation = moderation; this.metrics = metrics;
    this.moderationModel = planningChat.getOptions().getModel();
  }

  @Override public Optional<WorkerTaskSnapshot> start(String taskId, GenerationAttempt attempt) {
    return transactions.execute(status -> {
      GenerationTaskRecord task = mapper.findTask(taskId);
      if (task == null) {
        log.atWarn().addKeyValue("taskId", taskId).addKeyValue("attempt", attempt.number()).log("generation task not found while claiming");
        return Optional.empty();
      }
      if (task.status() != GenerationTaskStatus.QUEUED && task.status() != GenerationTaskStatus.GENERATING) {
        log.atInfo().addKeyValue("taskId", taskId).addKeyValue("status", task.status()).addKeyValue("attempt", attempt.number()).log("generation task is no longer claimable");
        return Optional.empty();
      }
      if (task.lastAttemptKey() != null && task.lastAttemptKey().equals(attempt.key())) {
        log.atInfo().addKeyValue("taskId", taskId).addKeyValue("attemptKey", attempt.key()).log("generation attempt already claimed");
        return Optional.empty();
      }
      if (attempt.number() > attempt.maxAttempts()) {
        log.atError().addKeyValue("taskId", taskId).addKeyValue("attempt", attempt.number()).addKeyValue("maxAttempts", attempt.maxAttempts()).log("generation attempt exceeded maximum");
        return Optional.empty();
      }
      int changed = mapper.claimQueuedTask(taskId, attempt.key());
      if (changed != 1) {
        log.atWarn().addKeyValue("taskId", taskId).addKeyValue("attemptKey", attempt.key()).log("generation task claim lost race");
        return Optional.empty();
      }
      GenerationTaskRecord claimed = mapper.findTask(taskId);
      String eventType = task.status() == GenerationTaskStatus.QUEUED ? "task.generating" : "task.retrying";
      insertEvent(taskId, eventType, GenerationTaskStatus.GENERATING.name(), Map.of("attempt", attempt.number(), "maxAttempts", attempt.maxAttempts()));
      log.atInfo().addKeyValue("taskId", taskId).addKeyValue("attempt", attempt.number()).addKeyValue("eventType", eventType).log("generation task claim persisted");
      return Optional.of(snapshot(claimed));
    });
  }

  @Override public boolean recordModeration(String taskId, String stage, ContentModerator.Decision decision) {
    return transactions.execute(status -> {
      int changed = "input".equals(stage) ? mapper.updateInputModeration(taskId, decision.approved() ? "APPROVED" : "REJECTED")
          : mapper.updateOutputModeration(taskId, decision.approved() ? "APPROVED" : "REJECTED");
      if (changed != 1) return false;
      if (!decision.approved()) {
        GenerationTaskRecord task = mapper.findTask(taskId);
        if (task == null) return false;
        String caseId = UUID.randomUUID().toString();
        String evidence = json(Map.of("code", decision.code() == null ? "MODEL_REJECTED" : decision.code()));
        int inserted = moderation.insertRejectedCase(caseId, task.id(), null, task.userId(),
            "input".equals(stage) ? "INPUT" : "OUTPUT",
            decision.code() == null ? "MODEL_REJECTED" : decision.code(),
            evidence, moderationModel, "moderation-schema-v1");
        if (inserted == 1) {
          moderation.insertAudit(UUID.randomUUID().toString(), caseId, "worker", "SYSTEM", "MODEL_REJECTED",
              json(Map.of()), json(Map.of("stage", stage, "model", moderationModel,
                  "reasonCode", decision.code() == null ? "MODEL_REJECTED" : decision.code())));
        }
        metrics.moderationPending(moderation.countPending());
      }
      insertEvent(taskId, "task." + stage + ".moderated", GenerationTaskStatus.GENERATING.name(), Map.of("approved", decision.approved(), "code", decision.code() == null ? "" : decision.code()));
      return true;
    });
  }

  @Override public boolean succeed(String taskId, List<StoredGenerationResult> results) {
    return transactions.execute(status -> {
      GenerationTaskRecord task = mapper.findTask(taskId);
      if (task == null || task.status() != GenerationTaskStatus.GENERATING) {
        log.atWarn().addKeyValue("taskId", taskId).log("generation success ignored because task is not generating");
        return false;
      }
      if (results.isEmpty() || results.size() > task.imageCount()) {
        log.atError().addKeyValue("taskId", taskId).addKeyValue("resultCount", results.size()).log("generation success rejected because result count is invalid");
        return false;
      }
      String terminalStatus = results.size() == task.imageCount() ? "SUCCEEDED" : "PARTIALLY_SUCCEEDED";
      if (mapper.transition(taskId, "GENERATING", terminalStatus, null, null, true) != 1) return false;
      for (StoredGenerationResult result : results) mapper.insertResult(result.id(), taskId, result.index(), result.imagePath(), result.objectKey(), result.thumbnailObjectKey(), result.checksumSha256(), result.width(), result.height(), result.mimeType(), result.byteSize(), result.thumbnailWidth(), result.thumbnailHeight(), result.thumbnailByteSize(), true);
      if (!quota.settle(task.userId(), task.id(), task.totalCost(), "CONSUME", "consume:" + task.id())) throw new IllegalStateException("quota consume failed");
      log.atInfo().addKeyValue("taskId", taskId).addKeyValue("status", terminalStatus).addKeyValue("resultCount", results.size()).log("generation success persisted and quota consumed");
      insertEvent(taskId, results.size() == task.imageCount() ? "task.succeeded" : "task.partially_succeeded", terminalStatus, Map.of("resultCount", results.size()));
      mapper.touchSession(task.sessionId());
      return true;
    });
  }

  @Override public boolean fail(String taskId, String code, String message, GenerationAttempt attempt, Map<String, Object> deadLetterPayload) {
    return transactions.execute(status -> {
      GenerationTaskRecord task = mapper.findTask(taskId);
      if (task == null || (task.status() != GenerationTaskStatus.GENERATING && task.status() != GenerationTaskStatus.QUEUED)) {
        log.atWarn().addKeyValue("taskId", taskId).addKeyValue("errorCode", code).log("generation failure ignored because task is already terminal");
        return false;
      }
      if (mapper.transition(taskId, task.status().name(), "FAILED", code, message, true) != 1) return false;
      if (!quota.settle(task.userId(), task.id(), task.totalCost(), "RELEASE", "failure-release:" + task.id())) throw new IllegalStateException("quota release failed");
      if (deadLetterPayload != null) mapper.upsertDeadLetter(UUID.randomUUID().toString(), task.id(), code, message, attempt.number(), json(deadLetterPayload));
      if (deadLetterPayload != null) insertEvent(taskId, "task.dead_lettered", "FAILED", Map.of("errorCode", code, "attempts", attempt.number()));
      insertEvent(taskId, "task.failed", "FAILED", Map.of("errorCode", code));
      log.atError().addKeyValue("taskId", taskId).addKeyValue("errorCode", code).addKeyValue("attempt", attempt.number()).addKeyValue("deadLetter", deadLetterPayload != null).log("generation failure persisted and quota released");
      mapper.touchSession(task.sessionId());
      return true;
    });
  }

  @Override public void savePlan(String taskId, GenerationPlanBundle plan, String inputHash, String status) {
    try {
      mapper.upsertPlan(UUID.randomUUID().toString(), taskId, "requirement-v1/structure-v1/visual-v1/prompt-v1", status,
          inputHash, json(plan.requirement()), json(plan.structure()), json(plan.visual()), json(plan.promptPackage()));
    } catch (RuntimeException error) { throw error; }
  }

  @Override public boolean updateResolvedDimensions(String taskId,
      com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio ratio, int width, int height) {
    return mapper.updateResolvedDimensions(taskId, ratio.databaseValue(), width, height) == 1;
  }

  @Override public void recordStage(String taskId, String type, String stage, Map<String, ?> payload) {
    Map<String, Object> event = new java.util.HashMap<>(); event.put("stage", stage); event.putAll(payload);
    mapper.insertEvent(taskId, type, GenerationTaskStatus.GENERATING.name(), json(event));
  }

  @Override public void recordIteration(String taskId, int iteration, String promptHash, String status,
      String provider, String model, String providerRequestId, EvaluationReport evaluation,
      RefinementPatch refinement, String errorCode) {
    mapper.upsertIteration(UUID.randomUUID().toString(), taskId, iteration, promptHash, status, provider, model,
        providerRequestId, json(evaluation), refinement == null ? null : json(refinement), errorCode,
        java.time.Instant.now());
  }

  private WorkerTaskSnapshot snapshot(GenerationTaskRecord task) {
    List<String> imageIds = task.imageIds() == null || !task.imageIds().isArray() ? List.of()
        : json.convertValue(task.imageIds(), json.getTypeFactory().constructCollectionType(List.class, String.class));
    return new WorkerTaskSnapshot(task.id(), task.userId(), task.sessionId(), task.prompt(), task.mode(), imageIds,
        task.model(), task.ratio(), task.resolution(), task.width(), task.height(), task.imageCount(), task.totalCost(), task.attempts());
  }
  private void insertEvent(String taskId, String type, String status, Map<String, ?> payload) { mapper.insertEvent(taskId, type, status, json(payload)); }
  private String json(Object value) { try { return json.writeValueAsString(value); } catch (IOException e) { throw new IllegalStateException(e); } }
}
