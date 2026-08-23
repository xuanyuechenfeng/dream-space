package com.dreamspace.worker.generation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record RequirementBrief(GenerationIntent intent, List<ImageAssignment> imageAssignments, String imageType, String industry, String coreSubject, String displayGoal,
    String targetAudience, JsonNode contentFacts, JsonNode constraints,
    Map<String, Object> inferredVisualPreferences, Map<String, Object> inferredLoopStrategy,
    JsonNode unknowns, double confidence, boolean needsClarification) {
  public enum GenerationIntent { TEXT_TO_IMAGE, EDIT_IMAGE, RECOMPOSE_IMAGE }
  public enum InputImageRole { TARGET_A, REFERENCE_B, UNUSED }
  public record ImageAssignment(String imageId, InputImageRole role, double confidence, String reasonCode) {}
}
