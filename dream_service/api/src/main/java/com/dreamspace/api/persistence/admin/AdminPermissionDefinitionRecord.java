package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record AdminPermissionDefinitionRecord(String id, String code, String resource, String action,
    String description, String riskLevel, String status, Instant createdAt, Instant updatedAt) {}
