package com.dreamspace.persistence.generation;

import com.dreamspace.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record GenerationTaskEventRecord(long id, String taskId, String type, GenerationTaskStatus status,
    JsonNode payload, Instant createdAt) {}
