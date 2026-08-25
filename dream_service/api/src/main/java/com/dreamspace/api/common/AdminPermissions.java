package com.dreamspace.api.common;

import java.util.Set;

public final class AdminPermissions {
  public static final String TASKS_READ = "tasks:read";
  public static final String TASKS_WRITE = "tasks:write";
  public static final String INSPIRATIONS_READ = "inspirations:read";
  public static final String INSPIRATIONS_WRITE = "inspirations:write";
  public static final String USERS_READ = "users:read";
  public static final String USERS_WRITE = "users:write";
  public static final String BILLING_READ = "billing:read";
  public static final String BILLING_WRITE = "billing:write";
  public static final String PRICING_READ = "pricing:read";
  public static final String PRICING_WRITE = "pricing:write";
  public static final String AUDIT_READ = "audit:read";
  public static final String ADMINS_READ = "admins:read";
  public static final String ADMINS_WRITE = "admins:write";
  public static final String ROLES_READ = "roles:read";
  public static final String ROLES_WRITE = "roles:write";

  public static final Set<String> ALL = Set.of(
      TASKS_READ, TASKS_WRITE, INSPIRATIONS_READ, INSPIRATIONS_WRITE,
      USERS_READ, USERS_WRITE, BILLING_READ, BILLING_WRITE,
      PRICING_READ, PRICING_WRITE, AUDIT_READ, ADMINS_READ, ADMINS_WRITE,
      ROLES_READ, ROLES_WRITE);

  private AdminPermissions() {}
}
