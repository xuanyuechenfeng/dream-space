package com.dreamspace.worker.generation;

public final class RequirementUnderstandingStage implements GenerationStage<WorkerTaskSnapshot, RequirementBrief> {
  private final PlanningModel model;
  public RequirementUnderstandingStage(PlanningModel model) { this.model = model; }
  @Override public String name() { return "requirement_understanding"; }
  @Override public RequirementBrief execute(WorkerTaskSnapshot input, StageContext context) { return model.understand(input, context); }
}
