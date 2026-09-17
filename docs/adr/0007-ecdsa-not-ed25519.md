# ADR 0007: Signing key is ECDSA/P-256, not Ed25519

**Status:** Accepted
**Date:** 2026-09-18

## Context

The Day 1 design (docs/PRD.md, `Identity.kt`'s original implementation)
called for an Ed25519 signing key generated inside the Android Keystore,
alongside the Curve25519 key used for Noise. This was flagged as an open
risk in `docs/adr/0002` ("Known risk: Ed25519 in Android Keystore on API
29-32") — but not yet fixed, because there was no way to test it until the
project actually ran on a device or emulator.

## What we found once we could actually run it

Installing and launching the app on a real API 34 emulator (the first time
this project ever ran, not just compiled) crashed immediately on startup:

```
java.lang.RuntimeException: Unable to create application com.sankatsetu.app.SankatSetuApplication:
java.security.NoSuchAlgorithmException: no such algorithm: Ed25519 for provider AndroidKeyStore
  at com.sankatsetu.app.mesh.crypto.Identity$Companion.ensureSigningKey(Identity.kt:106)
```

This is a stronger finding than the ADR 0002 risk note anticipated: Ed25519
is unavailable from `AndroidKeyStore` **even on API 34** (not just the
29-32 range originally flagged), at least on this Google APIs x86_64 system
image's Keymaster/KeyMint implementation.

We also checked whether `noise-java` (already a dependency, for Curve25519)
could supply an Ed25519 signing primitive as a software fallback — it
cannot. Its `com.southernstorm.noise.crypto` package implements only
Curve25519 and Curve448 (Diffie-Hellman curves, for Noise's own handshake),
with no EdDSA signature scheme at all.

## Decision

Switch the identity signing key from Ed25519 to **ECDSA on the P-256 curve**
(`SHA256withECDSA`, `KeyProperties.KEY_ALGORITHM_EC` with
`ECGenParameterSpec("secp256r1")`). ECDSA/P-256 has been supported by
`AndroidKeyStore` since API 18 — universally available across this
project's entire minSdk 29 - targetSdk 34 range, hardware-backed on any
device with a Keymaster/KeyMint implementation (which is all of them, since
it's the same curve used for standard TLS/HTTPS key exchange).

## A consequence we had to fix in the same pass: signature length

Ed25519 signatures are always exactly 64 bytes — the original wire protocol
(`MeshPacket`, `BinaryProtocol`) hardcoded a fixed 64-byte signature field
with no length prefix, matching Bitchat's own assumption. **ECDSA/P-256
signatures are DER-encoded and vary in length** (typically 68-72 bytes,
depending on whether the DER integer encoding of R and S needs a leading
zero byte to stay non-negative). A fixed 64-byte field would have silently
truncated every signature — corrupting it, and every peer's signature
verification would fail without any error message pointing at why.

Fixed by changing the signature field from fixed-size to length-prefixed
(1 byte length + N bytes signature) in `BinaryProtocol.kt`. Added
`BinaryProtocolTest`'s `variable-length ECDSA-sized signature round-trips`
test, covering 68/70/71/72-byte signatures explicitly, so this doesn't
regress silently if someone later "simplifies" the format back to a fixed
size.

## Consequences

- `Identity.sign()` / `Identity.verify()` now use `SHA256withECDSA`.
  `AnnouncementPacket`'s `signingPublicKey` field carries an X.509
  SubjectPublicKeyInfo-encoded EC public key instead of a raw 32-byte
  Ed25519 key — larger (~91 bytes vs 32), but this only affects the
  announce TLV payload size, not anything performance-critical.
- Anywhere `docs/PRD.md` or `docs/PLAN.md` says "Ed25519," read it as
  "the identity signing key" — the choice of curve/algorithm was always an
  implementation detail of that concept, not a load-bearing design decision
  those documents depend on. We are not doing a global find-replace across
  900 lines of planning docs for a primitive swap; this ADR is the source
  of truth for the actual algorithm in use.
- Verified end-to-end after this fix: app launches on the API 34 emulator
  without crashing (see the Day 1 verification notes in `README.md`).
