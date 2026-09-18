# Design

<!-- impeccable:design-schema 1 -->

Recorded from the shipped build (native Android, Jetpack Compose, Material
3), not written ahead of it. See
`docs/adr/0014-field-radio-design-language.md` for the research and
reasoning; this file is the token/component reference.

## World

**Field Radio + IMD Alert Colors.** The mesh is a Bluetooth radio relay
wearing a chat UI — hop count is already radio language. The app reads
like a field radio's own channel list and status panel, colored by India's
own IMD four-stage disaster-alert scale (Green/Yellow/Orange/Red), a system
the target user already trusts from TV and SMS weather alerts, instead of
an invented brand palette.

## Color

Defined in `ui/theme/Color.kt` (`SankatSetuColors`), wired into Material
3's full role set (including every `*Container`/`on*Container` pairing —
leaving those unset silently falls back to Material's own stock demo
tones, a real bug this pass found and fixed) in `ui/theme/Theme.kt`.

| Role | Light | Dark | Meaning |
|---|---|---|---|
| `primary` | `ImdGreen` #1E8E3E | `ImdGreen` #1E8E3E | The mesh's "all clear" — buttons, focus, selected states |
| `error` | `ImdRed` #C62828 | `ImdRed` #C62828 | Genuine danger only — never decorative |
| `tertiary` | `ImdOrange` #E8710A | `ImdOrange` #E8710A | Caution / pending / prepare |
| — | `ImdYellow` #F2B705 | same | Watch / connecting — direct reference only, no Material role fits between primary and tertiary |
| `secondary` | `ConsoleSlate` #3A4552 | `ConsoleSlateDark` #B8C2CC | Instrument-panel neutral |
| — | `OfflineGray` #9E9E9E | same | Peer disconnected — neutral, not alarming (a dropped BLE link isn't danger) |

Color strategy: Restrained (Operate mode default) — neutrals plus the IMD
scale carrying real state, not decoration.

## Type

- System sans (Material default) for all prose: headings, body, first-aid
  answers, labels — per Operate-mode convention, one family carries most
  of the UI.
- `FieldMono` (JetBrains Mono, OFL 1.1, `res/font/jetbrains_mono_*.ttf`) —
  reserved narrowly for the instrument register: peer IDs, hop counts,
  timestamps, signal/status readouts (`ConsoleReadoutStyle`, 12sp/Medium,
  uppercase status words like `2 HOPS · RELAYED`). Never body prose —
  craft-floor bans monospace as a generic "technical" costume.

## Components

- **`SignalBars`** (`ui/components/SignalBars.kt`) — a drawn four-bar
  signal-strength glyph replacing a plain colored dot for peer connection
  state. Filled-bar count + tint carry meaning (0 bars/gray = offline, 1
  bar/yellow = connecting, 1-4 bars/green scaled by hop count = ready).
  Canvas-drawn, not an icon-font glyph or emoji.
- **Channel-roster peer rows** (`ChatScreen.kt`) — nickname + signal bars +
  monospace status line, replacing the old dot-plus-badge pattern.
- **"I'm Safe" broadcast** — one-tap action at the top of the Chat tab,
  sends "I'm safe." to every peer with an established session. From the
  American Red Cross Emergency App UX research (`PRODUCT.md`, Evidence on
  Hand): the single most load-bearing pattern for panic-state cognitive
  load in that case study.
- **IOU status readout** (`PayScreen.kt`) — settled/pending/rejected use
  the same IMD-scale color + monospace word as peer status, one vocabulary
  across tabs rather than a per-screen palette.
- `TopAppBar` on all three tabs (Chat/Pay/Assistant) — Material platform
  conformance, carried over from the prior pass (ADR 0013).

## Known gaps (honest, not fixed this pass)

- Two-phone mesh (and therefore the "ready, N hops, green" signal-bar
  state) has not been field-tested — see `handoff.md` §6. Verified so far:
  offline (gray, 0 bars) and connecting (yellow, 1 bar) states, and the
  container-color/layout fixes below, all on single-device testing.
- No dedicated SOS/emergency entry point (still out of scope, same as ADR
  0013).
- Dynamic Color (Material You) intentionally stays off — a demo/judge
  phone should show this app's own palette, not per-device wallpaper
  theming.

## Real bugs this pass found and fixed (not aesthetic — functional)

1. **Stock purple leaking through**: `primaryContainer` and friends were
   never set, so Material's own hardcoded baseline violet rendered on the
   "Send Mesh IOU" card regardless of the rest of the palette. Fixed by
   setting every container/on-container role explicitly.
2. **Assistant input field hidden under the nav bar**: the empty-state
   `Column` used `fillMaxSize()` instead of `weight(1f)`, claiming all
   available height in the parent `Column` and pushing the question field
   and send button off-screen, under the app's own bottom navigation —
   present only when the turn list was empty, so a from-scratch install's
   very first screen was actually unusable until this pass's screenshot
   review caught it.
3. **Keyboard not dismissing after sending** in the Assistant tab (user
   report during this pass) — added `KeyboardActions(onSend)` plus an
   explicit `hide()` call on the send button.

## Provenance

`res/font/jetbrains_mono_regular.ttf`, `jetbrains_mono_medium.ttf` — from
https://github.com/JetBrains/JetBrainsMono, OFL 1.1, attributed in
`NOTICE.md`.
