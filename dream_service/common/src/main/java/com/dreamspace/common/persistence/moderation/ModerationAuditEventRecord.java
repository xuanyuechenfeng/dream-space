package com.dreamspace.common.persistence.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ModerationAuditEventRecord(String id, String caseId, String actorId, String actorType,
    String action, JsonNode beforeJson, JsonNode afterJson, Instant createdAt) {}
