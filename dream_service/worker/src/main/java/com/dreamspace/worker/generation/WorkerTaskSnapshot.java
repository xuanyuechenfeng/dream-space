package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;

public record WorkerTaskSnapshot(String id, String userId, String sessionId, String prompt, GenerationInputMode mode,
    java.util.List<String> imageIds, String model, GenerationRatio ratio,
    GenerationResolution resolution, Integer width, Integer height, int imageCount, int totalCost, int attempts) {
  public WorkerTaskSnapshot withOutput(GenerationRatio nextRatio, Integer nextWidth, Integer nextHeight) {
    return new WorkerTaskSnapshot(id, userId, sessionId, prompt, mode, imageIds, model, nextRatio,
        resolution, nextWidth, nextHeight, imageCount, totalCost, attempts);
  }
}
