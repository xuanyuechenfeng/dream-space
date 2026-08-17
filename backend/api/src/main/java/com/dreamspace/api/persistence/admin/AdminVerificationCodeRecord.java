package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record AdminVerificationCodeRecord(String id, String phone, String codeHash, Instant expiresAt,
    Instant consumedAt, int attempts, Instant createdAt) {}
