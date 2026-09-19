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

**Day 3 slice**:

- **Mesh**: BLE transport, binary wire protocol, TTL/dedup/jitter/fanout
  routing, Noise `XX`-encrypted 1:1 chat, packet fragmentation, a sender
  outbox, WhatsApp-style delivery ticks (queued/sent/delivered/read), a live
  connectivity indicator, and a write-based heartbeat that detects a dead
  BLE link in ~10-15s instead of waiting on Android's own (much slower, or
  sometimes never-firing) supervision timeout. **Verified with a real
  multi-router integration test (JVM, in-memory links) and on single real
  hardware** — actual two-phone-in-range mesh discovery/chat has **not**
  been field-tested; see `handoff.md` §6, this remains the single
  highest-risk untested surface in the app. (An earlier version of this
  README claimed two-phone verification and cited a `docs/adr/0010` that
  does not exist in this repo — that claim was never actually true and is
  corrected here; ADR numbering genuinely jumps 0009 → 0011, unexplained.)
- **Assistant**: a real on-device LLM (Qwen2.5-0.5B-Instruct, int8, via
  MediaPipe's LLM Inference API) answering from a genuine hybrid **BM25 +
  TF-IDF** retriever over a from-scratch, plain-text crisis-management
  knowledge base (22 documents — first aid, CPR, burns, fractures, natural
  disasters, water purification, psychological first aid, poisoning,
  emergency childbirth, and more — see `docs/knowledge-base/`). Falls back
  to an honest extractive answer (the retrieved passage verbatim) if no
  model is side-loaded or a generation comes back empty — never fabricates.
  See `docs/adr/0011-on-device-llm-model-choice.md` for the model research
  (why not Qwen3-0.6B, the toolchain constraints that shaped the final
  pick) and courier envelopes / neural embeddings as the honestly-deferred
  next step.
- **Pay tab**: USSD (`*99#`) and UPI 123Pay dial cards (open the system
  dialer pre-filled, never auto-dial — see `docs/adr/0012`), plus a
  mesh-signed IOU voucher (promise-to-pay, ECDSA-signed, delivered and
  verified over the same mesh transport as chat, settled manually since this
  app never executes a real financial transaction itself).
- Persistence (Room+SQLCipher, real migrations only) and a three-tab
  Compose UI (Chat, Pay, Assistant).

- **SOS broadcast**: a one-handed, category-only (no free typing) flooded
  emergency broadcast, the counterpart to "I'm Safe" — unsigned and
  un-Noise-encrypted on purpose, since it's meant to reach and be
  readable by every phone in range, including a stranger with no
  completed handshake. Press-and-hold to send, an interrupting dialog
  plus a reviewable log to receive. See `docs/TODO.md` for the full
  reasoning and honest gaps (a real two-phone delivery of this is
  untested, same limitation as the rest of the mesh).

Not yet built: courier envelopes (relaying via a third party's phone),
Nostr bridge, gateway coordinator, an SOS acknowledgment reply. Cedar
authorization **is** built and verified on-device (see `docs/adr/0017`)
— this line used to list it as not-yet-built; that was corrected once
the real cross-compile landed.

**Verified building and running, not just compiling**: `./gradlew
assembleDebug` succeeds (~120 MB APK — grew from an earlier ~57 MB once
the real Cedar native `.so` for both ABIs was added, see `docs/adr/0017`;
MediaPipe's native libs also included); `./gradlew testDebugUnitTest`
passes all 67 unit tests, 0 failures —
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

**Verified on real single-device hardware** (a Moto G57 Power across
several sessions, plus a Nothing CMF Phone 1 and a Samsung/Nothing-class
Android 16 phone in earlier sessions per prior handoffs): app launch, BLE
`MeshForegroundService` genuinely running, the on-device LLM generating
real answers grounded in the knowledge base, the current "Post Office
Passbook / Ledger Register" design (`docs/adr/0019` — the app has shipped
three visual worlds across its history, see that ADR and `DESIGN.md` for
the current one), and real Cedar policy authorization decisions evaluated
by a native engine on-device (`docs/adr/0017`). **Not verified**: two
phones actually discovering and chatting with each other
over BLE in range of one another. The Pay tab's IOU protocol logic is unit
tested and the UI verified with real (temporarily seeded, then removed)
data on-device; a live two-phone IOU send/receive/verify pass is still
pending physical hardware access to a *second* device.

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

The Assistant tab works out of the box with no setup (BM25+TF-IDF retrieval
over the 22-document knowledge base, extractive answers). To get real
on-device LLM-generated answers, side-load the model file (not bundled in
the APK — see `docs/adr/0005`):

```bash
curl -L -o qwen2.5-0.5b-instruct-q8.task \
  "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
adb push qwen2.5-0.5b-instruct-q8.task \
  /sdcard/Android/data/com.sankatsetu.app/files/models/qwen2.5-0.5b-instruct-q8.task
```

See `docs/adr/0011-on-device-llm-model-choice.md` for why this exact model
and file variant (not Qwen3-0.6B, not one of the other `.task` variants on
that page) — the short version: it's the only combination that both fits
under 1B params for fast on-device inference and actually loads with this
project's pinned `tasks-genai` version (`0.10.20`, itself constrained by the
Day 1 JDK11 toolchain pin — see `docs/adr/0006`).

## Repo layout

```
app/                    the Android app
  src/main/java/com/sankatsetu/app/
    mesh/
      protocol/         binary wire format, TLV packets, fragmentation — pure Kotlin, unit-tested
      crypto/           identity keys (ECDSA + Curve25519), Noise XX sessions
      router/           TTL/dedup/jitter/fanout/fragment/outbox dispatch — pure Kotlin, unit-tested
      transport/        BLE advertising/scanning/GATT, foreground service
      authz/            Cedar-based flood-control gate (CedarAuthorizer) — see docs/adr/0017
    assistant/          offline knowledge retrieval + MediaPipe LLM wrapper — mostly pure Kotlin, unit-tested
    data/               Room entities/DAOs, SQLCipher wiring
    di/                 hand-rolled composition root (AppContainer)
    ui/                 Compose screens + ViewModels (Chat, Assistant)
  src/main/assets/kb/   starter first-aid/disaster knowledge base (JSON)
  src/main/assets/cedar/ Cedar policy set + schema for the mesh flood-control gate
  src/test/             JVM unit tests — no device needed
docs/
  PRD.md                full product spec
  PLAN.md                day-by-day build plan
  adr/                  architecture decision records
  concepts/             protocol/crypto explainers
gateway/                Strands Agents SDK + Ollama reference agent mirroring the on-device
                        assistant pipeline — see gateway/README.md; satisfies the hackathon's
                        "use a real AWS open-source tool" requirement alongside Cedar/Corretto
scripts/                setup scripts for Corretto/Android SDK/Cedar cross-compile/Strands+Ollama
                        (network-dependent installs, see scripts/README.md)
reference/               gitignored clones of Bitchat/Flowpay/Briar used for porting — not part of this repo
```

## Attribution

This project deliberately builds on several open-source projects — see
[`NOTICE.md`](NOTICE.md) for the full list and what was ported vs. referenced
vs. imported wholesale, and `docs/adr/0002-vendoring-and-porting-strategy.md`
for the reasoning behind each choice.

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).
