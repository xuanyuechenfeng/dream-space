package com.dreamspace.worker.generation;

public interface PlanningModel {
  RequirementBrief understand(WorkerTaskSnapshot task, StageContext context);
  StructurePlan structure(WorkerTaskSnapshot task, RequirementBrief requirement, StageContext context);
  VisualSpec visualize(WorkerTaskSnapshot task, RequirementBrief requirement, StructurePlan structure, StageContext context);
  PromptPackage prompt(WorkerTaskSnapshot task, RequirementBrief requirement, StructurePlan structure,
      VisualSpec visual, StageContext context);

  /**
   * Plans an ordered image collection before a v2 task is created. The model
   * owns semantic decomposition; the caller still validates all billing and
   * persistence constraints before freezing the result.
   */
  default CollectionPlanProposal collection(WorkerTaskSnapshot task, Integer requestedCount,
      String imageCountMode, StageContext context) {
    throw new GenerationProviderException("PLANNING_COLLECTION_UNSUPPORTED",
        "collection planning is not supported by this planning model", false);
  }
}
