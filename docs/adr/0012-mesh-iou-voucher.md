# ADR 0012: Mesh IOU voucher — scope and what this app does not do

## Status
Accepted (Day 3). **The "What this app will never do by itself" section's
`CALL_PHONE`/`ACTION_CALL` claim is superseded as of 2026-09-20** by
`docs/adr/0025-qr-scan-to-pay.md` — written in a parallel session without
visibility into this ADR, then reconciled with the user's explicit
sign-off once the conflict was found during a master merge. `UssdDialer`
now uses `Intent.ACTION_CALL` and the app does request `CALL_PHONE`; see
ADR 0025 for why (two real live-carrier failures showed `ACTION_DIAL`
cannot carry a UPI VPA's letters through intact) and `handoff.md`'s
Payments section for the current, correct statement of the boundary. The
rest of this ADR (the IOU voucher's own scope and design) is unaffected
and still accurate.

## Context

docs/PRD.md §F3/F4 describes two payment features: real offline UPI payment
(via NPCI's `*99#` USSD channel and UPI 123Pay's IVR) and a mesh-signed IOU
voucher for when even those are unreachable. Both involve money, so the
scope boundary here matters more than most engineering decisions in this
project.

## What this app will never do by itself

Sankat Setu never executes a real financial transaction, and never dials a
phone number automatically. Concretely:

- **USSD/123Pay**: `PayScreen`'s two payment cards open the system dialer
  pre-filled with the code (`*99#` or the 123Pay IVR number) via
  `Intent.ACTION_DIAL` and stop there. The person must physically tap the
  call button themselves — the same as if they'd typed the number in by
  hand. The app never requests `CALL_PHONE` and never uses
  `Intent.ACTION_CALL`, which would place the call with no further
  confirmation.
- **IOU settlement**: `IouManager.markSettled()` is a manual, user-triggered
  status change. It does not transfer money, call a bank API, or verify a
  payment happened — it exists for the case where the person already paid
  the other party back through some real channel (UPI, cash, anything) and
  wants the mesh record to reflect that. See the class doc in
  `IouManager.kt`.

This isn't a missing feature so much as a considered boundary: an
automated agent (or a background service) initiating a real transfer or
phone call against someone's actual bank account or SIM, without a fresh,
physical action from that specific person for that specific amount, is not
something this codebase will do, hackathon deadline or not.

## What the mesh IOU voucher actually is

A cryptographically signed promise-to-pay, carried as a new
[MessageType.IOU_ENVELOPE] packet (0x41, reserved since Day 1) through the
exact same mesh transport as chat messages — same TTL/dedup/relay-jitter
behavior, so it reaches a nearby offline phone the same way a chat message
does, in seconds, hop by hop if needed.

- **Signing**: `IouPacket.signingBytes()` (id, amount, memo, timestamp — not
  the signature itself) is signed with the sender's existing
  Keystore-backed ECDSA key (`Identity.sign`, from Day 1's identity work).
- **Verification**: the receiver looks up the sender's signing public key
  from the `PeerEntity` row created when that peer's announce was first
  seen (which already carries `signingPublicKey` — see
  `AnnouncementPacket`), and calls the new `Identity.verifyWithPublicKey()`
  static helper (the existing `Identity.verify()` only checks against our
  *own* Keystore cert, useless for a peer's signature). An IOU that doesn't
  verify, or comes from a peer we've never seen an announce from, is
  dropped silently before it ever reaches the UI as money someone owes.
- **State machine** (deliberately smaller than the PRD's full
  `PENDING → SETTLING → SETTLED/REJECTED/SETTLEMENT_FAILED` machine, which
  assumes a real automated settlement attempt this app doesn't make):
  `pending → settled` (sender marks paid, `IOU_SETTLEMENT_ACK` notifies the
  receiver) or `pending → rejected` (receiver-only, local, "I don't
  recognize this debt" — no mesh message, matching the PRD's own note that
  a rejected IOU stays on the sender's side as-is until they notice).

## Privacy tradeoff, stated plainly

IOU envelopes are signed but **not** Noise-encrypted like 1:1 chat messages
are. Encrypting them would mean requiring an established Noise session
before the first IOU can be sent, adding a real dependency on
`ChatViewModel`'s session map from a different feature area. For Day 3
scope, an IOU's amount and memo travel in the clear (still tamper-evident
and non-forgeable thanks to the signature) to any device relaying it. This
is a real, documented gap, not an oversight — encrypting IOU envelopes
through the same Noise sessions chat already maintains is the natural next
step if this were to keep going past the hackathon.
