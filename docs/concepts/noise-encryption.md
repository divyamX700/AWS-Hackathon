# Concept: Noise Protocol encryption in this app

`mesh/crypto/NoiseSession.kt` wraps `com.southernstorm.noise-java`. This
explains what the Noise Protocol Framework actually is, why we use the `XX`
handshake pattern, and what "forward secrecy" buys us in a mesh where relays
are strangers' phones.

## What the Noise Protocol Framework is

Noise isn't one specific protocol — it's a small toolkit of handshake
*patterns* for establishing an encrypted, authenticated channel between two
parties, each pattern trading off different guarantees. It's the same
framework underlying WireGuard and Signal's underlying primitives. A pattern
name like `Noise_XX_25519_ChaChaPoly_SHA256` fully specifies: which
handshake pattern (`XX`), which elliptic curve for key agreement (Curve25519,
"25519"), which cipher for the encrypted transport (ChaCha20-Poly1305), and
which hash function (SHA-256).

## Why `XX` specifically

The `XX` pattern means: both sides send an ephemeral key, then both sides
send their static (long-term identity) key, encrypted under keys derived so
far. Three messages total:

```
Initiator -> Responder : e
Responder -> Initiator : e, ee, s, es
Initiator -> Responder : s, se
```

After message 3, both sides have mutually authenticated each other (each
now knows the other's long-term Curve25519 static key really did produce
this handshake) *and* derived a fresh symmetric transport key that depends
on both static keys **and** both ephemeral keys.

That "depends on ephemeral keys too" detail is what gives **forward
secrecy**: even if an attacker later steals your long-term static private
key, they cannot decrypt a session recorded during this handshake, because
reconstructing the session key also requires the ephemeral private keys,
which were generated fresh for this one session and are never stored
anywhere after the handshake completes.

Why this matters specifically for a disaster mesh: your phone might get
lost, seized, or physically compromised *after* an emergency — Noise `XX`
means messages sent during the emergency stay unreadable even if that
happens later.

## The one thing `XX` doesn't cover: offline mail

`XX` is a live, interactive handshake — both parties need to be online and
exchanging messages in real time. That's fine for direct chat when two
phones are both in range right now. It doesn't work for "I want to leave a
message for someone who isn't here" (Day 2's courier envelopes / store-and-
forward).

For that case, Bitchat (and eventually this app, Day 2) uses the one-way
`X` pattern instead: the sender encrypts directly to the *recipient's known
static public key*, in a single message, no round trip needed. This is
simpler and works offline, but **has no forward secrecy** — if the
recipient's long-term static private key is ever compromised, every piece
of sealed mail ever sent to that key (that's still sitting in someone's
courier storage, undelivered) becomes readable. This is a real,
acknowledged trade-off in Bitchat's own whitepaper (§5.2), not something we
introduced — and it's why Day 1's live chat (this file's actual scope) uses
`XX`, while Day 2's offline courier mail explicitly will not claim the same
guarantee.

## What a relay sees

A relay phone forwarding your `NOISE_ENCRYPTED` packet on to its next hop
sees:
- The outer `MeshPacket` header: sender ID, TTL, timestamp, recipient ID (if
  directed), packet type = `NOISE_ENCRYPTED`.
- An opaque ciphertext blob as the payload, padded to a fixed bucket size
  (see `docs/concepts/ble-mesh-protocol.md`'s padding section) so its exact
  length doesn't leak content length precisely.

It does **not** see: the plaintext message content, the sender's or
recipient's Noise session state, or anything that would let it decrypt the
payload even if it wanted to. The relay's job is purely "pass this sealed
envelope one hop closer" — this is the entire point of end-to-end
encryption over a mesh of untrusted intermediaries.

## Where the code lives

- `mesh/crypto/Identity.kt` — generates and persists the Curve25519 static
  keypair (for Noise) and the Ed25519 signing keypair (for signing
  announces/broadcasts — a separate concern from Noise entirely).
- `mesh/crypto/NoiseSession.kt` — one instance per peer, tracks handshake
  state, exposes `encrypt()`/`decrypt()` once established.
- `ui/chat/ChatViewModel.kt` — orchestrates *when* to start a handshake
  (on discovering a new one-hop peer via announce) and what to do with the
  three message types (`NOISE_HANDSHAKE` in both directions, then
  `NOISE_ENCRYPTED` for actual chat content).
