# Implementation checklist

1. Add common auth principals, cookie names, error codes, validators, and request-id filter.
2. Add user/admin auth services and MyBatis methods for code, session, agreement, and active-admin lookup.
3. Add Spring MVC controllers/advice and explicit CORS/CSRF/session configuration.
4. Add inspiration query/detail services and admin-independent published filtering.
5. Add upload validation, WebP normalization, ownership checks, and resource streaming/signed URL response.
6. Add MockMvc contract tests for success, expiry, replay, agreement, 401/403, published filtering, and upload failures.
7. Run API/persistence Maven tests, credential scan, `git diff --check`, and verify `bak/` is unchanged.
