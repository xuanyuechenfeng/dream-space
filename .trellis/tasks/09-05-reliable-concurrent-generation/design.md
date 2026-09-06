# Technical Design

## Boundaries

The API owns durable preflight/task handoff and idempotency. The Worker owns planning execution and image-slot execution. Common persistence owns row locks, unique constraints, and quota-facing mapper contracts. The frontend treats preflight state as recoverable submission state and formal task state as the only billable timeline item.

## Durable READY handoff

`CollectionPreflightService` first looks up `(userId, idempotencyKey)` and `(userId, draftKey, inputHash)`. A matching `READY` row is returned instead of creating another preflight. The frontend stores the pending preflight id and create idempotency key in session storage, reconnects by GET/SSE after reload, and retries `createFromPreflight` until a definitive response. `clearSessionState` must not cancel a server operation; it only closes local listeners. A preflight row is consumed under `FOR UPDATE` and the task/execution/slot rows plus full reserve are inserted in one transaction. Duplicate create requests return the original task.

Preflight processing uses two short transactions: claim `QUEUED -> PLANNING`, perform the remote call outside a transaction, then write `READY`/clarification/failure and event atomically. The queue republisher only republishes `QUEUED` rows, so a long model call cannot cause duplicate publication.

## Concurrent slot execution

`GenerationV2SlotProcessor` claims the execution once, validates the frozen plan, and submits each selected non-success slot to a bounded `ExecutorService`. The pool size comes from `dream-space.generation.v2.slot-concurrency` (default 4, clamped to 1..4). Each worker task claims its slot, performs provider call, quality loop, moderation, and object persistence, then calls the existing transactional settlement service to consume exactly one unit or records only that slot's failure. The execution waits for all selected slots and aggregates task status once. No sibling future is cancelled after a failure.

Provider calls and output storage occur outside settlement transactions. Settlement locks task, execution, slot, then quota through the existing service. A late result whose slot ownership or execution state is lost is deleted and ignored. Exceptions are captured per future.

The executor is a Spring-managed bean with a named daemon thread factory and graceful shutdown. Interrupted workers restore the interrupt flag and mark their slot failure/retry according to delivery semantics.

## Frontend recovery and presentation

Pending submission is persisted under a namespaced session-storage key containing preflight id, fingerprint, create idempotency key, and prompt snapshot. On store initialization and session activation, recover a pending entry and resume GET/SSE. Pending UI copy is natural preparation text only; it does not mention preflight, temporary work, or billing. Once a formal task response arrives, pending state is cleared and the task enters the normal waiting/running view. The client never uses a timer to fail active work.

Task SSE continues to refresh the authoritative task/quota projection. Slot cards retain fixed order and independently render waiting, generating, succeeded, failed, and cancelled states. Existing continue-missing and regenerate-all actions remain separate.

## Compatibility and observability

Version-1 tasks use the existing serial processor and task-level settlement. Version-2 executions use the concurrent processor only. Unknown model response fields are ignored after tree parsing; only documented required fields are validated. Raw responses log length/top-level shape/bounded preview, never credentials or full prompts. Log execution id, task id, slot index, attempt, concurrency capacity, outcome, and duration.
