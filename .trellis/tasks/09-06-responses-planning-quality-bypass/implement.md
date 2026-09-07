# Implementation Plan

1. Add the Responses planning client and its request/response/error contract tests.
2. Replace `ChatPlanningModel`'s Spring AI ChatModel dependency with the client while preserving stage prompts, JSON normalization, repair limits, metrics, and DTO contracts.
3. Update Worker configuration wiring and model-configuration logging to identify the `/responses` endpoint without changing existing credentials or model values.
4. Update `GenerationV2SlotProcessor` so successful iteration 3 skips quality evaluation, emits the quality-skipped event, and continues through moderation, persistence, and settlement.
5. Add or update focused tests for planning migration and quality bypass, including retry and redelivery edge cases.
6. Run `mvn -pl worker -am test`, inspect the diff for secret/config churn, and run the Trellis check workflow before completion.

Rollback points:

- planning migration can be reverted as one client/wiring change without touching database state;
- quality bypass is isolated to the slot loop and can be disabled by restoring the evaluation branch; no schema migration is involved.
