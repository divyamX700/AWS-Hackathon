# TODO / parked discussions

Things raised in conversation, deliberately not acted on yet, kept here so
they don't get lost. Not a task tracker for routine work — just the "let's
come back to this" pile. Remove an item once it's actually decided/built
(and gets its own ADR/concept doc) or explicitly dropped.

## Open

- **On-device LLM grounding regression — see `docs/adr/0021-llm-grounding-regression.md`.**
  Not resolved: three different real questions got the same generic
  wound-cleaning answer even after a real, confirmed prompt-overflow bug was
  fixed. A `matches.take(1)` mitigation shipped but was never re-verified on
  a device (it disconnected mid-session). Re-run the three questions in that
  ADR on real hardware before treating this as closed.

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

- **SOS broadcast — baseline built 2026-09-20, real gaps remain.** The
  ideation below (kept for its reasoning) is now implemented: `SosPacket`
  (`mesh/protocol/SosPacket.kt`, unsigned/unencrypted, a fixed 5-category
  enum, never free text, never touches the LLM), `SosManager`
  (`mesh/emergency/SosManager.kt`, mirrors `IouManager`'s own independent
  `inboundApplicationPackets` collector rather than routing through
  `ChatViewModel`), a `sos_alerts` Room table (migration 3→4), and Chat-tab
  UI (`ChatScreen.kt`'s `SosReportSection`/`HoldToSendRow`/`SosLogRow`,
  `MainActivity`'s global `SosInterruptDialog`). Placement: collapsed row
  on the Chat tab above "I'm Safe," a `hazardEdge` stripe border and
  correction-ink red distinguishing it from the calm green stamp button
  below — the user's own call after weighing this file's placement
  options. Press-and-hold (1.1s fill bar, not a circular countdown ring —
  simpler to implement reliably under real testing time, equally legible)
  replaces a single tap. Verified on real hardware this pass: send →
  local log entry → confirmation text → survives an app restart (Room
  migration and insert both genuinely committed, not just in-memory
  state); a quick tap does not send. **Not verified**: a real two-phone
  delivery (the interrupt dialog, the Cedar 5/minute cap actually
  throttling relay, an unknown sender showing "Unknown device") — same
  single-device limitation as the rest of the mesh, see `handoff.md` §6.
  Also not built: an `SOS_ACK` reply type (so a sender could see their
  SOS was seen) and any local send-rate limit (Cedar's cap only throttles
  *relaying* a flood at other nodes, not this device's own repeated
  sends — a user can locally spam their own log, which nothing currently
  stops).
