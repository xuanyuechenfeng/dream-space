# Authentication and inspiration API design

## Module boundaries

`backend/api` owns HTTP/session/RBAC orchestration. `backend/persistence` owns MyBatis records and object storage. Controllers bind and authorize only; application services own transactions; upload processing is an explicit image pipeline.

## Authentication flow

1. `POST /auth/codes` validates a normalized phone, applies rate limits, hashes a generated code, and stores an expiring `VerificationCode`.
2. `POST /auth/login` verifies the code atomically, verifies the three agreement flags, upserts `User`, creates a random session token, stores only its SHA-256 hash in `UserSession`, and sets an HttpOnly cookie.
3. `GET /auth/session` loads a non-expired session; `POST /auth/logout` deletes/invalidate it. Admin endpoints use the same flow with separate tables and cookie name.
4. A request filter resolves the user/admin principal before controllers. Admin permissions are checked by a method guard, not by route naming or Vue state.

## Inspiration and upload flow

`GET /inspirations` queries only `status = PUBLISHED`, applies category/search/page filters, and returns the old list envelope. `GET /inspirations/{slug}` returns the published record or `NOT_FOUND`.

`POST /uploads/references` streams into a bounded temporary file, checks magic bytes and decoded dimensions, converts to WebP, writes an approved `references/<id>/<file>.webp` key atomically, then persists `ReferenceUpload`. Any later failure deletes the object. Content reads first verify `userId` ownership and then stream or issue a short-lived signed URL.

## Security and error mapping

Use `SameSite=Lax`, `Secure` in production, explicit CORS origins, Origin/Referer and CSRF checks for state-changing requests, and a single error advice that emits `code/message/details/requestId`. Never echo codeHash, session token, object key outside the controlled content endpoint, or complete phone values.

## Transaction boundaries

- Code consumption, agreement acceptance, user upsert, and session creation are one transaction.
- Inspiration reads are read-only.
- Upload object publication and metadata persistence use a compensating delete; object storage is not part of the database transaction.
- Admin publish/unpublish uses an optimistic `updatedAt` check to prevent overwriting another operator.
