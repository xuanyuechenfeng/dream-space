# HTTP Contract Baseline

The API uses application prefixes: `/dream_web` for user-facing requests and `/manage_web` for management requests. All browser requests use `credentials: "include"`. Source contracts are in `bak/packages/contracts/src/index.ts`; route ownership is in the listed Controller files.

## Health and User Authentication

| Method and path | Auth | Request | Response and source |
| --- | --- | --- | --- |
| `GET /health` | None | None | `{ service, status: "ok", timestamp }`; `bak/apps/api/src/modules/health/health.controller.ts`. |
| `POST /dream_web/auth/codes` | None | `{ phone }` | `{ challengeId, expiresAt, retryAfterSeconds }`; when no real SMS provider is configured, returns `AUTH_CODE_PROVIDER_UNAVAILABLE` and does not create a challenge. |
| `POST /dream_web/auth/login` | None | `{ phone, challengeId, code, version, termsAccepted, privacyAccepted, aiTermsAccepted }` | `AuthSessionResponse`; sets `HttpOnly` `dreamspace_session`, `Path=/`, `SameSite=Lax`, secure only in production. |
| `GET /dream_web/auth/session` | Session cookie optional | None | `{ authenticated: false }` or `{ authenticated: true, user: { id, phoneMasked, createdAt } }`. |
| `POST /dream_web/auth/logout` | Session cookie optional | None | `204`; clears `dreamspace_session`. |

Sources: `bak/apps/api/src/modules/auth/auth.controller.ts`, `auth.service.ts`, `session-cookie.ts`.

## Inspirations and Uploads

| Method and path | Auth | Request | Response |
| --- | --- | --- | --- |
| `GET /dream_web/inspirations?category=&q=` | None | Optional category and query | `{ items: InspirationSummary[], total }`; summary includes `id, slug, title, promptSummary, category, imageUrl, thumbnailUrl, width, height, authorDisplayName, likeCount, modelName, ratio, resolutionLabel, isAiGenerated`. |
| `GET /dream_web/inspirations/:slug` | None | Path slug | Summary plus `prompt, sourceName, sourceUrl, publishedAt`; unpublished/missing returns not found. |
| `POST /dream_web/uploads/references` | User cookie | Multipart field `file` | `{ id, url, filename, mimeType, width, height, byteSize, checksumSha256 }`; accepts one JPG/PNG/WebP file up to 10 MiB. |
| `GET /dream_web/uploads/references/:uploadId/content` | User cookie and ownership | Path ID | Binary WebP with `Content-Type`, `Content-Length`, inline disposition, private cache, and `X-Content-Type-Options: nosniff`. |

Sources: `bak/apps/api/src/modules/inspirations/inspirations.controller.ts`, `uploads/uploads.controller.ts`, `uploads/uploads.service.ts`, and `bak/packages/contracts/src/index.ts`.

## Generation and SSE

| Method and path | Auth | Request | Response |
| --- | --- | --- | --- |
| `GET /dream_web/generation/options` | User cookie | None | `{ modes[], referenceImages:{max,maxBytes,mimeTypes[]}, costPerTask }`. |
| `GET /dream_web/generation/quota` | User cookie | None | `{ total, available, reserved, used, remainingPercent }`. |
| `GET /dream_web/generation/sessions` | User cookie | None | `{ items: [{ id, title, thumbnailUrl, createdAt, updatedAt }] }`. |
| `GET /dream_web/generation/sessions/:sessionId` | User cookie and ownership | Path ID | Session summary plus `draft` and `tasks[]`. |
| `PATCH /dream_web/generation/sessions/:sessionId` | User cookie and ownership | `{ title }` | Updated session detail. |
| `PATCH /dream_web/generation/sessions/:sessionId/draft` | User cookie and ownership | `{ prompt, model, ratio, resolution, imageCount, referenceImageUrls[] }` | Updated session detail. |
| `DELETE /dream_web/generation/sessions/:sessionId` | User cookie and ownership | None | `204`; active sessions are rejected. |
| `POST /dream_web/generation/tasks` | User cookie | `{ idempotencyKey, sessionId?, prompt, model, ratio, resolution, imageCount, referenceImageUrls[] }` | `{ session, task, quota, replayed }`; duplicate key replays only an identical request. |
| `GET /dream_web/generation/tasks/:taskId` | User cookie and ownership | Path ID | `GenerationTaskResponse` with task state, moderation, costs, timestamps, and results. |
| `POST /dream_web/generation/tasks/:taskId/cancel` | User cookie and ownership | None | Updated task response. |
| `GET /dream_web/generation/results/:resultId/content` | User cookie and ownership | Path ID | Binary/redirected result asset. |
| `GET /dream_web/generation/results/:resultId/thumbnail` | User cookie and ownership | Path ID | Binary/redirected thumbnail asset. |
| `GET /dream_web/generation/tasks/:taskId/events` | User cookie and ownership | Optional `Last-Event-ID` header | `text/event-stream`; replays events after the cursor, then polls until terminal state. |

Generation task input limits are idempotency key `8-128` characters matching `[A-Za-z0-9:_-]`, prompt `1-4000`, image count `1-8`, references `0-4`, and only configured ratio/resolution values. Sources: `generation.controller.ts`, `generation.service.ts`, `generation-api.ts`, and `generation-api.test.ts`.

SSE event types are `task.queued`, `task.generating`, `task.retrying`, `task.input.moderated`, `task.output.moderated`, `task.succeeded`, `task.partially_succeeded`, `task.failed`, `task.cancelled`, and `task.dead_lettered`. Each event carries `id, taskId, type, status, createdAt`; event ID is also the SSE `id` field.

## Admin API and Errors

| Method and path | Permission | Request/response |
| --- | --- | --- |
| `POST /manage_web/auth/codes`, `POST /manage_web/auth/login`, `GET /manage_web/auth/session`, `POST /manage_web/auth/logout` | Auth flow | Same challenge/session shape as user auth; login sets/clears `HttpOnly` `dreamspace_admin_session`. |
| `GET /manage_web/tasks` | `tasks:read` | Query `status, model, query, createdFrom, createdTo, page, pageSize`; returns `{ items, total, page, pageSize, pageCount }`. |
| `GET /manage_web/tasks/:taskId` | `tasks:read` | Returns task summary, references, errors/dead letter, and results. |
| `GET /manage_web/tasks/results/:resultId/content` and `/thumbnail` | `tasks:read` | Binary or signed redirect result assets. |
| `GET /manage_web/tasks/reconciliation/runs` | `tasks:read` | `{ items: AdminQuotaReconciliationRun[] }` including findings and `open/repaired/blocked` status. |
| `GET /manage_web/inspirations` | `inspirations:read` | Query `status, category, query, page, pageSize`; returns paginated records. |
| `GET /manage_web/inspirations/:id` | `inspirations:read` | Full `AdminInspirationRecord`. |
| `POST /manage_web/inspirations` and `PATCH /manage_web/inspirations/:id` | `inspirations:write` | `AdminInspirationInput`; returns the saved record. |
| `POST /manage_web/inspirations/:id/publish` and `/unpublish` | `inspirations:write` | `{ updatedAt }`; returns the state-updated record, or `409 OPTIMISTIC_CONFLICT` for a stale version. |

The legacy Nest default error body is `{ statusCode, message, error }`; `message` can be a string, string array, or a domain object containing `code`, `required`, and `available` (for example quota/idempotency errors). Spring adapters must preserve domain fields while mapping to the documented `code/message/details/requestId` envelope in `docs/design/06-contracts-and-security.md`.
