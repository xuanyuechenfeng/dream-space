# Technical Design

## Boundaries

The change is limited to `dream_web`. Generated result metadata and server
endpoints remain unchanged. Presentation changes live in the generation feature
styles; cache behavior is isolated in a reusable image utility and component.

## Data Flow

1. A result component receives the resolved API asset URL.
2. It asks `imageCache` for a blob URL. The utility opens a versioned
   Cache Storage bucket, returns a cached response when present, or fetches the
   URL with `credentials: include`, stores a successful clone, and returns the
   response blob.
3. The component owns and revokes the generated object URL when its source
   changes or it unmounts. If Cache Storage is unavailable or a request fails,
   it uses the original URL so the browser's normal image behavior remains the
   fallback.
4. Download uses the same blob lookup, preventing a second request after the
   image has already been loaded. Full-size preview remains sourced from the
   content URL while result cards use the server-provided thumbnail URL.

## Compatibility and Safety

- Cache keys are the exact resolved asset URLs, so immutable result URLs remain
  isolated and do not affect API calls.
- Only successful responses are inserted. No credentials or response metadata
  are serialized into local storage.
- Cache Storage is feature-detected for browsers, tests, and private browsing
  environments where it may be unavailable.
- Object URLs are revoked to avoid accumulating blob memory during navigation.

## Layout Decision

Single-result output is capped at 480px on desktop, with the existing responsive
full-width behavior below the mobile breakpoint. Multi-result grids keep their
existing 600px/two-column desktop presentation.
