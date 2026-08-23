package com.dreamspace.worker.generation;

import java.util.List;

public interface QualityEvaluationModel {
  EvaluationResult evaluate(WorkerTaskSnapshot task, GenerationPlanBundle plan,
      List<ProviderImage> images, int iteration);

  record EvaluationResult(EvaluationReport report, RefinementPatch refinement) {}
}
