package com.dreamspace.common.persistence.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionKind;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record GenerationExecutionRecord(String id, String taskId, GenerationExecutionKind kind,
    GenerationExecutionStatus status, String idempotencyKey, JsonNode slotIndexes, int reservedAmount,
    int consumedAmount, int releasedAmount, String pricingRuleId, Integer pricingRuleVersion,
    String queueMessageId, int attempts, String errorCode, Instant createdAt, Instant updatedAt) {}
