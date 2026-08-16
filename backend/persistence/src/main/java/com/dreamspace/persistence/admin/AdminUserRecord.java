package com.dreamspace.persistence.admin;

import com.dreamspace.persistence.database.DatabaseEnums.AdminRole;
import java.time.Instant;

public record AdminUserRecord(String id, String phone, String displayName, AdminRole role, boolean active,
    Instant createdAt, Instant updatedAt) {}
