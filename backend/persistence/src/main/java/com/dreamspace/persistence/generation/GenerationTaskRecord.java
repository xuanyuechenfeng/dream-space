package com.dreamspace.persistence.generation;

import com.dreamspace.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.persistence.database.DatabaseEnums.ModerationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record GenerationTaskRecord(String id, String sessionId, String userId, GenerationTaskStatus status,
    String prompt, String model, GenerationRatio ratio, GenerationResolution resolution, int imageCount,
    JsonNode referenceImageUrls, int unitCost, int totalCost, String idempotencyKey, String queueJobId,
    int attempts, String lastAttemptKey, String errorCode, String errorMessage,
    ModerationStatus inputModerationStatus, ModerationStatus outputModerationStatus,
    Instant startedAt, Instant completedAt, Instant createdAt, Instant updatedAt) {}
