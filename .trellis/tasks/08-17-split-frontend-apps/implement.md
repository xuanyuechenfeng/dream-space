# Implementation Plan

1. Move `frontend/web` and `frontend/admin` tracked files to root-level `dream_web` and `manage_web`; move the shared TypeScript base into each project.
2. Split the E2E support file and specs, screenshot baselines, and Playwright configuration by application.
3. Convert each package manifest to a standalone project and generate independent lockfiles; remove the old workspace files.
4. Update quality gates, GitHub Actions, design/knowledge documentation, and ignore rules.
5. Install dependencies and run typecheck, build, unit tests, E2E tests, and repository quality gates for both applications.
6. Review the diff for `bak/` changes and stale old project/route references, then commit the migration.
