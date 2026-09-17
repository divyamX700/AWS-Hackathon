# Sankat Setu — crisis bridge, no internet needed

Built for **First Commit** (Bharat Builds Tour, Sept 17-20, 2026), competing
in the **Build It** track.

An Android app that keeps a village or city block functioning during a
crisis when the internet and cell networks are down: a Bluetooth-mesh chat
network, offline UPI payments (plus a signed mesh IOU for when even those
rails are down), and an on-device LLM that answers "what do I do now" from a
packaged first-aid / disaster-response knowledge base.

Full product spec: [`docs/PRD.md`](docs/PRD.md). Day-by-day build plan:
[`docs/PLAN.md`](docs/PLAN.md). Architecture decisions and their reasoning:
[`docs/adr/`](docs/adr/). Protocol/crypto deep-dives: [`docs/concepts/`](docs/concepts/).

## Status

**Day 1 slice** (per `docs/adr/0001-hackathon-scope-and-day1-slice.md`):
BLE mesh transport, binary wire protocol, TTL/dedup/jitter/fanout routing,
Noise `XX`-encrypted 1:1 chat, minimal Room+SQLCipher persistence, minimal
Compose UI (peer list + one thread). Two phones in airplane mode + Bluetooth
on should be able to discover each other and exchange encrypted messages.

Not yet built: on-device LLM assistant, payment import, mesh IOU, Cedar
authorization, Nostr bridge, gateway coordinator. These land Day 2-3 per
`docs/PLAN.md` — this README will be updated as each lands.

**Verified building and running**: `./gradlew assembleDebug` succeeds and
produces a real `app-debug.apk` (~40 MB); `./gradlew testDebugUnitTest`
passes all unit tests (12/12, 0 failures) — protocol codec round-trips
(including ECDSA's variable-length signatures) and the dedup cache. The APK
was installed and launched on a real API 34 emulator: it does **not**
crash, `MainActivity`'s window reaches `reportedDrawn=true` (confirmed via
`dumpsys activity` — Compose actually renders), and the permission flow
fires correctly. This caught and fixed two real bugs before they became
Day-2 blockers — see `docs/adr/0006-jdk11-toolchain-downgrade.md` (this
build machine's JDK 17+ can't open an NIO Selector at all, forcing a
downgrade to AGP 7.4.2/Gradle 7.6.4/Kotlin 1.9.24/JDK 11 — **only relevant
if you hit the identical "Unable to establish loopback connection" error**;
most machines won't and can bump the toolchain back up freely) and
`docs/adr/0007-ecdsa-not-ed25519.md` (Ed25519 isn't available from
AndroidKeyStore in practice — even on API 34 — so the identity signing key
is ECDSA/P-256 instead, which also meant fixing the wire protocol's
signature field from fixed-64-bytes to length-prefixed).

**Not yet verified**: BLE mesh discovery/pairing between two real phones —
an emulator has no Bluetooth radio, so this genuinely needs physical
hardware. Everything else in the Day 1 slice has been verified running, not
just compiling.

## Running it

```bash
git clone <this repo>
cd SankatSetu
# Open in Android Studio, let Gradle sync.
# Or from the command line, once local.properties points at your SDK:
./gradlew assembleDebug
```

If Gradle fails with `java.io.IOException: Unable to establish loopback
connection` / `Unable to establish loopback connection`, see
`docs/adr/0006-jdk11-toolchain-downgrade.md` — this is a machine-specific
JDK/Windows bug, not a project bug, and that ADR has the diagnosis and fix
(pin `org.gradle.java.home` to a JDK 11 install in your own
`~/.gradle/gradle.properties`, not the project's).

Requires two Android 10+ (API 29+) devices with Bluetooth LE for the mesh
demo — an emulator has no real Bluetooth radio, so mesh discovery/pairing
can only be verified on physical hardware. No AWS account, no server, no
internet connection needed for Day 1's chat feature.

Day 2's Assistant tab will additionally require side-loading a Gemma model
file — see `docs/adr/0005-model-assets-not-committed.md` once that lands.

## Repo layout

```
app/                    the Android app
  src/main/java/com/sankatsetu/app/
    mesh/
      protocol/         binary wire format, TLV packets — pure Kotlin, unit-tested
      crypto/           identity keys, Noise XX/X sessions
      router/           TTL/dedup/jitter/fanout dispatch — pure Kotlin, unit-tested
      transport/        BLE advertising/scanning/GATT, foreground service
    data/               Room entities/DAOs, SQLCipher wiring
    di/                 hand-rolled composition root (AppContainer)
    ui/                 Compose screens + ViewModels
  src/test/             JVM unit tests (protocol + router — no device needed)
docs/
  PRD.md                full product spec
  PLAN.md                day-by-day build plan
  adr/                  architecture decision records
  concepts/             protocol/crypto explainers
gateway/                 Day 3: Strands agent + SAM Local + OpenSearch coordinator (not yet built)
reference/               gitignored clones of Bitchat/Flowpay/Briar used for porting — not part of this repo
```

## Attribution

This project deliberately builds on several open-source projects — see
[`NOTICE.md`](NOTICE.md) for the full list and what was ported vs. referenced
vs. imported wholesale, and `docs/adr/0002-vendoring-and-porting-strategy.md`
for the reasoning behind each choice.

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).
