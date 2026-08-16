# Implement generation API and web workspace

## Goal

Deliver sessions, quota, task submission, SSE API, and web generation workspace.

## Requirements

- Implement generation session CRUD, draft persistence, options, quota read, task submission/cancel/retry, result access, and SSE events according to the existing HTTP contracts.
- Validate prompt/reference exclusivity, 1-8 images, 0-4 references, ratio/resolution enums, MIME/size limits, and integer pricing before reserving quota.
- Make task creation idempotent by `(userId, idempotencyKey)`; conflicting parameters must return `GENERATION_IDEMPOTENCY_CONFLICT`.
- Publish a Redis generation message only after the PostgreSQL transaction commits. PostgreSQL task/event state remains authoritative.
- Build the Vue generation workspace with session sidebar, composer, uploads, quota summary, timeline, cancellation, retry, preview, and download behavior from `bak`.

## Acceptance Criteria

- [ ] Session create/list/rename/delete/draft endpoints enforce ownership and preserve old JSON fields.
- [ ] A valid submission reserves exactly `unitCost * imageCount`; duplicate replay returns the original task without a second reserve.
- [ ] Invalid parameters, insufficient quota, conflicting idempotency keys, and forbidden task access return documented errors.
- [ ] SSE replays events after `Last-Event-ID`, deduplicates client events, closes on terminal state, and never leaks another user's task.
- [ ] Cancel is atomic for QUEUED/GENERATING and worker-safe; terminal tasks cannot be cancelled.
- [ ] Web workspace passes mock-generation desktop/mobile flows and visual baseline checks.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
