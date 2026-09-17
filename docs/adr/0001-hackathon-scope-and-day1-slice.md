# ADR 0001: Hackathon scope and the Day 1 slice

**Status:** Accepted
**Date:** 2026-09-18 (pre-kickoff prep)

## Context

`docs/PRD.md` specifies seven features (F1-F7: mesh chat, on-device LLM,
offline UPI payments, mesh IOU, Cedar authorization, Nostr bridge, gateway
coordinator). Building all seven at once, in parallel, with a small team and
a 4-day clock, historically produces seven half-finished things instead of
one working thing plus stubs.

## Decision

Follow `docs/PLAN.md`'s day-by-day order **strictly**, not the "scaffold
everything in parallel" alternative. Concretely for Day 1:

**In scope for Day 1:**
- Gradle project scaffold, package layout matching PRD §8's architecture diagram.
- Full binary wire protocol (`mesh/protocol/`): packet header, TLV
  announce/private-message payloads, PKCS#7-style padding — ported faithfully
  from Bitchat's `BinaryProtocol.swift` / `Packets.swift` (public domain).
- BLE transport (`mesh/transport/`): advertising, scanning, dual GATT
  server/client role, one link per discovered peer.
- Router (`mesh/router/`): TTL clamp, LRU dedup, jitter, deterministic fanout
  subset selection — ported from `TransportConfig.swift` /
  `BLEFanoutSelector.swift`.
- Noise `XX` handshake + transport encryption for 1:1 chat
  (`mesh/crypto/NoiseSession.kt`, wrapping `com.southernstorm.noise-java`).
- Minimal Room persistence (peers + messages only) with SQLCipher, following
  Flowpay's Keystore-wrapped-passphrase pattern.
- Minimal Compose UI: peer list + one thread view. No tabs, no navigation
  library — that's premature for a two-screen Day 1 app.

**Explicitly deferred:**
- Store-and-forward (courier envelopes, sender outbox) — Day 2.
- Fragmentation for payloads over one BLE write — Day 2.
- Gossip sync / public history — Day 2.
- On-device LLM, payments import, IOU, Cedar, Nostr, gateway — Days 2-3 per PLAN.md.

## Consequences

- Every file in the Day 1 slice is either a faithful protocol port (checkable
  against `docs/concepts/ble-mesh-protocol.md`'s parameter table) or has an
  explicit `// Day 2` marker where something was deliberately left out. There
  should be no silent gaps — if a whitepaper-documented behavior is missing,
  a comment says so and points at the day it lands.
- The two-phone one-hop encrypted chat gate (PLAN.md Day 1) is the sole
  Day 1 acceptance bar. Multi-hop, courier delivery, and everything else are
  *not* Day 1 blockers.
- This means Day 1's code is real and mergeable, not throwaway scaffolding —
  Day 2 builds directly on `MessageRouter`/`MeshTransport` rather than
  replacing them.
