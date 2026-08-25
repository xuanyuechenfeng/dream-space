-- Billing, pricing, payment, and user-management foundation.
ALTER TABLE "User"
  ADD COLUMN IF NOT EXISTS "status" VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN IF NOT EXISTS "displayName" VARCHAR(120),
  ADD COLUMN IF NOT EXISTS "disabledAt" TIMESTAMP(3),
  ADD COLUMN IF NOT EXISTS "disabledBy" TEXT,
  ADD COLUMN IF NOT EXISTS "disabledReason" VARCHAR(500),
  ADD COLUMN IF NOT EXISTS "lastLoginAt" TIMESTAMP(3),
  ADD COLUMN IF NOT EXISTS "deletedAt" TIMESTAMP(3);

ALTER TABLE "User"
  ADD CONSTRAINT "User_status_check" CHECK ("status" IN ('ACTIVE', 'DISABLED', 'DELETED'));

CREATE INDEX IF NOT EXISTS "User_status_createdAt_idx" ON "User"("status", "createdAt");
CREATE INDEX IF NOT EXISTS "User_lastLoginAt_idx" ON "User"("lastLoginAt");

ALTER TABLE "QuotaLedgerEntry"
  ADD COLUMN IF NOT EXISTS "sourceType" VARCHAR(32),
  ADD COLUMN IF NOT EXISTS "sourceId" TEXT,
  ADD COLUMN IF NOT EXISTS "ruleId" TEXT,
  ADD COLUMN IF NOT EXISTS "ruleVersion" INTEGER,
  ADD COLUMN IF NOT EXISTS "reasonCode" VARCHAR(120),
  ADD COLUMN IF NOT EXISTS "metadata" JSONB,
  ADD COLUMN IF NOT EXISTS "expiresAt" TIMESTAMP(3);

ALTER TABLE "GenerationTask"
  ADD COLUMN IF NOT EXISTS "pricingRuleId" TEXT,
  ADD COLUMN IF NOT EXISTS "pricingRuleVersion" INTEGER;

CREATE TABLE "PricingRule" (
  "id" TEXT NOT NULL,
  "code" VARCHAR(80) NOT NULL,
  "version" INTEGER NOT NULL,
  "operation" VARCHAR(80) NOT NULL,
  "modelPattern" VARCHAR(120) NOT NULL DEFAULT '*',
  "resolution" VARCHAR(16) NOT NULL DEFAULT 'ANY',
  "minWidth" INTEGER,
  "maxWidth" INTEGER,
  "minHeight" INTEGER,
  "maxHeight" INTEGER,
  "unitCreditCost" INTEGER NOT NULL,
  "formula" VARCHAR(120) NOT NULL DEFAULT 'unitCost*imageCount',
  "effectiveFrom" TIMESTAMP(3) NOT NULL,
  "effectiveTo" TIMESTAMP(3),
  "status" VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  "createdBy" TEXT NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PricingRule_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "PricingRule_version_check" CHECK ("version" > 0),
  CONSTRAINT "PricingRule_cost_check" CHECK ("unitCreditCost" > 0),
  CONSTRAINT "PricingRule_status_check" CHECK ("status" IN ('DRAFT', 'ACTIVE', 'RETIRED')),
  CONSTRAINT "PricingRule_window_check" CHECK ("effectiveTo" IS NULL OR "effectiveTo" > "effectiveFrom"),
  CONSTRAINT "PricingRule_code_version_key" UNIQUE ("code", "version")
);
CREATE INDEX "PricingRule_lookup_idx" ON "PricingRule"("operation", "status", "effectiveFrom");

CREATE TABLE "CreditProduct" (
  "id" TEXT NOT NULL,
  "code" VARCHAR(80) NOT NULL,
  "name" VARCHAR(160) NOT NULL,
  "creditAmount" INTEGER NOT NULL,
  "amountMinor" BIGINT NOT NULL,
  "currency" VARCHAR(3) NOT NULL DEFAULT 'CNY',
  "validityDays" INTEGER,
  "status" VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  "sortOrder" INTEGER NOT NULL DEFAULT 0,
  "metadata" JSONB,
  "createdBy" TEXT NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "CreditProduct_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "CreditProduct_code_key" UNIQUE ("code"),
  CONSTRAINT "CreditProduct_amount_check" CHECK ("creditAmount" > 0 AND "amountMinor" > 0),
  CONSTRAINT "CreditProduct_status_check" CHECK ("status" IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);
CREATE INDEX "CreditProduct_status_sortOrder_idx" ON "CreditProduct"("status", "sortOrder");

CREATE TABLE "BillingOrder" (
  "id" TEXT NOT NULL,
  "orderNo" VARCHAR(40) NOT NULL,
  "userId" TEXT NOT NULL,
  "productId" TEXT NOT NULL,
  "productCode" VARCHAR(80) NOT NULL,
  "productName" VARCHAR(160) NOT NULL,
  "quantity" INTEGER NOT NULL DEFAULT 1,
  "creditAmount" INTEGER NOT NULL,
  "amountMinor" BIGINT NOT NULL,
  "currency" VARCHAR(3) NOT NULL,
  "status" VARCHAR(24) NOT NULL DEFAULT 'CREATED',
  "provider" VARCHAR(40) NOT NULL,
  "idempotencyKey" VARCHAR(128) NOT NULL,
  "expiresAt" TIMESTAMP(3) NOT NULL,
  "paidAt" TIMESTAMP(3),
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "BillingOrder_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "BillingOrder_orderNo_key" UNIQUE ("orderNo"),
  CONSTRAINT "BillingOrder_user_idempotency_key" UNIQUE ("userId", "idempotencyKey"),
  CONSTRAINT "BillingOrder_amount_check" CHECK ("quantity" > 0 AND "creditAmount" > 0 AND "amountMinor" > 0),
  CONSTRAINT "BillingOrder_status_check" CHECK ("status" IN ('CREATED', 'PAYING', 'PAID', 'CANCELLED', 'EXPIRED', 'REFUNDING', 'REFUNDED', 'PARTIALLY_REFUNDED'))
);
CREATE INDEX "BillingOrder_user_createdAt_idx" ON "BillingOrder"("userId", "createdAt");
CREATE INDEX "BillingOrder_status_createdAt_idx" ON "BillingOrder"("status", "createdAt");

CREATE TABLE "PaymentTransaction" (
  "id" TEXT NOT NULL,
  "orderId" TEXT NOT NULL,
  "provider" VARCHAR(40) NOT NULL,
  "providerTransactionId" VARCHAR(160),
  "providerEventId" VARCHAR(160),
  "status" VARCHAR(16) NOT NULL DEFAULT 'INITIATED',
  "amountMinor" BIGINT NOT NULL,
  "currency" VARCHAR(3) NOT NULL,
  "signatureVerified" BOOLEAN NOT NULL DEFAULT false,
  "rawPayloadRef" TEXT,
  "paidAt" TIMESTAMP(3),
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PaymentTransaction_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "PaymentTransaction_status_check" CHECK ("status" IN ('INITIATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'CLOSED')),
  CONSTRAINT "PaymentTransaction_provider_tx_key" UNIQUE ("provider", "providerTransactionId"),
  CONSTRAINT "PaymentTransaction_provider_event_key" UNIQUE ("provider", "providerEventId")
);
CREATE INDEX "PaymentTransaction_order_createdAt_idx" ON "PaymentTransaction"("orderId", "createdAt");

CREATE TABLE "Refund" (
  "id" TEXT NOT NULL,
  "orderId" TEXT NOT NULL,
  "paymentTransactionId" TEXT,
  "amountMinor" BIGINT NOT NULL,
  "reason" VARCHAR(500) NOT NULL,
  "status" VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
  "providerRefundId" VARCHAR(160),
  "idempotencyKey" VARCHAR(128) NOT NULL,
  "createdBy" TEXT NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "completedAt" TIMESTAMP(3),
  CONSTRAINT "Refund_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "Refund_idempotency_key" UNIQUE ("idempotencyKey"),
  CONSTRAINT "Refund_status_check" CHECK ("status" IN ('REQUESTED', 'SUCCEEDED', 'FAILED')),
  CONSTRAINT "Refund_amount_check" CHECK ("amountMinor" > 0)
);

CREATE TABLE "BillingAuditEvent" (
  "id" TEXT NOT NULL,
  "actorId" TEXT NOT NULL,
  "actorType" VARCHAR(16) NOT NULL,
  "action" VARCHAR(80) NOT NULL,
  "subjectType" VARCHAR(40) NOT NULL,
  "subjectId" TEXT NOT NULL,
  "beforeJson" JSONB,
  "afterJson" JSONB,
  "reason" VARCHAR(500),
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "BillingAuditEvent_pkey" PRIMARY KEY ("id")
);
CREATE INDEX "BillingAuditEvent_subject_createdAt_idx" ON "BillingAuditEvent"("subjectType", "subjectId", "createdAt");
CREATE INDEX "BillingAuditEvent_actor_createdAt_idx" ON "BillingAuditEvent"("actorId", "createdAt");

INSERT INTO "QuotaLedgerEntry" ("id", "userId", "type", "amount", "balanceAfter", "idempotencyKey", "sourceType", "sourceId", "reasonCode", "createdAt")
SELECT 'initial-grant:' || q."userId", q."userId", 'GRANT'::"QuotaLedgerType", q."total", q."total", 'initial-grant:' || q."userId", 'INITIAL_GRANT', q."userId", 'INITIAL_ALLOWANCE', q."createdAt"
FROM "QuotaAccount" q
WHERE NOT EXISTS (SELECT 1 FROM "QuotaLedgerEntry" l WHERE l."userId" = q."userId" AND l."type" = 'GRANT'::"QuotaLedgerType");

INSERT INTO "PricingRule" ("id","code","version","operation","modelPattern","resolution","unitCreditCost","formula","effectiveFrom","status","createdBy")
VALUES ('pricing-image-generation-2k-v1','IMAGE_GENERATION_2K',1,'IMAGE_GENERATION','*','2K',1,'unitCost*imageCount',CURRENT_TIMESTAMP,'ACTIVE','system')
ON CONFLICT ("code","version") DO NOTHING;
INSERT INTO "PricingRule" ("id","code","version","operation","modelPattern","resolution","unitCreditCost","formula","effectiveFrom","status","createdBy")
VALUES ('pricing-image-generation-4k-v1','IMAGE_GENERATION_4K',1,'IMAGE_GENERATION','*','4K',2,'unitCost*imageCount',CURRENT_TIMESTAMP,'ACTIVE','system')
ON CONFLICT ("code","version") DO NOTHING;

INSERT INTO "CreditProduct" ("id","code","name","creditAmount","amountMinor","currency","status","sortOrder","createdBy")
VALUES ('credit-pack-100','CREDIT_PACK_100','100 点额度',100,990,'CNY','ACTIVE',10,'system')
ON CONFLICT ("code") DO NOTHING;
INSERT INTO "CreditProduct" ("id","code","name","creditAmount","amountMinor","currency","status","sortOrder","createdBy")
VALUES ('credit-pack-500','CREDIT_PACK_500','500 点额度',500,3990,'CNY','ACTIVE',20,'system')
ON CONFLICT ("code") DO NOTHING;
