# Worker and Spring AI design

## Consumer

Use a `generation` stream and `generation-workers` group. A message contains `schemaVersion`, `taskId`, `attemptKey`, `attemptNumber`, `maxAttempts`, and `queuedAt`. Group initialization is idempotent. Consumers use `XREADGROUP`, persist a claim before work, `XACK` only after the task transaction/compensation completes, and reclaim idle pending entries. A poison message creates `GenerationDeadLetter` then is acknowledged.

## Pipeline and compensation

The pipeline is ordered and observable: claim -> input moderation -> model -> output moderation -> ImagePipeline -> object storage -> result/event persistence -> quota settlement. Each step has a compensating action. Object writes are removed when persistence fails; a database success with event publish failure is recovered by outbox/replay. `(taskId,index)` and settlement idempotency keys are the duplicate guards.

## Model adapter

`OpenAiCompatibleGenerationModel` is the only class aware of Spring AI milestone APIs. It accepts a domain `GenerationRequest`, builds fixed system/user messages and `ChatOptions`, decodes URL/base64 image output, and returns a provider-neutral result. `DeterministicMockProvider` is selected by profile and keeps the documented prompt fault markers.

## Reconciliation

A scheduled worker creates one `QuotaReconciliationRun` per `windowKey`, compares task terminal state to ledger entries and account arithmetic, repairs missing consume/release only when the expected amount is unambiguous, and records all other findings as OPEN/BLOCKED.
