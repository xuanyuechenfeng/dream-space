package com.dreamspace.common.persistence.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationPlanStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.CollectionMode;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.apache.ibatis.annotations.AutomapConstructor;

public record GenerationPlanRecord(String id, String taskId, String schemaVersion, GenerationPlanStatus status,
    String inputHash, JsonNode requirementJson, JsonNode structureJson, JsonNode visualJson, JsonNode promptJson,
    Instant createdAt, Instant updatedAt, JsonNode collectionJson, CollectionMode collectionMode) {
  @AutomapConstructor
  public GenerationPlanRecord {}

  public GenerationPlanRecord(String id, String taskId, String schemaVersion, GenerationPlanStatus status,
      String inputHash, JsonNode requirementJson, JsonNode structureJson, JsonNode visualJson, JsonNode promptJson,
      Instant createdAt, Instant updatedAt) {
    this(id, taskId, schemaVersion, status, inputHash, requirementJson, structureJson, visualJson, promptJson,
        createdAt, updatedAt, null, null);
  }
}
