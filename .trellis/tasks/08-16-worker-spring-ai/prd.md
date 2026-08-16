# Implement worker and Spring AI pipeline

## Goal

Deliver Redis Stream processing, OpenAI-compatible ChatModel, images, retries, and reconciliation.

## Requirements

- Implement the `generation` Redis Stream consumer group `generation-workers`, pending reclaim, bounded retries, dead-letter persistence, and durable ack semantics.
- Implement the task state machine and eight-step pipeline: claim, input moderation, ChatModel invocation, output moderation, image conversion, storage, result persistence, and quota settlement.
- Integrate Spring AI 2.0.0-M5 `ChatModel` through an OpenAI-compatible adapter; preserve deterministic mock semantics and classify retryable/non-retryable provider failures.
- Normalize images to WebP with EXIF rotation, target crop/size, thumbnails, SHA-256, and compensating object cleanup.
- Implement quota reconciliation runs and findings; only provably safe missing settlements may be repaired automatically.

## Acceptance Criteria

- [ ] Consumer creates the group, claims messages, acknowledges only after durable processing, and reclaims idle messages without duplicate settlement.
- [ ] QUEUED/GENERATING/CANCELLED races are resolved by conditional database updates; terminal tasks are ignored without model invocation.
- [ ] Mock success, one retry, repeated retryable error, moderation rejection, malformed provider output, and dead-letter cases pass tests.
- [ ] Real adapter sends an OpenAI-compatible request through Spring AI and maps timeout/429/5xx/format/permission failures correctly.
- [ ] Main/thumbnail objects, database results, events, and quota ledger remain consistent after each injected failure.
- [ ] Reconciliation detects drift, repairs only safe findings, and marks ambiguous money drift BLOCKED.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
