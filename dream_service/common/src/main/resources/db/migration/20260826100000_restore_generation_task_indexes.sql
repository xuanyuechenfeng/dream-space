-- Restore indexes required by GenerationTask query and idempotency contracts.
-- IF NOT EXISTS keeps this repair safe for databases where the original
-- migration objects are still intact.
CREATE INDEX IF NOT EXISTS "GenerationTask_sessionId_createdAt_idx"
    ON "GenerationTask"("sessionId", "createdAt");

CREATE INDEX IF NOT EXISTS "GenerationTask_userId_status_createdAt_idx"
    ON "GenerationTask"("userId", "status", "createdAt");

CREATE UNIQUE INDEX IF NOT EXISTS "GenerationTask_userId_idempotencyKey_key"
    ON "GenerationTask"("userId", "idempotencyKey");
