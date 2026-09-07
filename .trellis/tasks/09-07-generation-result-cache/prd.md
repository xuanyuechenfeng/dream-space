# 优化1比1结果展示与客户端图片缓存

## Goal

Improve the generated-image result experience in the web client without changing
generation APIs or server-side billing behavior. Single 1:1 results should use
less vertical space, and already fetched result images should render from a
client-local cache on later visits.

## Requirements

- Optimize the desktop presentation of a single 1:1 generated result so the
  result block is visually smaller while remaining readable and accessible.
- Keep multi-image results and mobile result layouts usable; do not introduce
  horizontal overflow or change result ordering.
- For generated result thumbnails, previews, and downloads, check the browser's
  persistent client cache before making a network request.
- On a cache miss, fetch the image with the existing authenticated request
  behavior, store a successful response locally, and use it for the current
  render.
- Cache failures must not make the result page unusable; fall back to the
  original asset URL and preserve existing error handling.
- Do not change server APIs, result contracts, quota calculations, or billing
  state.

## Acceptance Criteria

- [x] A desktop single 1:1 result is rendered within a reduced, stable maximum
      width (target: 480px), while mobile remains full-width within its parent.
- [x] Multi-image result grids retain their existing two-column desktop and
      one-column mobile behavior.
- [x] The first successful load of a generated image writes it to a named
      browser Cache Storage entry; a subsequent render reads that entry without
      issuing another image request.
- [x] Preview and download paths use the same cache lookup and do not bypass it.
- [x] Cache Storage unavailability, failed requests, and stale cache entries
      fall back safely without uncaught UI errors.
- [x] Unit tests cover cache hit, cache miss/write, and cache-unavailable paths;
      the frontend typecheck/build and relevant tests pass.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
