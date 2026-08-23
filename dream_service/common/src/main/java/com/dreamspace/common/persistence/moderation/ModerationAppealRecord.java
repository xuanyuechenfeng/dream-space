package com.dreamspace.common.persistence.moderation;

import java.time.Instant;

public record ModerationAppealRecord(String id, String caseId, String userId, String reason,
    String status, Instant createdAt, Instant resolvedAt) {}
