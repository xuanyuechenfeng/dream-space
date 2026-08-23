package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GenerationWorkerStore {
  Optional<WorkerTaskSnapshot> start(String taskId, GenerationAttempt attempt);
  boolean recordModeration(String taskId, String stage, ContentModerator.Decision decision);
  boolean succeed(String taskId, List<StoredGenerationResult> results);
  boolean fail(String taskId, String code, String message, GenerationAttempt attempt, Map<String, Object> deadLetterPayload);

  default void savePlan(String taskId, GenerationPlanBundle plan, String inputHash, String status) {}
  default boolean updateResolvedDimensions(String taskId, GenerationRatio ratio, int width, int height) { return true; }
  default void recordStage(String taskId, String type, String stage, Map<String, ?> payload) {}
  default void recordIteration(String taskId, int iteration, String promptHash, String status,
      String provider, String model, String providerRequestId, EvaluationReport evaluation,
      RefinementPatch refinement, String errorCode) {}
}
