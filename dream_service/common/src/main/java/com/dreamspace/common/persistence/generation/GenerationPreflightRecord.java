package com.dreamspace.common.persistence.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.CollectionMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationPreflightStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record GenerationPreflightRecord(String id, String userId, String sessionId, GenerationPreflightStatus status,
    String idempotencyKey, String draftKey, String inputHash, JsonNode inputJson, JsonNode planJson,
    String planSchemaVersion, CollectionMode collectionMode, Integer imageCount, String ratio, String resolution,
    Integer width, Integer height, String pricingRuleId, Integer pricingRuleVersion, Integer unitCost,
    Integer estimatedCost, String errorCode, String errorDetails, Instant readyAt, Instant expiresAt,
    Instant consumedAt, String taskId, Instant createdAt, Instant updatedAt) {}
