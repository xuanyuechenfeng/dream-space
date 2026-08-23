# Split frontend into two standalone applications

## Goal

Remove the `frontend` aggregation directory and make the user-facing and admin-facing Vue applications independently installable, testable, and deployable from root-level `dream_web/` and `manage_web/` projects.

## Requirements

- Preserve all existing Vue source, routes, assets, styles, API behavior, and visual baselines.
- Move web-only E2E tests and Playwright configuration into `dream_web/`; move admin-only E2E tests and Playwright configuration into `manage_web/`.
- Give each project its own package manifest and lockfile; remove the obsolete frontend workspace manifest, shared lockfile, and aggregation scripts.
- Update CI, quality gates, documentation, and developer commands to use the two root-level projects.
- Do not modify `bak/` or backend code.

## Acceptance Criteria

- [x] `dream_web/` and `manage_web/` each work as standalone pnpm projects with `install`, `typecheck`, `build`, `test:unit`, and `test:e2e` commands.
- [x] No tracked source, workflow, or documentation reference requires `frontend/web`, `frontend/admin`, or the removed frontend workspace files.
- [x] Web and admin E2E suites start only their matching Vite app and retain screenshot/accessibility assertions.
- [x] Both projects pass typecheck, production build, unit tests, and quality gates with the new paths; all 15 E2E cases reached `ok` before the local Windows runner hung during process cleanup.
- [x] Existing untracked user files remain untouched and `bak/` remains unchanged.
