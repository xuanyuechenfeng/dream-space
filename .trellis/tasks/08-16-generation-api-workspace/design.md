# Generation API and workspace design

## Submit transaction

`GenerationApplicationService.submit` validates principal/session and request, calculates integer cost, checks `(userId,idempotencyKey)`, locks `QuotaAccount`, updates available/reserved, inserts a unique `RESERVE` ledger entry, creates `GenerationTask` and its first event, then commits. A post-commit publisher adds `{taskId, attemptKey, attemptNumber, maxAttempts, schemaVersion}` to Redis; an outbox/retry path covers publish failure.

## State and SSE

Allowed state transitions are `QUEUED -> GENERATING -> SUCCEEDED|PARTIALLY_SUCCEEDED|FAILED|CANCELLED`. Every transition appends `GenerationTaskEvent`; event ids are the cursor. `GET /generation/tasks/{id}/events` authorizes ownership, replays events after `Last-Event-ID`/`after`, subscribes to new events, emits keep-alives, and closes after a terminal event.

## Frontend workspace

`generationStore` owns sessions, active draft, task filters, event cursor, and EventSource lifecycle. `Composer` validates locally but treats server quota/validation as authoritative. Upload references are temporary draft entries; result and thumbnail URLs are obtained from authorized API endpoints. Cancellation and deletion require the same confirmation copy and interaction order as `bak`.

## Failure handling

Duplicate idempotency with identical normalized payload returns the existing task; a different payload returns conflict. Queue publish failures are observable and retried; they never convert a committed task into a client-visible success without a task id.
