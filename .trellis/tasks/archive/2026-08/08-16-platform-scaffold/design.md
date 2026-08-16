# Platform Scaffold Design

## Frontend

`frontend/web` and `frontend/admin` are independent package roots so their deployments and ports remain independent. Each application owns its router and Pinia instance, while `frontend/tsconfig.base.json` keeps strict compiler options aligned. Route components are deliberately thin placeholders in this milestone; the parity tasks replace them with the migrated layouts and features.

Vite development proxies `/api` to `http://localhost:4000` and removes the prefix before forwarding. Browser clients use relative `/api` paths, avoiding hard-coded development origins.

## Backend

The Maven parent imports Spring Boot dependency management and Spring AI BOM. `common` contains only shared types. `persistence` depends on common but does not yet open database connections. `api` and `worker` are independent Boot executable modules. Component scanning remains inside each application module.

The `api` profile starts MVC endpoints only. The `worker` profile uses `WebApplicationType.NONE`, runs an application runner, and has no MVC controllers. Health readiness is intentionally a local bootstrap probe; storage-aware readiness is added with the persistence/infrastructure task.

## Spring AI Test Boundary

The worker imports `spring-ai-starter-model-openai` and exposes only standard environment-backed properties: `OPENAI_BASE_URL`, `OPENAI_API_KEY`, and `OPENAI_MODEL`. The test starts WireMock on an ephemeral local port, stubs `/v1/chat/completions`, creates a Spring context with those test-only properties, and calls `ChatModel.call`. No provider key is written to disk.

## Rollback

Remove `frontend/` and `backend/`; no database migrations, Redis keys, or legacy files are changed by this task.
