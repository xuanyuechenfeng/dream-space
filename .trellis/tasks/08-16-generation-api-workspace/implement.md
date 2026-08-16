# Implementation checklist

1. Add common generation contracts, validation enums, error codes, and cost calculator.
2. Implement session/draft/task/result MyBatis methods and application services with ownership and quota transactions.
3. Add task event repository, post-commit queue publisher, cancellation conditional update, and SSE emitter service.
4. Add MockMvc/JSON fixture tests for idempotency, quota, cancellation, SSE replay/reconnect, and authorization.
5. Implement web generation store, session sidebar, composer, upload, timeline, preview, retry, cancel, and download.
6. Run backend tests, frontend typecheck/build, mock generation smoke, and desktop/mobile screenshot checks.
