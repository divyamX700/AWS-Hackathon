# Concept: the BLE mesh protocol

This explains how `mesh/protocol/`, `mesh/router/`, and `mesh/transport/`
fit together, and exactly which parameters we ported from Bitchat's
whitepaper v2.0 (public domain — see `NOTICE.md`) versus what we simplified
for a 4-day build. Read this before touching any file in those packages.

## Why a mesh at all

A single Bluetooth Low Energy (BLE) connection only reaches ~10-30 meters.
That's useless for "the whole village can talk to each other." A mesh fixes
this: every phone is simultaneously a sender, a receiver, and a relay for
other people's messages, so a message can hop across several phones to
reach someone who was never in direct range of the sender.

## The three layers

```
mesh/protocol/   — what bytes mean (packet format, TLV payloads)
mesh/router/     — what to do with a packet (relay? dedup? to whom?)
mesh/transport/  — how bytes actually move (BLE advertising/scanning/GATT)
```

`MeshTransport` never makes a routing decision — it just hands raw bytes to
`MessageRouter.handleInboundBytes()` and calls `MeshLink.send()` when told
to. `MessageRouter` never touches `BluetoothGatt` — it only knows about the
abstract `MeshLink` interface. This separation is why Day 2's fragmentation
and courier-envelope work can be added to the router without touching a
single line of BLE-specific code.

## Wire format

```
+---------+------+-----+-----------+-------+--------+
| version | type | ttl | timestamp | flags | length |
| 1 byte  |1 byte|1byte|  8 bytes  |1 byte | 2 bytes|
+---------+------+-----+-----------+-------+--------+
+----------+--------------+---------+-----------+
| senderID | recipientID* | payload | signature*|
| 8 bytes  |   8 bytes    |variable |  64 bytes |
+----------+--------------+---------+-----------+
```
`*` = optional, presence indicated by a flag bit. See
`mesh/protocol/BinaryProtocol.kt` for the exact byte-for-byte codec.

`senderID` is the first 8 bytes of SHA-256(sender's Curve25519 static public
key) — see `mesh/crypto/Identity.kt`. This is **stable across sessions**
(doesn't rotate) until a panic wipe. That's a deliberate, documented
trade-off — see "Metadata leakage" below.

## Flood control (the part that keeps a mesh from melting down)

Every parameter below is taken directly from Bitchat's whitepaper v2.0 and
its `TransportConfig.swift` constants, not invented by us:

| Parameter | Value | Where enforced |
|---|---|---|
| Default TTL (hop budget) | 7 | `MeshPacket.DEFAULT_TTL` |
| Dense-graph TTL clamp | 5, when ≥6 live links | `MessageRouter.DENSE_BROADCAST_TTL_CAP` |
| Dedup window | 1000 entries / 5 min | `SeenMessageCache` |
| Relay jitter (sparse) | 10-220 ms | `MessageRouter.SPARSE_JITTER_MS` |
| Relay jitter (dense, ≥6 links) | 40-220 ms (widened, not narrowed) | `MessageRouter.DENSE_JITTER_MS` |
| Directed-traffic jitter | 5-40 ms (tight — no need to spread a 1:1 send) | `MessageRouter.DIRECTED_JITTER_MS` |
| Fanout subset size | ⌈log2(link count)⌉ | `FanoutSelector.subsetSize` |
| Announce interval | 4s isolated → 15-30s jittered once connected | `ChatViewModel.announceLoop` |
| Announce reachability window | 60s since last verified announce | not yet enforced — Day 2 TODO |

**Why jitter and subsetting at all?** If every phone relayed every broadcast
to every neighbor immediately, one message from N phones in range of each
other creates O(N²) redundant sends in the first few hundred milliseconds —
a "broadcast storm." Jitter spreads sends out in time so
`SeenMessageCache`'s dedup on the receiving end absorbs most of the
redundancy before it happens (a relay that sees a duplicate arrive during
its own jitter delay just... doesn't send — see the "SeenMessageCache
already filtered" comment in `scheduleRelay`). Subsetting spreads sends out
across *neighbors* so a dense mesh doesn't need every single link carrying
every single message to still achieve full coverage.

## Dual role: why Android's version is simpler than Bitchat's

iOS's Core Bluetooth stack can end up with *two* simultaneous connections to
the same peer — one where your phone is GATT-central writing to their
peripheral, one where they're GATT-central writing to yours. Bitchat's real
`BLEFanoutSelector.swift` has to actively collapse these duplicate links so
it doesn't send the same packet down both.

Android's `BluetoothDevice.connectGatt()` doesn't have quite the same
duplicate-connection problem in practice, so `MeshTransport.kt` sidesteps it
entirely with a MAC-address tie-break: when two devices can see each other
(both scanning, both advertising), only the one with the
lexicographically-lesser Bluetooth MAC address initiates the central-role
connection. This guarantees **at most one GATT connection per peer pair**,
which means `FanoutSelector` and `MessageRouter` never have to think about
duplicate links at all — a real simplification versus Bitchat's design, not
a corner we cut carelessly. See `docs/adr/0002` for the full reasoning.

## Metadata leakage (a known limitation, not a bug)

Because `senderID` never rotates and `AnnouncementPacket` broadcasts a
nickname and both public keys in cleartext (see `mesh/protocol/AnnouncementPacket.kt`),
a passive listener within radio range can:
- Recognize the same device across multiple sessions/reboots.
- Build a map of who's near whom, from the neighbor-list TLV.
- Estimate hop distance to the origin from the TTL value on a packet.

This is Bitchat's own documented trade-off (whitepaper §8/§9: "epoch-rotating
peer IDs" is listed as *future work*, not shipped even in the reference
implementation), and we inherited it rather than solving it, because solving
it properly (rotating IDs recognized only through a shared-secret tag) is a
real cryptographic design project, not a 4-day hackathon task. **Don't claim
in the demo video or writeup that this app provides anonymity** — it
provides confidentiality (nobody can read your messages) and no accounts/no
phone numbers, which is a different and true claim.

## Privacy trade-offs in padding

Only `NOISE_HANDSHAKE` and `NOISE_ENCRYPTED` frames get padded to fixed
buckets (256/512/1024/2048 bytes — `mesh/protocol/MessagePadding.kt`).
Public messages, announces, and (once they exist) SOS/IOU broadcasts go out
at their natural length. This means an observer can distinguish "this is
probably a short chat message" from "this is probably a long one" for
public traffic, but cannot correlate encrypted 1:1 message lengths to guess
content. Same trade-off Bitchat makes, for the same reason: padding
*everything* wastes meaningful airtime on a bandwidth-constrained BLE link,
and public broadcasts are already observable in full plaintext anyway — the
padding buys nothing there.

## Android background execution (a known limitation)

`mesh/transport/MeshForegroundService.kt` runs as a foreground service with
a persistent notification specifically because Android's battery-optimization
subsystems (especially on Xiaomi/Realme/OnePlus/Samsung's aggressive
variants) will kill a background process holding BLE connections within
minutes otherwise. A foreground service with a visible notification is the
platform's documented way to say "the user knows this is running and wants
it to keep running." This still isn't bulletproof on the worst OEM ROMs —
see `docs/PLAN.md`'s risk table: demo phones are stock Pixels, kept awake
with the screen on, specifically to sidestep this for the recorded video.
Production robustness against every OEM's background-kill heuristics is
explicitly out of scope (`docs/PRD.md` NG1).
