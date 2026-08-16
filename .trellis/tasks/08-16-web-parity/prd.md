# Implement web visual shell and public flows

## Goal

Port user-facing layout, themes, inspiration, and login behavior with bak parity.

## Requirements

- Rebuild `frontend/web` with Vue 3, TypeScript, Vite 5, Vue Router, and Pinia while keeping `bak/apps/web` routes, copy, DOM semantics, interactions, and assets equivalent.
- Implement the user shell, inspiration gallery/detail, login/agreement modal, theme/language preferences, and auth intent flow.
- Reuse the documented light/dark tokens, navigation/session widths, typography, icon system, and responsive breakpoints; do not introduce a second visual language.
- Centralize typed API calls and cookie credentials; do not put session tokens or API URLs in components/localStorage.

## Acceptance Criteria

- [ ] `/`, `/inspiration`, `/inspiration/:slug`, `/login`, `/generate`, and `/generate/:sessionId` route correctly with loading/error/empty states.
- [ ] Inspiration search history, category filter, debounce, copy, like/follow local state, and do-the-same flow match `bak` behavior.
- [ ] Login validates phone/code/three agreements, supports modal Escape/outside close, countdown, auth intent, and session restore.
- [ ] Light/dark/system and zh/en preferences persist under the documented keys; no hard-coded duplicate token set exists.
- [ ] Playwright screenshots pass at 1440x900, 1024x768, and 390x844, with no horizontal overflow, duplicate IDs, missing DOM targets, or untranslated overflow.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
