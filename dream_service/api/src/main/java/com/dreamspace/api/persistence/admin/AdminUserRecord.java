package com.dreamspace.api.persistence.admin;

import com.dreamspace.common.persistence.database.DatabaseEnums.AdminRole;
import java.time.Instant;
import org.apache.ibatis.annotations.AutomapConstructor;

public record AdminUserRecord(String id, String phone, String displayName, AdminRole role, boolean active,
    Instant createdAt, Instant updatedAt, long permissionRevision, String status, int version,
    Instant lastLoginAt, String createdBy, Instant disabledAt, String disabledBy, String disabledReason) {
  @AutomapConstructor
  public AdminUserRecord {
  }

  public AdminUserRecord(String id, String phone, String displayName, AdminRole role, boolean active,
      Instant createdAt, Instant updatedAt, long permissionRevision) {
    this(id, phone, displayName, role, active, createdAt, updatedAt, permissionRevision,
        active ? "ACTIVE" : "DISABLED", 1, null, "system", null, null, null);
  }
}
