-- Bring the physical model in line with the administrator-management contract.
-- This is additive and idempotent; previously applied migrations remain immutable.
UPDATE "AdminUser"
SET "status" = 'DISABLED'
WHERE "active" = FALSE AND "status" = 'ACTIVE';

UPDATE "AdminUser"
SET "active" = FALSE
WHERE "status" IN ('INVITED', 'DISABLED') AND "active" = TRUE;

UPDATE "AdminUser"
SET "active" = TRUE
WHERE "status" = 'ACTIVE' AND "active" = FALSE;

UPDATE "AdminUser"
SET "createdBy" = 'system'
WHERE "createdBy" IS NULL;

ALTER TABLE "AdminUser"
  ALTER COLUMN "createdBy" SET DEFAULT 'system',
  ALTER COLUMN "createdBy" SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'AdminUser_status_check'
  ) THEN
    ALTER TABLE "AdminUser" ADD CONSTRAINT "AdminUser_status_check"
      CHECK ("status" IN ('INVITED', 'ACTIVE', 'DISABLED'));
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'AdminUser_status_active_check'
  ) THEN
    ALTER TABLE "AdminUser" ADD CONSTRAINT "AdminUser_status_active_check"
      CHECK (("status" = 'ACTIVE' AND "active" = TRUE)
          OR ("status" IN ('INVITED', 'DISABLED') AND "active" = FALSE));
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'AdminUser_version_check'
  ) THEN
    ALTER TABLE "AdminUser" ADD CONSTRAINT "AdminUser_version_check" CHECK ("version" > 0);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS "AdminUser_status_role_createdAt_idx"
  ON "AdminUser"("status", "role", "createdAt");

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'AdminOperationIdempotency_scope_key_check'
  ) THEN
    ALTER TABLE "AdminOperationIdempotency" ADD CONSTRAINT "AdminOperationIdempotency_scope_key_check"
      CHECK (length(trim("scope")) > 0 AND length(trim("idempotencyKey")) > 0);
  END IF;
END $$;
