package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;

public record OutputDimensions(int width, int height) {
  public static OutputDimensions resolve(GenerationRatio ratio, GenerationResolution resolution) {
    int edge = resolution == GenerationResolution.K4 ? 4096 : 2048;
    int[] parts = switch (ratio) {
      case SMART, RATIO_1_1 -> new int[] {1, 1};
      case RATIO_21_9 -> new int[] {21, 9};
      case RATIO_16_9 -> new int[] {16, 9};
      case RATIO_3_2 -> new int[] {3, 2};
      case RATIO_4_3 -> new int[] {4, 3};
      case RATIO_3_4 -> new int[] {3, 4};
      case RATIO_2_3 -> new int[] {2, 3};
      case RATIO_9_16 -> new int[] {9, 16};
    };
    if (parts[0] >= parts[1]) return new OutputDimensions(edge, Math.round((float) edge * parts[1] / parts[0]));
    return new OutputDimensions(Math.round((float) edge * parts[0] / parts[1]), edge);
  }
}
