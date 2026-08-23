CREATE TABLE "ModerationReviewCase" (
    "id" TEXT NOT NULL,
    "taskId" TEXT NOT NULL,
    "resultId" TEXT,
    "userId" TEXT NOT NULL,
    "stage" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "reasonCode" TEXT NOT NULL,
    "evidenceJson" JSONB NOT NULL DEFAULT '{}'::jsonb,
    "model" TEXT NOT NULL,
    "modelVersion" TEXT NOT NULL,
    "version" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resolvedAt" TIMESTAMP(3),
    CONSTRAINT "ModerationReviewCase_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "ModerationReviewCase_stage_check" CHECK ("stage" IN ('INPUT', 'OUTPUT')),
    CONSTRAINT "ModerationReviewCase_status_check" CHECK ("status" IN ('PENDING', 'APPROVED', 'REJECTED', 'APPEALED', 'RESOLVED')),
    CONSTRAINT "ModerationReviewCase_task_stage_key" UNIQUE ("taskId", "stage")
);

CREATE TABLE "ModerationAppeal" (
    "id" TEXT NOT NULL,
    "caseId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "reason" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resolvedAt" TIMESTAMP(3),
    CONSTRAINT "ModerationAppeal_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "ModerationAppeal_status_check" CHECK ("status" IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT "ModerationAppeal_case_user_key" UNIQUE ("caseId", "userId")
);

CREATE TABLE "ModerationAuditEvent" (
    "id" TEXT NOT NULL,
    "caseId" TEXT NOT NULL,
    "actorId" TEXT NOT NULL,
    "actorType" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "beforeJson" JSONB,
    "afterJson" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ModerationAuditEvent_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "ModerationAuditEvent_actor_type_check" CHECK ("actorType" IN ('SYSTEM', 'USER', 'ADMIN'))
);

CREATE INDEX "ModerationReviewCase_status_createdAt_idx" ON "ModerationReviewCase"("status", "createdAt");
CREATE INDEX "ModerationReviewCase_userId_createdAt_idx" ON "ModerationReviewCase"("userId", "createdAt");
CREATE INDEX "ModerationAppeal_status_createdAt_idx" ON "ModerationAppeal"("status", "createdAt");
CREATE INDEX "ModerationAuditEvent_caseId_createdAt_idx" ON "ModerationAuditEvent"("caseId", "createdAt");

ALTER TABLE "ModerationReviewCase" ADD CONSTRAINT "ModerationReviewCase_taskId_fkey" FOREIGN KEY ("taskId") REFERENCES "GenerationTask"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "ModerationReviewCase" ADD CONSTRAINT "ModerationReviewCase_resultId_fkey" FOREIGN KEY ("resultId") REFERENCES "GenerationResult"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "ModerationReviewCase" ADD CONSTRAINT "ModerationReviewCase_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "ModerationAppeal" ADD CONSTRAINT "ModerationAppeal_caseId_fkey" FOREIGN KEY ("caseId") REFERENCES "ModerationReviewCase"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "ModerationAppeal" ADD CONSTRAINT "ModerationAppeal_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "ModerationAuditEvent" ADD CONSTRAINT "ModerationAuditEvent_caseId_fkey" FOREIGN KEY ("caseId") REFERENCES "ModerationReviewCase"("id") ON DELETE CASCADE ON UPDATE CASCADE;
