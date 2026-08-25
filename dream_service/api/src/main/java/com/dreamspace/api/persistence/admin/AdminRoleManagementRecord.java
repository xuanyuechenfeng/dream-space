package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record AdminRoleManagementRecord(String id, String code, String name, String description,
    boolean system, String status, int version, long accountCount, long permissionCount,
    Instant createdAt, Instant updatedAt) {}
