# Contract and Visual Baseline Implementation Plan

1. Enumerate route files and frontend API adapters below `bak/apps/web` and `bak/apps/admin`.
2. Enumerate API controllers, tests, SSE code, cookies, and error handling below `bak/apps/api`.
3. Enumerate data model, migrations, queues, object-key generation, and worker behavior below `bak/packages/db` and `bak/apps/worker`.
4. Write the four `docs/migration-baselines/` documents with explicit source references.
5. Run a credential-pattern scan, `git diff --check`, and source-reference existence checks.

## Rollback

Remove only the newly added `docs/migration-baselines/` directory. No legacy or runtime data is changed.
