package com.dreamspace.worker.generation;

public record GenerationAttempt(String key, int number, int maxAttempts) {
  public GenerationAttempt {
    if (key == null || key.isBlank() || number < 1 || maxAttempts < number) throw new IllegalArgumentException("invalid generation attempt");
  }
}
