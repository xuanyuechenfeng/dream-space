package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptAlignmentValidatorTest {
  private final PromptAlignmentValidator validator = new PromptAlignmentValidator();

  @Test
  void preservesFaithfulExpansion() {
    PromptPackage prompt = new PromptPackage("a red bicycle, studio lighting", "", Map.of(), "exact", "v2",
        PromptPackage.PromptRelation.EXPANDED, 0.92, "added lighting");

    assertThat(validator.validate(task(), prompt).positivePrompt()).isEqualTo(prompt.positivePrompt());
  }

  @Test
  void restoresOriginalPromptWhenModelDrifts() {
    PromptPackage prompt = new PromptPackage("a blue car in a city", "", Map.of(), "exact", "v2",
        PromptPackage.PromptRelation.DRIFTED, 0.2, "changed bicycle to car");

    PromptPackage validated = validator.validate(task(), prompt);

    assertThat(validated.positivePrompt()).isEqualTo("a red bicycle");
  }

  private static WorkerTaskSnapshot task() {
    return new WorkerTaskSnapshot("task-1", "user-1", "session-1", "a red bicycle", GenerationInputMode.AUTO,
        List.of(), "image-model", GenerationRatio.RATIO_1_1, GenerationResolution.K2, 1024, 1024, 1, 1, 0);
  }
}
