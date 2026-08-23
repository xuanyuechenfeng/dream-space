package com.dreamspace.worker.generation;

public record GenerationPlanBundle(RequirementBrief requirement, StructurePlan structure,
    VisualSpec visual, PromptPackage promptPackage, com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio resolvedRatio,
    Integer resolvedWidth, Integer resolvedHeight) {
  public GenerationPlanBundle(RequirementBrief requirement, StructurePlan structure, VisualSpec visual, PromptPackage promptPackage) {
    this(requirement, structure, visual, promptPackage, null, null, null);
  }
  public String targetImageId() { return role(RequirementBrief.InputImageRole.TARGET_A); }
  public String referenceImageId() { return role(RequirementBrief.InputImageRole.REFERENCE_B); }
  public WorkerTaskSnapshot applyOutput(WorkerTaskSnapshot task) {
    return resolvedRatio == null ? task : task.withOutput(resolvedRatio, resolvedWidth, resolvedHeight);
  }
  private String role(RequirementBrief.InputImageRole expected) {
    if (requirement.imageAssignments() == null) return null;
    return requirement.imageAssignments().stream().filter(item -> item != null && item.role() == expected)
        .map(RequirementBrief.ImageAssignment::imageId).findFirst().orElse(null);
  }
}
