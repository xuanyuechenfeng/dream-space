package com.dreamspace.common.persistence.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.ModerationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.apache.ibatis.annotations.AutomapConstructor;

public record GenerationTaskRecord(String id, String sessionId, String userId, GenerationTaskStatus status,
    String prompt, GenerationInputMode mode, JsonNode imageIds,
    String model, GenerationRatio ratio, GenerationResolution resolution, Integer width, Integer height, int imageCount,
    int unitCost, int totalCost, String idempotencyKey, String queueJobId,
    int attempts, String lastAttemptKey, String errorCode, String errorMessage,
    ModerationStatus inputModerationStatus, ModerationStatus outputModerationStatus,
    Instant startedAt, Instant completedAt, Instant createdAt, Instant updatedAt,
    String pricingRuleId, Integer pricingRuleVersion, Short settlementVersion, String preflightId, int consumedCost) {
  /**
   * Select the complete v2 constructor when MyBatis maps rows containing the
   * settlement and preflight columns. The shorter constructor below is kept
   * only for callers that create legacy v1 snapshots.
   */
  @AutomapConstructor
  public GenerationTaskRecord {
  }

  public GenerationTaskRecord(String id, String sessionId, String userId, GenerationTaskStatus status,
      String prompt, GenerationInputMode mode, JsonNode imageIds, String model, GenerationRatio ratio,
      GenerationResolution resolution, Integer width, Integer height, int imageCount, int unitCost, int totalCost,
      String idempotencyKey, String queueJobId, int attempts, String lastAttemptKey, String errorCode,
      String errorMessage, ModerationStatus inputModerationStatus, ModerationStatus outputModerationStatus,
      Instant startedAt, Instant completedAt, Instant createdAt, Instant updatedAt) {
    this(id, sessionId, userId, status, prompt, mode, imageIds, model, ratio, resolution, width, height,
        imageCount, unitCost, totalCost, idempotencyKey, queueJobId, attempts, lastAttemptKey, errorCode,
        errorMessage, inputModerationStatus, outputModerationStatus, startedAt, completedAt, createdAt, updatedAt,
        null, null, (short) 1, null, 0);
  }
  public short settlementVersionValue() { return settlementVersion == null ? 1 : settlementVersion; }
}
