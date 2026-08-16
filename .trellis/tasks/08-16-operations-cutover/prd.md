# Implement operations and dual-stack cutover

## Goal

Add Docker profiles, observability, bridge, rollout, and rollback controls.

## Requirements

- Provide repeatable local/staging/production deployment definitions for PostgreSQL 17, Redis 8, MinIO/S3, API, Worker, web, and admin.
- Implement liveness/readiness, structured logs, request/task correlation, metrics, dashboards, and alerts for API, queue, model, storage, quota, and reconciliation.
- Implement a BullMQ-to-Redis-Stream bridge or verified dual-write/dual-read strategy for the migration window without duplicate generation or settlement.
- Document and automate backup, restore, schema migration, object lifecycle, canary rollout, traffic switch, and rollback.

## Acceptance Criteria

- [ ] Containers start with health dependencies and no secret baked into images or Compose files.
- [ ] API/Worker readiness fails for unavailable required dependencies and liveness remains process-only.
- [ ] Old and new runtimes process the same fixture without duplicate results/events/ledger entries during the bridge window.
- [ ] Dashboards and alerts expose latency, errors, queue lag/pending, provider retries, generation outcomes, S3 errors, and quota drift.
- [ ] A staging backup/restore, canary, rollback, and data-consistency rehearsal is recorded and repeatable.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
