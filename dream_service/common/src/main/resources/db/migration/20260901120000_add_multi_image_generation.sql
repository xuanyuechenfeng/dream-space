-- Multi-image generation v2 is additive. Existing migrations remain immutable.
CREATE TYPE "GenerationPreflightStatus" AS ENUM ('QUEUED','PLANNING','READY','NEEDS_CLARIFICATION','FAILED','CONSUMED','EXPIRED','SUPERSEDED');
CREATE TYPE "GenerationCollectionMode" AS ENUM ('VARIATIONS','DIMENSIONAL','SEQUENCE');
CREATE TYPE "GenerationSlotStatus" AS ENUM ('WAITING','GENERATING','SUCCEEDED','FAILED','CANCELLED');
CREATE TYPE "GenerationExecutionKind" AS ENUM ('INITIAL','CONTINUATION');
CREATE TYPE "GenerationExecutionStatus" AS ENUM ('QUEUED','GENERATING','SUCCEEDED','FAILED','CANCELLED');

ALTER TABLE "GenerationTask"
  DROP CONSTRAINT IF EXISTS "GenerationTask_imageCount_single_check",
  ADD CONSTRAINT "GenerationTask_imageCount_multi_check" CHECK ("imageCount" BETWEEN 1 AND 4),
  ADD COLUMN IF NOT EXISTS "settlementVersion" SMALLINT NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS "preflightId" TEXT,
  ADD COLUMN IF NOT EXISTS "consumedCost" INTEGER NOT NULL DEFAULT 0,
  ADD CONSTRAINT "GenerationTask_consumedCost_check" CHECK ("consumedCost" >= 0 AND "consumedCost" <= "totalCost");

ALTER TABLE "GenerationPlan"
  ADD COLUMN IF NOT EXISTS "collectionJson" JSONB,
  ADD COLUMN IF NOT EXISTS "collectionMode" "GenerationCollectionMode";

CREATE TABLE "GenerationPreflight" (
  "id" TEXT NOT NULL,
  "userId" TEXT NOT NULL,
  "sessionId" TEXT,
  "status" "GenerationPreflightStatus" NOT NULL DEFAULT 'QUEUED',
  "idempotencyKey" TEXT NOT NULL,
  "draftKey" TEXT NOT NULL,
  "inputHash" VARCHAR(64) NOT NULL,
  "inputJson" JSONB NOT NULL,
  "planJson" JSONB,
  "planSchemaVersion" TEXT,
  "collectionMode" "GenerationCollectionMode",
  "imageCount" INTEGER,
  "ratio" TEXT NOT NULL,
  "resolution" TEXT NOT NULL,
  "width" INTEGER,
  "height" INTEGER,
  "pricingRuleId" TEXT,
  "pricingRuleVersion" INTEGER,
  "unitCost" INTEGER NOT NULL,
  "estimatedCost" INTEGER,
  "errorCode" TEXT,
  "errorDetails" TEXT,
  "readyAt" TIMESTAMP(3),
  "expiresAt" TIMESTAMP(3),
  "consumedAt" TIMESTAMP(3),
  "taskId" TEXT,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "GenerationPreflight_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "GenerationPreflight_user_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE,
  CONSTRAINT "GenerationPreflight_task_fkey" FOREIGN KEY ("taskId") REFERENCES "GenerationTask"("id") ON DELETE SET NULL,
  CONSTRAINT "GenerationPreflight_count_check" CHECK ("imageCount" IS NULL OR "imageCount" BETWEEN 1 AND 4)
);
CREATE UNIQUE INDEX "GenerationPreflight_user_idempotency_key" ON "GenerationPreflight" ("userId", "idempotencyKey");
CREATE INDEX "GenerationPreflight_draft_status_idx" ON "GenerationPreflight" ("userId", "draftKey", "status", "updatedAt");

CREATE TABLE "GenerationPreflightEvent" (
  "id" BIGSERIAL NOT NULL,
  "preflightId" TEXT NOT NULL,
  "type" TEXT NOT NULL,
  "status" "GenerationPreflightStatus" NOT NULL,
  "payload" JSONB NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "GenerationPreflightEvent_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "GenerationPreflightEvent_fkey" FOREIGN KEY ("preflightId") REFERENCES "GenerationPreflight"("id") ON DELETE CASCADE
);
CREATE INDEX "GenerationPreflightEvent_cursor_idx" ON "GenerationPreflightEvent" ("preflightId", "id");

CREATE TABLE "GenerationResultSlot" (
  "taskId" TEXT NOT NULL,
  "slotIndex" INTEGER NOT NULL,
  "label" TEXT NOT NULL,
  "role" TEXT NOT NULL,
  "intentSummary" TEXT NOT NULL DEFAULT '',
  "promptHash" VARCHAR(64) NOT NULL,
  "status" "GenerationSlotStatus" NOT NULL DEFAULT 'WAITING',
  "resultId" TEXT,
  "activeExecutionId" TEXT,
  "slotAttempt" INTEGER NOT NULL DEFAULT 0,
  "errorCode" TEXT,
  "errorMessage" TEXT,
  "startedAt" TIMESTAMP(3),
  "completedAt" TIMESTAMP(3),
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "GenerationResultSlot_pkey" PRIMARY KEY ("taskId", "slotIndex"),
  CONSTRAINT "GenerationResultSlot_task_fkey" FOREIGN KEY ("taskId") REFERENCES "GenerationTask"("id") ON DELETE CASCADE,
  CONSTRAINT "GenerationResultSlot_index_check" CHECK ("slotIndex" BETWEEN 0 AND 3)
);
CREATE UNIQUE INDEX "GenerationResultSlot_result_key" ON "GenerationResultSlot" ("resultId") WHERE "resultId" IS NOT NULL;
ALTER TABLE "GenerationResultSlot"
  ADD CONSTRAINT "GenerationResultSlot_result_fkey"
  FOREIGN KEY ("resultId") REFERENCES "GenerationResult"("id") ON DELETE SET NULL;

CREATE TABLE "GenerationExecution" (
  "id" TEXT NOT NULL,
  "taskId" TEXT NOT NULL,
  "kind" "GenerationExecutionKind" NOT NULL,
  "status" "GenerationExecutionStatus" NOT NULL DEFAULT 'QUEUED',
  "idempotencyKey" TEXT NOT NULL,
  "slotIndexes" JSONB NOT NULL,
  "reservedAmount" INTEGER NOT NULL,
  "consumedAmount" INTEGER NOT NULL DEFAULT 0,
  "releasedAmount" INTEGER NOT NULL DEFAULT 0,
  "pricingRuleId" TEXT,
  "pricingRuleVersion" INTEGER,
  "queueMessageId" TEXT,
  "attempts" INTEGER NOT NULL DEFAULT 0,
  "errorCode" TEXT,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "GenerationExecution_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "GenerationExecution_task_fkey" FOREIGN KEY ("taskId") REFERENCES "GenerationTask"("id") ON DELETE CASCADE,
  CONSTRAINT "GenerationExecution_amount_check" CHECK ("reservedAmount" >= 0 AND "consumedAmount" >= 0 AND "releasedAmount" >= 0 AND "reservedAmount" >= "consumedAmount" + "releasedAmount"),
  CONSTRAINT "GenerationExecution_idempotency_key" UNIQUE ("taskId", "idempotencyKey")
);
CREATE UNIQUE INDEX "GenerationExecution_active_task_key" ON "GenerationExecution" ("taskId") WHERE "status" IN ('QUEUED','GENERATING');

ALTER TABLE "GenerationIteration"
  ADD COLUMN IF NOT EXISTS "slotIndex" INTEGER,
  ADD COLUMN IF NOT EXISTS "slotAttempt" INTEGER,
  ADD COLUMN IF NOT EXISTS "executionId" TEXT;
ALTER TABLE "QuotaLedgerEntry"
  ADD COLUMN IF NOT EXISTS "executionId" TEXT,
  ADD COLUMN IF NOT EXISTS "slotIndex" INTEGER;
CREATE INDEX "QuotaLedgerEntry_execution_slot_idx" ON "QuotaLedgerEntry" ("executionId", "slotIndex");
