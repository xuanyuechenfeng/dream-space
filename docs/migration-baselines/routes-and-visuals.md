# Route and Visual Baseline

## User Application

| Route | Source | Required states |
| --- | --- | --- |
| `/` | `bak/apps/web/app/page.tsx` | Redirects to `/inspiration`. |
| `/inspiration` | `bak/apps/web/app/inspiration/page.tsx`, `bak/apps/web/components/inspiration/inspiration-gallery.tsx` | Loading, populated, empty, request error, category filter, debounced search, search history, refreshed random ordering. |
| `/inspiration/:slug` | `bak/apps/web/app/inspiration/[slug]/page.tsx`, `bak/apps/web/components/inspiration/inspiration-detail.tsx` | Loading, not found, request error, copy success/error, like/follow state, unauthenticated auth intent, composer prefill. |
| `/login` | `bak/apps/web/app/login/page.tsx`, `bak/apps/web/components/auth/login-screen.tsx` | Phone entry, code countdown, invalid code, agreement validation, protocol modal, return-to action, mobile layout. |
| `/generate` | `bak/apps/web/app/generate/page.tsx`, `bak/apps/web/components/generation/generation-workspace.tsx` | Empty draft, options loading/error, upload validation, insufficient quota, submitting, queue, generation, success, partial success, failure, cancellation. |
| `/generate/:sessionId` | `bak/apps/web/app/generate/[sessionId]/page.tsx` | Session loading/not found, draft restore, rename, delete confirmation, task timeline, SSE reconnect, result preview/download. |

## Admin Application

| Route | Source | Required states |
| --- | --- | --- |
| `/` | `bak/apps/admin/app/page.tsx` | Redirects to `/tasks`. |
| `/login` | `bak/apps/admin/app/login/page.tsx`, `bak/apps/admin/components/admin-login.tsx` | Phone entry, countdown, invalid code, unauthorized user, session error, mobile layout. |
| `/tasks` | `bak/apps/admin/app/(console)/tasks/page.tsx`, `bak/apps/admin/components/admin-tasks.tsx` | Loading, filters, pagination, empty, request error, task detail drawer, result assets, reconciliation strip, viewer read-only state. |
| `/inspirations` | `bak/apps/admin/app/(console)/inspirations/page.tsx`, `bak/apps/admin/components/admin-inspirations.tsx` | Loading, filters, empty, editor drawer, draft/published/archived status, validation error, operator/admin write actions, viewer read-only state. |

## Visual Tokens and Breakpoints

The new Vue CSS must first port the existing tokens from `bak/apps/web/app/globals.css` and `bak/apps/admin/app/globals.css`, rather than introduce a second visual system.

| Surface | Key values |
| --- | --- |
| Web light | `#f7f8f9` background, `#ffffff` surface, `#17191c` text, `#6f747c` muted, `#e5e8eb` border, `#0e8f7c` primary, `#d04444` danger, `#b26a16` warning. |
| Web dark | `#0f1012` background, `#191b1e` surface, `#24272b` strong surface, `#f3f5f6` text, `#a5abb1` muted, `#30343a` border, `#183a35` primary soft. |
| Admin | `#f4f6f7` background, `#ffffff` surface, `#eef1f2` muted surface, `#dfe4e6` border, `#1b1f23` text, `#687178` muted, `#087f6d` accent, `#bb3e46` danger, `#9a6500` warning. |

Required structural tokens are web `72px` navigation, `280px` session rail, and `8px` radius; admin sidebar is `236px`, collapsed to `72px`.

| Viewport rule | Expected behavior | Source |
| --- | --- | --- |
| `max-width: 1599px` and `1399px` | Hide/reduce web secondary controls as defined by the existing CSS. | `bak/apps/web/app/globals.css` media blocks. |
| `max-width: 1199px` | Compress web composer/generation filters and admin table columns. | Both `globals.css` files. |
| `max-width: 800px` | Web remains two-column generation layout where supported; admin changes to top navigation and full-width drawer. | Both `globals.css` files. |
| `max-width: 767px` | Web uses a `64px` bottom navigation, two-column gallery flow, and stacked detail layout. | `bak/apps/web/app/globals.css`. |
| `max-width: 520px` | Login forms and admin filters collapse to one column; drawers use full width. | Both `globals.css` files. |

## Capture Matrix

Capture at `1440x900`, `1024x768`, and `390x844` for inspiration gallery/detail, login, and generation empty/in-progress/success/error states. Capture admin login/tasks/inspirations at `1440x900`, `1200x1024`, and `390x844`. Freeze the clock, disable animation, and compare color, dimensions, position, overflow, duplicate IDs, missing DOM targets, and reduced-motion behavior.
