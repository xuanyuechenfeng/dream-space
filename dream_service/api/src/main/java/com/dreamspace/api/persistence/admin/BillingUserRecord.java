package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record BillingUserRecord(String id, String phone, String status, String displayName,
    Instant createdAt, Instant lastLoginAt, Instant disabledAt, String disabledReason) {}
