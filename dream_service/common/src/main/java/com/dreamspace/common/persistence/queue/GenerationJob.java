package com.dreamspace.common.persistence.queue;

public record GenerationJob(String taskId, String attemptKey, int attemptNumber, int maxAttempts, int schemaVersion,
    String kind, String targetId, String idempotencyKey) {
  public GenerationJob(String taskId, String attemptKey, int attemptNumber, int maxAttempts, int schemaVersion) {
    this(taskId, attemptKey, attemptNumber, maxAttempts, schemaVersion, "EXECUTION", taskId, attemptKey);
  }
  public GenerationJob(String taskId) { this(taskId, taskId + ":1", 1, 3, 1); }
  public GenerationJob {
    if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId is required");
    kind = kind == null || kind.isBlank() ? "EXECUTION" : kind.toUpperCase(java.util.Locale.ROOT);
    targetId = targetId == null || targetId.isBlank() ? taskId : targetId;
    idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? attemptKey : idempotencyKey;
    if (!kind.equals("PREFLIGHT") && !kind.equals("EXECUTION")) throw new IllegalArgumentException("invalid generation work item kind");
  }
  public static GenerationJob preflight(String preflightId, String idempotencyKey, int maxAttempts) {
    return new GenerationJob(preflightId, preflightId + ":preflight", 1, maxAttempts, 2, "PREFLIGHT", preflightId, idempotencyKey);
  }
}
