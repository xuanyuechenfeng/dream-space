# Reliable concurrent multi-image generation

## Goal

Make collection generation reliable after asynchronous planning and execute all requested image slots concurrently. A planning result that reaches `READY` must survive reloads, navigation, retries, and transient network failures until it is either consumed into a formal task or expires. A failure in one image must not prevent sibling images from running or being settled.

## Requirements

1. A `READY` preflight can be resumed from the server by idempotency key and draft/input hash; formal task creation is atomic and can be retried without duplicate task, execution, reserve, or queue records.
2. Preflight claiming and terminal state writes use short database transactions. The long planning/model call must not hold a transaction that makes `QUEUED` rows visible to republishers.
3. Once a formal task exists, the API and frontend expose it as waiting/running. No elapsed-time timeout converts active work into a retry prompt.
4. One execution may process its selected slots concurrently with a bounded, configurable worker pool. Each slot owns its provider call, output persistence, quality retry, result record, quota consume, and terminal failure path.
5. A slot failure does not cancel, skip, or mark sibling slots as failed. The execution waits for all selected slots, then aggregates task status: all success `SUCCEEDED`, mixed success/failure `PARTIALLY_SUCCEEDED`, all failure `FAILED`.
6. Duplicate queue deliveries, worker restarts, cancellation races, and late provider responses remain idempotent. Successful slots are never regenerated or charged twice.
7. Continue-missing retries only failed/waiting slots and reuses the frozen collection plan. Frontend slot updates remain server-authoritative and preserve stable order.
8. Required model response fields remain validated while unknown fields are ignored; raw model responses are logged with bounded metadata and prompts explicitly constrain required fields.

## Acceptance Criteria

- [ ] READY preflight survives page reload/navigation and is consumed exactly once by a retried formal-create request.
- [ ] No duplicate preflight publication is observed while planning is in progress.
- [ ] A formal task is visible as waiting/running immediately after creation; no planning-timeout UI appears for an active task.
- [ ] For N slots, at least two independent provider calls overlap when pool capacity permits, and pool size never exceeds configuration.
- [ ] One slot can fail while all other slots complete and settle independently.
- [ ] Final aggregation matches all-success, mixed, and all-failure cases; quota invariant remains `reserve = consume + release`.
- [ ] Continue-missing does not touch successful slots and is safe under duplicate requests.
- [ ] Existing single-image flow and version-1 historical tasks remain compatible.
- [ ] Backend tests, frontend typecheck/build/unit tests, and focused concurrency/recovery tests pass.
