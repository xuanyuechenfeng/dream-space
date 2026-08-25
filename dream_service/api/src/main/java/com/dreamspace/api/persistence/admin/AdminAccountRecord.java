package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record AdminAccountRecord(String id, String phone, String displayName, String role, boolean active,
    String status, int version, Instant createdAt, Instant updatedAt, Instant lastLoginAt,
    String createdBy, Instant disabledAt, String disabledBy, String disabledReason) {}
