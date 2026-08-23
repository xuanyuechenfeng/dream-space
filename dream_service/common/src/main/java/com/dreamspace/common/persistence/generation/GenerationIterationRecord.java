package com.dreamspace.common.persistence.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationIterationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record GenerationIterationRecord(String id, String taskId, int iteration, String promptHash,
    GenerationIterationStatus status, String provider, String model, String providerRequestId,
    JsonNode evaluationJson, JsonNode refinementJson, String errorCode, Instant startedAt, Instant completedAt) {}
