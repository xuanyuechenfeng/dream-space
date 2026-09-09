# Technical Design

## Current State

`GenerationOutputPipeline` currently normalizes the provider image into PNG and
creates a 480-pixel PNG thumbnail. `GenerationService` exposes separate content
and thumbnail URLs, and generation cards prefer the thumbnail. The preview
dialog still opens the content URL. Both endpoints return `private, no-store`,
and the thumbnail read path falls back to `imagePath` when its object key is
missing.

Local storage contains 18 sampled results: originals average 3.1 MiB and peak at
9.3 MiB; PNG thumbnails average 245 KiB and peak at 606 KiB.

## Output Contract

Each new result owns two immutable objects:

- `results/{taskId}/{resultId}.png`: full-resolution original for explicit use.
- `thumbnails/{taskId}/{resultId}-v2.webp`: browser preview.

The preview uses contain-style proportional resizing. Let `longest` be the
larger source dimension and `scale = min(1, 640 / longest)`. Rounded output
dimensions are at least one pixel. The encoder uses WebP quality `0.80`.

The existing `GenerationResult` columns remain authoritative:
`thumbnailObjectKey`, `thumbnailWidth`, `thumbnailHeight`, and
`thumbnailByteSize`. No schema extension is required because object media type
is derived from the versioned object-key extension.

## Worker Changes

Add a proportional preview operation to `WebpImageWriter`. It validates input
and maximum decoded pixels using the existing error contract, avoids upscaling,
and returns encoded bytes plus actual dimensions. Refactor `PngImageWriter` so
the output pipeline can normalize the original without also producing a PNG
thumbnail.

`GenerationOutputPipeline.persistOne` will:

1. Validate and normalize the provider output as the original PNG.
2. Encode a WebP preview locally from the same provider bytes.
3. Upload the original, then the preview.
4. Return result metadata only after both writes succeed.
5. Delete both keys best-effort when either write or later settlement fails.

Preview processing is local CPU work and never invokes an AI client. Conversion
errors retain the existing non-retryable provider-output failure semantics.

## API Changes

Keep the existing URL contract. Thumbnail reads require a nonblank
`thumbnailObjectKey`; they return a dedicated 404 when metadata or storage is
missing. Content reads may retain legacy `imagePath` fallback only for old rows
without object metadata.

Return `Cache-Control: private, max-age=86400` and an ETag derived from the
selected immutable object key and recorded byte size. Honor `If-None-Match` with
a 304 response before reading object bytes where possible. Authorization and
ownership lookup still happens before validator handling.

The admin result endpoints follow the same preview fallback and caching rules.

## Frontend Changes

Remove `thumbnailUrl || contentUrl` from all result presentation paths. A failed
or unavailable preview renders the existing unavailable/error state and never
causes an implicit full-resolution request.

Replace `previewUrl` with a typed preview state containing both URLs and result
metadata. Opening the dialog initially renders only `thumbnailUrl`. Explicit
controls allow the user to request the original in the dialog or download it.
Once explicitly loaded, the existing client image cache may reuse the original
for download during the same or later session.

The cached-image component remains responsible for authenticated fetching and
object-URL cleanup. The cache namespace will be versioned so stale PNG preview
entries do not shadow WebP previews after rollout.

## Historical Backfill

Implement an explicit worker-side maintenance command, disabled by default and
activated by a command property. It scans candidates in stable ID order and
supports batch size, start cursor, dry-run, and maximum item count.

A candidate has a missing thumbnail key, a non-WebP thumbnail, an excessive
preview edge, or invalid preview metadata. For each candidate, the command:

1. Reads the original object; legacy unsafe/unavailable paths are reported and
   skipped.
2. Writes `{resultId}-v2.webp`.
3. Uses a compare-and-set mapper update against the previous thumbnail key.
4. Deletes the new object if the row changed concurrently.
5. Deletes the old thumbnail best-effort only after the database update commits.

The versioned deterministic key and conditional update make reruns idempotent.
Failures are logged per result and do not abort the whole batch.

## Rollout And Rollback

Deploy backend support first, then the frontend, verify new results, and finally
run backfill in bounded batches. Old PNG thumbnails remain readable throughout.
Rollback requires no schema rollback; previous application versions ignore the
WebP extension and storage implementations already return its correct MIME type.

## Observability

Record preview encoding duration, preview byte size, original-to-preview byte
ratio, backfill processed/succeeded/failed/skipped counts, and warnings for
previews larger than 150 KiB. Do not log image bytes or authenticated URLs.
