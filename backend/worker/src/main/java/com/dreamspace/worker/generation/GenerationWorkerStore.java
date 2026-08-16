package com.dreamspace.worker.generation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GenerationWorkerStore {
  Optional<WorkerTaskSnapshot> start(String taskId, GenerationAttempt attempt);
  boolean recordModeration(String taskId, String stage, ContentModerator.Decision decision);
  boolean succeed(String taskId, List<StoredGenerationResult> results);
  boolean fail(String taskId, String code, String message, GenerationAttempt attempt, Map<String, Object> deadLetterPayload);
}
