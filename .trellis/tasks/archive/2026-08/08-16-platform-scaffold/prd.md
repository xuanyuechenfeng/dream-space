# Bootstrap Vue and Spring platform

## Goal

Create the runnable, version-locked platform required by M1: independent Vue 3 + TypeScript + Vite 5 web/admin applications and a Spring Boot 4 multi-module backend with API and worker profiles.

## Requirements

- Create `frontend/web` and `frontend/admin` as separate Vite 5 applications using Vue 3, TypeScript strict mode, Vue Router, Pinia, port `3000`/`3001`, `/api` development proxy, path aliases, and production build scripts.
- Create `backend` Maven modules: `common`, `persistence`, `api`, and `worker`. Use Java 21, Maven Wrapper, Spring Boot `4.0.0`, and Spring AI `2.0.0-M5` from Maven Central.
- Keep module boundaries from `docs/design/01-architecture.md`: common has no Spring beans; persistence has no controllers; API has MVC/health only; worker has no user HTTP controllers.
- Provide API health compatibility endpoint `/health` plus liveness `/health/live` and readiness `/health/ready`. The scaffold readiness endpoint may be local-only until the persistence task wires PostgreSQL, Redis, and object storage checks.
- Provide worker profile startup and an OpenAI-compatible Spring AI `ChatModel` configuration surface. Test a real `ChatModel.call` request against WireMock; no external model connection or secret is allowed.
- Add only non-secret `.env.example` configuration. Leave `bak/` unchanged.

## Acceptance Criteria

- [x] `pnpm --dir frontend/web build` and `pnpm --dir frontend/admin build` succeed with strict TypeScript.
- [x] `backend/mvnw.cmd test` succeeds under JDK 21 and compiles all four modules.
- [x] API starts with profile `api`, exposes all three health endpoints, and worker starts with profile `worker` without exposing user controllers.
- [x] WireMock verifies one OpenAI-compatible `ChatModel.call` using the configured local base URL.
- [x] No credentials, generated dependency caches, or changes beneath `bak/` are committed.
