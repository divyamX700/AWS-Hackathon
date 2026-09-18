# ADR 0014: Field-radio design language, IMD alert colors, "I'm Safe"

## Status

Accepted (Day 3)

## Context

ADR 0013 fixed concrete bugs (emoji icons, red-everywhere, duplicate
peers). This pass went further on explicit request: research real
crisis-app and adjacent design patterns, drop attachment to the prior
session's own choices, and build a visual identity from that research
rather than continuing to iterate on an invented palette.

## Research basis

- **American Red Cross Emergency App** (UX case study, Hurricane Sandy
  usage data): the operative crisis-UX principle is clarity over flair,
  offline-first content, and — the single most load-bearing pattern found
  — a one-tap **"I'm Safe"** broadcast that removes typing from the
  panic-state path entirely.
- **India's IMD (Meteorological Department) four-stage alert scale** —
  Green (no warning) / Yellow (watch) / Orange (alert) / Red (warning) —
  published on every monsoon/cyclone advisory nationally. Real,
  government-recognized, and already correctly read by the target user
  from TV and SMS alerts, unlike an invented app palette.
- **NDMA's own Sachet portal** (sachet.ndma.gov.in), inspected directly as
  an anti-reference: dated clip-art illustration, boxed bureaucratic
  layout — confirms what to avoid, not what to imitate.
- **Zello and PTT/walkie-talkie app conventions** (channel lists, signal
  strength, battery/connection iconography) — a legitimate reference
  because Sankat Setu's Bluetooth mesh *is* a radio relay; hop count was
  already radio-relay language, just not dressed as one.

## Decisions

1. **Field Radio + IMD Alert Colors** as the committed direction: the app
   reads like a field radio's channel list and status panel, colored by
   the IMD scale instead of an invented palette. Chosen by the user from a
   compact decision round (this direction vs. a "Field Manual / civil
   defense signage" alternate vs. a restrained evolution of ADR 0013's
   palette) — see full reasoning and the disclosed substitution for
   Impeccable's `concept-seed`/decision-page pipeline in
   `.impeccable/surfaces/app.md`'s direction contract. That pipeline
   assumes a web decision page and image generation; this is native
   Android with real-device screenshot verification and no confirmed
   image-gen tool, and the user's explicit instruction this session was to
   do all work in-thread, no subagents — so directions were derived from
   the research above and confirmed via a structured question round
   instead.
2. **`primary` = IMD Green**, not an invented brand blue: the mesh working
   correctly *is* the app's normal operating state, so its everyday chrome
   (buttons, focus, selected tab) earns the color that means exactly that.
   `error` = IMD Red, reserved for genuine danger only (carried over from
   ADR 0013's own rule, now grounded in a real external standard).
   `tertiary` = IMD Orange for caution/pending. IMD Yellow has no natural
   Material role between primary and tertiary, so it's used as a direct
   color reference for "connecting" states only.
3. **Signal bars, not a colored dot**, for peer connection state — a drawn
   four-bar glyph (`SignalBars.kt`) whose fill count and tint read like an
   actual radio's signal meter, scaled from hop count.
4. **A reserved monospace register** (JetBrains Mono, OFL 1.1) for peer
   IDs, hop counts, timestamps, and status words only — never body prose,
   per craft-floor's ban on monospace as a generic "technical" costume.
5. **"I'm Safe" broadcast** added to the Chat tab: one tap sends "I'm
   safe." to every peer with an established encrypted session, reusing the
   existing per-peer messaging path rather than a new wire message type.
6. **IOU status recolored to the same IMD scale** as peer status
   (settled=green, pending=orange, rejected=red) — one vocabulary across
   tabs instead of a per-screen palette.

## What this pass found by actually looking (not assumed)

Screenshots off Phone A during this pass surfaced three real, functional
bugs, not aesthetic ones — see `DESIGN.md`'s "Real bugs this pass found and
fixed" section for full detail: Material's stock purple leaking through on
the IOU card (unset container color roles), the Assistant tab's question
field and send button rendering entirely hidden behind the app's own nav
bar on first launch (an empty-state `Column` using `fillMaxSize()` instead
of `weight(1f)`), and the keyboard not dismissing after sending a
question (fixed after a direct user report on this same build).

## What this pass did not do

No `impeccable-finish-reviewer` subagent was spawned — the user's explicit
instruction this session was to do all work in one thread, no subagents.
The finish review (craft-floor.md + android.md conformance check) was done
in-thread against real-device screenshots instead, disclosed here as that
substitution. No formal `impeccable audit` numeric score was produced. Two-
phone mesh testing (and therefore the "ready" signal-bar state at 2+ hops)
remains unverified — see `handoff.md`.
