# Design

<!-- impeccable:design-schema 1 -->

Recorded from the shipped build (native Android, Jetpack Compose, Material
3), not written ahead of it. **Rewritten 2026-09-19** — this is the third
visual world this app has shipped; see `docs/adr/0019-ledger-register-
redesign.md` for the research, the decision process, and a real bug this
pass found on-device. The "Superseded" section at the bottom covers what
the previous two worlds looked like.

## World

**Post Office Passbook / Ledger Register.** The app reads as a bound
ledger — ruled rows, a stamped mark for what's confirmed, a torn
counterfoil edge for what's still pending — India's own century-old
low-tech trust object, not a chat app wearing crisis-themed colors and not
a radio panel either (both this file's own prior worlds). A post office
passbook is a real, specific thing a rural or disaster-affected user
already knows how to read without instruction, the same way the product's
own USSD payment rail is real rather than invented.

Chosen after a structured decision round (`impeccable concept-seed
--scope direction --mode operate`, seed key `721b5aee`) that dealt this
direction from the dice, against the session's own top-ranked candidate
(a ham radio operator's station) and a standing category-default exit.
The user reframed the decision with a real constraint — offline chat,
offline UPI, and the on-device assistant are the product's three core
pillars; the mesh IOU is real but secondary, never its own identity — and
the passbook world was the stronger fit for that: it makes all three
pillars the ledger's own headline entries, with the IOU folded in as one
entry type among them, where the radio world had no natural payment
metaphor at all.

## Color

Defined in `ui/theme/Color.kt` (`SankatSetuColors`), wired into Material
3's full role set in `ui/theme/Theme.kt`. A ledger has three inks, not one
accent:

| Ink | Role | Meaning |
|---|---|---|
| Pen ink — deep indigo (`SignalBlue`/`SignalBlueDark`) | `primary` | What you write with: interactive elements, focus states, ruled-line structure. Never status. |
| Stamp ink — green (`StatusSafe`) | drawn via `StampMark` | Confirmed, ready, settled, all-clear. A round stamp pad ink is a real, standard color (a "VERIFIED"/"PAID" stamp), not an invented mapping. |
| Correction ink — red (`StatusCritical`) | `error` | Genuine danger only. Never a stamp, never decorative — unchanged meaning from both prior worlds. |
| Aged-carbon amber (`StatusCaution`) | `tertiary`, dashed via `CounterfoilEdge` | Pending, connecting, not yet settled — rendered as a torn counterfoil edge, not just a color. |

Dark is the operating default — PRODUCT.md's own Operating Context (low
battery, poor light, a frightened or moving user) forces the answer, not
category habit — rendered as a *night ledger*: warm near-black page
surfaces (`SurfaceBase` #121110, not a cool blue-black), a dim khaki rule
line (`HairlineOnDark`) standing in for a ledger's own printed rule rather
than plain white-alpha. Light is a real manila/buff paper tone
(`SurfaceBaseLight` #F3EEE1), fully designed and screenshotted, not an
afterthought. Dynamic Color (Material You) stays off, unchanged from
prior passes — a demo phone shows this app's own palette.

## Type

- **Inter** for all prose, unchanged typeface choice from the prior pass —
  Operate mode's own convention (system stacks and workhorse UI faces,
  not a display serif) already argued against changing it, and a ledger's
  real typography is a plain grotesk or typewriter face, never italic
  display serif. This is a deliberate non-default: the calibration risk
  for a "bookish, paper" subject is a cream ground plus serif-italic plus
  lamplight, and this build stays away from all three.
- **JetBrains Mono** (`FieldMono`/`ConsoleReadoutStyle`), unchanged
  typeface from the first pass, now with a stronger material reason: a
  ledger's numeric columns are genuinely ruled and tabular in real life
  (amounts, dates, entry numbers), not monospace-as-a-technical-costume.
  Used for every numeric or identifying column: hop counts, timestamps,
  peer status words, IOU amounts, the docs register's entry numbers.

## Shapes & Motion

- **`ui/theme/Shapes.kt`** — corners went almost square (3-6dp, down from
  the prior pass's already-tight-but-still-rounded 8-28dp scale). Ledger
  rows are ruled rectangles, not floating rounded cards; a little
  rounding survives only on the primary "I'm Safe" action and Material's
  own platform chrome (dialogs, menus). `PillShape` (full round) is now
  reserved for the `StampMark` glyph's own circle — the ledger world has
  no other pill-shaped chrome.
- **`ui/theme/Motion.kt`** — unchanged from the prior pass (the spring
  presets are generic physics, not tied to either discarded world's
  metaphor) — reused as-is for the new components.

## Components

- **`StampMark`** (`ui/components/StampMark.kt`, new) — a drawn double-
  ring ink-stamp seal with a deterministic per-item rotation jitter (seeded
  from the label, so the same status always stamps the same way, never a
  fresh random wobble), paired with the status word in the monospace
  register. Replaces `StatusPill` for the primary confirmed/settled/ready
  states across Chat and Pay; `StatusPill` survives for lighter secondary
  tags (CONNECTING, OFFLINE), now rectangular instead of a rounded pill.
- **`CounterfoilEdge`** (`ui/components/CounterfoilEdge.kt`, new) — a
  dashed-border `Modifier` standing in for a receipt book's tear-off
  counterfoil. Marks a row as still pending, not yet settled. Used only on
  the mesh IOU composer entry point and a pending IOU card, deliberately
  never on a real UPI/USSD action — those are the app's actual core
  feature and get the confirmed stamp instead once resolved, per the
  user's own instruction that the IOU stays secondary.
- **`SignalBars`** (`ui/components/SignalBars.kt`) — unchanged from the
  first pass, kept as a generic reachability glyph; still legible outside
  its original "radio signal" framing, low risk to retain.
- **Empty-state blinking cursor** (`ChatScreen.kt`'s `BlinkingCursor`,
  new, private) — replaces the deleted `MeshRadar` sweep for the Chat
  tab's "nothing to show yet" moment: a blank ruled line with a steady
  on/off cursor blink, like a register waiting for its next entry. Still
  motion, not a frozen icon, but drawn from this world's own vocabulary
  instead of the discarded radio metaphor's.
- **Docs browser as a reference-index register** (`AssistantScreen.kt`) —
  each guide numbered `01`, `02`, … in tabular monospace, section counts
  relabeled "entries" — the printed rules page bound into the back of a
  real passbook, not a generic file list.

## Known gaps (honest, not fixed this pass)

- **A live two-phone mesh connection's "confirmed" `StampMark` state is
  still unverified** — every screenshot this pass used a single, already-
  offline test peer; the READY/confirmed stamp rendering against a real,
  live handshake has never actually been seen, only reasoned about from
  the same code path the prior passes used. See `handoff.md` — the
  two-phone test remains the single highest-risk untested surface in the
  app, unchanged by this pass.
- **TalkBack and 200% font scale** — PRODUCT.md's own stated goals, not
  verified against this build, same unresolved status as every prior
  pass.
- **The rename/forget-peer dialogs and the chat-thread message bubbles**
  inherited the new theme tokens automatically (color, shape) but weren't
  individually redesigned or screenshotted this pass — low risk since
  they're plain `AlertDialog`/`Card` usage, not new composition, but
  worth a look before a demo.
- **StampMark's stamp glyph reads small at default list-row scale** — a
  double-ring circle at 14dp is legible but subtle; a demo audience
  glancing quickly may read it as a generic dot rather than a stamp. Would
  benefit from a slightly larger treatment on the single most important
  instance (the Chat tab's `MeshActiveCard` ready-count) if there's time
  before submission.

## Real bug this pass found and fixed (not aesthetic — functional)

**`PerforationMargin` ate the entire Chat tab.** Its first version was
called with `Modifier.fillMaxSize()` at two of its three call sites.
Compose's `fillMaxSize()` forces the incoming constraints to an exact
`[available, available]` range; the component's own internal
`.width(10.dp)`, applied after that, doesn't override an already-exact
range, it coerces 10dp into it — so the component rendered at full screen
width instead of a thin margin, and its unweighted sibling in the `Row`
(the entire tab's content — the "I'm Safe" button, the peer list,
everything) got zero leftover width to lay out in. No crash, no log line;
the screen just rendered blank except the top bar, bottom nav, and a
stray column of dots down the middle. Found only by actually screenshotting
the running app on the connected phone, not by reading the code. Fixed by
passing `Modifier.fillMaxHeight()` instead at every call site.

**`PerforationMargin` itself was removed entirely after this fix shipped.**
Direct user feedback: the dotted spine read as visual noise, was applied
inconsistently (list screens only, not reading screens — a distinction
that made sense while building it, not while using it), and — the
deciding point — even the person who added it briefly mistook it for a
rendering bug while testing the fix above. A decorative element a
builder can't tell apart from a defect fails this product's own "clarity
beats flair" bar. The file is deleted; do not re-add it without a
different execution.

**A second, unrelated color bug found after shipping**: the outgoing
message bubble's fill (`primary`) and the "read" delivery tick's color
(`ReadBlue`) were set to the identical hex value, making the read tick
invisible against its own background. Fixed by reusing the stamp-ink
green (`StatusSafe`) for the read tick instead — also more correct for
this world's own ink logic, since green already means "confirmed"
everywhere else. `ReadBlue` is removed from `Color.kt`.

## Superseded: the prior two worlds

**"Apple-craft instrument panel"** (this file's second version, see git
history): a restrained near-black canvas, Inter typography, one signal-
blue accent, `MeshRadar`'s pulsing sweep for the searching state, a bento-
style status dashboard. Sound execution of its own brief, but the user's
request this pass was explicit — research and build a full design
philosophy without being constrained by what's already shipped — so it's
treated as evidence of what this app is, not authority over what it
becomes, per the `impeccable` skill's own redesign convention.

**"Field Radio + IMD Alert Colors"** (the original version of this file):
India's four-stage disaster-alert color scale as the primary palette, a
channel-roster peer list, signal-bar glyphs. Superseded by the Apple-craft
pass after direct user feedback that the shipped app still read as "a very
basic, wireframe kind of build" — a token-level reskin without changing
what a screen contains reads as a reskin, not a redesign, the same lesson
this pass's own research applied a second time by changing composition
(ruled rows, drawn stamps, counterfoil edges), not just tokens.

## Provenance

`res/font/inter_variable.ttf` — SIL OFL, Google Fonts, attributed in
`NOTICE.md`. `res/font/jetbrains_mono_regular.ttf`,
`jetbrains_mono_medium.ttf` — OFL 1.1, from
https://github.com/JetBrains/JetBrainsMono, attributed in `NOTICE.md`. No
new fonts or external assets added this pass — every new component
(`StampMark`, `PerforationMargin`, `CounterfoilEdge`) is drawn in Compose
`Canvas`/`drawBehind`, not an imported asset.
