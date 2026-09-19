---
version: 1
slug: "app"
primary_target: "app"
related_targets: []
---

## Scope and visitor mode

Whole-app visual world replacement, Operate mode (task completion, not
persuasion) — Chat, Pay, Assistant tabs, native Jetpack Compose / Material 3.
Second replacement of this surface's world (see git history for the prior
"Field Radio + IMD Alert Colors" and "Apple-craft instrument panel" worlds,
both now anti-reference, not authority).

## Audience, job, action, proof, constraints

Someone in a disaster-affected area in India with no cell signal, phone in
hand, possibly frightened or moving. Job: confirm a nearby person is safe,
get first-aid guidance, pay for something small, settle a debt later.
User-confirmed constraint this round: the three offline pillars — mesh
chat, UPI/USSD payment, on-device assistant — are the product's core, and
must read as primary; the mesh IOU voucher is real but secondary, one
entry type inside Pay, never its own identity. Material 3 governs
structure/navigation/interaction on Android (non-negotiable per platform
reference); brand expresses through Material's theming only.

## Direction contract

**THESIS:** Sankat Setu is trusted the way a post-office passbook is
trusted — a plain, physically-marked record of what happened, not a
polished consumer app. This surface refuses the generic-chat-app default
(rounded bubbles, floating cards, a colored dot for status) and refuses
its own prior "field radio" costume too, and instead treats the phone as
a bound register: ruled rows, a stamped mark for what is confirmed, a
torn counterfoil edge for what is still pending — India's own century-old
low-tech trust object, one a rural or disaster-affected user already
reads without instruction.

**OWN-WORLD:** Material 3 structure throughout (mandatory). Dark is the
operating default — the scene is a phone at low battery, at night, in
poor light — rendered as a night ledger: near-black "page" surfaces, a
muted khaki/buff rule line standing in for a ledger's feint printed rule,
tabular monospace (JetBrains Mono, already bundled) for every numeric or
ID column — hop count, timestamp, amount, peer ID — because a ledger's
numeric columns are ruled and aligned in real life, not because
"monospace reads technical." One ink for confirmed state: a deep
postal-indigo stamp mark, a drawn circular seal, not a hex-only color
role — chosen because real Indian postal/bank rubber stamps run blue-
violet or black, not red, which keeps red free for its one honest job:
genuine danger, never decoration. Pending/unsettled state reads as a
dashed, notched "counterfoil" edge, not a color at all. Rows are ruled
rectangles with hairline top/bottom borders, not rounded floating cards.
Prose (first-aid answers, labels) stays in Inter, plain and undecorated —
Operate mode's own convention, content never wears the world's costume.

**STORY:** A user opens Chat and reads it like a ledger page: who's on
this page (a peer), what's been exchanged, stamped or still pending — a
grammar already legible to anyone who has ever held a savings passbook,
no onboarding needed. Pay reads as the actual ledger the metaphor is
named for: UPI/USSD actions are the primary stamped rows, the IOU sits
among them as one visually distinct counterfoil-edged row, never a
competing tab, matching the user's own instruction that it is secondary.
Assistant's existing knowledge-base browser becomes a reference-index
register — the printed rules page bound into the back of a real
passbook — while the generated answers themselves stay plain prose, never
performing the ledger's costume.

**FIRST VIEWPORT:** Chat tab. Top app bar "Sankat Setu." A left
perforation margin (a thin dotted spine with small circular notches) runs
down the screen's edge, the book-binding cue. Below the bar, "I'm Safe"
as a full-width stamped action, not a rounded pill. Peer list as ruled
ledger rows: nickname left, hop-count/status right-aligned in tabular
monospace like an amount column, a drawn stamp-circle glyph left of
confirmed/ready peers, a plain grayed line for offline ones. Empty state:
an unstamped blank ruled page, "Scanning for nearby phones…" set in the
same monospace a waiting ledger entry would use, not a spinner.

**FORM:** Post Office Passbook / Ledger Register — assigned by
`impeccable concept-seed --scope direction --mode operate` (seed key
`721b5aee`, assigned index 3 of the session's own researched, resonance-
ranked candidate list: ham radio operator's station, relief-camp
hand-written noticeboard, **post office passbook/ledger** [assigned],
Indian Railways split-flap board, telegram-wire brevity, NDRF/civil-
defense field signage, All India Radio bulletin typography). Presented to
the user against the session's own top-ranked candidate (ham radio, as
an "Impeccable's Pick" card, declined by the user on the honest grounds
that a radio metaphor has no natural payment analog and would leave UPI
feeling bolted-on) and a standing "polished category-standard" exit,
through the structured question tool (no confirmed image-generation tool
this session; no decision-page comp round). The catalog's six dealt
challengers (festival lineup poster, zoo/garden guide map, origami crane
fold sequence, iridescent cloud edge, Ikeda-style datamatics, film
cutting-bench select rail) were fused and judged and every one declined
on audience identification — none carry any tie to India or crisis
response — but two donated real raises to the assigned direction: state
shown as a physical stamp/mark rather than a color alone (from the
cutting-bench's grease-cross/tape-flag/pin vocabulary, which a real
passbook already does), and color confined to a narrow "active" band
while the body stays achromatic (from the iridescent-cloud edge), which
sharpened the decision to make postal-indigo the only stamp ink rather
than a full color-coded row treatment. User-driven, not dice-only: the
user's explicit constraint that the three offline pillars, not the IOU,
must read as primary reframed how the ledger world applies to Pay before
the direction was locked.

**FINISH:** unreviewed and undocumented is unfinished; this build ends
with the finish review, the verdict, DESIGN.md, and every shipping raster
carrying its provenance. No confirmed subagent capability with a browser
in this session for the finish-review/documenter roles; substituted
in-thread against `craft-floor.md` and `android.md`, disclosed here and
to the user, not silently.

## Unresolved decisions

- Exact stamp-circle Canvas geometry (perfect circle vs. hand-stamped
  imperfect wobble) — resolved during build in favor of a slight,
  consistent imperfection (rotation + radius jitter seeded per status)
  for authenticity, kept subtle enough not to read as a bug.
- Whether the perforation-margin motif appears on every screen or only
  list/register screens (Chat, Pay) — resolved during build: list/register
  screens only; the Assistant answer view and chat thread view are
  reading surfaces, not registers, and keep a plain margin.
