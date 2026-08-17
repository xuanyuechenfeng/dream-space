package com.dreamspace.api.persistence.admin;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.ModerationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record AdminTaskRecord(String id, String sessionId, String sessionTitle, String userId,
    String userPhone, GenerationTaskStatus status, String prompt, String model, GenerationRatio ratio,
    GenerationResolution resolution, int imageCount, int resultCount, int totalCost, int attempts,
    JsonNode referenceImageUrls, String errorCode, String errorMessage,
    ModerationStatus inputModerationStatus, ModerationStatus outputModerationStatus,
    Instant createdAt, Instant startedAt, Instant completedAt) {}
