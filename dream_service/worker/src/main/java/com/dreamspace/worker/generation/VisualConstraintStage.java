package com.dreamspace.worker.generation;

public final class VisualConstraintStage implements GenerationStage<PlanningStageInput, VisualSpec> {
  private final PlanningModel model;
  public VisualConstraintStage(PlanningModel model) { this.model = model; }
  @Override public String name() { return "visual_constraints"; }
  @Override public VisualSpec execute(PlanningStageInput input, StageContext context) { return model.visualize(input.task(), input.requirement(), input.structure(), context); }
}
