package com.dreamspace.api.persistence.admin;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record BillingAuditRecord(String id, String actorId, String actorType, String action,
    String subjectType, String subjectId, JsonNode beforeJson, JsonNode afterJson, String reason,
    Instant createdAt) {}
