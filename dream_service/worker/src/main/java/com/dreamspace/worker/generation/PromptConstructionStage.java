package com.dreamspace.worker.generation;

public final class PromptConstructionStage implements GenerationStage<PlanningStageInput, PromptPackage> {
  private final PlanningModel model;
  public PromptConstructionStage(PlanningModel model) { this.model = model; }
  @Override public String name() { return "prompt_construction"; }
  @Override public PromptPackage execute(PlanningStageInput input, StageContext context) {
    return model.prompt(input.task(), input.requirement(), input.structure(), input.visual(), context);
  }
}
