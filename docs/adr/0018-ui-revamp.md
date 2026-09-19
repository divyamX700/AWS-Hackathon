# ADR 0018: UI/UX revamp for hackathon submission — Apple-craft design system

## Status

Accepted, implemented and verified on real hardware.

## Context

The app was functionally complete but visually plain — stock Material 3
defaults everywhere (default `Card`, default `TopAppBar`, default corner
radii, no motion, three-style `Typography`). Fine for a crisis-response
tool's honest first pass, but the user's stated goal for the hackathon
submission video is winning the judging round, and a visually flat demo
undersells genuinely working functionality (real BLE mesh, real Cedar
authorization, real on-device LLM).

The user's brief, given directly: research Apple's design craft and
Human Interface Guidelines specifically, plus what recent well-regarded
(often YC-backed) startups do in their app/product design, and do a
complete visual revamp of every screen — UI/UX only, not functionality.
Explicitly: "somewhere between" a literal iOS reskin and Android-idiomatic
craft-borrowing, and a *new* visual identity (not the prior IMD/field-radio
palette) that still keeps the crisis-management use case in mind.

## Research summary

Two research passes (Apple HIG mechanics; modern/YC app trends +
emergency-app precedent) converged on a consistent direction:

- **Restrained, mostly-monochrome palette + exactly one accent.** Both
  Apple's own color guidance (semantic tokens, color reserved for the one
  interactive tint) and current design-praised apps (Linear, Raycast,
  Perplexity — near-black canvas, single accent, ~98% achromatic UI) point
  the same direction. This replaces the prior IMD four-color scheme as the
  *primary* palette (see docs/adr/0014) — but the semantics (real severity,
  never decorative, colorblind-legible via icon+text pairing) survive
  unchanged, since that reasoning was sound; only the execution changed.
- **Type discipline over exotic sizes.** Apple's actual hierarchy technique
  is a handful of size/weight pairs reused everywhere, tighter tracking at
  large sizes. Inter (SIL OFL, free, bundled as a static asset — this
  app's whole premise is zero connectivity, so a Google-Fonts network
  provider was never on the table) is the closest free typeface to SF
  Pro's screen-optimized proportions.
- **Spring-physics motion, used sparingly.** Apple's damping-ratio +
  response model translates directly to Compose's `spring(dampingRatio,
  stiffness)`. Research explicitly recommended reserving bouncy/expressive
  motion for positive confirmations only (message delivered, peer
  connected, "I'm safe" sent) — calmer motion everywhere else, since a
  crisis tool that feels *too* playful undercuts trust.
- **Precision-instrument density, not soft consumer rounding.** Tighter
  corner radii (8-16dp on most surfaces) than Material's stock defaults,
  hairline 1px borders instead of heavy drop-shadow elevation, reserving
  the largest radius/most visual weight for the one primary CTA per
  screen.
- **Anti-pattern warning, taken seriously**: the emergency-app precedent
  research flagged Citizen's aggressive upsell interstitials as a
  documented dark pattern that damages credibility specifically for safety
  software. No monetization-style interstitial exists anywhere in this
  app, and none was added.

## Decision

Rebuilt the design system from the token layer up, then applied it to
every screen:

- **`ui/theme/Color.kt`** — new `SankatSetuColors`: one accent (`SignalBlue`),
  three status colors (`StatusSafe`/`StatusCaution`/`StatusCritical`,
  conceptually anchored to the same severity ladder as the old IMD scale),
  and a named-by-elevation dark/light surface stack.
- **`ui/theme/Shapes.kt`** (new) — a graduated `Shapes` scale, 8dp→28dp,
  tighter than Material defaults. True continuous "squircle" corners
  (Apple's actual curve) would need a custom superellipse path or
  `androidx.graphics:graphics-shapes` — skipped as a new-dependency risk
  not worth taking this close to a submission deadline; a consistent large
  `RoundedCornerShape` reads as premium enough on its own.
- **`ui/theme/Type.kt`** — Inter (variable font, `res/font/inter_variable.ttf`,
  referenced per-weight via explicit `FontVariation.Settings` — required,
  not decorative, since an unset variation axis renders every weight
  identically) mapped onto a full Material 3 type scale. `FieldMono`
  (JetBrains Mono, unchanged from ADR 0014) stays reserved for the
  instrument-panel register.
- **`ui/theme/Motion.kt`** (new) — two spring presets (`PressSpring`,
  `ConfirmSpring`/`EntrySpring`) and a `Modifier.pressScale()` extension
  (scale-down + haptic on press, matching Apple's physical "I felt that"
  feedback) applied to every primary interactive element.
- **`ui/components/StatusPill.kt`** (new) — one status vocabulary (tinted
  pill, monospace uppercase word) shared across Chat's peer rows and Pay's
  IOU cards, replacing ad-hoc colored text.
- **Every screen** (`ChatScreen.kt`, `PayScreen.kt`, `AssistantScreen.kt`,
  `MainActivity.kt`) rebuilt on these tokens: hairline-bordered
  `surfaceContainer` cards instead of default elevated `Card`, the "I'm
  Safe" button as a full-width accent pill with press/haptic feedback, a
  shimmer-based thinking indicator replacing the plain spinner, a
  `Crossfade` between bottom-nav tabs, and a real divider + tinted
  indicator pill on the nav bar itself.

## Revision: the first pass wasn't enough

The first version of this ADR covered a token-level reskin (colors, type,
shapes, motion primitives) applied to the *existing* screen compositions
unchanged. Direct user feedback after seeing it: "this looks exactly like
whatever was before... very basic, wireframe kind of build." That
criticism was correct and specific — recoloring existing components
without changing what a screen actually *contains* reads as a reskin, not
a redesign, especially on screens with little or no data where the fix
needed to be structural, not chromatic.

Second pass, in direct response:

- **`ui/components/MeshRadar.kt`** (new) — a pulsing radar sweep (concentric
  rings expanding from a center dot, phase-offset for a continuous pulse)
  replacing a static icon for the mesh's "actively searching" state. This
  is the app's own real metaphor (a Bluetooth radio listening for other
  radios) rendered as motion instead of a frozen glyph — the single
  biggest fix for the "wireframe" complaint, since a screen with nothing
  on it doesn't read as designed no matter how refined its typography is.
- **`MeshSearchingCard`/`MeshActiveCard`** (`ChatScreen.kt`) — a proper
  bordered hero panel wrapping the radar for the empty state, and a
  bento-style status dashboard (a real number — peer count — plus a ready
  count pill and a mini radar) once peers exist, so the Chat tab reads as
  "here's the state of your mesh" before it reads as "here's a list."
- **Verified with real populated data, not just empty states**: a
  temporary, clearly-marked debug seed (`AppContainer.kt`, added and then
  removed after verification — never committed as shipped behavior)
  populated 3 fake peers and 3 fake IOUs so the populated Chat list and
  Pay tab's IOU cards could actually be screenshotted and reviewed, since
  no second phone exists this session to produce that state for real. This
  is exactly the gap the first pass's verification missed: it only ever
  showed empty states, which is a materially different (and materially
  worse-looking) claim than "the app looks good."
- **A real crash found by this seeding, not by inspection**: opening a
  chat thread for a fake peer crashed with `IllegalArgumentException: bad
  base-64` — `ChatViewModel.onThreadOpened()` calls `Base64.decode()` on
  the peer ID unconditionally, and the seed's fake IDs (`"demo-anaya"`)
  weren't valid base64. Confirmed this is a test-harness artifact, not a
  real production bug — every real code path constructs `peerIdBase64` via
  `Base64.encodeToString()`, never a literal string — but it's exactly the
  kind of thing that only surfaces by actually tapping through the app on
  a device, not by reading the code, which is why this ADR keeps
  documenting on-device verification as its own claim separate from
  "compiles" or "passes unit tests."

## Verification

Real, not assumed: `./gradlew assembleDebug testDebugUnitTest` both pass
(47/47 tests, unchanged — this pass touched zero business logic).
Installed on a real connected Android phone; screenshotted, with the
temporary seed in place: the Chat tab's empty-state radar card, the
populated `MeshActiveCard` + peer list (3 entries, correctly showing
OFFLINE since faked peers never get a live session), a real chat thread
open with no crash after the base64 fix, and the Pay tab's IOU list fully
populated (pending/settled pills, "Mark paid" action) — all rendering
correctly in dark theme. The seed was then removed and the app rebuilt,
tested, and reinstalled clean before this pass was considered done.
**Not verified on real hardware**: the light theme (device was in system
dark mode throughout), and the rename/forget-peer dialogs — both reuse
already-verified components with no new patterns, so risk is low but this
is genuinely untested, not just unlikely to fail.

## What this ADR does not claim

This is a visual/UX layer change only — zero business logic, mesh
protocol, Cedar authorization, or Strands/Ollama code was touched. The
`docs/PRODUCT.md`/`DESIGN.md` files describing the prior "Field Radio +
IMD Alert Colors" identity are now superseded by this pass for the
*primary* palette and are worth a follow-up update, not done as part of
this ADR to keep the change scoped to what was actually asked for.
