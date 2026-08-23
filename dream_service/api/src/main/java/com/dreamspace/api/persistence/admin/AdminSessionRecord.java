package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record AdminSessionRecord(String id, String tokenHash, String adminUserId, Instant expiresAt,
    Instant createdAt, Instant lastSeenAt) {}
