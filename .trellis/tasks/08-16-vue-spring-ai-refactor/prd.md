# Vue Spring AI refactor delivery

## Goal

Deliver the documented Vue 3, TypeScript, Vite 5, Spring Boot 4.0, Spring MVC, and Spring AI 2.0.0-M5 refactor. The resulting product must preserve the user-facing and administrative behavior, route set, visual system, database semantics, and external contracts defined by `bak/`.

## Requirements

- Treat `docs/design/` as the approved implementation specification and `bak/` as a read-only compatibility baseline.
- Create all new runtime code under `frontend/` and `backend/`; do not modify `bak/`.
- Preserve PostgreSQL table and column names, enum values, identifiers, task states, quota ledger semantics, Redis/S3 contracts, and public API compatibility.
- Preserve web and admin routes, Chinese/English content, themes, responsive breakpoints, and interactions. Visual parity is required at 1440x900, 1024x768, and 390x844.
- Use feature branches and reviewable pull requests. Do not merge into `main` without explicit user approval.
- Keep credentials out of source, tests, documentation, commits, and task artifacts.

## Delivery Task Map

1. `08-16-contract-visual-baselines`: compatibility inventories and repeatable visual baselines.
2. `08-16-platform-scaffold`: runnable Vue applications and Spring Maven modules.
3. `08-16-persistence-infrastructure`: PostgreSQL, Redis, S3, configuration, and integration adapters.
4. `08-16-auth-inspiration-api`: authentication, RBAC, inspirations, and uploads.
5. `08-16-web-parity`: user-facing shell, inspiration, and login parity.
6. `08-16-generation-api-workspace`: generation REST/SSE APIs and the web workspace.
7. `08-16-worker-spring-ai`: Stream worker, ChatModel, image pipeline, and reconciliation.
8. `08-16-admin-api-application`: admin APIs and admin Vue application.
9. `08-16-quality-regression`: contract, E2E, visual, accessibility, and responsive gates.
10. `08-16-operations-cutover`: runtime profiles, observability, bridge, rollout, and rollback.
11. `08-16-legacy-retirement`: retirement only after production cutover criteria are met.

## Acceptance Criteria

- [ ] Every child task has its own verifiable PRD and, when complex, design and implementation checklist before execution.
- [ ] Frontend, API, worker, data, security, and operational requirements in `docs/design/01-architecture.md` through `docs/design/07-testing-migration.md` are implemented and verified.
- [ ] API and visual compatibility baselines pass before traffic cutover.
- [ ] Mock and OpenAI-compatible `ChatModel` paths are both repeatably tested.
- [ ] A tested rollback period is retained before the legacy runtime is retired.
