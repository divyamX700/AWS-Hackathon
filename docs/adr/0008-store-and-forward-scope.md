# ADR 0008: Sender outbox now, courier envelopes deferred to Day 3

**Status:** Accepted
**Date:** 2026-09-18

## Context

`docs/PLAN.md`'s Day 2 scope includes "store-and-forward (courier envelopes,
sender outbox)." These are two related but distinct mechanisms in Bitchat's
design (see `docs/concepts/ble-mesh-protocol.md` and the whitepaper §6):

- **Sender outbox**: *I* hold onto a message I'm trying to send, and keep
  retrying it myself once the recipient (or a path to them) reappears.
- **Courier envelopes**: a *third party* — someone else's phone, physically
  moving through the world — carries a sealed message on my behalf to a
  recipient neither of us may ever be simultaneously in range of. This needs
  one-way sealed encryption (Noise `X` pattern, or an equivalent), a rotating
  recipient tag so couriers can't learn who mail is for, spray-and-wait
  budget halving, and trust-tiered quotas.

## Decision

Implemented the sender outbox in full this session — `SenderOutbox.kt`,
wired into `MessageRouter.sendDirected`/`retryOutbox`, with the exact
Bitchat parameters (100 messages/peer, 24h TTL, 8 attempts) and a passing
integration test (`MessageRouterOutboxTest`) proving a message sent with
*zero* mesh links queues instead of vanishing, and delivers once
`retryOutbox` is called after a link appears.

**Courier envelopes are deferred to Day 3.** Reasoning:

1. **It needs real one-way sealed encryption we don't have yet.**
   `NoiseSession.kt` only implements the `XX` pattern (interactive,
   both-sides-present handshake) — that's the wrong shape for "seal a
   message to someone who isn't here." Bitchat uses one-way Noise `X`.
   Implementing this properly (not just bolting on the existing `XX`
   session type) is real, non-trivial crypto work — see the follow-up ADR
   this will get once it's built.
2. **The quota/spray/tier logic is substantial on its own.** Bitchat's real
   `CourierStore` (ported for reference in
   `reference/bitchat/bitchat/Services/Courier/CourierStore.swift`, read in
   full while researching this) has per-depositor quotas split by trust
   tier, oldest-first eviction that never lets verified-tier mail crowd out
   favorites, spray-and-wait budget halving with rollback-safe accept
   semantics, and disk-merge logic for a process restart mid-carry. Doing
   this correctly (not a token gesture) is a full day's work on its own.
3. **The sender outbox already delivers the primary user-facing value.**
   "My message waits for the recipient instead of vanishing" is the
   behavior most people would actually notice; "a stranger's phone might
   carry my message to someone neither of us can currently reach" is a
   real and valuable mesh property, but a secondary one for a hackathon
   demo where the three demo phones are typically all in range of each
   other or one hop apart, not genuinely partitioned for hours.

## What Day 3 needs to add

- One-way sealed encryption: either a proper Noise `X` session type
  alongside `NoiseSession`'s existing `XX`, or (simpler to implement
  correctly under time pressure) a dedicated `SealedBox` primitive using
  `noise-java`'s `Curve25519` for ECDH + HKDF-SHA256 + the JDK's built-in
  `ChaCha20-Poly1305` `Cipher` — the same security shape as libsodium's
  `crypto_box_seal`, whether or not it's byte-for-byte Noise `X`. Whichever
  is chosen, document it plainly rather than implying Noise-`X`
  compatibility if it isn't literally that.
- `CourierEnvelope` protocol struct (TLV, rotating recipient tag —
  `HMAC-SHA256(recipientStaticKey, "context" || epochDay)`, truncated to 16
  bytes, checked against yesterday/today/tomorrow to tolerate clock skew and
  midnight boundaries — exactly as in the Swift reference).
- `CourierStore` with Bitchat's quota shape (40 total / 20 verified-tier cap
  / 5 per favorite depositor / 2 per verified depositor), deposit, handover
  (on encountering the recipient), and spray-and-wait (on encountering
  another courier) — as a testable pure-Kotlin class first, Room persistence
  wrapper second, matching how `SenderOutbox` was built this session.
- Wiring into `MessageRouter`: when a directed send's recipient isn't
  reachable via any link *and* couriers are available, seal and deposit
  instead of (or in addition to) queuing in the sender outbox.

## Consequences

The current mesh already has a working, tested "wait for the recipient"
mechanism. Anyone demoing before Day 3 lands should describe courier
envelopes (multi-hop-via-strangers store-and-forward) as designed and
partially referenced but not yet implemented — not claim it works.
