# Migrate planning model to Responses API and bypass third quality evaluation

## Requirements

1. The Worker planning model must call `POST /v1/responses` instead of Spring AI's `POST /v1/chat/completions` path.
2. Existing planning stages and the `PlanningModel` contract remain stable: requirement understanding, structure planning, visual constraints, prompt construction, collection planning, JSON validation, and one syntax-only repair request must continue to work.
3. Text and reference-image inputs must use the Responses API input-item contract. Structured JSON output must use `text.format`; the request must not depend on Chat Completions' `response_format`.
4. Planning provider failures (HTTP 429/5xx, timeout, connection failure, empty response) remain retryable; authorization and invalid-request failures remain non-retryable. Provider failures must not be mislabeled as invalid model output.
5. Only the planning model migrates in this task. Content moderation and quality evaluation may continue using their existing Chat Completions client.
6. For each image slot, the third quality-loop image generation (`iteration == 3`) must skip quality evaluation and accept the generated image for the existing output moderation, persistence, and settlement flow. Queue delivery retries are unchanged.

## Acceptance Criteria

- Planning requests target the configured base URL with `/responses` and include `instructions`, Responses `input` items, `text.format`, and `store: false`.
- Planning text-only repair and multimodal requests both parse standard Responses output items into the existing DTO pipeline.
- At most one JSON syntax repair request is sent, and it uses the Responses endpoint.
- Unit tests cover request mapping, response extraction, retry/error classification, and malformed/empty Responses payloads.
- When iterations 1 and 2 are rejected but repairable, iteration 3 does not invoke `QualityEvaluationModel.evaluate`; the image still reaches output moderation, persistence, and settlement.
- Existing quality pass, provider failure, slot isolation, and queue redelivery behavior remain unchanged.
- `mvn -pl worker -am test` passes, and configuration/YAML test data remains unchanged except for implementation-required wiring.

## Constraints

- Preserve the existing API key, model, and Base URL test values in YAML.
- Do not add a Chat Completions fallback for planning; protocol behavior must be deterministic.
- Keep the public planning DTOs and persistence schema unchanged.
