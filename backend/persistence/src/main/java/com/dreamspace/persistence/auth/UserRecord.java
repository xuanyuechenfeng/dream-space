package com.dreamspace.persistence.auth;

import java.time.Instant;

public record UserRecord(String id, String phone, Instant createdAt, Instant updatedAt) {}
