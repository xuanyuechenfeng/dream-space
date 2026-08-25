-- Administrator lifecycle and role-management fields. Keep legacy role/active
-- columns for login and compatibility while relation tables are canonical.
ALTER TABLE "AdminUser"
  ADD COLUMN IF NOT EXISTS "status" VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN IF NOT EXISTS "version" INTEGER NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS "lastLoginAt" TIMESTAMP(3),
  ADD COLUMN IF NOT EXISTS "createdBy" TEXT,
  ADD COLUMN IF NOT EXISTS "disabledAt" TIMESTAMP(3),
  ADD COLUMN IF NOT EXISTS "disabledBy" TEXT,
  ADD COLUMN IF NOT EXISTS "disabledReason" VARCHAR(500);

UPDATE "AdminUser" SET "status" = CASE WHEN "active" THEN 'ACTIVE' ELSE 'DISABLED' END
WHERE "status" IS NULL OR "status" NOT IN ('ACTIVE', 'DISABLED');
CREATE INDEX IF NOT EXISTS "AdminUser_status_createdAt_idx" ON "AdminUser"("status", "createdAt");
CREATE INDEX IF NOT EXISTS "AdminUser_lastLoginAt_idx" ON "AdminUser"("lastLoginAt");

CREATE TABLE IF NOT EXISTS "AdminOperationIdempotency" (
  "scope" VARCHAR(80) NOT NULL,
  "idempotencyKey" VARCHAR(160) NOT NULL,
  "subjectId" TEXT NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "AdminOperationIdempotency_pkey" PRIMARY KEY ("scope", "idempotencyKey")
);
CREATE INDEX IF NOT EXISTS "AdminOperationIdempotency_createdAt_idx"
  ON "AdminOperationIdempotency"("createdAt");

INSERT INTO "AdminPermissionDefinition" ("id", "code", "resource", "action", "description", "riskLevel") VALUES
  ('admin-permission-admins-read', 'admins:read', 'admins', 'read', '查看管理员账号', 'MEDIUM'),
  ('admin-permission-admins-write', 'admins:write', 'admins', 'write', '创建、停用和维护管理员账号', 'HIGH'),
  ('admin-permission-roles-read', 'roles:read', 'roles', 'read', '查看角色和权限目录', 'LOW'),
  ('admin-permission-roles-write', 'roles:write', 'roles', 'write', '维护角色和权限矩阵', 'HIGH')
ON CONFLICT ("code") DO NOTHING;
INSERT INTO "AdminRolePermission" ("roleId", "permissionId", "grantedBy")
SELECT role_definition."id", permission_definition."id", 'system'
FROM "AdminRoleDefinition" role_definition
JOIN "AdminPermissionDefinition" permission_definition
  ON permission_definition."code" IN ('admins:read', 'admins:write', 'roles:read', 'roles:write')
WHERE role_definition."code" = 'ADMIN'
ON CONFLICT ("roleId", "permissionId") DO NOTHING;
