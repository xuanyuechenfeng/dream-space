package com.dreamspace.worker.generation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Keeps model prompt expansion within the user's declared intent. */
public final class PromptAlignmentValidator {
  private static final Logger log = LoggerFactory.getLogger(PromptAlignmentValidator.class);
  private static final double MIN_ALIGNMENT_SCORE = 0.55;

  public PromptPackage validate(WorkerTaskSnapshot task, PromptPackage prompt) {
    if (prompt == null) throw new GenerationProviderException("PLANNING_OUTPUT_INVALID",
        "prompt artifact is empty", false);
    if (prompt.positivePrompt() == null || prompt.positivePrompt().isBlank()) {
      throw new GenerationProviderException("PLANNING_OUTPUT_INVALID",
          "planning model returned an empty positive prompt", false);
    }
    boolean drifted = prompt.promptRelation() == PromptPackage.PromptRelation.DRIFTED
        || prompt.alignmentScore() < MIN_ALIGNMENT_SCORE;
    if (!drifted) return prompt;
    log.atWarn().addKeyValue("taskId", task.id())
        .addKeyValue("relation", prompt.promptRelation())
        .addKeyValue("alignmentScore", prompt.alignmentScore())
        .addKeyValue("expansionReason", prompt.expansionReason())
        .log("planning prompt drifted from user input; original prompt restored");
    return prompt.withPositivePrompt(task.prompt());
  }
}
