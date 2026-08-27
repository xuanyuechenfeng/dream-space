package com.dreamspace.api.persistence.auth;

import java.time.Instant;
import org.apache.ibatis.annotations.AutomapConstructor;

public record UserRecord(String id, String phone, String email, String passwordHash,
    Instant createdAt, Instant updatedAt) {
  @AutomapConstructor
  public UserRecord {
  }

  public UserRecord(String id, String phone, String passwordHash, Instant createdAt, Instant updatedAt) {
    this(id, phone, null, passwordHash, createdAt, updatedAt);
  }
}
