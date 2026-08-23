package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import org.junit.jupiter.api.Test;

class OutputDimensionsTest {
  @Test
  void roundsProviderDimensionsToTheSupportedStep() {
    assertThat(OutputDimensions.resolve(GenerationRatio.RATIO_21_9, GenerationResolution.K2))
        .isEqualTo(new OutputDimensions(2048, 896));
    assertThat(OutputDimensions.resolve(GenerationRatio.RATIO_9_16, GenerationResolution.K4))
        .isEqualTo(new OutputDimensions(2304, 4096));
  }

  @Test
  void preservesExplicitDimensions() {
    assertThat(OutputDimensions.resolve(GenerationRatio.CUSTOM, GenerationResolution.K2, 1280, 768))
        .isEqualTo(new OutputDimensions(1280, 768));
  }
}
