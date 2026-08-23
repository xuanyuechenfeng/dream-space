package com.dreamspace.common.persistence.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.ModerationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record GenerationTaskRecord(String id, String sessionId, String userId, GenerationTaskStatus status,
    String prompt, GenerationInputMode mode, JsonNode imageIds,
    String model, GenerationRatio ratio, GenerationResolution resolution, Integer width, Integer height, int imageCount,
    int unitCost, int totalCost, String idempotencyKey, String queueJobId,
    int attempts, String lastAttemptKey, String errorCode, String errorMessage,
    ModerationStatus inputModerationStatus, ModerationStatus outputModerationStatus,
    Instant startedAt, Instant completedAt, Instant createdAt, Instant updatedAt) {}
