package com.dreamspace.api.persistence.auth;

import java.time.Instant;

public record UserSessionRecord(String id, String tokenHash, String userId, Instant expiresAt,
    Instant createdAt, Instant lastSeenAt) {}
