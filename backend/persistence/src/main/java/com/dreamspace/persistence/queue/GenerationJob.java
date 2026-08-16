package com.dreamspace.persistence.queue;

public record GenerationJob(String taskId, String attemptKey, int attemptNumber, int maxAttempts, int schemaVersion) {
  public GenerationJob(String taskId) { this(taskId, taskId + ":1", 1, 3, 1); }
  public GenerationJob { if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId is required"); }
}
