# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

People in an Indian disaster-affected area (flood, cyclone, earthquake,
landslide) where cell towers and data are down or overloaded, but their
phone still has battery and Bluetooth. Primary scenario: finding out if
family nearby are okay, getting first-aid/evacuation guidance, and paying
for something small (auto fare, water, a shop) with no signal at all.
Also: a concerned relative outside the affected area (deferred — no
internet-bridge feature is currently built; see Capabilities).

## Product Purpose

Sankat Setu keeps three things working with zero internet and zero cell
signal, in one Android app: phone-to-phone chat over a Bluetooth mesh
(multi-hop relay through other nearby phones), a real UPI payment path via
USSD/IVR dialing, and a signed offline IOU voucher for when even that's
unreachable, plus an on-device LLM that answers first-aid/survival
questions from a bundled knowledge base with no network call ever. Success
is a stranger in a disaster zone reaching a neighbor's phone, getting
correct guidance, and settling a debt later — not a demo checkbox.

## Positioning

Every mainstream messaging/payment app (WhatsApp, GPay, PhonePe) is a thin
client over an IP network and is fully dead the moment towers go down.
Sankat Setu's mechanism — Bluetooth mesh relay, so a message reaches
someone beyond direct radio range by hopping through other phones running
the app — is what a neighboring app cannot truthfully claim without
rebuilding its transport layer from zero. This is the product's real,
defensible difference, not a UI treatment.

## Operating Context

Used one-handed, often in poor light, by someone who may be frightened,
injured, or moving. Phones may be at low battery. Bluetooth range is
~30-100m; reaching further requires other phones relaying, so "hop count"
is a real, user-relevant fact, not decoration. Sessions are often short and
interrupted. The user did not choose this moment — it chose them.

## Capabilities and Constraints

Built and working today (verified on real hardware, single-phone/self-loop
tested; two-phone multi-hop mesh not yet field-tested, see
[handoff.md](handoff.md)):

- BLE mesh chat: peer discovery, Noise-encrypted 1:1 threads, hop-count
  display, sender outbox (queues if no peer is present), delivery-status
  ticks.
- On-device LLM assistant (MediaPipe, local model file not committed to
  git): structured multi-turn first-aid/survival Q&A grounded in a 22-file
  bundled knowledge base, hybrid BM25+TF-IDF retrieval. A question that
  doesn't match the knowledge base (a greeting, small talk) still gets a
  real generated reply instead of a refusal — it just isn't presented as
  knowledge-base-grounded guidance.
- USSD/IVR buttons that open the system dialer pre-filled for `*99#` real
  UPI payment (Android does not allow apps to auto-dial; the user taps the
  actual call button themselves — this is a platform constraint, not a
  missed feature).
- Mesh IOU: a signed, offline "I owe you ₹X" promise sent over Bluetooth
  when no payment rail is reachable at all. Explicitly NOT money movement —
  a bookkeeping/trust record, manually marked settled later. See
  handoff.md §5 for the exact scope and why this distinction matters for
  any demo claim.
- SOS broadcast: a category-only (no free typing), press-and-hold
  emergency report, flooded unencrypted to every phone in range rather
  than the directed encrypted send chat/IOU use — the point is a stranger
  relaying it can still read it. The counterpart to "I'm Safe," built
  2026-09-20; see `docs/TODO.md` for the reasoning and honest gaps (a real
  two-phone delivery of this is untested, same limitation as the rest of
  the mesh).

Not built / aspirational only (present in the original PRD, out of current
scope): Cedar-authorized channels, Nostr internet-bridge for reaching
relatives outside the affected area, the laptop-side field-coordinator
gateway dashboard, an SOS acknowledgment reply, multi-language UI beyond
English. Do not assume any of these exist in code.

Constraints: `compileSdk`/`targetSdk` pinned to 34 (not 35 — a real
toolchain incompatibility, see `docs/adr/0006`), JDK 11, `minSdk` 29, no
DI framework (hand-rolled `AppContainer`), no backend/server of any kind —
nothing in this app calls the internet except optional future
connectivity-return features, which do not exist yet.

## Brand Commitments

Name: **Sankat Setu** ("crisis bridge," Hindi/Sanskrit) — working title,
not user-tested, but load-bearing enough (used throughout code, docs, and
the hackathon submission) to treat as fixed unless the user says otherwise.
No existing logo, wordmark, or visual identity — this is genuinely
uninitiated visual ground, not an incomplete brand to reverse-engineer.

## Evidence on Hand

Real, working: 20-file offline first-aid knowledge base
(`app/src/main/assets/kb/docs/`), real BLE mesh transport with unit tests,
real Noise-protocol encryption, a real signed IOU wire format
(`IouPacket.kt`), real USSD dialing tested by the user on his own bank/SIM.
No user research beyond the team's own reasoning from published disaster
case studies (Hurricane Sandy Red Cross app usage data, IMD's four-stage
alert taxonomy) — no interviews with actual disaster-affected users. State
this honestly in any demo material; do not invent user quotes or adoption
numbers.

## Product Principles

1. **Offline is the default case, not the fallback.** Every screen must
   work correctly with zero connectivity; a network-returns path is a
   bonus, never a requirement for core function.
2. **Clarity beats flair under stress.** The target user may be frightened,
   injured, or moving one-handed. Every decision trades toward
   immediately-parseable over impressive.
3. **Don't invent a payment rail.** The USSD/IVR path is real bank-backed
   money movement; the mesh IOU is a signed promise, never blurred into
   looking like a transfer.
4. **The mesh is a radio, not a chat gimmick.** Hop count, relay, and
   reachability are real physical facts about Bluetooth range the UI should
   surface honestly, not hide behind a generic "online/offline" dot.
5. **Say what isn't tested.** Two-phone mesh, USSD across different banks,
   and any claim beyond what's been run on real hardware gets flagged, not
   assumed, in both code comments and demo narration.

## Accessibility & Inclusion

TalkBack reachable primary actions, 48dp minimum touch targets, font scale
honored to 200%, high-contrast in both light/dark — stated goals in the
original PRD, not yet verified against the current build. English-only UI
currently; Hindi was PRD-scoped but not implemented.
