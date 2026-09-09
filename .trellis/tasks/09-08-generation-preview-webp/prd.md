# Optimize generated-result preview loading

## Goal

Reduce the time and bandwidth required to open generation sessions by serving a
small WebP preview for generated-result presentation while retaining the
original image for explicit full-resolution viewing and download.

## Requirements

- Call the image-generation model exactly once per generated result. Preview
  creation must be local post-processing of that result.
- Keep the original generated image unchanged in the existing result object and
  content endpoint.
- Generate a proportional, uncropped WebP preview whose longest edge is at most
  640 pixels and never upscale a smaller source.
- Use the preview for generation result cards, collection slots, session-history
  thumbnails, and the default preview dialog.
- Fetch the original only after an explicit "view original" or download action.
- A missing or invalid preview must not silently fall back to the original image.
- Preserve ownership and authentication checks for both preview and original
  endpoints.
- Give immutable generated assets useful private browser caching while avoiding
  cross-user public caching.
- Provide an explicit, restartable process for converting existing PNG or
  missing previews to the new WebP representation without blocking API startup.
- Preserve existing YAML API URLs, keys, and model values.

## Acceptance Criteria

- [x] Newly generated results store the original object and a valid WebP preview
      object with accurate preview dimensions and byte size.
- [x] Preview generation preserves the entire image, caps the longest edge at
      640 pixels, and does not upscale smaller images.
- [x] Opening or revisiting a generation page issues no result `/content`
      requests until the user explicitly requests the original or downloads it.
- [x] The normal preview dialog displays the preview immediately and provides
      explicit original-view and download actions.
- [x] A missing preview returns a dedicated not-found response instead of the
      original bytes.
- [x] Generated image responses use private cache headers and validators that
      allow repeat requests to avoid retransferring unchanged bytes.
- [x] Existing result rows can be migrated in bounded, idempotent batches; a
      failed row is reported without preventing later rows from being processed.
- [x] Unit/integration tests cover image conversion, storage cleanup, API
      fallback removal, frontend URL selection, and backfill idempotency.
- [x] Backend tests and frontend typecheck, build, and relevant unit tests pass.

## Out of Scope

- Changing the image-generation provider or making a second model call.
- Changing quota, billing, moderation, or generation-settlement behavior.
- Converting original result files to WebP or reducing original resolution.
- Introducing a CDN or changing local/SFTP storage providers.
