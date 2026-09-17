# ADR 0003: Declaring INTERNET, unlike Flowpay

**Status:** Accepted
**Date:** 2026-09-18

## Context

Flowpay's headline privacy claim is "no `INTERNET` permission — the app
*cannot* talk to a server, full stop." Sankat Setu's PRD (§7.5) calls for a
Nostr bridge (F6) that publishes SOS reports and IOU settlements to public
relays whenever a phone reaches connectivity, and for a gateway coordinator
that receives them anywhere in India.

## Decision

Sankat Setu declares `INTERNET` and `ACCESS_NETWORK_STATE` in the manifest,
used **only** by the Nostr client (Day 3), and never by the mesh, LLM, or
payment code paths, which remain zero-internet exactly as in Flowpay.

## Why this is still honest

- The BLE mesh, the on-device LLM, and the UPI payment flows work completely
  offline and never construct a socket. That's provable by code inspection
  (grep for `Socket`, `HttpURLConnection`, `OkHttp` outside
  `mesh/nostr/` once that package exists in Day 3).
- The Nostr bridge is a genuine, disclosed feature — "connectivity returns"
  global reach — not a hidden telemetry channel. The writeup and demo video
  both name it explicitly.
- We are not claiming "no INTERNET permission" as a badge the way Flowpay
  does. We're claiming "works fully offline; gets *better*, not required,
  when connectivity returns" — a different and equally honest claim.

## Consequences

The judging writeup (`docs/PRD.md` §12) must not copy Flowpay's "zero
INTERNET permission" framing verbatim — it doesn't apply to this app. The
correct framing is in this ADR's "why this is still honest" section above.
