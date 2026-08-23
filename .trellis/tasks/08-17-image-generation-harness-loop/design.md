# Technical Design

## Scope and Boundaries

The image-generation aggregate spans the API task contract, shared generation records, worker orchestration, model adapters, output pipeline, and `dream_web` progress view. The API owns request validation and task creation. The worker owns all five stages, image iterations, output persistence, and quota settlement. Shared code contains records, ports, database/queue/storage adapters, schemas, and event constants used by both applications.

## Input Contract

```json
{"mode":"TEXT_TO_IMAGE","prompt":"生成一张解释 AI 工作流的中文信息图"}
```

`EDIT_IMAGE` requires `targetImageId` and `prompt`; `RECOMPOSE_IMAGE` requires `targetImageId`, `referenceImageId`, and `prompt`. Unknown structured fields are rejected. The model infers image type, industry, display goal, audience, content structure, visual preferences, and loop strategy. Runtime limits such as ratio, resolution, image count, and loop count are server policy values or bounded model output.

## Runtime Flow

1. API validates mode, text, image ownership/MIME/size, reserves quota, creates a task and publishes `GenerationJob`.
2. Worker claims the task and calls `GenerationHarness`.
3. Harness executes the four planning stages in fixed order, validates strict JSON artifacts, persists each artifact, and emits a stage event.
4. `LoopEngine` calls the independent `ImageGenerationModel`, runs ordered evaluators, persists `GenerationIteration`, and either accepts, creates a `RefinementPatch`, or terminates with partial/failure status.
5. Accepted provider images pass through the existing output pipeline; task and quota state are finalized transactionally and progress is available through existing SSE.

## Key Ports and Records

- `GenerationStage<I,O>` and `ArtifactValidator<T>` define stage execution.
- `GenerationHarness` owns trace/stage IDs, retry classification, schema validation, redaction, event emission, and artifact storage.
- `PlanningModel` adapts only the configured multimodal `ChatModel`; no runtime deterministic fallback is allowed.
- `QualityEvaluationModel` adapts the same configured multimodal model with a separate evaluation protocol and receives generated/reference image bytes.
- `ImageGenerationModel` adapts URL/Base64/Data URL provider responses into `ProviderImage`.
- `LoopEngine` consumes `PromptPackage`, `ImageGenerationModel`, and evaluators; it never mutates a Prompt directly.
- `GenerationPlan` stores the four immutable planning artifacts and status.
- `GenerationIteration` stores provider identity, prompt hash, evaluation, refinement, and timing facts.

## Persistence and Events

Add sortable migrations under `dream_service/common/src/main/resources/db/migration` for `generation_plan` and `generation_iteration`, using existing quoted identifiers and JSONB handlers. Add mappers in `common.persistence.generation`. Reuse `GenerationTaskEvent` with stage names: `task.requirement_understood`, `task.structure_planned`, `task.visual_constraints_ready`, `task.prompt_constructed`, `task.generation_started`, `task.evaluation_completed`, `task.refinement_started`, and `task.generation_accepted`.

## Configuration and Security

Use separate `dream-space.ai.planning` and `dream-space.ai.image` configuration properties and qualified beans. Missing image-model configuration is a configuration error, never a fallback to a ChatModel. Validate reference image roles and block untrusted remote URLs using existing upload/storage rules. Redact prompts, credentials, and provider payloads before persistence or logging.

## Compatibility and Rollout

The old direct-generation branch and all generation-module deterministic providers are removed from the active path and source tree. Worker startup requires explicit live model configuration. Manual verification uses real planning, image, and evaluation providers; no WireMock/Mockito external-provider substitutes are maintained. If a provider is unavailable, tasks fail or partially succeed with quota release according to the existing transaction policy.

## Production Completion Design

`docs/design/13-frontend-worker-real-implementation.md` is the authoritative extension for frontend and Worker production completion. Shared WebP encoding belongs in `common`; API upload and Worker output both consume it. Moderation cases, appeals and audit events are additive PostgreSQL facts owned by the API/Worker boundary. Worker health and Micrometer instrumentation are production runtime behavior, not test-only adapters. Visual and E2E checks exercise real HTTP, queue, database and storage boundaries; model-provider checks remain manual against real credentials.
