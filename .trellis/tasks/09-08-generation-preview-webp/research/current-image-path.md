# Current generated-image path

## Findings

- Result cards already prefer `thumbnailUrl`, but their fallback is the original
  `contentUrl`.
- The full-screen preview always uses `contentUrl`.
- Worker thumbnails are lossless PNG with a 480-pixel maximum width rather than
  a longest-edge constraint.
- API result binaries use `Cache-Control: private, no-store`.
- Thumbnail service reads fall back to `imagePath`, which can resolve to the
  original when preview metadata is unavailable.
- Local sample: 18 originals total 54.81 MiB, average 3.1 MiB, maximum 9.3 MiB.
  Corresponding thumbnails total 4.32 MiB, average 245 KiB, maximum 606 KiB.
- `WebpImageWriter`, object-key support for `.webp`, and MIME detection already
  exist and can be reused without a new codec dependency.

## Decision

Generate a 640-pixel longest-edge WebP preview locally, prohibit implicit
original fallback, and reserve original fetches for explicit user actions.

## Preview Measurement

The implemented encoder was run read-only against all 18 locally stored result
images using a 640-pixel longest edge and quality 0.80. The previews totaled
686,812 bytes, averaged 38,156 bytes (37.3 KiB), and peaked at 63,166 bytes
(61.7 KiB). No sample exceeded the 150-KiB warning threshold. Preview bytes
were 1.20% of the 57,473,587 original bytes.
