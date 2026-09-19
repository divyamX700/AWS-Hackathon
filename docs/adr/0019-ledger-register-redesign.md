# ADR 0019: Post Office Passbook / Ledger Register — a second full redesign

**Status:** Accepted
**Date:** 2026-09-19

## Context

Two design passes already exist in git history: "Field Radio + IMD Alert
Colors" (`docs/adr/0014`) and "Apple-craft instrument panel"
(`docs/adr/0018`). The user asked for a third pass, explicitly not
constrained by either: full research into what makes a crisis-response
app both intuitive and distinctive, using the `impeccable` design skill,
avoiding AI-generated-writing patterns in any copy, tested live on a
connected phone (`ZN5224PDZ8`, Moto G57 Power).

## Process

Ran the skill's required direction-seed script
(`impeccable concept-seed --scope direction --mode operate`, seed key
`721b5aee`) against a researched, resonance-ranked list of seven visual
systems drawn from India's actual disaster-response and rural-trust
culture: a ham radio operator's station, a relief-camp hand-written
noticeboard, a post office passbook/ledger, an Indian Railways split-flap
board, telegram-wire brevity, NDRF/civil-defense field signage, and All
India Radio bulletin typography. The dice assigned index 3, the post
office passbook.

Presented to the user against the session's own top-ranked candidate (ham
radio, honestly flagged as the more obvious, more expected pick, and
weaker on payments specifically) and a standing "polished category-
standard" exit. The user didn't pick a card outright — they added a real
constraint instead: the three offline pillars (mesh chat, UPI/USSD
payment, on-device assistant) are the product's core, and the mesh IOU is
real but secondary, never its own identity. That reframed the decision:
the passbook world maps all three pillars as primary (correspondence
register, the actual ledger, a reference-index page) with the IOU folded
in as one entry type, where the ham radio world has no natural payment
metaphor at all. The user then authorized building without further
check-ins.

Full reasoning, the six catalog challengers' fused verdicts, and the two
real raises they donated (state as a physical mark rather than color
alone; color confined to a narrow "active" band while the body stays
achromatic) are recorded in `.impeccable/surfaces/app.md`'s direction
contract, not duplicated here.

## Decision

The app now reads as a bound ledger, not a chat app and not a radio
panel. Concretely, in `ui/theme/`:

- **Three inks, not one accent.** A deep indigo "pen ink"
  (`SankatSetuColors.SignalBlue`/`SignalBlueDark`) for interactive
  elements and structure; stamp-ink green (`StatusSafe`) for confirmed/
  settled state, drawn as an actual seal (`StampMark.kt`), not a flat
  pill; a reserved correction-ink red (`StatusCritical`) for genuine
  danger only, unchanged in meaning from both prior passes.
- **Dark stays the operating default**, reframed as a night ledger —
  warm near-black page surfaces, a dim khaki rule line
  (`HairlineOnDark`) standing in for a printed ledger rule, rather than
  plain white-alpha. Light is a real manila/buff paper tone, deliberately
  not paired with serif/italic type — Operate mode's own convention (a
  workhorse grotesk throughout, Inter for prose, JetBrains Mono for every
  numeric/ID column) sidesteps the "cream ground plus display serif"
  cliché a bookish subject usually pulls toward.
- **Shapes went almost square** (`Shapes.kt`, 3-6dp corners) — ledger
  rows are ruled rectangles, not floating rounded cards. `PillShape`
  survives only for the drawn stamp glyph's own circle, not general
  chrome.
- **New components**: `StampMark.kt` (a drawn double-ring seal, a
  deterministic per-item rotation jitter for a hand-stamped feel, never a
  fresh random wobble), `PerforationMargin.kt` (a dotted spine with
  notches down a register screen's left edge — the book-binding cue),
  `CounterfoilEdge.kt` (a dashed border `Modifier` marking a still-
  pending row, used only on the IOU composer entry point and a pending
  IOU card — never a real UPI/USSD entry, which is the app's actual core
  feature and gets the confirmed-stamp treatment instead once settled).
- **`MeshRadar.kt` deleted** — it belonged to the discarded "field radio"
  world (a Bluetooth radio metaphor); a ledger doesn't have a radar, it
  has a page waiting to be written on. The empty Chat state now shows a
  blank ruled line with a blinking write-cursor instead.
- Applied across all three tabs: Chat's peer list as a correspondence
  register, Pay's USSD/UPI actions promoted as the primary ledger entries
  with the IOU visually demoted via the counterfoil edge (matching the
  user's own instruction), Assistant's docs browser as a numbered
  reference-index register (`01`, `02`, … in tabular monospace).

## A real bug found by testing on the device, not by reading the code

`PerforationMargin`'s first version was called with
`modifier = Modifier.fillMaxSize()` at two of its three call sites. Compose's
`fillMaxSize()` forces the incoming constraints to `minWidth = maxWidth =
available`; the component's own internal `.width(10.dp)` modifier, applied
*after* that, doesn't override an already-exact incoming range — it
coerces its requested 10dp into `[available, available]`, which resolves
to `available`. The dotted spine rendered at full screen width, centered,
and silently ate 100% of the Row's space, leaving the sibling `Column`
(the entire Chat tab's content — the "I'm Safe" button, the peer list,
everything) zero width to lay out in. No crash, no log line — the screen
just rendered blank except the top bar, bottom nav, and a stray line of
dots down the middle. Caught only by actually screenshotting the running
app on the connected phone. Fixed by passing `Modifier.fillMaxHeight()`
instead at every call site — the component doesn't need a width hint from
its caller, only a height one, since it sets its own width internally and
that's only safe to do when the incoming width constraint is still loose.

## Consequences

- `DESIGN.md` is fully rewritten from this build, not from this ADR's
  intentions — see that file for the token/component reference and its
  own "Known gaps" section for what this pass did not verify (a live
  two-phone mesh connection's "confirmed" stamp state, TalkBack).
- The prior two worlds' identifiers (`SankatSetuColors.SignalBlue`,
  `StatusSafe`, `SurfaceBase`, etc.) were kept unrenamed on purpose — see
  `Color.kt`'s own doc comment for why re-deriving the world lives in
  values and components, not identifier spelling, was judged the better
  time/risk trade-off this close to submission.
- Verified end-to-end on `ZN5224PDZ8` (Moto G57 Power): dark theme (the
  operating default) and light theme both screenshotted across Chat, Pay,
  and the Assistant docs browser; `./gradlew assembleDebug
  testDebugUnitTest` passes (63/63 unit tests, unrelated to this pass's
  UI-only changes). Not verified: TalkBack/screen-reader pass, font-scale
  200% (PRODUCT.md's stated accessibility goal, unchanged status from
  prior passes), a live two-phone mesh session's actual "confirmed" stamp
  rendering (still only ever screenshotted against a single offline test
  peer — see `handoff.md` for the still-open two-phone test gap this
  pass didn't touch).
