# Technical Design

## Boundaries

`common` owns shared database enums, records, migrations, quota ledger mappers, and transaction primitives. `api` owns authenticated user/admin HTTP contracts and application services. `worker` continues to settle generation tasks and reads immutable task pricing snapshots. Each Vue app owns its route, API client, and views.

## Data Flow

Generation: resolve active `PricingRule` -> snapshot `ruleId/ruleVersion/unitCost/totalCost` on `GenerationTask` -> reserve credits and append `RESERVE` ledger -> worker appends `CONSUME` or `RELEASE`.

Payment: create order from immutable `CreditProduct` snapshot -> provider adapter creates payment attempt -> webhook verifies provider data and idempotency -> lock order -> mark `PAID` -> append `GRANT(sourceType=ORDER)` in the same transaction -> publish an outbox-compatible event.

## Consistency

All balance mutations remain inside `QuotaTransactionService`. Order callbacks use unique provider event and transaction keys. User disablement invalidates active sessions before returning. Pricing rules are selected under a transaction and copied to the task, so later rule edits cannot alter history.

## Compatibility

Existing `QuotaLedgerType` values remain valid. New metadata columns are nullable during migration and old rows remain queryable. Existing generation requests continue to work after seeded active rules are installed. The first payment provider implementation is an in-process mock adapter for unit/integration tests; production adapters can be added behind the same interface.

## Failure Handling

Invalid pricing configuration fails task creation with a domain error. Webhook failures are recorded as failed payment attempts and are safe to retry. Grant and payment writes are idempotent. Refunds are restricted to paid, unused orders in the first release.
