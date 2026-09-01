# Generation empty-state visual layout research

## Scope and sources

- User reference: `C:/Users/52535/AppData/Local/Temp/codex-clipboard-308f2558-a78a-46cf-8160-8d760e4350a0.png` (2752 x 1528 physical pixels).
- Current component: `dream_web/src/features/generation/GenerationWorkspaceView.vue`.
- Current shared styles: `dream_web/src/styles.css`.
- Existing visual baselines and viewports: `dream_web/e2e/web.spec.ts`, `dream_web/playwright.config.ts`, and `dream_web/e2e/__screenshots__/web-*/web-*-generate.png`.
- The component and stylesheet already contain unrelated, uncommitted generation-workspace refinements. The implementation must edit in place and preserve them.

## What the reference establishes

The reference is a restrained creation workspace, not a card-based landing page. Its important geometry is:

1. The session rail remains a distinct fixed column.
2. A compact header occupies the top of the creation canvas.
3. The empty-state title, supporting copy, and three starter prompts form one centered group in the remaining canvas above the composer.
4. The composer is horizontally centered near the bottom, shares the same visual center line as the empty-state group, and has stable side gutters.
5. The title and copy are centered; the starter prompt labels remain left aligned inside equal-width buttons.

"Centered" should therefore be implemented relative to the **available `.generation-main` canvas**, after the fixed primary navigation and session sidebar have been accounted for. It should not use `left: 50vw`, fixed pixel offsets, or transforms relative to the browser viewport.

## Existing structure to reuse

No new wrapper is needed. The current DOM already has the right ownership boundaries:

- `.generation-main.is-empty`
- `.generation-header`
- `.timeline`
- `.empty-session`
- `.starter-prompts` / `.starter-prompt`
- `.composer.composer-shell.is-expanded`

`GenerationWorkspaceView.vue` already has the empty title/copy, three localized starter prompts, header, mobile history controls, recent-prompt popover, autosizing textarea, and keyboard submit. Preserve these dirty changes. The implementation should only change the `newSession` behavior separately and refine layout CSS; it should not replace the component markup to achieve centering.

## Minimal CSS plan

### 1. Make the main canvas the only layout coordinate system

Use the existing grid on `.generation-page .generation-main`:

```css
.generation-page .generation-main {
  grid-template-rows: auto minmax(0, 1fr) auto;
}
```

The header is row 1, timeline is row 2, and composer is row 3. For the empty state, explicitly neutralize the old spacer pseudo-element:

```css
.generation-page .generation-main.is-empty::after { content: none; }
```

This is important because the legacy `.generation-main.is-empty::after` still claims `grid-row: 3`; the current dirty override also places the composer in row 3. They presently overlap and make the grid behavior unnecessarily brittle.

Avoid the current `minmax(300px, 1fr)` desktop and `minmax(220px, 1fr)` mobile row minima. They can exceed the available height on short windows while `.generation-main` has `overflow: hidden`. `minmax(0, 1fr)` lets `.timeline` own overflow safely.

### 2. Center the empty group within row 2

Prefer explicit two-axis grid centering rather than the current flex alignment plus viewport-relative translation:

```css
.generation-page .generation-main.is-empty .timeline {
  display: grid;
  place-items: center;
  min-height: 0;
  overflow-y: auto;
  padding: clamp(24px, 5vh, 56px) clamp(16px, 5vw, 76px);
}

.generation-page .empty-session {
  width: min(880px, 100%);
  margin: auto;
  transform: none;
  text-align: center;
}
```

The composer already removes height from row 2, so mathematical centering in row 2 naturally places the prompt group in the upper half of the full page, matching the reference without `translateY(-4vh)` / `translateY(-2vh)`. Removing those transforms also prevents clipping on short screens.

Keep `.starter-prompts` at three equal `minmax(0, 1fr)` columns on desktop/tablet and keep `.starter-prompt span` clamped/wrappable. On mobile, stack all three prompts in one column. The existing rule `.starter-prompt:nth-child(n + 3) { display: none; }` conflicts with the requirement to present three suggestions and should be removed or overridden for this view.

### 3. Center the composer with bounded gutters

Continue using normal grid flow and auto margins; do not make the workspace composer fixed or absolutely positioned:

```css
.generation-page .generation-main > .composer.is-expanded,
.generation-page .generation-main.is-empty > .composer {
  width: min(980px, calc(100% - 72px));
  max-width: 100%;
  margin-inline: auto;
}
```

For mobile, retain the existing `width: calc(100% - 24px)` and `margin: 0 12px 12px`. Add `min-width: 0` to the generation composer textarea/flex children if needed so the long Chinese/English placeholder cannot establish a wider min-content size.

Scope changes under `.generation-page`. The unqualified shared selectors `.composer`, `.prompt-row`, `.composer-footer`, and `.field-btn` are also used by `InspirationDetailView.vue`; changing them would unintentionally alter its floating composer.

### 4. Preserve footer fit across the tablet boundary

At 768-800px viewport width, the fixed 72px primary navigation plus 248px session rail leave only 448-480px for `.generation-main`. The empty composer then has roughly 368-400px outer width, while image mode, generation settings, keyboard hint, credit estimate, and submit button all remain on one row. The current `.composer-hint` is hidden only at `max-width: 767px`, so this interval can overflow.

Minimal fix: hide `.generation-page .composer-hint` through the narrow-tablet range (for example `max-width: 1023px`), while preserving the controls and credit estimate. Also ensure `.generation-page .composer-footer { min-width: 0; }`; use wrapping only if a visual check proves it necessary because wrapping changes composer height and therefore the centered canvas geometry.

## Exact selectors and current cascade concerns

| Selector | Current role | Recommendation |
| --- | --- | --- |
| `.generation-page .generation-main` | Fixed-height grid, currently overridden near the end of `styles.css` | Keep `auto minmax(0, 1fr) auto` as the single final grid definition. |
| `.generation-page .generation-main.is-empty` | Currently reintroduces `minmax(300px, 1fr)` | Do not use a hard minimum for the flexible row. |
| `.generation-main.is-empty::after` | Legacy third-row spacer | Disable for the new three-row header/timeline/composer layout. |
| `.generation-page .generation-main.is-empty .timeline` | Empty-state canvas | Use `display: grid; place-items: center; min-height: 0; overflow-y: auto`. |
| `.generation-page .empty-session` | Empty title/copy/prompt group | Keep bounded width and auto margins; remove viewport-relative transform. |
| `.generation-page .starter-prompts` | Three desktop suggestions | Keep equal columns; switch to one column on mobile. |
| `.generation-page .starter-prompt:nth-child(n + 3)` | Hides the third mobile suggestion | Remove/override so all three suggestions remain visible. |
| `.generation-page .generation-main.is-empty > .composer` | Empty composer width/row | Keep in normal grid flow, set bounded width and auto inline margins. |
| `.generation-page .composer-hint` | Keyboard hint in footer | Hide before the session rail collapses, not only below 768px. |
| `.generation-page.sidebar-collapsed .generation-header` | Header when sidebar is collapsed | Reserve left space for the absolute `.sidebar-expand` button, or move that button into the header, to avoid overlap. |

One existing selector is dead: `.generation-page.is-empty > .generation-main > .composer` can never match because `is-empty` is applied to `.generation-main`, not `.generation-page`. Remove it when consolidating the block, but preserve the matching `.generation-page .generation-main.is-empty > .composer` rule.

The dirty stylesheet currently appends a second generation layout block after an earlier generation block and after the mobile rules. Consolidation is preferable, but a minimal implementation may add narrowly scoped final overrides. Do not delete the dirty conversation, header, mobile drawer, prompt history, or task-label rules while doing so.

## Breakpoint and state risks

- **767/768px discontinuity:** the session rail appears at 768px, immediately consuming 248px in addition to the 72px primary navigation. This is the narrowest effective desktop canvas and the highest overflow risk.
- **800 x 1024 tablet portrait:** Playwright config defines this project but there is currently no checked-in generation baseline for it. It must be exercised explicitly.
- **Short-height windows / mobile landscape:** hard grid-row minima plus the composer can exceed the available `100vh` / `100dvh`; the timeline must scroll rather than clip the composer.
- **320px minimum width:** verify the menu button, header title, new-session icon, composer upload button, parameter button, cost, and submit control do not compete for width. The title flex item needs `min-width: 0` (already present), and the long keyboard hint should stay hidden.
- **English locale:** starter prompt and header strings are materially longer than Chinese. Verify wrapping/clamping with `data-language="en"` as well as Chinese.
- **Textarea growth / references / errors:** autosizing prompt text, uploaded reference chips, parameter popover, recent-prompt popover, and an error banner increase composer height. Row-2 centering must respond to the actual composer height without overlap.
- **Sidebar collapse:** `.sidebar-expand` is absolutely positioned at the same top-left area now occupied by `.generation-header`; visual centering can be correct while these controls overlap.
- **Loaded conversation state:** empty-only centering selectors must not change `.timeline` scrolling or task widths when `.generation-main` lacks `is-empty`.
- **Dark theme:** the layout does not need theme-specific geometry, but transparent composer/prompt backgrounds and borders need a quick visual check because the stylesheet already has dark overrides.

## Verification matrix

Use the existing Playwright viewport matrix and add geometric assertions to the generation test after the behavioral change makes `/generate` deterministically empty:

| Viewport | Purpose |
| --- | --- |
| 1440 x 900 | Primary desktop baseline; three prompt columns, centered title group and composer. |
| 1024 x 768 | Tablet landscape with visible session rail; footer fit and three prompt columns. |
| 800 x 1024 | Narrowest desktop/tablet canvas before the 767px navigation switch; primary overflow regression target. |
| 390 x 844 | Required mobile acceptance viewport; three stacked suggestions and bottom-navigation clearance. |
| 320 x 568 | Minimum-width and short-height stress check; timeline scroll, composer/control fit. |
| 844 x 390 | Mobile-landscape stress check for hard-height assumptions and popover clipping. |

Recommended assertions:

```ts
const empty = page.locator('.empty-session');
const main = page.locator('.generation-main');
const composer = page.locator('.generation-main > .composer');

expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
// Compare element center X values with a small pixel tolerance.
// Assert all three .starter-prompt nodes are visible in the empty state.
// Assert empty/composer bounding boxes do not overlap.
// Assert the composer bottom remains above the 64px mobile nav at <= 767px.
```

Capture/update `web-desktop-generate.png`, `web-tablet-generate.png`, `web-tablet-portrait-generate.png`, and `web-mobile-generate.png` only after inspecting them. Existing generation screenshots depict the previous auto-opened/loading state and are not useful as the expected empty-state composition.

