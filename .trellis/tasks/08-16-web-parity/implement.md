# Implementation checklist

1. Add shared frontend TypeScript/Vite aliases and typed API client with `credentials: include`.
2. Migrate user shell, navigation, theme/language stores, tokens, assets, and responsive CSS from `bak`.
3. Implement inspiration gallery/detail, search history, filters, empty/error/loading views, copy, and do-the-same intent.
4. Implement login, verification countdown, agreements modal, auth intent, and route/session guards.
5. Add fixture-driven Vitest tests and Playwright screenshot flows for the routes and breakpoints.
6. Run typecheck/build, duplicate-ID and missing-target checks, and verify no changes under `bak/`.
