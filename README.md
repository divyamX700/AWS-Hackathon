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

**Day 2 slice** (per `docs/adr/0001-hackathon-scope-and-day1-slice.md` and
its Day 2 follow-ons):

- **Mesh**: BLE transport, binary wire protocol, TTL/dedup/jitter/fanout
  routing, Noise `XX`-encrypted 1:1 chat, packet fragmentation for payloads
  over one BLE write, and a sender outbox so a message queued for an
  unreachable peer waits instead of vanishing (courier envelopes — relaying
  via a *third party's* phone — deferred to Day 3, see
  `docs/adr/0008-store-and-forward-scope.md`).
- **Assistant**: a real, working offline Q&A tab — keyword/term-overlap
  retrieval (not yet the neural-embedding search the PRD describes) over a
  small hand-written first-aid/disaster corpus, with the wiring already in
  place for real on-device Gemma generation via MediaPipe once a model file
  is side-loaded. See `docs/adr/0009-on-device-assistant-scope.md` for
  exactly what's real vs. deferred here.
- Persistence (Room+SQLCipher) and a two-tab Compose UI (Chat, Assistant).

Not yet built: payment import, mesh IOU, courier envelopes, Cedar
authorization, Nostr bridge, gateway coordinator. Day 3 per `docs/PLAN.md`.

**Verified building and running, not just compiling**: `./gradlew
assembleDebug` succeeds (~57 MB APK, MediaPipe's native libs included);
`./gradlew testDebugUnitTest` passes all 44 unit tests, 0 failures —
including a genuine multi-router mesh integration test (two and three
`MessageRouter`s wired together via in-memory links, proving encode →
fragment → relay → reassemble → dedup → deliver end to end, with real
multi-hop relay) and an outbox integration test proving a message queued
with zero mesh links delivers once a link appears. The built APK was
installed and launched on a real API 34 emulator in both a permissions-
denied and a permissions-granted run: no crash either way, and with
Bluetooth permissions granted, `MeshForegroundService` is confirmed
actually running (`isForeground=true` via `dumpsys activity services`) —
not just "didn't crash," genuinely alive.

This process caught four real bugs before they shipped, each documented in
an ADR: a build-machine JDK/Windows networking bug that forced a toolchain
downgrade (`docs/adr/0006`), Ed25519 being unavailable from AndroidKeyStore
in practice plus the resulting fixed-vs-variable-length signature bug
(`docs/adr/0007`), a `MutableSharedFlow` replay-timing bug in a test that
mirrors a real footgun for any late-attaching collector, and — found only
by actually running the Day 2 build on a device — `MainActivity` crashing
on Android 14 by starting a Bluetooth-typed foreground service without
checking whether Bluetooth permissions were actually granted, not just
requested (`docs/adr/0009`'s bug note).

**Not yet verified**: BLE mesh discovery/pairing between two real phones —
an emulator has no Bluetooth radio, so this genuinely needs physical
hardware. Everything else has been verified running, not just compiling.

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
internet connection needed for the mesh or Assistant features.

The Assistant tab works out of the box with no setup (offline keyword
retrieval over the starter corpus). To get real on-device LLM-generated
answers instead of the extractive fallback, side-load a Gemma model file —
see `docs/adr/0005-model-assets-not-committed.md` and
`docs/adr/0009-on-device-assistant-scope.md` for the expected path
(`MediaPipeLlmAssistant.defaultModelPath`) and exact filename.

## Repo layout

```
app/                    the Android app
  src/main/java/com/sankatsetu/app/
    mesh/
      protocol/         binary wire format, TLV packets, fragmentation — pure Kotlin, unit-tested
      crypto/           identity keys (ECDSA + Curve25519), Noise XX sessions
      router/           TTL/dedup/jitter/fanout/fragment/outbox dispatch — pure Kotlin, unit-tested
      transport/        BLE advertising/scanning/GATT, foreground service
    assistant/          offline knowledge retrieval + MediaPipe LLM wrapper — mostly pure Kotlin, unit-tested
    data/               Room entities/DAOs, SQLCipher wiring
    di/                 hand-rolled composition root (AppContainer)
    ui/                 Compose screens + ViewModels (Chat, Assistant)
  src/main/assets/kb/   starter first-aid/disaster knowledge base (JSON)
  src/test/             JVM unit tests — no device needed (44 tests)
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
