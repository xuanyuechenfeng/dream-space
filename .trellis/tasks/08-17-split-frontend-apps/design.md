# Technical Design

## Target layout

```text
dream_web/                         # standalone public Vue application
  package.json
  pnpm-lock.yaml
  playwright.config.ts
  e2e/
manage_web/                       # standalone operations Vue application
  package.json
  pnpm-lock.yaml
  playwright.config.ts
  e2e/
```

The existing application directories are moved without changing source imports or visual assets. The former `frontend/` package is removed because it only orchestrated two apps and held shared E2E tooling.

## Dependency and test isolation

Each project owns its runtime, build, unit-test, and Playwright dependencies. Its Playwright config starts only its own Vite server and uses the existing API fixtures and screenshot baselines. The two projects therefore have no package-manager workspace coupling.

## Compatibility

Vite ports remain `3000` for web and `3001` for admin. API proxy behavior, strict TypeScript settings, path aliases, routes, and asset paths remain unchanged. CI runs the same gates independently for both projects and uploads both reports.
