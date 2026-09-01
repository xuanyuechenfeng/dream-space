# Generation new-session state research

## Current behavior

- `dream_web/src/router/index.ts` maps both `/generate` and `/generate/:sessionId` to the same `GenerationWorkspaceView` and passes route params as props.
- `useGenerationStore.load(sessionId)` loads options, quota and session summaries. When `sessionId` is absent it currently calls `openSession(sessions[0].id)`, so a base-route visit displays a historical conversation.
- `GenerationWorkspaceView.newSession()` currently calls `generation.createSession()` and navigates to the returned ID. This persists an empty history row before the user submits a prompt.
- The prop watcher only handles a truthy ID. Navigating from `/generate/:id` to `/generate` leaves the previously active store session rendered because the `undefined` branch performs no reset.
- `restoreIntent()` pre-creates a session when no active session exists, even when the restored intent is only a draft and `submitOnRestore` is false.
- `GenerationWorkspaceView` already renders the desired empty-state title, copy, three starter prompts and composer whenever there is no active session/task detail. The missing behavior is reaching and retaining that state.

## Existing atomic submission contract

`useGenerationStore.submit()` already reads `active.value?.id` and conditionally spreads `sessionId` into the task request. With no active session, it sends no ID. `GenerationService.submit` accepts a nullable session ID and creates the session in the same transaction as the first task. The response contains the new session and task, and the store inserts one summary before the view replaces the URL with `/generate/:sessionId`.

The existing test `submits without creating an empty session first` proves the store does not call `api.generation.createSession()` and omits `sessionId`. This behavior should be extended, not replaced with a separate two-request flow.

## Recommended store boundary

Add a synchronous action such as `resetSession()` that:

1. closes all active `EventSource` connections;
2. sets `active` to `null`;
3. restores `draft` from `blankDraft()` and reapplies loaded options;
4. clears per-task event cursors;
5. preserves `sessions`, `options` and `quota`.

Use this action in `load()` when no `sessionId` is supplied and in view-level new-session/base-route transitions. Keeping the state transition in the store prevents the component from independently mutating `active`, `draft` and SSE state.

`createSession()` can remain exposed for compatibility, but the standard new-conversation UI and restored unauthenticated intent must no longer call it.

## Route and view contract

- Initial `/generate`: call `load(undefined)`; after remote read data arrives, call the reset action instead of opening `sessions[0]`.
- Initial `/generate/:id`: call `load(id)` and open only that ID.
- History click: retain the current explicit `openSession(id)` plus route replacement.
- New-session click: reset locally, close the mobile drawer, clear local view errors, and route to `/generate`. If already at the base route, reset must still run.
- Prop watcher: when ID becomes truthy, open it; when it becomes undefined, reset. This is required because Vue Router reuses the same component instance between both route records.
- First submit: retain the existing submit call and replace the URL only after a valid response.
- Submit failure: remain on `/generate` with the draft intact.

Avoid making the watcher immediate because `onMounted(load)` already owns the initial fetch. An immediate watcher would duplicate the initial session request.

## Restored authentication intent

The inspiration flow writes `dream-space-restored-auth-intent` and returns the user to `/generate`. After parsing and optionally uploading its reference image, assign the merged draft and apply generation options.

- If an explicit active session exists, retain `saveDraft()`.
- If no active session exists, do not call `createSession()` or `saveDraft()`; keep the draft in Pinia.
- If `submitOnRestore` is true, immediately call `submit()`. Since `active` remains null, the request follows the atomic creation contract and the caller routes to the returned session ID.

The upload path is compatible with a local draft: `uploadReference` persists the binary first, while `saveDraft()` is already a no-op when `active` is null. The returned upload ID remains in the task request.

## Regression tests

Extend `dream_web/src/stores/generation.test.ts` with focused cases:

- mock non-empty `sessions()` and assert `load()` leaves `active` null and never calls `session()` or `createSession()`;
- call `load("session-1")` and assert it opens exactly that session;
- open a session, mutate/observe its draft and event connection, call the reset action, then assert `active` is null, the standard draft is restored, connections are closed and session summaries remain;
- submit from the reset state and assert no `sessionId` and no `createSession()` call;
- retain the existing idempotency retry and multiple-SSE tests to guard unrelated submission behavior.

The route watcher and `restoreIntent()` live in the SFC and currently have no component test harness. Cover them through build/typecheck plus a browser scenario that navigates `/generate/:id -> /generate`, clicks new session with existing history, and restores a draft without observing a session POST.

## Risks

- Reset before options load must still call `applyOptions()` after options arrive so disabled resolution fallbacks remain valid.
- A stale active session must not flash while a base-route load is pending; reset at the start or ensure the component remains in loading state until the post-load reset.
- Closing events on reset is necessary to stop old task updates from changing the empty state.
- Route changes during an in-flight `openSession()` can race. The current code already has this risk; avoid broad cancellation work unless verification reproduces it.
- Do not delete the server create-session endpoint or alter GenerationService transactionality as part of this UI behavior change.
