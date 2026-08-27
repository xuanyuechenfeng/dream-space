# Implementation Plan

1. [x] Add migrations and shared domain records/enums for user status, pricing rules, products, orders, payments, refunds, audit events, ledger metadata, and initial grant backfill.
2. [x] Extend common quota transaction/mappers with initial grant, ledger pagination, admin adjustment, order grant, rule metadata and refund debit operations.
3. [x] Implement API services/controllers for user account/ledger, admin users/audit, pricing rules/products, orders, payment webhook, cancellation, and full unused-order refund.
4. [x] Replace hardcoded generation pricing with active rule resolution and task snapshots; preserve existing generation behavior with seeded rules.
5. [x] Add user web account/ledger/orders/product routes and views.
6. [x] Add manage web users, billing orders, pricing rules, products, and audit routes/views.
7. [ ] Add focused backend/frontend tests, run typecheck/build/unit tests, and run migration/resource checks. Frontend user typecheck and Trellis/migration checks pass; manage_web dependencies and backend target output are blocked by local filesystem permissions.
8. [x] Update knowledge/design documentation with final endpoint and schema differences.

Validation commands:

- `mvnw.cmd -f dream_service/pom.xml test`
- `npm run typecheck` and `npm run build` in both frontend packages
- `npm run test:unit` in both frontend packages
- `git diff --check`
