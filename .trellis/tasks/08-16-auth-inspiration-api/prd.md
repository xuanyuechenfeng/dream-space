# Implement authentication and inspiration APIs

## Goal

Implement user/admin authentication, RBAC, inspiration APIs, and upload contracts.

## Requirements

- Implement separate user and administrator verification-code sessions. Store only code/session hashes, enforce expiry, attempt limits, logout, and cookie isolation.
- Implement user agreement acceptance for the current terms/privacy/AI version and reject login until all three flags are accepted.
- Implement `AdminRole` authorization (`VIEWER`, `OPERATOR`, `ADMIN`) in the server guard; UI visibility is not an authorization boundary.
- Implement public inspiration list/detail endpoints with published-only filtering, category/search/pagination, slug lookup, and stable JSON contracts from `docs/migration-baselines/http-contracts.md`.
- Implement authenticated reference upload/content endpoints with magic-byte validation, 10 MB/40 MP limits, WebP normalization, ownership checks, and object-key policy.
- Preserve the route, response, error-code, cookie, CORS, and redaction contracts in `docs/design/03-backend-api.md` and `docs/design/06-contracts-and-security.md`.

## Acceptance Criteria

- [ ] User code request/login/expiry/retry/logout pass MockMvc contract tests; expired or consumed codes cannot authenticate.
- [ ] Admin sessions use a distinct cookie and cannot be accessed with a user cookie; VIEWER writes return 403.
- [ ] Missing agreement acceptance returns `AUTH_AGREEMENT_REQUIRED`; accepted version is persisted once per user/version.
- [ ] Inspiration list/detail returns only `PUBLISHED` rows and preserves category, slug, image, prompt, and pagination fields.
- [ ] Invalid MIME, magic bytes, oversized, over-pixel, path-traversal, and cross-user upload access are rejected with documented errors.
- [ ] No token, verification code, phone number, or secret appears in logs, fixtures, or responses; API and persistence tests pass.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
