package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record BillingLedgerRecord(String id, String userId, String taskId, String type, int amount,
    int balanceAfter, String sourceType, String sourceId, String ruleId, Integer ruleVersion,
    String reasonCode, Instant createdAt) {}
