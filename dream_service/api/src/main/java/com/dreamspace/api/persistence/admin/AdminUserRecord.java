package com.dreamspace.api.persistence.admin;

import com.dreamspace.common.persistence.database.DatabaseEnums.AdminRole;
import java.time.Instant;

public record AdminUserRecord(String id, String phone, String displayName, AdminRole role, boolean active,
    Instant createdAt, Instant updatedAt, long permissionRevision) {}
