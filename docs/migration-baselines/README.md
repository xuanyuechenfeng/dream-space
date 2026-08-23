# Migration Baselines

These files are the compatibility evidence for the Vue/Spring migration. `bak/` is read-only and remains the source of truth until the new implementation passes the same checks.

## Files

- [routes-and-visuals.md](routes-and-visuals.md): route, state, CSS, breakpoint, and screenshot matrix.
- [http-contracts.md](http-contracts.md): HTTP, cookie, upload, error, and SSE contracts.
- [data-contracts.md](data-contracts.md): PostgreSQL, enum, task, queue, object-key, and quota contracts.

## Deterministic Fixture Rules

1. Use symbolic IDs such as `user-fixture-1`, `session-fixture-1`, and `task-fixture-1`.
2. Use masked phones such as `138****8000`; never put a real phone, token, cookie, connection string, or provider key in a fixture.
3. Fix the clock to `2026-08-03T10:00:00Z`, seed order explicitly, and disable animations and random image ordering during captures.
4. Use only local `mock` services and the checked-in `bak/apps/web/public/inspiration` assets. Do not call a live model or external object store.
5. Keep API JSON field names, enum casing at the HTTP boundary, status transitions, and asset URL shapes unchanged.

## Regeneration

Run these read-only inventory checks from the repository root when the baseline source changes:

```powershell
rg --files bak/apps/web/app bak/apps/admin/app
rg -n "@(Get|Post|Patch|Delete|Sse)|@Controller" bak/apps/api/src
rg -n "^(model|enum) " bak/packages/db/prisma/schema.prisma
rg -n "generationQueueName|GENERATION_QUEUE|objectKey|thumbnailObjectKey" bak/apps bak/packages
```

For a running local legacy stack, capture each required state with the Playwright CLI. The command must use a fixture database, `EXTERNAL_SERVICES_MODE=mock`, a fixed clock, and the viewport matrix in `routes-and-visuals.md`:

```powershell
npx playwright screenshot --device="Desktop Chrome" http://localhost:3000/inspiration docs/migration-baselines/screenshots/web-inspiration-1440.png
npx playwright screenshot --device="iPhone 13" http://localhost:3000/inspiration docs/migration-baselines/screenshots/web-inspiration-390.png
npx playwright screenshot --device="Desktop Chrome" http://localhost:3001/tasks docs/migration-baselines/screenshots/admin-tasks-1440.png
```

Screenshots are review artifacts, not source inputs. Do not commit screenshots containing credentials or personal data.
