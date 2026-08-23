package com.dreamspace.common.persistence.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ModerationReviewCaseRecord(String id, String taskId, String resultId, String userId,
    String stage, String status, String reasonCode, JsonNode evidenceJson, String model,
    String modelVersion, int version, Instant createdAt, Instant resolvedAt) {}
