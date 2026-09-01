# Billing ledger visibility research

## Current path and constraints

- The account page loads the first ledger page through `api.account.ledger()` and renders every returned item without filtering (`dream_web/src/views/AccountView.vue:29`, `dream_web/src/views/AccountView.vue:81`; client route at `dream_web/src/api/client.ts:69-75`).
- The user endpoint delegates to `BillingService.ledger`, whose `countLedger` and `listLedger` calls currently query all four ledger types before applying `LIMIT/OFFSET` (`dream_service/api/src/main/java/com/dreamspace/api/controller/BillingController.java:18-20`, `dream_service/api/src/main/java/com/dreamspace/api/service/BillingService.java:77-84`, `dream_service/api/src/main/java/com/dreamspace/api/persistence/admin/BillingMapper.java:29-32`).
- The admin endpoint currently reuses the same service method, so changing `BillingService.ledger` without separating the admin path would also hide operational facts from administrators (`dream_service/api/src/main/java/com/dreamspace/api/controller/AdminBillingController.java:21-22`, `dream_service/api/src/main/java/com/dreamspace/api/service/BillingService.java:101-104`).
- `RESERVE` and `RELEASE` are accounting facts required by quota settlement and reconciliation. They must remain stored and queryable for administration; no enum, migration, mutation, or reconciliation change is appropriate (`dream_service/common/src/main/java/com/dreamspace/common/persistence/quota/QuotaTransactionService.java:64-94`, `docs/migration-baselines/data-contracts.md:52-56`).
- The account page displays `items.length` rather than the API `total`, so its “共 X 条记录” label is already capped at the loaded page size (`dream_web/src/views/AccountView.vue:29`, `dream_web/src/views/AccountView.vue:75`). No account fixtures or account-page tests currently exist; the only focused ledger test checks nullable enum casts in mapper SQL (`dream_service/api/src/test/java/com/dreamspace/api/persistence/BillingMapperSqlTest.java:10-23`).

## Recommended implementation

1. Treat the user ledger as a read projection, not a mutation change. Add one shared SQL fragment in `BillingMapper`, for example `ACCOUNT_LEDGER_VISIBILITY_FILTER`, that excludes exactly `RESERVE` and `RELEASE` using explicit `QuotaLedgerType` casts.
2. Apply that fragment to both the user-facing list and count queries. Filtering must happen in SQL before `ORDER BY`, `LIMIT`, and `OFFSET`; filtering `l.items` in Vue or Java after pagination would produce short/empty pages and an incorrect `total/pageCount`.
3. Give the admin path a separate full-fidelity list/count pair. A clear shape is `listAccountLedger/countAccountLedger` for the filtered projection and `listUserLedger/countUserLedger` for the administrator query. `BillingService.ledger` uses the former; `BillingService.userLedger` performs its own pagination with the latter rather than delegating to `ledger`.
4. Preserve the existing nullable `type` filter on both paths. On the user endpoint, `type=RESERVE` or `type=RELEASE` should return an empty page (`items=[]`, `total=0`) rather than bypassing visibility; the admin endpoint keeps returning those types.
5. Keep `BillingService.account`, `QuotaTransactionService`, ledger inserts, database enums, and reconciliation untouched. This preserves `total/available/reserved/used` and all audit facts.
6. Store the returned ledger and order totals in `AccountView` and use them in the record-count label. This makes the displayed count agree with the filtered server total even when more than 20 visible records exist. Adding pagination controls is outside this narrow request unless product explicitly asks for them.

The existing `(userId, createdAt)` ledger index still supports the proposed query; this read-only predicate does not justify a migration. Keep the existing type labels as harmless fallback formatting, but do not add a second client-side visibility rule as the source of truth.

## Focused regression tests

- Extend `BillingMapperSqlTest` to assert the account list and count SQL contain the same `RESERVE/RELEASE` exclusion, and that the exclusion appears in the list query before `LIMIT/OFFSET`.
- In the same test, assert the admin list/count SQL retain the nullable enum cast but do not contain the account visibility exclusion.
- Add a `BillingServiceTest` with a mocked mapper: verify page/size/offset and `pageCount` are computed from the filtered count, and verify `userLedger` calls the full-fidelity admin pair and can return `RESERVE`/`RELEASE`.
- Add an account-view test (or route-mocked Playwright case) where the response has two visible items but `total > items.length`; assert no technical rows render and the heading uses `total`, not page length. The backend contract test remains the primary proof that hidden rows cannot create pagination holes.
