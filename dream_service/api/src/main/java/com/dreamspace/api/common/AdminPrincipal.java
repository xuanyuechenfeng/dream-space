package com.dreamspace.api.common;

import com.dreamspace.common.persistence.database.DatabaseEnums.AdminRole;

public record AdminPrincipal(String id, String displayName, AdminRole role) {
  public boolean allows(AdminRole minimum) {
    return rank(role) >= rank(minimum);
  }
  private static int rank(AdminRole role) {
    return switch (role) { case VIEWER -> 1; case OPERATOR -> 2; case ADMIN -> 3; };
  }
}
