# Implement billing, user management, pricing rules, and payment orders

## Objective

Implement the first usable version of the capabilities described in `docs/design/18-billing-user-management-and-payments.md`.

## Requirements

1. Add user account lifecycle fields and admin user management: list/search/detail, disable/enable, revoke sessions, and audited credit adjustments.
2. Make the existing quota ledger explainable: initial grants, source metadata, pricing-rule metadata, and user/admin ledger queries.
3. Add versioned generation pricing rules and use an active rule snapshot when creating a generation task.
4. Add credit products, provider-neutral billing orders, payment transactions, webhook idempotency, refunds, and order-backed credit grants.
5. Add user-facing account/ledger/orders/products views and management views/API clients for users, orders, pricing rules, and products.

Out of scope: real payment provider credentials, subscriptions, tax invoices, expiring credit lots, multi-currency settlement, and changing the existing generation UI to support more than one output image.

## Acceptance Criteria

- Existing generation reserve/consume/release behavior remains green and historical tasks retain their pricing snapshot.
- Newly created quota accounts have an explicit `GRANT` ledger entry with `sourceType=INITIAL_GRANT`.
- User ledger queries are ownership-protected, paginated, filterable, and expose task/order/source metadata without exposing private phone data.
- Admins can list/search users, view details, disable/enable users, revoke sessions, and adjust credits with a required reason and immutable audit entry.
- Pricing rules are versioned, non-overlapping, publishable/retirable, and missing rules reject generation instead of silently using a hardcoded price.
- Repeating an order idempotency key returns the original order; repeating a valid payment webhook grants credits exactly once.
- Invalid payment amount/currency/signature/order callbacks do not change order state or quota.
- Only paid orders grant credits; refunds create separate records and initially support full refunds for unused purchased credits.
- User and admin pages have loading, empty, error, and permission-denied states and pass typecheck/build/unit tests.
- Existing tests and `git diff --check` pass; no real credentials are added.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
