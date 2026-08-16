# Implementation checklist

1. Map every baseline contract and design acceptance criterion to a stable test id.
2. Complete backend unit, MockMvc, WireMock, PostgreSQL, Redis, S3/local storage, and migration suites.
3. Complete frontend Vitest, Playwright journey, accessibility, DOM-target, localization, theme, and overflow audits.
4. Generate and review deterministic visual baselines for all required routes/states/viewports.
5. Add CI jobs, reports, traces, credential scan, and `bak/` immutability gate.
6. Run the complete matrix twice to prove repeatability and record results in the task notes.
