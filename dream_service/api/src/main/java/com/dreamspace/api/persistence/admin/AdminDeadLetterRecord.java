package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record AdminDeadLetterRecord(String errorCode, String errorMessage, int attempts,
    Instant createdAt, Instant resolvedAt) {}
