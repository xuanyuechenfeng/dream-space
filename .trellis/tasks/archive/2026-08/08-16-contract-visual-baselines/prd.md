# Freeze API contracts and visual baselines

## Goal

Create versioned, reviewable compatibility baselines from `bak/` before any new runtime implementation begins.

## Requirements

- Inventory every user/admin page route and its primary visual states from the existing applications.
- Inventory public and administrative HTTP endpoints, request fields, response fields, cookie behavior, error envelopes, and SSE event names from the existing API and tests.
- Inventory the Prisma data model, database migrations, enums, Redis stream names, storage object-key formats, task states, and quota-ledger invariants without changing the legacy schema.
- Add deterministic fixture samples and baseline-capture instructions. Do not include credentials, personal data, or real provider outputs.
- State the source file and extraction method for every baseline item so future tasks can trace and regenerate it.

## Acceptance Criteria

- [x] `docs/migration-baselines/routes-and-visuals.md` lists every web/admin route, source path, required visual state, viewports, and capture command.
- [x] `docs/migration-baselines/http-contracts.md` lists supported endpoint groups, cookies, error shape, and SSE event semantics with source references.
- [x] `docs/migration-baselines/data-contracts.md` lists schema sources, enum/state values, queue names, object-key rules, and quota invariants.
- [x] `docs/migration-baselines/README.md` defines deterministic fixture rules and regeneration procedure.
- [x] The documents contain no credential-like strings, personal data, or modifications below `bak/`.
