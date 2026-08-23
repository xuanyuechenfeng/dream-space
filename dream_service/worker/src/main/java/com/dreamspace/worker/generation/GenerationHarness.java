package com.dreamspace.worker.generation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;

public class GenerationHarness {
  private final GenerationWorkerStore store;
  private final PlanningModel planning;
  private final boolean failOnClarification;
  private final int maxStageAttempts;
  private final RequirementUnderstandingStage requirementStage;
  private final ContentStructurePlanningStage structureStage;
  private final VisualConstraintStage visualStage;
  private final PromptConstructionStage promptStage;
  private final PromptAlignmentValidator promptAlignmentValidator;

  public GenerationHarness(GenerationWorkerStore store, PlanningModel planning, boolean failOnClarification) {
    this(store, planning, failOnClarification, 2);
  }

  public GenerationHarness(GenerationWorkerStore store, PlanningModel planning, boolean failOnClarification,
      int maxStageAttempts) {
    this.store = store; this.planning = planning; this.failOnClarification = failOnClarification;
    this.maxStageAttempts = Math.max(1, maxStageAttempts);
    this.requirementStage = new RequirementUnderstandingStage(planning);
    this.structureStage = new ContentStructurePlanningStage(planning);
    this.visualStage = new VisualConstraintStage(planning);
    this.promptStage = new PromptConstructionStage(planning);
    this.promptAlignmentValidator = new PromptAlignmentValidator();
  }

  public GenerationPlanBundle execute(WorkerTaskSnapshot task, GenerationAttempt attempt) {
    String trace = UUID.randomUUID().toString();
    RequirementBrief requirement = run(() -> requirementStage.execute(task, context(trace, task, attempt, "requirement_understanding")));
    require(requirement, "requirement");
    validateImageAssignments(task, requirement);
    store.recordStage(task.id(), "task.requirement_understood", "requirement_understanding",
        Map.of("confidence", requirement.confidence(), "needsClarification", requirement.needsClarification()));
    if (failOnClarification && requirement.needsClarification()) throw new GenerationProviderException(
        "PLANNING_NEEDS_CLARIFICATION", "planning needs clarification", false);
    StructurePlan modelStructure = run(() -> structureStage.execute(new PlanningStageInput(task, requirement, null, null), context(trace, task, attempt, "structure_planning")));
    require(modelStructure, "structure");
    WorkerTaskSnapshot resolvedTask = resolveSmartOutput(task, modelStructure);
    StructurePlan structure = modelStructure.withOutput(resolvedTask);
    store.recordStage(task.id(), "task.structure_planned", "structure_planning", Map.of("moduleCount", structure.modules().size()));
    VisualSpec visual = run(() -> visualStage.execute(new PlanningStageInput(resolvedTask, requirement, structure, null), context(trace, resolvedTask, attempt, "visual_constraints")));
    require(visual, "visual");
    store.recordStage(task.id(), "task.visual_constraints_ready", "visual_constraints", Map.of("style", visual.style()));
    PromptPackage prompt = run(() -> promptStage.execute(new PlanningStageInput(resolvedTask, requirement, structure, visual), context(trace, resolvedTask, attempt, "prompt_construction")));
    require(prompt, "prompt");
    prompt = promptAlignmentValidator.validate(resolvedTask, prompt);
    GenerationPlanBundle bundle = new GenerationPlanBundle(requirement, structure, visual, prompt,
        resolvedTask.ratio() == task.ratio() ? null : resolvedTask.ratio(), resolvedTask.width(), resolvedTask.height());
    store.savePlan(task.id(), bundle, hash(requirement.coreSubject() + ":" + task.mode()), "RUNNABLE");
    store.recordStage(task.id(), "task.prompt_constructed", "prompt_construction",
        Map.of("promptHash", hash(prompt.positivePrompt()), "promptVersion", prompt.promptVersion()));
    return bundle;
  }

  private WorkerTaskSnapshot resolveSmartOutput(WorkerTaskSnapshot task, StructurePlan structure) {
    if (task.ratio() != GenerationRatio.SMART) return task;
    StructureCanvas canvas = structure.canvas();
    String value = canvas == null ? null : canvas.aspectRatio();
    GenerationRatio ratio = ratio(value);
    if (ratio == null || ratio == GenerationRatio.SMART || ratio == GenerationRatio.CUSTOM)
      throw new GenerationProviderException("PLANNING_OUTPUT_INVALID", "smart output ratio is missing or invalid", false);
    int edge = task.resolution().databaseValue().equals("4K") ? 4096 : 2048;
    OutputDimensions dimensions = OutputDimensions.resolve(ratio, task.resolution());

    if (dimensions.width() > edge || dimensions.height() > edge)
      throw new GenerationProviderException("PLANNING_OUTPUT_INVALID", "smart output exceeds resolution limit", false);
    if (!store.updateResolvedDimensions(task.id(), ratio, dimensions.width(), dimensions.height()))
      throw new GenerationProviderException("PLANNING_OUTPUT_INVALID", "smart output could not be persisted", false);
    return task.withOutput(ratio, dimensions.width(), dimensions.height());
  }

  private static GenerationRatio ratio(String value) {
    if (value == null) return null;
    for (GenerationRatio candidate : GenerationRatio.values()) if (candidate.databaseValue().equals(value.trim().toLowerCase())) return candidate;
    return null;
  }
  private static String text(Object value) { return value == null ? null : String.valueOf(value); }


  private StageContext context(String trace, WorkerTaskSnapshot task, GenerationAttempt attempt, String stage) {
    return new StageContext(trace, task.id(), attempt.key(), trace + ":" + stage);
  }
  private static void require(Object value, String stage) {
    if (value == null) throw new GenerationProviderException("PLANNING_OUTPUT_INVALID", stage + " artifact is empty", false);
  }
  private static void validateImageAssignments(WorkerTaskSnapshot task, RequirementBrief requirement) {
    List<RequirementBrief.ImageAssignment> assignments = requirement.imageAssignments() == null
        ? java.util.List.of() : requirement.imageAssignments();
    if (assignments.size() != task.imageIds().size()) throw ambiguous("planning must assign every attached image");
    java.util.Set<String> attached = new java.util.HashSet<>(task.imageIds());
    java.util.Set<String> assigned = new java.util.HashSet<>();
    int target = 0, reference = 0;
    for (RequirementBrief.ImageAssignment assignment : assignments) {
      if (assignment == null || assignment.imageId() == null || !attached.contains(assignment.imageId())
          || !assigned.add(assignment.imageId()) || assignment.role() == null || assignment.confidence() < 0.70) {
        throw ambiguous("planning returned an invalid or ambiguous image assignment");
      }
      if (assignment.role() == RequirementBrief.InputImageRole.TARGET_A) target++;
      if (assignment.role() == RequirementBrief.InputImageRole.REFERENCE_B) reference++;
    }
    if (requirement.needsClarification()) throw ambiguous("planning needs image role clarification");
    switch (requirement.intent()) {
      case TEXT_TO_IMAGE -> { if (target > 0 || reference > 0) throw ambiguous("text-to-image cannot use assigned source images"); }
      case EDIT_IMAGE -> { if (target != 1 || reference > 1) throw ambiguous("edit intent requires one target image"); }
      case RECOMPOSE_IMAGE -> { if (target != 1 || reference != 1) throw ambiguous("recompose intent requires target and reference images"); }
    }
  }
  private static GenerationProviderException ambiguous(String message) {
    return new GenerationProviderException("GENERATION_IMAGE_ROLE_AMBIGUOUS", message, false);
  }
  private <T> T run(Callable<T> call) {
    GenerationProviderException last = null;
    for (int attempt = 1; attempt <= maxStageAttempts; attempt++) {
      try { return call.call(); } catch (GenerationProviderException error) {
        last = error; if (!error.retryable() || attempt == maxStageAttempts) throw error;
      } catch (Exception error) { throw new GenerationProviderException("PLANNING_STAGE_FAILED", "planning stage failed", false, error); }
    }
    throw last == null ? new GenerationProviderException("PLANNING_STAGE_FAILED", "planning stage failed", false) : last;
  }
  static String hash(String value) {
    try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder(); for (byte b : digest) out.append(String.format("%02x", b)); return out.toString();
    } catch (Exception error) { throw new IllegalStateException(error); }
  }
}
