# Data and Runtime Contract Baseline

## PostgreSQL Schema

The schema source is `bak/packages/db/prisma/schema.prisma`; migrations are under `bak/packages/db/prisma/migrations/`. Keep table/column names, relations, indexes, unique constraints, and enum mappings unchanged.

| Group | Models and invariant |
| --- | --- |
| Content | `Inspiration`; unique `slug`; status/category/sort indexes; published timestamp semantics. |
| User auth | `User`, `VerificationCode`, `UserSession`, `AgreementAcceptance`; phone is unique and sessions store token hashes. |
| Admin auth | `AdminUser`, `AdminVerificationCode`, `AdminSession`; unique phone/token hash, active flag, role-based access. |
| Generation | `GenerationSession`, `GenerationTask`, `GenerationResult`, `GenerationTaskEvent`, `GenerationDeadLetter`; task ownership, ordered result index, event cursor, and one dead-letter record per task. |
| Uploads | `ReferenceUpload`; unique object key, ownership, checksum, normalized WebP metadata, soft deletion. |
| Quota | `QuotaAccount`, `QuotaLedgerEntry`; integer balances and unique ledger idempotency key. |
| Reconciliation | `QuotaReconciliationRun`, `QuotaReconciliationFinding`; one window key per run and explicit `OPEN/REPAIRED/BLOCKED` findings. |

## Enum Values

Preserve these database values and their lower-case HTTP mappings:

- `InspirationCategory`: `PORTRAIT`, `PHOTOGRAPHY`, `ANIME`, `ILLUSTRATION`, `DESIGN`.
- `InspirationStatus`: `DRAFT`, `PUBLISHED`, `ARCHIVED`.
- `InspirationSourceType`: `AI_PUBLIC_GALLERY`, `LICENSED`, `INTERNAL`.
- `GenerationTaskStatus`: `QUEUED`, `GENERATING`, `SUCCEEDED`, `PARTIALLY_SUCCEEDED`, `FAILED`, `CANCELLED`.
- `QuotaLedgerType`: `GRANT`, `RESERVE`, `CONSUME`, `RELEASE`.
- `QuotaReconciliationRunStatus`: `RUNNING`, `COMPLETED`, `FAILED`.
- `QuotaReconciliationFindingKind`: `MISSING_RESERVE`, `MISSING_RELEASE`, `MISSING_CONSUME`, `SETTLEMENT_AMOUNT_MISMATCH`, `TOTAL_DRIFT`, `RESERVED_DRIFT`, `AVAILABLE_DRIFT`.
- `QuotaReconciliationFindingStatus`: `OPEN`, `REPAIRED`, `BLOCKED`.
- `AdminRole`: `VIEWER`, `OPERATOR`, `ADMIN`.
- `GenerationRatio`: `smart`, `21:9`, `16:9`, `3:2`, `4:3`, `1:1`, `3:4`, `2:3`, `9:16` (database enum names map to these values).
- `GenerationResolution`: `2K`, `4K`.
- `ModerationStatus`: `PENDING`, `APPROVED`, `REJECTED`.

## Task and Event State

Allowed transitions are `QUEUED -> GENERATING | CANCELLED | FAILED` and `GENERATING -> SUCCEEDED | PARTIALLY_SUCCEEDED | FAILED | CANCELLED`; terminal states do not transition. Event types are the ten values listed in `http-contracts.md`. Source: `bak/packages/core/src/generation-task.ts`, `bak/packages/contracts/src/index.ts`.

## Queues and Jobs

The current queue names are `foundation-health` and `image-generation` (`bak/apps/worker/src/queues/names.ts`). Generation jobs use name `generate` and payload `{ taskId }`; the BullMQ compatibility settings are `jobId=taskId`, `attempts=3`, exponential backoff starting at `500ms`, and bounded removal of completed/failed jobs. New Spring Streams must preserve the business queue name, payload fields, attempt count, and idempotency behavior.

## Object Keys and Assets

- Uploaded reference: `references/{userId}/{uuid}.webp`; public API path `/uploads/references/{uploadId}/content`.
- Result content: `results/{taskId}/{resultId}.webp`.
- Result thumbnail: `thumbnails/{taskId}/{resultId}.webp`.
- User result API paths: `/generation/results/{resultId}/content` and `/thumbnail`.
- Admin result API paths: `/admin/tasks/results/{resultId}/content` and `/thumbnail`.

Stored output is normalized WebP with checksum, width, height, MIME type, byte size, and thumbnail metadata. Object storage can be local or S3-compatible; the key, ownership check, signed URL TTL, and cleanup behavior must remain stable.

## Quota Ledger Invariants

All values are integers. For each account, `total = available + reserved + used`, where `used` is derived as `total - available - reserved`. Reservation atomically decreases `available`, increases `reserved`, and writes a unique `RESERVE` ledger entry. Successful settlement decreases `reserved` and writes `CONSUME`; cancellation, queue failure, provider failure, or moderation rejection increases `available`, decreases `reserved`, and writes `RELEASE`. Every settlement/release key is idempotent (`consume:{taskId}`, `cancel-release:{taskId}`, `failure-release:{taskId}`, or the equivalent stable key). Reconciliation findings must never be silently discarded.

Sources: `bak/apps/api/src/modules/generation/generation.repository.ts`, `bak/apps/worker/src/generation/prisma-generation-store.ts`, and `bak/apps/worker/src/reconciliation/quota-reconciliation.ts`.
