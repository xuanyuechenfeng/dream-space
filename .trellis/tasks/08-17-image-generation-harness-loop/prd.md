# Implement staged image generation harness and loop

## Goal

Implement the new staged image-generation aggregate for development use. Every generation task must execute requirement understanding, content structure planning, visual constraints, Prompt construction, image-model invocation, and bounded evaluation/refinement through a Harness + Loop Engineering workflow.

## Requirements

- Accept only `TEXT_TO_IMAGE`, `EDIT_IMAGE`, and `RECOMPOSE_IMAGE` input modes.
- Forbid frontend submission of image type, industry, display goal, audience, visual preferences, and loop policy. The planning model infers these fields from the user prompt and supplied images.
- Preserve target/reference image roles: edit modifies image A; recompose modifies image A using image B as a reference.
- Persist versioned, redacted artifacts for the four planning outputs and iteration/evaluation facts.
- Use an independently configured multimodal planning `ChatModel` and image-generation model; never use the planning ChatModel as an image provider.
- Enforce stage ordering, strict JSON/schema/business validation, timeout/retry/idempotency/budget controls, stage events, and prompt redaction in the Harness.
- Evaluate generated images for technical, structure, text, visual, and policy constraints; refine only with bounded, repairable patches and retain partial-success semantics.
- Reuse existing quota, queue, output pipeline, object storage, PostgreSQL, Redis, and SSE contracts where possible without a legacy direct-generation branch.

## Acceptance Criteria

- [ ] API accepts and validates the three input modes and rejects inferred design fields from client payloads.
- [ ] A task produces queryable RequirementBrief, StructurePlan, VisualSpec, and PromptPackage artifacts or a clear stage failure reason.
- [ ] Harness records ordered stage events, hashes/redacted summaries, retry and timeout outcomes, and an idempotent task/iteration key.
- [ ] Planning and image provider configurations are independent, including credentials, endpoint, model, and timeout.
- [ ] Loop stops on an accepted evaluation, applies bounded refinement when repairable, and reports partial success or failure at the configured limit.
- [ ] Quota settlement, output processing, SSE progress, and task terminal states remain consistent on success and every failure path.
- [ ] Unit, provider-contract, persistence/integration, and API contract tests cover the new flow; existing regression tests remain green.
- [ ] No changes are made under `bak/`.

## Constraints

- Do not retain compatibility with the old direct-generation path; this is a development-stage replacement.
- Do not invent facts, numbers, brands, or protected elements from reference images.
- Keep prompt text and credentials out of logs, events, API responses, and persisted artifacts; persist hashes and redacted summaries only.
- Respect existing backend module boundaries and database migration conventions.

## Extended Production Scope

The implementation must also complete the nine production gaps defined by `docs/design/13-frontend-worker-real-implementation.md`: the four `dream_web` items F-01 through F-04 and the five Worker/model items W-01 through W-05.

- Reference uploads and Worker output must share a mandatory real WebP writer. Missing codec support is an explicit failure, never an original-image fallback.
- End-to-end and visual acceptance must use the real API, PostgreSQL, Redis, object storage, Worker and model providers. Frontend network fixtures cannot stand in for generation results.
- Worker readiness and metrics must cover model providers, queue, image processing, object cleanup, dead letters and quota reconciliation.
- Model moderation decisions must enter a persistent operator review, appeal and immutable audit workflow.
- External-provider WireMock, Mockito stubs, deterministic providers and fixed success payloads are prohibited.
- `bak/` remains immutable and is only a visual/behavioral reference.

## Extended Acceptance Criteria

- [ ] F-01 through F-04 and W-01 through W-05 in design document 13 have production implementation paths and verifiable acceptance artifacts.
- [ ] Real-provider verification failures are reported as failures; no mock fallback is enabled.
- [ ] PostgreSQL, Redis, S3 or any required model outage makes readiness fail.
- [ ] Metrics contain no prompt text, credentials, user phone, image URL or full provider request ID.
