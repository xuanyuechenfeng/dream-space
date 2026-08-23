package com.dreamspace.worker.generation;

public record StageContext(String traceId, String taskId, String attemptKey, String stageRunId) {}
