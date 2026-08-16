package com.dreamspace.persistence.auth;

import java.time.Instant;

public record VerificationCodeRecord(String id, String phone, String codeHash, Instant expiresAt,
    Instant consumedAt, int attempts, Instant createdAt) {}
