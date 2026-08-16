# Quality and regression design

## Test matrix

Backend layers are JUnit/Mockito, MyBatis + PostgreSQL Testcontainers, Redis Testcontainers, WireMock OpenAI-compatible, and MockMvc contract fixtures. Frontend layers are Vitest, Playwright journeys, axe checks, and screenshot comparisons. Each frozen contract maps to at least one executable test id.

## Visual and DOM gates

Use deterministic mock data and fixed time. Capture user pages at 1440x900, 1024x768, 390x844 and admin pages at 1440x900, 800x1024, 390x844. Run both locale/theme variants where content or token behavior differs. A DOM audit fails on duplicate ids, labels without targets, unresolved `aria-controls`, missing translation keys, horizontal overflow, or text outside controls.

## CI

CI stages are dependency install -> static checks -> unit/contract -> integration -> E2E/visual -> security/immutability. Testcontainers jobs are explicit and cannot silently skip in CI; local execution may report Docker-unavailable skip. Artifacts retain reports, traces, diffs, and logs with secrets redacted.
