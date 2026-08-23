# Implementation Plan

## Ordered Work Packages

1. Freeze the three-mode request/response/event contracts, inferred-field rules, error codes, and separate planning/image configuration properties.
2. Add `GenerationPlan` and `GenerationIteration` migrations, records, enum mappings, mappers, and repository tests.
3. Implement the Harness stage protocol, stage context, strict artifact validation, redaction/hash utilities, retry policy, and artifact/event persistence.
4. Implement requirement understanding, content structure planning, visual constraints, and Prompt construction stages with the configured multimodal ChatModel and object-storage-backed image media.
5. Implement the independent `ImageGenerationModel` port and OpenAI-compatible adapter with URL/Base64/Data URL normalization and provider error mapping.
6. Implement technical checks plus real multimodal structure/text/visual/policy evaluation, `EvaluationReport`, `RefinementPatch`, and bounded Loop Engine behavior.
7. Replace worker direct generation with the Harness + Loop flow, including mode-specific image roles, iteration idempotency, output pipeline reuse, events, and quota settlement.
8. Extend API endpoints/SSE and `dream_web` to submit only mode-specific user input and display stage/iteration progress and artifacts.
9. Remove external-provider WireMock/Mockito substitutes and perform manual real-provider verification; retain only non-provider regression checks.

## Dependency and Validation Gates

- Work package 1 must pass API contract tests before package 2.
- Packages 2-5 must compile and pass focused unit/provider tests before worker integration.
- Package 7 requires migration, mapper, Harness, provider, and Loop tests green.
- Package 8 requires API/SSE contract tests and a browser smoke test at desktop and mobile widths.
- Package 9 is the final full-scope gate: `git diff --check`, Maven tests for all modules, frontend type-check/build, and security assertions for redaction, ownership, SSRF, and inferred-field rejection.

## Rollback Points

- Before package 2: revert contract/config changes only.
- Before package 7: keep new components behind deterministic worker wiring while fixing provider/persistence defects.
- Before package 8: worker can continue with API contract tests and deterministic provider; no database destructive rollback is allowed.
- Database changes are additive and use new migrations; never edit an applied migration.

## Execution Status

- [x] Contract/config foundation and three-mode frontend input.
- [x] Plan/iteration migration, records, mappers, and plan endpoint.
- [x] Harness, four ordered stages, real multimodal planning adapter with object-storage-backed media; deterministic planning adapter deleted.
- [x] Independent OpenAI-compatible image model adapter with input-image bytes, output normalization and SSRF checks; deterministic image provider deleted.
- [x] Real multimodal quality evaluator, bounded Loop Engine, evaluation report, refinement patch, and iteration events; deterministic moderator/evaluation paths deleted.
- [x] Worker integration, quota/output reuse, SSE stage events, and frontend timeline.
- [ ] Remove WireMock/provider-substitute tests and dependencies; code removal is complete, manual real-provider verification still requires provider credentials.

## Production Completion Work Packages

10. Extract a mandatory shared WebP writer and use it from API reference uploads and Worker output processing.
11. Add model readiness, S3 validation and Micrometer metrics/alerts for queue, attempts, dead letters, providers, image processing, cleanup and reconciliation.
12. Add persistent moderation review cases, appeals and immutable audit events with RBAC-protected API operations.
13. Replace inspiration-detail URL/local-only generation handoff with real upload IDs and clearly disable controls without backend contracts.
14. Add real-infrastructure environment/test entry points, visual baseline matrices and a real-provider manual acceptance record.

## Production Completion Status

- [x] Shared mandatory WebP writer used by API and Worker.
- [x] Worker readiness, S3 validation, metrics and alert rules.
- [x] Moderation queue, appeal and audit persistence/API workflow.
- [x] Frontend real reference-image handoff and non-fake unavailable states.
- [x] Real infrastructure and visual acceptance entry points; execution remains environment-gated.

## Verification Record

- JDK 21 `mvn -q test`: passed for common, API and Worker.
- JDK 21 `mvn -q -DskipTests package`: passed.
- `dream_web` and `manage_web` type-check/build: passed.
- Playwright no longer intercepts API requests. Suites run only with `RUN_REAL_E2E=1`; without real services, 18 viewport scenarios are explicitly skipped.
- Worker startup with complete but unreachable live dependency configuration: main HTTP port disabled, management port `4010` started, readiness returned HTTP 503/DOWN, Prometheus returned HTTP 200.
- PostgreSQL 17, Redis 8 and MinIO/S3 Testcontainer contracts are present and use real services; Docker-unavailable developer runs skip them, while CI fails when Docker is unavailable.
- Real planning/evaluation/image-provider manual verification is not marked complete because no provider endpoint, model names or credentials are available in this workspace.
