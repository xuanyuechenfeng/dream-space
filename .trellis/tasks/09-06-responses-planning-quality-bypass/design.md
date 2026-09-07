# Technical Design

## Scope and Boundaries

The public `PlanningModel` interface and all planning stage consumers remain unchanged. The current Spring AI `ChatModel` is retained for moderation and quality evaluation. A Worker-local `ResponsesPlanningClient` owns the Responses HTTP contract and is injected only into `ChatPlanningModel`.

This keeps the protocol migration isolated from the harness, preflight persistence, image generation, and downstream DTO validation.

## Planning Request Flow

```text
PlanningModel stage
  -> ChatPlanningModel
  -> ResponsesPlanningClient
  -> POST {baseUrl}/responses
  -> output[] message/content[] output_text
  -> existing JSON tree normalization and DTO validation
```

The client uses the existing Java HTTP client pattern already used by the image adapter. It receives the configured base URL, API key, model, and timeout, normalizes the base URL once, and appends `/responses`. No Chat Completions fallback is added.

Request mapping:

- system prompt -> top-level `instructions`;
- user text -> an `input` message containing `input_text`;
- loaded reference bytes -> `input_image` with a `data:<mime>;base64,...` URL;
- JSON mode -> `text.format.type = json_object`;
- `store = false`;
- omit `temperature` for the Responses planning request to avoid reasoning-model/provider compatibility failures.

The repair request uses the same endpoint and JSON format with a text-only input. The client returns extracted text plus HTTP status, provider request id, and bounded response metadata needed by logging and error mapping.

## Response and Error Contract

The extractor searches the typed Responses shape for `output` items with `type=message`, then `content` items with `type=output_text`. It may accept one documented top-level `output_text` compatibility shape, but it must not use arbitrary string scraping.

The existing `ChatPlanningModel` retains complete-tree parsing, documented compatibility normalization, required-field validation, and one syntax-only repair. Client errors are mapped before DTO parsing:

- 2xx with missing/blank text: retryable empty-response error;
- 408/425/429/5xx and network/timeouts: retryable planning-unavailable error;
- 401/403: non-retryable unauthorized error;
- other 4xx: non-retryable provider-rejected error.

Logs include stage, model, endpoint, HTTP status, request id, response length, response shape, and bounded preview. Keys, image payloads, and complete sensitive prompts are never logged.

## Third Quality Iteration

`GenerationV2SlotProcessor.processSlot` keeps the existing quality loop. After a successful provider image is produced:

1. Iterations below 3 continue to evaluate and may carry a refinement patch into the next generation.
2. Iteration 3 records a `task.slot.quality_skipped` event, bypasses `QualityEvaluationModel.evaluate`, and proceeds with the candidate image.
3. Output moderation, image persistence, slot settlement, quota accounting, and cleanup on ownership loss remain mandatory.

This rule is keyed to quality-loop `iteration`, not `GenerationAttempt.number()` or Redis delivery count. A provider failure on iteration 3 still follows existing retry/dead-letter handling.

## Configuration and Compatibility

The existing OpenAI base URL, API key, model, and timeout properties remain the source of configuration. `GenerationWorkerConfiguration` adds the Responses client bean and wires it into planning; detection and evaluation continue to use the existing OpenAI ChatModel bean.

No database migration is required. The quality bypass is observable through the existing task-event mechanism and a bounded structured log.

## Verification Strategy

- client unit tests with an in-process HTTP server verify URL, headers, JSON request mapping, image data URLs, output extraction, repair, and status classification;
- planning tests verify all stage calls use the client and preserve existing DTO validation;
- slot processor tests verify third-iteration bypass, continued moderation/persistence/settlement, and unchanged first/second iteration behavior;
- focused Maven worker tests, followed by the module test suite, are the implementation gate.
