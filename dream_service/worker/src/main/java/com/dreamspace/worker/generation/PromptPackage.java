package com.dreamspace.worker.generation;

import java.util.Map;

public record PromptPackage(String positivePrompt, String negativePrompt, Map<String, Object> modelInput,
    String textPolicy, String promptVersion, PromptRelation promptRelation, double alignmentScore,
    String expansionReason) {
  public PromptPackage(String positivePrompt, String negativePrompt, Map<String, Object> modelInput,
      String textPolicy, String promptVersion) {
    this(positivePrompt, negativePrompt, modelInput, textPolicy, promptVersion,
        PromptRelation.ALIGNED, 1.0, null);
  }

  public PromptPackage {
    if (!Double.isFinite(alignmentScore)) alignmentScore = 0.0;
  }

  public PromptPackage withPositivePrompt(String value) {
    return new PromptPackage(value, negativePrompt, modelInput, textPolicy, promptVersion,
        promptRelation, alignmentScore, expansionReason);
  }

  public enum PromptRelation { ALIGNED, EXPANDED, DRIFTED }
}
