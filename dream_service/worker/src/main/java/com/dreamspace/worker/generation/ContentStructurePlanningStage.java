package com.dreamspace.worker.generation;

public final class ContentStructurePlanningStage implements GenerationStage<PlanningStageInput, StructurePlan> {
  private final PlanningModel model;
  public ContentStructurePlanningStage(PlanningModel model) { this.model = model; }
  @Override public String name() { return "structure_planning"; }
  @Override public StructurePlan execute(PlanningStageInput input, StageContext context) { return model.structure(input.task(), input.requirement(), context); }
}
