---
version: 1
slug: "app"
primary_target: "app"
related_targets: []
---

## Scope and visitor mode

Whole-app visual world replacement, Operate mode (task completion, not
persuasion) — Chat, Pay, Assistant tabs, native Jetpack Compose / Material 3.

## Audience, job, action, proof, constraints

Someone in a disaster-affected area in India with no cell signal, phone in
hand, possibly frightened or moving. Job: confirm a nearby person is safe,
get first-aid guidance, pay for something small, settle a debt later.
Constraint: Material 3 governs structure/navigation/interaction on Android
(non-negotiable per platform reference); brand expresses through Material's
theming only.

## Direction contract

**THESIS:** Sankat Setu's mesh is a radio relay wearing a generic chat
UI — hop count is already radio-relay language, just not dressed as one.
This surface refuses the category default (a calm-blue Material chat app
indistinguishable from WhatsApp) and instead treats the phone as a field
radio: a channel/peer list, signal-strength hop indicators, and India's own
IMD four-stage alert taxonomy (Green/Yellow/Orange/Red) as the app's
semantic color language, not an invented palette.

**OWN-WORLD:** Material 3 structure throughout (mandatory). Color roles
carry IMD's four states — Green (ready/safe), Yellow (connecting/watch),
Orange (degraded/caution), Red (offline/danger, reserved for genuine
danger, never decorative) — mapped onto Material's tonal system, not raw
hex swapped in. A monospace face (JetBrains Mono, sourced not
system-default) is reserved strictly for the "instrument reading" register:
peer IDs, hop counts, timestamps, signal readouts — never body prose or
first-aid answer text, which stays in the system sans per Operate-mode
convention. Peer connection state reads as a signal-bar icon (1-4 bars)
colored by the IMD scale, replacing a plain colored dot.

**STORY:** A user glances at the Chat tab and reads it like a radio
operator reads a channel list: who's in range, how many hops away, signal
quality at a glance, not a colored dot they have to interpret fresh. IMD
colors communicate severity/status instantly because the user already
trusts that system from TV and SMS weather alerts — no new visual language
to learn under stress. A single-tap "I'm Safe" broadcast (from the American
Red Cross case study: the most load-bearing pattern for panic-state
cognitive load) sits where it can be found without reading, not buried in
a menu.

**FIRST VIEWPORT:** Chat tab. Top app bar "Sankat Setu". Below it, a
prominent "I'm Safe" action (broadcasts to all connected peers). Peer list
styled as a channel roster: each row shows nickname, a short identity tag,
a 1-4 bar signal glyph colored via the IMD scale (not a dot), hop count and
status in monospace ("2 HOPS · RELAYED" / "1 HOP · READY"), long-press to
forget. Empty state: "Scanning for nearby phones…" with the same signal-bar
glyph animating at zero bars, not a generic spinner.

**FORM:** Field Radio + IMD Alert Colors — chosen 2026-09-18 by the user
from a compact decision round (three directions offered: this fusion,
a "Field Manual / civil-defense signage" alternate, and a restrained
evolution of the prior blue/red Material redesign) plus a confirmed
addition ("I'm Safe" broadcast). No `concept-seed`/decision-page script was
run: this is native Android with real-device screenshot verification and
no confirmed image-generation tool in this session, making the web
comp/decision-page pipeline impractical, and the user's explicit
instruction this session was "do not deploy subagents, do everything
yourself." Substituted real domain research (American Red Cross Emergency
App UX case study, India's NDMA/IMD four-stage alert taxonomy, Zello/PTT
app conventions) plus a structured `AskUserQuestion` round covering the
same decision — disclosed here and to the user at the time.

**FINISH:** unreviewed and undocumented is unfinished; this build ends
with the finish review, the verdict, DESIGN.md, and every shipping raster
carrying its provenance. Finish review substituted in-thread (no subagent
spawn, per the user's explicit instruction this session) against
craft-floor.md and android.md, disclosed as a substitution, not silently.

## Unresolved decisions

- Exact monospace face licensing/bundling (JetBrains Mono, Apache 2.0 —
  confirmed available, bundle as a font resource).
- Whether "I'm Safe" needs its own Room table/wire message type, or reuses
  the existing broadcast/public-message path — resolved during build.
