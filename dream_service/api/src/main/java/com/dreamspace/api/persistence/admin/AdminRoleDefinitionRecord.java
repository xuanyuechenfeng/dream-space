package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record AdminRoleDefinitionRecord(String id, String code, String name, String description,
    boolean system, String status, int version, Instant createdAt, Instant updatedAt) {}
