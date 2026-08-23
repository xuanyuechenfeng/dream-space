ALTER TABLE "User" ADD COLUMN "passwordHash" TEXT;

CREATE TABLE "LoginCaptcha" (
    "id" TEXT NOT NULL,
    "clientKeyHash" TEXT NOT NULL,
    "codeHash" TEXT NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "consumedAt" TIMESTAMP(3),
    "attempts" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "LoginCaptcha_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "LoginCaptcha_clientKeyHash_createdAt_idx"
    ON "LoginCaptcha" ("clientKeyHash", "createdAt");
