package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record AdminUserRoleRecord(String adminUserId, String roleId, String assignedBy,
    Instant assignedAt) {}
