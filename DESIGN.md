# Design

<!-- impeccable:design-schema 1 -->

Recorded from the shipped build (native Android, Jetpack Compose, Material
3), not written ahead of it. **Rewritten 2026-09-19** — everything below
describes the *current* system after the UI/UX revamp; see
`docs/adr/0018-ui-revamp.md` for the full research and reasoning, and the
"Superseded" section at the bottom for what this file used to say.

## World

**Apple-craft instrument panel.** A restrained, mostly-monochrome dark-first
canvas (near-black surfaces, one signal-blue accent) carrying almost all of
the UI, with three status colors reserved strictly for real mesh/payment
state — never decoration. This replaced the prior "Field Radio + IMD Alert
Colors" identity (India's own Green/Yellow/Orange/Red disaster-alert scale
as the primary palette) after direct user feedback that the shipped app
still read as a plain wireframe. The crisis-management use case is still
the design brief — clarity under stress, real state never hidden behind
decoration, no monetization-style interstitials anywhere (a documented
Citizen-app anti-pattern this research explicitly flagged and avoided) —
but the execution is now closer to Apple's own HIG craft (type discipline,
restrained color, spring motion) blended with current well-regarded app
trends (Linear/Raycast-style monochrome-plus-accent, hairline borders over
heavy elevation).

## Color

Defined in `ui/theme/Color.kt` (`SankatSetuColors`), wired into Material
3's full role set in `ui/theme/Theme.kt`. Dark is the primary design
target; light is fully designed, not an afterthought, since a judge's
phone may be in either mode.

| Role | Meaning |
|---|---|
| `SignalBlue` (`primary`) | The one accent — interactive elements, focus, the mesh's "in range" glow. Never used for status, so blue never means "good" or "bad," only "interactive." |
| `StatusSafe` (green) | Delivered, ready, settled, all-clear |
| `StatusCaution` (amber) | Connecting, pending, handle-soon |
| `StatusCritical` (red) | Genuine danger only — never decorative |
| Surface stack (`SurfaceBase`/`SurfaceRaised`/`SurfaceOverlay`/`SurfaceOverlayHigh`) | Named by elevation, dark-first with a full light mirror |
| `OfflineGray` | Peer disconnected — neutral, not alarming (a dropped BLE link isn't danger) |

Dynamic Color (Material You) stays off, unchanged from the prior pass — a
demo/judge phone should show this app's own palette, not per-device
wallpaper theming.

## Type

- **Inter** (`res/font/inter_variable.ttf`, SIL OFL, bundled as a static
  asset — never fetched over network, since this app's whole premise is
  zero connectivity) for all prose: headings, body, first-aid answers,
  labels. Referenced as a variable font with explicit `FontVariation.Settings`
  per weight (`ui/theme/Type.kt`) — the closest free typeface to SF Pro's
  screen-optimized proportions, chosen over Android's default Roboto to
  match the Apple-craft brief.
- `FieldMono` (JetBrains Mono, OFL 1.1, `res/font/jetbrains_mono_*.ttf`) —
  unchanged from the prior pass, still reserved narrowly for the
  instrument register: peer IDs, hop counts, timestamps, signal/status
  readouts (`ConsoleReadoutStyle`). Never body prose.

## Shapes & Motion

- **`ui/theme/Shapes.kt`** (new) — a graduated corner-radius scale, 8dp→28dp,
  tighter than Material's stock defaults ("precision instrument" density
  per the trend research), reserving the largest radius for the one
  primary CTA and full-screen sheets.
- **`ui/theme/Motion.kt`** (new) — two spring presets (`PressSpring`,
  `ConfirmSpring`/`EntrySpring`, translated from Apple's WWDC18
  damping-ratio/response model into Compose's `spring()` API) and a
  `Modifier.pressScale()` extension (scale-down + haptic on press) applied
  to every primary interactive element. Bouncy/expressive motion is
  reserved for positive confirmations only (message delivered, peer
  connected, "I'm safe" sent) — calmer motion everywhere else, per the
  research's explicit warning against a crisis app feeling too playful.

## Components

- **`MeshRadar`** (`ui/components/MeshRadar.kt`, new) — a pulsing radar
  sweep (concentric rings expanding from a center dot) for the mesh's
  "actively searching" state — the app's own real metaphor (a Bluetooth
  radio listening for other radios) rendered as motion instead of a frozen
  icon. Added specifically because a static icon floating in empty space
  was the core of a "this looks like a wireframe" complaint — a screen
  with nothing on it doesn't read as designed no matter how refined its
  typography is.
- **`MeshSearchingCard`/`MeshActiveCard`** (`ChatScreen.kt`) — a bordered
  hero panel wrapping the radar for the empty state, and a bento-style
  status dashboard (a real peer-count number, a ready-count pill, a mini
  radar) once peers exist. The Chat tab now reads as "here's the state of
  your mesh" before it reads as "here's a list."
- **`StatusPill`** (`ui/components/StatusPill.kt`, new) — one status
  vocabulary (tinted pill, monospace uppercase word, background at low
  opacity so it reads as a tag not an alert) shared across Chat's peer
  rows and Pay's IOU cards, replacing the old raw colored-text pattern.
- **`SignalBars`** (`ui/components/SignalBars.kt`) — unchanged from the
  prior pass: a drawn four-bar signal-strength glyph for peer connection
  state, still used inside peer rows alongside the new `StatusPill`.
- **"I'm Safe" broadcast** — unchanged behavior, restyled as a full-width
  accent-colored pill button with spring/haptic press feedback (was a
  plain Material `Button` before).
- **Assistant tab icon**: a sparkle (`Icons.Filled.AutoAwesome`), not the
  prior "robot head" (`SmartToy`) — the generic cartoon-bot glyph read as
  an AI-feature placeholder sticker, not a considered choice. The sparkle
  matches Apple's own AI-feature glyph and this app's existing
  on-device-generated-content marker (already used in the Assistant
  screen's empty state and "Generated on-device" label).
- Hairline `BorderStroke(1.dp, outlineVariant)` + `surfaceContainer` fill
  replaces default elevated `Card` styling everywhere — the "precision
  instrument" treatment over Material's heavier drop-shadow elevation.

## Known gaps (honest, not fixed this pass)

- **Light theme is untested on real hardware** — the device used for this
  pass's verification stayed in system dark mode throughout. The light
  `ColorScheme` in `Theme.kt` is fully defined but never actually
  screenshotted.
- **Rename/forget-peer dialogs** reuse already-verified components
  (`AlertDialog`, `OutlinedTextField`) with no new patterns, so risk is
  low, but they were not individually screenshotted this pass.
- Two-phone mesh (and therefore the "ready, N hops, green" `StatusPill`
  state on a *real* connected peer) has still not been field-tested — see
  `handoff.md` §6. This pass verified the populated-list *layout* using a
  temporary fake-data seed (added and removed, never shipped), which
  necessarily shows every fake peer as `OFFLINE` since it never has a live
  session — the real "READY, green" state on an actual connection remains
  unverified.
- No dedicated SOS/emergency entry point (still out of scope, same as ADR
  0013).

## Real bugs this pass found and fixed (not aesthetic — functional)

1. **A real crash, found only by testing populated states**: opening a
   chat thread threw `IllegalArgumentException: bad base-64` —
   `ChatViewModel.onThreadOpened()` decodes the peer ID unconditionally.
   The temporary fake-data seed used non-base64 peer IDs
   (`"demo-anaya"`); confirmed this is a test-harness artifact and not
   reachable by real app data (every real code path constructs
   `peerIdBase64` via `Base64.encodeToString()`), but it's exactly the
   category of bug that only surfaces by actually tapping through the app
   on a device.
2. **A modifier-ordering bug in `MeshRadar`**: its first version hardcoded
   `Canvas(modifier = modifier.size(120.dp))`, which meant a caller's own
   size modifier (for the small "mini radar" variant in `MeshActiveCard`)
   was always overridden by the internal 120dp — fixed by making the
   diameter an explicit parameter instead of relying on modifier chaining.
3. From the prior pass, still true: Material's own stock demo-app purple
   leaking through unset `*Container` roles, the Assistant tab's input
   field rendering hidden behind the bottom nav bar on first launch, and
   the keyboard not dismissing after sending an Assistant question — see
   git history (ADR 0013/0014) for detail; not revisited this pass.

## Superseded: the prior "Field Radio + IMD Alert Colors" system

The previous version of this file described the mesh reading like "a field
radio's own channel list and status panel," colored by India's own IMD
four-stage disaster-alert scale (`ImdGreen`/`ImdYellow`/`ImdOrange`/`ImdRed`)
as the *primary* palette, not just a status-color reference. That research
(`docs/adr/0014-field-radio-design-language.md`) was sound and the
underlying idea — borrow a color language the target user already trusts
from TV/SMS weather alerts — is still credited, but direct user feedback
after seeing it shipped was that the app still looked like "a very basic,
wireframe kind of build" with "some color differences here and there." The
fix needed to be structural (what a screen *contains*), not just a
different color mapping, which is why this revamp went further: new
typography, new motion, new components (`MeshRadar`, hero dashboard cards),
not just new hex values. The status-color *semantics* (real severity,
colorblind-legible, never decorative) carry over unchanged, per
`docs/adr/0018-ui-revamp.md`.

## Provenance

- `res/font/jetbrains_mono_regular.ttf`, `jetbrains_mono_medium.ttf` — from
  https://github.com/JetBrains/JetBrainsMono, OFL 1.1, attributed in
  `NOTICE.md`.
- `res/font/inter_variable.ttf` — from https://github.com/google/fonts/tree/main/ofl/inter
  (upstream: https://github.com/rsms/inter), OFL 1.1, attributed in
  `NOTICE.md`.
