package com.dreamspace.worker.generation;

public interface PlanningModel {
  RequirementBrief understand(WorkerTaskSnapshot task, StageContext context);
  StructurePlan structure(WorkerTaskSnapshot task, RequirementBrief requirement, StageContext context);
  VisualSpec visualize(WorkerTaskSnapshot task, RequirementBrief requirement, StructurePlan structure, StageContext context);
  PromptPackage prompt(WorkerTaskSnapshot task, RequirementBrief requirement, StructurePlan structure,
      VisualSpec visual, StageContext context);
}
