# Delivery Design

## Scope Boundary

This parent task coordinates the staged migration described in `docs/design/`. It does not own application code. Each child owns an independently testable deliverable and records its dependency order in its own planning artifacts.

## Dependency Order

`contract-visual-baselines` establishes the compatibility evidence. `platform-scaffold` follows and enables `persistence-infrastructure`, `web-parity`, and the API work. Authentication and persistence must be available before generation and admin APIs. The worker depends on persistence plus generation contracts. Quality gates evolve with each child and become blocking before operations cutover. Legacy retirement is prohibited until the cutover task verifies rollback requirements.

## Branching

The documentation branch remains the base while its review is open. Each implementation child uses a focused `feature/` branch and reviewable PR. Once the design PR is merged, outstanding implementation branches are rebased onto `main` before their PRs target `main`.

## Compatibility Sources

- `docs/design/01-architecture.md` through `docs/design/07-testing-migration.md`
- `bak/apps/web`, `bak/apps/admin`, `bak/apps/api`, and `bak/apps/worker`
- `bak/packages/db/prisma/schema.prisma` and its migrations
