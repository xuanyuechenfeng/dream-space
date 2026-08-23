package com.dreamspace.api.persistence.auth;

import java.time.Instant;

public record LoginCaptchaRecord(String id, String clientKeyHash, String codeHash,
    Instant expiresAt, Instant consumedAt, int attempts, Instant createdAt) {}
