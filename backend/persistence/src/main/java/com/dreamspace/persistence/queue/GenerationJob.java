package com.dreamspace.persistence.queue;

public record GenerationJob(String taskId) {
  public GenerationJob { if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId is required"); }
}
