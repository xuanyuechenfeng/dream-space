# Implementation Plan

1. [x] Complete shared configuration and persistence contracts for slot concurrency, preflight recovery lookup, execution aggregation, and idempotent queue publication.
2. [x] Refactor `CollectionPreflightProcessor` so claim, remote planning, and finish are separate transaction boundaries; add regression tests for duplicate publication visibility and READY replay.
3. [x] Make `CollectionPreflightService` and frontend submission recovery replay-safe across reload/navigation while preserving the no-temporary-item presentation rule.
4. [x] Refactor `GenerationV2SlotProcessor` into bounded concurrent slot jobs with per-slot exception isolation, executor lifecycle management, and aggregate completion.
5. [x] Extend settlement/mapper tests for mixed/all-failed aggregation, duplicate futures, late responses, cancellation, and exact reserve/consume/release accounting.
6. [x] Add frontend store tests for persisted pending state, formal task transition, no timeout, and independent slot refreshes.
7. [x] Run focused backend tests, full module tests, frontend typecheck/build/unit tests, and inspect the final diff for unrelated changes.

Rollback: disable v2 writes or set slot concurrency to 1; retain the existing v1 processor and immutable migrations.

Validation completed on 2026-09-05: `mvn test` (common 23 with 2 external integration skips, API 51, Worker 105), `npm run typecheck`, `npm run build`, and `npm run test:unit` (42 tests).
