ALTER TABLE "User" ALTER COLUMN "phone" DROP NOT NULL;
ALTER TABLE "User" ADD COLUMN "email" TEXT;

CREATE UNIQUE INDEX "User_email_key" ON "User"("email");

CREATE TABLE "RegistrationEmailCode" (
    "id" TEXT NOT NULL,
    "emailHash" TEXT NOT NULL,
    "codeHash" TEXT NOT NULL,
    "clientKeyHash" TEXT NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "consumedAt" TIMESTAMP(3),
    "attempts" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RegistrationEmailCode_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "RegistrationEmailCode_emailHash_createdAt_idx"
    ON "RegistrationEmailCode" ("emailHash", "createdAt");
CREATE INDEX "RegistrationEmailCode_clientKeyHash_createdAt_idx"
    ON "RegistrationEmailCode" ("clientKeyHash", "createdAt");
