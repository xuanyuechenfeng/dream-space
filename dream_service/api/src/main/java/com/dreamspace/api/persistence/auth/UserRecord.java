package com.dreamspace.api.persistence.auth;

import java.time.Instant;

public record UserRecord(String id, String phone, String passwordHash, Instant createdAt, Instant updatedAt) {}
