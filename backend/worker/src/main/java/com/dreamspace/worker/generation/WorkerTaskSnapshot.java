package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;

public record WorkerTaskSnapshot(String id, String userId, String sessionId, String prompt, String model,
    GenerationRatio ratio, GenerationResolution resolution, int imageCount, int totalCost, int attempts) {}
