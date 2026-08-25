-- Database-backed RBAC for management APIs. The legacy AdminUser.role remains
-- as a compatibility/display field; authorization reads the relation tables.
ALTER TABLE "AdminUser"
  ADD COLUMN IF NOT EXISTS "permissionRevision" BIGINT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS "AdminRoleDefinition" (
  "id" TEXT NOT NULL,
  "code" VARCHAR(80) NOT NULL,
  "name" VARCHAR(120) NOT NULL,
  "description" VARCHAR(500),
  "system" BOOLEAN NOT NULL DEFAULT false,
  "status" VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  "version" INTEGER NOT NULL DEFAULT 1,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "AdminRoleDefinition_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "AdminRoleDefinition_code_key" UNIQUE ("code"),
  CONSTRAINT "AdminRoleDefinition_status_check" CHECK ("status" IN ('ACTIVE', 'DISABLED')),
  CONSTRAINT "AdminRoleDefinition_version_check" CHECK ("version" > 0)
);

CREATE TABLE IF NOT EXISTS "AdminPermissionDefinition" (
  "id" TEXT NOT NULL,
  "code" VARCHAR(120) NOT NULL,
  "resource" VARCHAR(80) NOT NULL,
  "action" VARCHAR(80) NOT NULL,
  "description" VARCHAR(500),
  "riskLevel" VARCHAR(16) NOT NULL DEFAULT 'LOW',
  "status" VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "AdminPermissionDefinition_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "AdminPermissionDefinition_code_key" UNIQUE ("code"),
  CONSTRAINT "AdminPermissionDefinition_code_check" CHECK ("code" ~ '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$'),
  CONSTRAINT "AdminPermissionDefinition_risk_check" CHECK ("riskLevel" IN ('LOW', 'MEDIUM', 'HIGH')),
  CONSTRAINT "AdminPermissionDefinition_status_check" CHECK ("status" IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE IF NOT EXISTS "AdminUserRole" (
  "adminUserId" TEXT NOT NULL,
  "roleId" TEXT NOT NULL,
  "assignedBy" TEXT NOT NULL DEFAULT 'system',
  "assignedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "AdminUserRole_pkey" PRIMARY KEY ("adminUserId", "roleId"),
  CONSTRAINT "AdminUserRole_adminUserId_fkey" FOREIGN KEY ("adminUserId") REFERENCES "AdminUser"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "AdminUserRole_roleId_fkey" FOREIGN KEY ("roleId") REFERENCES "AdminRoleDefinition"("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS "AdminRolePermission" (
  "roleId" TEXT NOT NULL,
  "permissionId" TEXT NOT NULL,
  "grantedBy" TEXT NOT NULL DEFAULT 'system',
  "grantedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "AdminRolePermission_pkey" PRIMARY KEY ("roleId", "permissionId"),
  CONSTRAINT "AdminRolePermission_roleId_fkey" FOREIGN KEY ("roleId") REFERENCES "AdminRoleDefinition"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "AdminRolePermission_permissionId_fkey" FOREIGN KEY ("permissionId") REFERENCES "AdminPermissionDefinition"("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX IF NOT EXISTS "AdminUserRole_roleId_idx" ON "AdminUserRole"("roleId");
CREATE INDEX IF NOT EXISTS "AdminRolePermission_permissionId_idx" ON "AdminRolePermission"("permissionId");

INSERT INTO "AdminRoleDefinition" ("id", "code", "name", "description", "system") VALUES
  ('admin-role-viewer', 'VIEWER', '只读管理员', '只读查看现有管理资源', true),
  ('admin-role-operator', 'OPERATOR', '运营管理员', '内容、用户和订单运营', true),
  ('admin-role-admin', 'ADMIN', '平台管理员', '平台治理和全部现有管理能力', true)
ON CONFLICT ("code") DO NOTHING;

INSERT INTO "AdminPermissionDefinition" ("id", "code", "resource", "action", "description", "riskLevel") VALUES
  ('admin-permission-tasks-read', 'tasks:read', 'tasks', 'read', '查看任务和审核记录', 'LOW'),
  ('admin-permission-tasks-write', 'tasks:write', 'tasks', 'write', '处理审核任务', 'MEDIUM'),
  ('admin-permission-inspirations-read', 'inspirations:read', 'inspirations', 'read', '查看灵感内容', 'LOW'),
  ('admin-permission-inspirations-write', 'inspirations:write', 'inspirations', 'write', '维护和发布灵感内容', 'MEDIUM'),
  ('admin-permission-users-read', 'users:read', 'users', 'read', '查看用户和额度流水', 'LOW'),
  ('admin-permission-users-write', 'users:write', 'users', 'write', '启停用户、撤销会话和调整额度', 'HIGH'),
  ('admin-permission-billing-read', 'billing:read', 'billing', 'read', '查看订单和支付记录', 'LOW'),
  ('admin-permission-billing-write', 'billing:write', 'billing', 'write', '执行退款等订单写操作', 'HIGH'),
  ('admin-permission-pricing-read', 'pricing:read', 'pricing', 'read', '查看计费规则和商品', 'LOW'),
  ('admin-permission-pricing-write', 'pricing:write', 'pricing', 'write', '维护计费规则和商品', 'HIGH'),
  ('admin-permission-audit-read', 'audit:read', 'audit', 'read', '查询管理审计事件', 'MEDIUM')
ON CONFLICT ("code") DO NOTHING;

-- Preserve the behavior of the previous role-rank interceptor. In particular,
-- moderation writes were available to OPERATOR even though the old response
-- permission list did not expose tasks:write.
INSERT INTO "AdminRolePermission" ("roleId", "permissionId", "grantedBy")
SELECT role_definition."id", permission_definition."id", 'system'
FROM "AdminRoleDefinition" role_definition
JOIN "AdminPermissionDefinition" permission_definition ON
  (role_definition."code" = 'VIEWER' AND permission_definition."code" IN
    ('tasks:read', 'inspirations:read', 'users:read', 'billing:read', 'pricing:read'))
  OR
  (role_definition."code" = 'OPERATOR' AND permission_definition."code" IN
    ('tasks:read', 'tasks:write', 'inspirations:read', 'inspirations:write',
     'users:read', 'users:write', 'billing:read', 'billing:write', 'pricing:read'))
  OR
  (role_definition."code" = 'ADMIN')
ON CONFLICT ("roleId", "permissionId") DO NOTHING;

INSERT INTO "AdminUserRole" ("adminUserId", "roleId", "assignedBy")
SELECT admin_user."id", role_definition."id", 'system'
FROM "AdminUser" admin_user
JOIN "AdminRoleDefinition" role_definition ON role_definition."code" = admin_user."role"::TEXT
ON CONFLICT ("adminUserId", "roleId") DO NOTHING;

-- Authorization changes invalidate all affected sessions in the database, so
-- later role-management code cannot accidentally leave old permissions alive.
CREATE OR REPLACE FUNCTION revoke_admin_sessions_for_user(affected_admin_user_id TEXT) RETURNS VOID AS $$
BEGIN
  UPDATE "AdminUser"
    SET "permissionRevision" = "permissionRevision" + 1,
        "updatedAt" = CURRENT_TIMESTAMP
    WHERE "id" = affected_admin_user_id;
  DELETE FROM "AdminSession" WHERE "adminUserId" = affected_admin_user_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION revoke_admin_sessions_for_role(affected_role_id TEXT) RETURNS VOID AS $$
BEGIN
  UPDATE "AdminUser" admin_user
    SET "permissionRevision" = admin_user."permissionRevision" + 1,
        "updatedAt" = CURRENT_TIMESTAMP
    FROM "AdminUserRole" admin_user_role
    WHERE admin_user_role."roleId" = affected_role_id
      AND admin_user_role."adminUserId" = admin_user."id";
  DELETE FROM "AdminSession" admin_session
    USING "AdminUserRole" admin_user_role
    WHERE admin_user_role."roleId" = affected_role_id
      AND admin_session."adminUserId" = admin_user_role."adminUserId";
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION invalidate_admin_user_sessions() RETURNS trigger AS $$
BEGIN
  IF TG_OP <> 'INSERT' THEN
    PERFORM revoke_admin_sessions_for_user(OLD."adminUserId");
  END IF;
  IF TG_OP <> 'DELETE'
      AND (TG_OP <> 'UPDATE' OR NEW."adminUserId" IS DISTINCT FROM OLD."adminUserId") THEN
    PERFORM revoke_admin_sessions_for_user(NEW."adminUserId");
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS "AdminUserRole_invalidate_sessions" ON "AdminUserRole";
CREATE TRIGGER "AdminUserRole_invalidate_sessions"
AFTER INSERT OR UPDATE OR DELETE ON "AdminUserRole"
FOR EACH ROW EXECUTE FUNCTION invalidate_admin_user_sessions();

CREATE OR REPLACE FUNCTION invalidate_admin_role_sessions() RETURNS trigger AS $$
BEGIN
  IF TG_OP <> 'INSERT' THEN
    PERFORM revoke_admin_sessions_for_role(OLD."roleId");
  END IF;
  IF TG_OP <> 'DELETE'
      AND (TG_OP <> 'UPDATE' OR NEW."roleId" IS DISTINCT FROM OLD."roleId") THEN
    PERFORM revoke_admin_sessions_for_role(NEW."roleId");
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS "AdminRolePermission_invalidate_sessions" ON "AdminRolePermission";
CREATE TRIGGER "AdminRolePermission_invalidate_sessions"
AFTER INSERT OR UPDATE OR DELETE ON "AdminRolePermission"
FOR EACH ROW EXECUTE FUNCTION invalidate_admin_role_sessions();

CREATE OR REPLACE FUNCTION invalidate_admin_role_definition_sessions() RETURNS trigger AS $$
BEGIN
  IF NEW."status" IS DISTINCT FROM OLD."status" THEN
    PERFORM revoke_admin_sessions_for_role(NEW."id");
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS "AdminRoleDefinition_invalidate_sessions" ON "AdminRoleDefinition";
CREATE TRIGGER "AdminRoleDefinition_invalidate_sessions"
AFTER UPDATE OF "status" ON "AdminRoleDefinition"
FOR EACH ROW EXECUTE FUNCTION invalidate_admin_role_definition_sessions();

CREATE OR REPLACE FUNCTION invalidate_admin_permission_definition_sessions() RETURNS trigger AS $$
DECLARE affected_role RECORD;
BEGIN
  IF NEW."code" IS DISTINCT FROM OLD."code" OR NEW."status" IS DISTINCT FROM OLD."status" THEN
    FOR affected_role IN
      SELECT role_permission."roleId"
      FROM "AdminRolePermission" role_permission
      WHERE role_permission."permissionId" = NEW."id"
    LOOP
      PERFORM revoke_admin_sessions_for_role(affected_role."roleId");
    END LOOP;
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS "AdminPermissionDefinition_invalidate_sessions" ON "AdminPermissionDefinition";
CREATE TRIGGER "AdminPermissionDefinition_invalidate_sessions"
AFTER UPDATE OF "code", "status" ON "AdminPermissionDefinition"
FOR EACH ROW EXECUTE FUNCTION invalidate_admin_permission_definition_sessions();

CREATE OR REPLACE FUNCTION invalidate_admin_sessions_on_account_disable() RETURNS trigger AS $$
BEGIN
  IF NEW."active" IS DISTINCT FROM OLD."active" THEN
    PERFORM revoke_admin_sessions_for_user(NEW."id");
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS "AdminUser_active_invalidate_sessions" ON "AdminUser";
CREATE TRIGGER "AdminUser_active_invalidate_sessions"
AFTER UPDATE OF "active" ON "AdminUser"
FOR EACH ROW EXECUTE FUNCTION invalidate_admin_sessions_on_account_disable();
