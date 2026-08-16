package com.dreamspace.worker.generation;

import java.util.List;

public interface GenerationProvider {
  List<ProviderImage> generate(WorkerTaskSnapshot task, GenerationAttempt attempt);
}
