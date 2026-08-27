package com.dreamspace.api.persistence.auth;

import java.time.Instant;

public record RegistrationEmailCodeRecord(String id, String emailHash, String codeHash,
    String clientKeyHash, Instant expiresAt, Instant consumedAt, int attempts, Instant createdAt) {}
