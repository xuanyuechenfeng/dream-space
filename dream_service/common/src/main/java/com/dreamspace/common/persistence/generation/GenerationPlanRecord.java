package com.dreamspace.common.persistence.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationPlanStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record GenerationPlanRecord(String id, String taskId, String schemaVersion, GenerationPlanStatus status,
    String inputHash, JsonNode requirementJson, JsonNode structureJson, JsonNode visualJson, JsonNode promptJson,
    Instant createdAt, Instant updatedAt) {}
