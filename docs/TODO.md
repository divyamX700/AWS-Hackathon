# TODO / parked discussions

Things raised in conversation, deliberately not acted on yet, kept here so
they don't get lost. Not a task tracker for routine work — just the "let's
come back to this" pile. Remove an item once it's actually decided/built
(and gets its own ADR/concept doc) or explicitly dropped.

## Open

- **Online fallback from the on-device LLM to a bigger, Strands-powered
  model.** Idea: the phone always uses the small on-device Qwen2.5-0.5B
  (MediaPipe) when offline, same as today, but if the phone *does* have
  connectivity, it could call out to a more capable model via a real
  Strands agent instead. **Does not exist today** — see
  `gateway/README.md`: the Strands/Ollama reference agent
  (`gateway/agent/`) is a fully separate, disconnected laptop CLI script
  with no server and no code path from the Android app to it, under any
  connectivity condition. Building this for real would need: (1) an actual
  HTTP endpoint wrapping `gateway/agent/main.py`'s logic (it's currently a
  one-shot CLI, not a server), (2) a network client added to the Kotlin
  app (new territory — today only the not-yet-built Nostr bridge is
  scoped to ever use `INTERNET`, see `docs/adr/0003`), (3) a connectivity
  check to decide which path to use, (4) a decision on what "bigger model"
  even means here (Strands+Ollama needs a machine to run on — is that the
  user's own laptop tethered somehow, or does this only make sense once a
  real coordinator/server exists, which is explicitly out of scope per
  the user — see `docs/PRD.md`/handoff.md's "no central coordinator"
  stance). Worth scoping properly before building anything.

- **An SOS broadcast, the counterpart to "I'm Safe."** One-tap "I'm Safe"
  exists; a "help, something is wrong" equivalent does not, despite real
  groundwork already sitting unused in the protocol:
  `MessageType.SOS_BROADCAST` (`0x40`) is a reserved wire byte with no
  packet payload format built for it, Cedar's policy already has an
  `"sos"` resource kind capped at 5/minute per sender
  (`app/src/main/assets/cedar/policies.cedar`), and
  `ChatViewModel.observeInbound()`'s `else -> Unit` branch silently drops
  any SOS packet that arrived today. `MainActivity.kt` still has a stale
  comment about an "SOS tab" from the original PRD that was never built.
  Ideation so far, not yet decided or built:
  1. **Not a copy of "I'm Safe."** "I'm Safe" only reaches peers with a
     completed Noise handshake — a deliberate, private, directed send.
     SOS wants the opposite: flooded multi-hop reach to everyone in
     range, the same way `ANNOUNCE` already floods, regardless of
     whether a handshake ever happened. Probably shouldn't be
     Noise-encrypted at all, since the point is anyone nearby (including
     a stranger relaying it) can read it.
  2. **Content**: a category picker (Medical / Trapped or can't move /
     Fire / Flood-water / Other), not free typing — matches
     PRODUCT.md's "used one-handed, may be frightened or injured"
     constraint. Must never route through the LLM: instant and
     deterministic, working even with no model side-loaded, unlike the
     Assistant's own generation path.
  3. **Sending needs more friction than "I'm Safe," not less.** A false
     "I'm Safe" is harmless; a false SOS wastes attention and could
     cause real panic. A press-and-hold with a visible countdown (the
     same convention iOS/Android already use for their own emergency
     SOS) resists an accidental tap in a way a single button can't.
  4. **Receiving needs its own surface**, not a chat-thread row —
     something that actually interrupts (this may be the one legitimate
     modal-worthy moment in the whole app) and a small reviewable log,
     since more than one person could be sending SOS at once.
  5. **Honest limitation**: no GPS/location exists anywhere in this app.
     An SOS can say what's wrong, not where, beyond hop count (a real
     physical fact — fewer hops means physically closer — but not a
     location). State this plainly in the UI rather than implying more
     precision than exists.
  6. **Placement undecided**: a genuinely separate tab (closer to the
     original PRD's intent) vs. a persistent small affordance reachable
     from every tab (since someone in danger might be on Pay or
     Assistant, not Chat, when they need it) — leaning toward NOT
     stacking a second emergency-colored button next to "I'm Safe" on
     the Chat tab, since two competing high-alert actions on one screen
     is exactly the cognitive-load problem the Red Cross UX research
     (cited earlier this session) warns against. Needs a decision before
     building.
