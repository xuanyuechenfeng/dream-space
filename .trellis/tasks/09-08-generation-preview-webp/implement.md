# Implementation Plan

1. Extend common image processing with proportional WebP preview encoding and
   focused unit tests for landscape, portrait, square, and no-upscale cases.
2. Update `GenerationOutputPipeline` to store PNG originals plus versioned WebP
   previews, including partial-write cleanup and metadata tests.
3. Separate thumbnail and original API resolution, add private cache validators,
   remove original fallback for thumbnail reads, and cover user/admin endpoints.
4. Add a bounded, idempotent worker backfill command and mapper operations for
   historical preview conversion; test dry-run, success, retry, and CAS conflict.
5. Update generation-page preview state and templates so result cards and the
   default dialog use only thumbnail URLs; keep original loading explicit.
6. Version the client image-cache namespace and add frontend tests proving that
   page/default-preview rendering never fetches result content URLs.
7. Run targeted backend and frontend tests, then full module checks. Inspect the
   diff for billing, settlement, moderation, and unrelated-worktree changes.
8. Measure representative stored previews against the 640-pixel and 150-KiB
   targets, document exceptions, and update durable project specs if needed.

## Validation Commands

```text
./mvnw -pl common,worker,api -am test
npm run typecheck
npm run build
npm run test:unit
```

## Rollback Points

- Image/API changes are compatible with existing database rows and can be
  reverted before running backfill.
- Backfill writes versioned objects and updates one row at a time; stop the
  command without rolling back completed rows.
- Do not delete legacy thumbnails until each corresponding database update has
  committed.
