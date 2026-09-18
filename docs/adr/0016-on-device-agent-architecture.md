# ADR 0016: On-device agent architecture (Option A) + Bedrock bonus (Option B)

## Status

Accepted (Day 3)

## Context

Hackathon requires real use of the AWS "Build It" open-source stack to be
prize-eligible (confirmed against wemakedevs.org/aws/first-commit, not
just the original draft PRD): Strands, PartyRock, SAM CLI, LocalStack,
Firecracker, Corretto, OpenSearch, Cedar. The rule is lighter than the
PRD assumed — "using an AWS open-source project or service is mandatory to
win a prize," i.e. at least one, not all eight.

The user's explicit instruction: integrate for real, inside the app itself,
never a separate admin/gateway surface (superseding the original PRD's
laptop-side coordinator). PartyRock/Bedrock specifically cannot run
offline — it's a cloud model call, full stop, no engineering workaround
changes that. Given this app's core promise is offline-first, the
resolution agreed with the user:

- **Option A (this ADR's main content):** design the full agentic
  architecture — not just prompt wording — using PartyRock's free
  playground (Builder Center account, no billed AWS account needed) to
  validate the pipeline *shape*, then port that architecture to the
  existing on-device model. Fully offline, no exceptions, this is what
  ships as the real feature.
- **Option B (separate, deferred):** a real Bedrock Runtime API call,
  wired but only enabled with connectivity, kept for later. Needs a real
  billed AWS account + Cognito Identity Pool (steps given to the user
  directly, out of scope for this ADR).

## Why one call, not three round-trips

The original sketch (triage agent → action agent → comms agent, three
separate model calls chained) is the right shape for a large cloud model
with sub-second latency. It is the wrong shape for this app's actual
on-device model: a 1B-parameter model that already takes 12-20s for one
structured answer (see docs/adr/0011). Three sequential calls would mean
36-60s before a frightened, possibly injured person gets anything — a
regression against the latency work already done.

**Decision:** merge triage (what kind of situation is this) and action
(is a real app action warranted) into the *same* generation call that
already produces the structured guide, by asking for one extra
constrained line (`Action: NONE|BROADCAST_SAFE|OPEN_PAY`) at the end of
the existing format. This costs a few extra tokens, not a second
round-trip. The comms/drafting stage stays genuinely separate, because
it's optional and most questions never need it — folding it in would tax
every answer for a feature most turns don't use. It only runs when the
person taps "Draft a message to share."

## Why only two possible actions

`SuggestedAction` is `NONE | BROADCAST_SAFE | OPEN_PAY` — deliberately not
a general tool-calling surface with an open set of actions (the original
PRD's F2.4 sketch: `search_kb`, `broadcast_sos`, `list_shelters`,
`compose_iou`). Those don't all exist as real app capabilities today
(there's no SOS composer, no shelter list). The agent only ever offers
actions the app can actually, honestly perform: broadcasting "I'm safe"
(existing feature, docs/adr/0014) and opening the Pay tab (existing
screen). A model deciding to *suggest* a fabricated action would be worse
than not suggesting anything.

**Neither action ever fires automatically.** The model's output is always
read as a suggestion; the UI renders it as a tappable chip the person must
press. This matters specifically for `BROADCAST_SAFE` — it sends a real
message to real people, and an agent should never do that on its own
initiative from a single, possibly-misread turn.

## What changed

- `AssistantEngine.answer()`: prompt gains an `Action:` line requirement;
  the response is parsed with a regex, the line is stripped before display,
  and `AssistantAnswer.suggestedAction` carries the result. Malformed or
  missing action lines default to `NONE` — a parsing failure never blocks
  the answer itself from showing.
- `AssistantEngine.draftShareableMessage(question, answer)`: new, separate
  call, only invoked on request. Returns null if no model is loaded (the
  UI simply doesn't offer the "Draft a message" button's result in that
  case — no fake fallback for a feature that's explicitly a bonus).
- `AssistantViewModel`: `AssistantTurn` gains `suggestedAction`, `draft`,
  `isDrafting`; `requestDraft(turnId)` drives the second stage.
- `AssistantScreen`: an `AssistChip` renders the suggested action (when
  present) under a generated answer; a "Draft a message to share" button
  triggers the second stage and shows the result with a copy-to-clipboard
  action — no auto-send, no cross-tab prefill plumbing (clipboard is the
  lazy-correct choice per the ladder: paste into any Chat thread yourself).
- `MainActivity` wires the two real actions: `onBroadcastSafe` calls the
  existing `ChatViewModel.broadcastImSafe()`; `onOpenPay` switches the
  bottom-nav tab to Pay.

## What this pass did not do

PartyRock's actual playground validation is the user's own step (their
Builder Center account, not something I can drive). Bedrock/Option B
wiring is deferred, tracked separately, needs the user's AWS console setup
first. Cedar and Corretto integration are separate, not covered here.
