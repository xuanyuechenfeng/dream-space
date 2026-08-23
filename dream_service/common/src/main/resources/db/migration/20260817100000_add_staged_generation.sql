CREATE TYPE "GenerationInputMode" AS ENUM ('TEXT_TO_IMAGE', 'EDIT_IMAGE', 'RECOMPOSE_IMAGE');
CREATE TYPE "GenerationPlanStatus" AS ENUM ('PLANNING', 'RUNNABLE', 'NEEDS_CLARIFICATION', 'FAILED');
CREATE TYPE "GenerationIterationStatus" AS ENUM ('GENERATED', 'EVALUATING', 'ACCEPTED', 'REFINING', 'FAILED');

ALTER TABLE "GenerationTask"
  ADD COLUMN "mode" "GenerationInputMode" NOT NULL DEFAULT 'TEXT_TO_IMAGE',
  ADD COLUMN "targetImageId" TEXT,
  ADD COLUMN "referenceImageId" TEXT;

CREATE TABLE "GenerationPlan" (
  "id" TEXT NOT NULL,
  "taskId" TEXT NOT NULL,
  "schemaVersion" TEXT NOT NULL,
  "status" "GenerationPlanStatus" NOT NULL,
  "inputHash" VARCHAR(64) NOT NULL,
  "requirementJson" JSONB,
  "structureJson" JSONB,
  "visualJson" JSONB,
  "promptJson" JSONB,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "GenerationPlan_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "GenerationPlan_taskId_key" UNIQUE ("taskId"),
  CONSTRAINT "GenerationPlan_taskId_fkey" FOREIGN KEY ("taskId") REFERENCES "GenerationTask"("id") ON DELETE CASCADE
);
CREATE INDEX "GenerationPlan_status_updatedAt_idx" ON "GenerationPlan"("status", "updatedAt");

CREATE TABLE "GenerationIteration" (
  "id" TEXT NOT NULL,
  "taskId" TEXT NOT NULL,
  "iteration" INTEGER NOT NULL,
  "promptHash" VARCHAR(64) NOT NULL,
  "status" "GenerationIterationStatus" NOT NULL,
  "provider" TEXT,
  "model" TEXT,
  "providerRequestId" TEXT,
  "evaluationJson" JSONB,
  "refinementJson" JSONB,
  "errorCode" TEXT,
  "startedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "completedAt" TIMESTAMP(3),
  CONSTRAINT "GenerationIteration_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "GenerationIteration_task_iteration_key" UNIQUE ("taskId", "iteration"),
  CONSTRAINT "GenerationIteration_taskId_fkey" FOREIGN KEY ("taskId") REFERENCES "GenerationTask"("id") ON DELETE CASCADE
);
CREATE INDEX "GenerationIteration_status_startedAt_idx" ON "GenerationIteration"("status", "startedAt");
CREATE INDEX "GenerationIteration_providerRequestId_idx" ON "GenerationIteration"("providerRequestId");
