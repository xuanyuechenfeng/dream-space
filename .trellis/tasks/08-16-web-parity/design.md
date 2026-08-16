# Web parity design

## Application structure

`frontend/web/src` is split into `app`, `layouts`, `features`, `stores`, `api`, `styles`, and `assets`. `main.ts` mounts the app; `router/index.ts` owns route guards; stores own cross-view state; API modules own fetch/SSE and error decoding. Vue SFCs must not construct URLs or mutate persistence directly.

## Route and state model

Use `HomeRedirectView`, `InspirationGalleryView`, `InspirationDetailView`, `LoginView`, and `GenerationWorkspaceView` with the exact paths in `docs/design/02-frontend.md`. `authStore` loads `/auth/session` before protected transitions. `preferencesStore` writes only `dream-space-language` and `dream-space-theme`. `generationStore` is reserved for the generation task and is implemented by the generation task after this shell is ready.

## Visual parity

Migrate the old class names and CSS variables first, then split files without changing computed styles. User tokens include `#f7f8f9`, `#ffffff`, `#17191c`, `#0e8f7c`, `#e5e8eb`; dark tokens and mobile navigation follow the design document. Use Lucide Vue icons and preserve 8px radii, typography, spacing, and modal focus behavior.

## Verification

Stub API fixtures for deterministic screenshots. Compare stable regions only; mask timestamps/random images. Check keyboard focus, reduced-motion, Chinese/English text fit, duplicate IDs, missing targets, and route reloads on desktop/mobile.
