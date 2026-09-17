# ADR 0002: Vendoring and porting strategy

**Status:** Accepted
**Date:** 2026-09-18

## Context

The PRD calls for lifting code from several open-source projects wholesale:
Bitchat (Swift, public domain), Flowpay (Kotlin, Apache 2.0), Briar
(Kotlin/Java, GPLv3). The user has explicitly said license friction is not a
concern for this build — the priority is a working hackathon demo, with
attribution handled honestly in `NOTICE.md` rather than through license
compliance engineering.

## Decision

### What we cloned

`reference/bitchat`, `reference/flowpay`, `reference/briar` — shallow clones
under `reference/`, **gitignored**, not part of our source tree or our
commit history. They exist on the build machine purely as reading/porting
material. This keeps our repo's own git history clean (relevant to the
hackathon's Rule 05 — a submission's repo history has to match the event
dates; vendored reference repos with years of unrelated history must not be
merged in).

### What we ported vs. what we copied vs. what we referenced

| Source | Treatment | Why |
|---|---|---|
| Bitchat's `BinaryProtocol.swift`, `Packets.swift`, `MessageType.swift`, `BLEFanoutSelector.swift`, `TransportConfig.swift` constants | **Line-by-line ported to Kotlin**, same field layout, same numeric constants | These are algorithms/wire formats, not UI or Swift-specific code — a faithful port is both correct and honest about what we built ourselves (a Kotlin implementation) vs. what we're standing on (a public-domain protocol design) |
| Flowpay's payment module | **Copied wholesale as a git submodule / library**, Day 3 | It's Apache 2.0, it's a complete working implementation of a hard problem (bank SMS parsing, DTMF dialing), and the PRD explicitly wants Flowpay-parity payments, not a reimplementation |
| Briar's `bramble-android` foreground-service scaffolding | **Referenced for design, not copied** | GPLv3 — copying source would put the whole app under GPL, which the team hasn't decided to accept. We read it for the *pattern* (how Briar keeps BLE alive against OEM kills) and wrote our own `MeshForegroundService.kt` from scratch |
| MediaPipe LLM Inference Android sample | **Lifted wholesale as the Assistant module's starting point**, Day 2 | Apache 2.0, sample code is meant to be copied |
| cedar-java | **Standard Maven dependency**, Day 3 | Apache 2.0, no reason to vendor source |

The one boundary we *did* keep, despite "don't care about licenses": we did
not copy GPL source into this repo. Not because of a licensing audit, but
because mixing licenses sloppily is exactly the kind of thing that creates
real legal confusion for whoever inherits this project after the hackathon,
and it costs nothing to just write the ~150 lines of foreground-service
boilerplate ourselves instead.

### Why Bitchat's dual peripheral/central link-collapsing logic was NOT ported

Bitchat's real `BLEFanoutSelector.swift` handles the case where iOS's BLE
stack can hold *two* simultaneous connections to the same peer (we-as-central
writing to their peripheral, and them-as-central subscribed to ours) and
needs to collapse duplicate sends onto one link. Our Android transport
(`MeshTransport.kt`) enforces at most one GATT connection per peer via a MAC
address tie-break (see `MeshTransport.scanCallback`), so that whole
complexity category doesn't apply. This is documented inline in
`FanoutSelector.kt` so a future contributor reading Bitchat's source doesn't
wonder where the other half of the logic went.

### Known risk: `noise-java` Maven coordinate

`app/build.gradle.kts` depends on `com.southernstorm:noise-java:0.1.0`
(rweather/noise-java, the reference Java implementation of the Noise
Protocol Framework). This coordinate has **not been verified resolvable**
from Maven Central in this session (no Android SDK/Gradle available on the
authoring machine — see the session's own environment check). First build
action for whoever picks this up: run a Gradle sync and confirm the artifact
resolves. If it doesn't, the fallback is vendoring the ~15 source files
directly from the GitHub repo into
`app/src/main/java/com/southernstorm/noise/` — the package name in
`NoiseSession.kt` is written to make that swap a no-op for the rest of the
codebase.

### Known risk: Ed25519 in Android Keystore on API 29-32

`Identity.kt`'s `ensureSigningKey()` requests `"Ed25519"` from
`AndroidKeyStore`. Ed25519 Keystore support is guaranteed from API 33+; on
29-32 it depends on the OEM's Keymaster/KeyMint implementation and may throw
`NoSuchAlgorithmException`. **Not yet handled** — first Day 1 smoke-test task
is confirming this on an actual API 29/30 device, with the fallback being an
in-process (non-Keystore-backed) Ed25519 keypair via a pure-Kotlin
implementation (e.g. bundling a small Ed25519 library) if Keystore support
is absent. Tracked, not silently ignored.
