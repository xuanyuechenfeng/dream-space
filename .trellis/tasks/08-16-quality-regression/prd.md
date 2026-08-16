# Implement quality gates and regression suites

## Goal

Add contract, E2E, visual, accessibility, and responsive regression verification.

## Requirements

- Establish executable HTTP/JSON, database, Redis, object-storage, and Spring AI compatibility tests against the frozen migration baselines.
- Add Vitest and Playwright coverage for user/admin workflows, accessibility, localization, themes, responsive breakpoints, duplicate IDs, missing DOM targets, and visual screenshots.
- Make security, credential, `bak/` immutability, build, typecheck, and migration checks blocking in CI.
- Keep fixtures deterministic and scrub credentials, phone numbers, prompts, timestamps, random images, and signed query strings.

## Acceptance Criteria

- [ ] Backend unit, MockMvc, WireMock, PostgreSQL/Redis Testcontainers, and migration contract tests pass in CI.
- [ ] Frontend typecheck/build/Vitest and Playwright user/admin journeys pass at required viewports.
- [ ] Visual baselines cover light/dark/system, zh/en, loading/empty/error/terminal states, modal/drawer, and reduced motion.
- [ ] CI fails on duplicate ID, missing DOM target, overflow, missing translation, credential pattern, or modified `bak/`.
- [ ] Test reports identify the exact contract, route, viewport, and fixture for every failure.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
