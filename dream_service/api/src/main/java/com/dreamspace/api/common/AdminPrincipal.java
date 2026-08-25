package com.dreamspace.api.common;

import com.dreamspace.common.persistence.database.DatabaseEnums.AdminRole;
import java.util.Set;

public record AdminPrincipal(String id, String displayName, AdminRole role, Set<String> permissions) {
  public AdminPrincipal {
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
  }

  public boolean allows(String permission) {
    return permission != null && permissions.contains(permission);
  }
}
