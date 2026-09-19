# Handoff: Sankat Setu

Written 2026-09-19 (supersedes the earlier 2026-09-19 version — that one is
still readable in git history at commit `e73a3bd` if you want the earlier
snapshot, and the one before that at `000357b`). Read this fully before
touching code. Every claim here reflects actual tested state, not
aspiration — where something is untested, partially done, or broken, it
says so plainly.

**GitHub**: https://github.com/divyamX700/AWS-Hackathon — everything
described here is pushed and current as of commit `4fdf572`. Working tree
was clean at the time this handoff was written.

**This handoff was written specifically because work is moving to a new
coding environment/machine.** Read §8 first if you're setting up fresh —
it's the fastest path to a working build.

## 1. Hackathon context — confirmed against the real rules page

- Event: "Bharat Builds Tour" / "First Commit", wemakedevs.org/aws/first-commit.
- Project name: **Sankat Setu** ("crisis bridge" — Hindi/Sanskrit).
- Platform: native Android (Kotlin, Jetpack Compose), offline-first by
  design — the whole pitch is crisis response with zero connectivity.
- Competing in the **Build It** track: "open source, on your machine — no
  AWS account, no card, no bill."
- **The real Build It tool list**: Strands Agents SDK, PartyRock, SAM CLI,
  LocalStack, Firecracker, Corretto, OpenSearch, Cedar — 8 tools.
- **The actual judging rule**: *"Using an AWS open-source project or AWS
  service is mandatory to win a prize."* One tool, not all eight.
- **Current status: 3 of 8 tools (Corretto, Cedar, Strands) are genuinely
  verified working on real hardware** — this comfortably satisfies the
  "at least one" rule. See §4a for full detail; this is a fundamentally
  different (and much stronger) position than earlier handoffs, which
  reported zero tools integrated.

## 2. The pitch / what we're building

A phone-to-phone crisis app for disaster scenarios where cellular/internet
is down but phones still have Bluetooth. Three pillars, all real and
working (see §4):

1. **Bluetooth mesh chat** — multi-hop relay through nearby phones, no
   internet, no central server.
2. **Offline payments** — real UPI money movement via USSD `*99#`/IVR
   123Pay (a genuine bank-backed rail), plus a **mesh IOU voucher** (a
   signed offline promise-to-pay, explicitly NOT money movement — see §5).
3. **On-device AI assistant** — a local LLM answering first-aid questions
   from a bundled knowledge base, a real 3-stage agent (triage,
   action-suggestion, message-drafting) — see §4.

All three are now gated at the mesh-router level by **real, on-device
Cedar authorization** (flood/blocked-peer control) — see §4a.

Target user: someone in a flood/earthquake/cyclone-affected area in India
where cell towers are down but phones have battery and are physically
near each other.

## 3. Current architecture

```
app/src/main/java/com/sankatsetu/app/
├── mesh/
│   ├── transport/    BLE GATT client+server, MeshForegroundService
│   ├── protocol/     wire format: packet framing, fragmentation, IouPacket
│   ├── crypto/       Identity (ECDSA keypair), NoiseSession, NicknameStore
│   ├── authz/         CedarAuthorizer + MeshAuthorizer interface — real
│   │                    on-device Cedar policy evaluation (docs/adr/0017)
│   └── router/       MessageRouter — hop relay, dedup, TTL, and now the
│                        Cedar flood/blocked-peer gate, all at the one real
│                        choke point every packet passes through
├── data/              Room DB: MessageEntity/Dao, PeerEntity/Dao, IouEntity/Dao, AppDatabase
├── assistant/         KnowledgeBaseLoader, KnowledgeDocumentParser, KnowledgeChunk,
│                       KnowledgeRetriever (BM25+TF-IDF), AssistantEngine (3-stage
│                       on-device agent), MediaPipeLlmAssistant
├── payments/          UssdDialer, IouManager
├── di/                AppContainer — hand-rolled DI, owns singletons + knowledgeBase + cedarAuthorizer
└── ui/
    ├── chat/          ChatListScreen (MeshSearchingCard/MeshActiveCard hero
    │                    dashboard, "I'm Safe" broadcast, editable nickname),
    │                    ChatThreadScreen, ChatViewModel
    ├── pay/           PayScreen, PayViewModel
    ├── assistant/      AssistantScreen (agent UI + Docs browser), AssistantViewModel
    ├── components/     SignalBars, MeshRadar (new — pulsing radar sweep),
    │                    StatusPill (new — shared status vocabulary)
    ├── theme/          Color.kt, Theme.kt, Type.kt (Inter + JetBrains Mono),
    │                    Shapes.kt (new), Motion.kt (new — spring presets + pressScale)
    └── MainActivity.kt  bottom-nav shell, 3 tabs, app-wide Back handling

gateway/               Strands Agents SDK + Ollama reference agent (Python,
                       standalone, mirrors AssistantEngine's pipeline — see
                       gateway/README.md)
scripts/               setup scripts for a new machine — see §8
```

Also at the project root: **`PRODUCT.md`** (product truth) and
**`DESIGN.md`** (visual-system tokens/components — **rewritten this
session**, read it fresh even if you've seen it before, the whole palette
changed).

**Key non-obvious facts a new agent needs:**

- `compileSdk`/`targetSdk` pinned to **34, not 35** — `docs/adr/0006`.
- **JDK 11 pinned**, specifically **Amazon Corretto 11**. This pin lives
  in the machine-local `~/.gradle/gradle.properties`, NOT in the repo —
  **on a new machine you must re-pin this yourself**, see §8.
- No Hilt/Dagger/Koin — hand-written `AppContainer.kt` (`docs/adr/0002`).
- `minSdk = 29`.
- No backend server. Nothing calls the internet.
- LLM model file **not committed to git** (`docs/adr/0005`) — side-loaded
  via `adb push`. See README's "Running it" section for the exact command.
- **Typography**: Inter (bundled, `res/font/inter_variable.ttf`) for all
  prose, JetBrains Mono for the instrument-panel register only. See
  `DESIGN.md`.
- **The Cedar native `.so` is committed** (`app/src/main/jniLibs/`, ~16MB
  arm64-v8a + ~11MB armeabi-v7a) — you do **not** need to redo the Rust
  cross-compile to build/run the app. You'd only need
  `scripts/build-cedar-ffi.sh` again if rebuilding that `.so` from scratch
  (e.g., a new Cedar version, or if it goes missing from the working tree).
- **A patched `cedar-java` jar is committed** at
  `app/libs/cedar-java-4.3.1-methodparams-stripped.jar` — the unmodified
  Maven jar cannot be dexed by any D8 available on the last dev machine
  (a real upstream R8/D8 bug, see `docs/adr/0017`). Don't replace it with
  the plain Maven dependency without re-verifying this is fixed upstream.
- **`settings.gradle.kts` has a load-bearing Guava fix** — a
  content-filtered repository override that forces `com.google.guava:guava`
  to resolve from its plain Maven POM instead of Gradle Module Metadata.
  Without this, the app **crashes on real-device launch** with
  `IncompatibleClassChangeError` the moment Cedar's `EntityTypeName.toString()`
  runs. Full diagnosis in `docs/adr/0017`. Do not "simplify" this away.

## 4. What is actually built and verified working (on real hardware)

All tested this session on a real connected Android 16 phone (Nothing
CMF-class device). Two-phone mesh testing still **has not been done** —
see §6, this remains the single highest-risk untested surface in the app.
(An earlier README claimed two-phone verification with specific phone
models and a citation to a non-existent `docs/adr/0010` — that claim was
false and has been corrected in this session's README update. Take any
claim of "verified on two phones" in git history before this correction
with real skepticism.)

- **BLE mesh chat**: transport, Noise encryption, router, outbox, editable
  nickname, "I'm Safe" broadcast — all unchanged in behavior from prior
  sessions, now gated by real Cedar authorization (see below) and wrapped
  in a substantially revamped UI (see next bullet).
- **Complete UI/UX revamp** (`docs/adr/0018-ui-revamp.md`) — this is the
  single biggest change this session besides the AWS integration work.
  Two rounds:
  1. First round: a token-level design system (new dark-first palette,
     Inter typography, tighter shapes, spring motion) applied to the
     existing screen layouts.
  2. **User feedback was direct and correct**: "this looks exactly like
     whatever was before... very basic, wireframe kind of build." A
     token-level reskin without changing what a screen *contains* reads
     as a reskin, not a redesign. Second round added `MeshRadar.kt` (a
     pulsing radar sweep replacing a static icon for the mesh's
     "searching" state) and a proper bento-style status dashboard on the
     Chat tab (`MeshSearchingCard`/`MeshActiveCard`) — structural
     additions, not recoloring.
  3. **Verified with populated data, not just empty screens**: a
     temporary debug seed (added and removed, never shipped) populated
     fake peers/IOUs so the populated Chat list and Pay tab could
     actually be screenshotted. This is what the demo video will actually
     show, and it's a materially different (and harder) bar than an empty
     inbox — worth remembering as a QA pattern.
  4. **A real crash found by this seeding**: opening a chat thread for a
     fake peer threw `IllegalArgumentException: bad base-64` —
     `ChatViewModel.onThreadOpened()` decodes the peer ID unconditionally,
     and the fake seed's IDs weren't valid base64. Confirmed as a
     test-harness artifact, not reachable by real data, but exactly the
     class of bug on-device testing catches that code review doesn't.
  5. Also fixed on direct request: the Assistant tab's icon was
     `SmartToy` (a cartoon robot head) — swapped for a sparkle
     (`AutoAwesome`), matching Apple's own AI-feature glyph and this
     app's existing on-device-generated-content marker.
  6. **Not verified**: light theme (device stayed in dark mode all
     session), the rename/forget-peer dialogs (reuse verified components,
     not individually screenshotted). See `DESIGN.md`'s "Known gaps."
- **On-device LLM Assistant**: unchanged 3-stage agent behavior from prior
  sessions (`docs/adr/0016`) — triage/action-suggestion merged into the
  main answer, separate on-request message-drafting, both fully offline.
- **Docs browser, back navigation, message timestamps**: unchanged from
  prior sessions.

## 4a. AWS Build It integration — verified on real hardware

| Tool | Status | Detail |
|---|---|---|
| **Corretto** | 🟢 Verified | Amazon Corretto 11.0.32, pinned via machine-local `~/.gradle/gradle.properties` (`docs/adr/0006`). `./gradlew -version` confirms `JVM: 11.0.32.1 (Amazon.com Inc.)`. |
| **Cedar** | 🟢 Verified, real hardware, real decisions | Native `libcedar_java_ffi.so` cross-compiled from real `cedar-policy` source and **committed** for both ABIs. Wired into `MessageRouter.handleInboundBytes()`. A temporary on-device self-test (32 rapid authorization calls) proved the real native engine correctly allowed calls 1-30 and denied call 31 against the `PUBLIC` rate-limit policy — matching the policy text exactly, not a JVM-test fake. Three real bugs found and fixed getting here (full diagnosis in `docs/adr/0017`): no host linker on the dev machine (MinGW-w64 + GNU Rust toolchain), an upstream R8/D8 crash on `cedar-java-4.3.1.jar`'s `MethodParameters` attribute (fixed via a committed patched jar), and a Guava Gradle-variant-selection bug that crashed the app on first real-device launch (fixed via a `settings.gradle.kts` repository override — **do not remove this**, see §3). |
| **Strands Agents SDK** | 🟢 Verified | `gateway/agent/` — a real `strands.Agent` + `OllamaModel` mirroring `AssistantEngine.kt`'s pipeline, running against `qwen2.5:0.5b-instruct` (the closest Ollama tag to the on-device model). Produces real, correctly-grounded generations. A real bug found and fixed: an early version gave the model a tool and told it to decide when to retrieve — a 0.5B model did this unreliably; fixed to match the Kotlin engine's actual design (retrieval is a guaranteed deterministic step, never a model judgment call). See `gateway/README.md`. |
| **PartyRock** | 🔴 Not done — needs the user in a browser at partyrock.aws. |
| **SAM CLI** / **LocalStack** | 🔴 Blocked on Docker (not installed on any dev machine used so far). |
| **Firecracker** | ⛔ Not applicable — Linux/KVM-only, dev machines have been Windows. |
| **OpenSearch** | 🔴 Not started — no Docker needed for the standalone distribution, most tractable remaining option if a 4th tool is ever wanted. |

**One open item**: `strands-agents` was installed into the dev machine's
system-wide Python 3.12, not a venv, and pip reported conflicts with
unrelated already-installed packages (`gradio`, `fastapi`, `streamlit`,
`langchain-chroma`). On a fresh machine, just use a venv from the start
(`python -m venv gateway/.venv`, already gitignored) to avoid this
entirely.

## 5. Known problems, open questions, things that don't fully work

- **Two-phone mesh has never been tested.** Still the single highest-risk
  untested surface — see §1's correction of a prior false README claim.
- **Cedar has not been tested on a *second* connected device** — the
  32-call self-test proves the native engine works, but a real flood
  scenario over an actual BLE link (not a synthetic loop) is untested.
- **Light theme is untested on real hardware** — see §4/`DESIGN.md`.
- **Mesh IOU is NOT a payment feature** — still true, still worth
  restating to avoid misrepresenting it to judges.
- **USSD PIN-then-balance-only issue (OPEN, unexplained)** — unchanged
  from prior handoffs, not touched this session.
- **LLM occasionally produces a wrong/hallucinated fact** — flagged in a
  prior session (a snake-bite query once generated an incorrect,
  unrelated claim about rabies), not fixed, not touched this session.
- **No dedicated SOS/emergency button** — still out of scope.
- **`docs/adr/0010` is missing** — ADR numbering jumps 0009 → 0011,
  unexplained. A prior README cited this non-existent ADR as evidence for
  a false two-phone-verification claim; that citation is now removed.
- **`gateway/`'s Python dependencies are in the system Python, not a
  venv** — see §4a.

## 6. Immediate next steps, in priority order

1. **Two-phone mesh test** — still the single biggest untested functional
   risk in the entire app, independent of everything else. Needs a second
   physical Android device.
2. **Record the demo video** — the app is now visually ready for this
   (see §4's UI revamp); the main remaining risk is the two-phone gap
   above, since a demo showing only single-device state is a materially
   weaker submission than one showing real mesh chat between two phones.
3. **Verify Cedar's flood-denial behavior on a real BLE link**, not just
   the synthetic self-test loop — ideally as part of the two-phone test.
4. Consider a venv for `gateway/`'s Python dependencies (§4a/§5) before
   any further Strands work.
5. Resolve or route around the USSD PIN/balance-only issue (§5) if time
   allows.
6. Consider the LLM hallucination issue (§5) if time allows — it's a real
   quality issue in a safety-critical answer.
7. OpenSearch remains the most tractable *additional* AWS tool if a 4th
   angle is ever wanted, though 3 already satisfies the judging rule.

## 7. Repository / handoff mechanics

- Everything through commit `4fdf572` is pushed to
  **https://github.com/divyamX700/AWS-Hackathon**, `master` branch.
  Working tree is clean.
- Git push access: this session used the `mahichauhan11` GitHub account,
  which intermittently returned `403 Permission denied` errors on push
  even after being told it had access — if you hit this, just retry the
  push once or twice before assuming it's a real permissions problem; it
  resolved itself without any configuration change both times it happened
  this session.
- Read `DESIGN.md` fresh even if you've read a prior version — the entire
  visual system changed this session (see §4). `PRODUCT.md` is unchanged
  and still accurate. Then `docs/adr/` in numeric order — 0017 and 0018
  are the two most load-bearing for anyone continuing UI or AWS-tool work.
- `gateway/README.md` documents the Strands reference agent's own setup
  and known limitations separately from this file.

## 8. Setting up on a new machine — start here

1. **JDK**: install Amazon Corretto 11 (`scripts/setup-corretto.sh`), pin
   `org.gradle.java.home` in your own `~/.gradle/gradle.properties` (NOT
   the repo's — see `docs/adr/0006`).
2. **Android SDK**: `scripts/setup-android-sdk.sh` — installs
   command-line tools, platform 34, build-tools 34.0.0, platform-tools,
   and the NDK; writes `local.properties`. Note: the *latest* Android
   cmdline-tools needs JDK 17 to run `sdkmanager` itself (separate from
   the JDK 11 the actual Gradle build needs) — if you hit a
   `UnsupportedClassVersionError` running `sdkmanager`, that's why; the
   script already works around this by using an older cmdline-tools
   release, but this is worth knowing if you ever touch that script.
3. **Verify the base build works**: `./gradlew assembleDebug
   testDebugUnitTest` — should succeed with 47/47 tests passing, using
   the *already-committed* Cedar `.so` and patched jar (no Rust/NDK setup
   needed for this step).
4. **If a device is connected**: `adb install -r
   app/build/outputs/apk/debug/app-debug.apk`, launch, and confirm no
   crash — the Guava fix in `settings.gradle.kts` is what prevents the
   real on-device crash described in §3, so this is a meaningful check,
   not a formality.
5. **Only if you need to rebuild the Cedar `.so` from scratch** (you
   normally don't, it's committed): `scripts/build-cedar-ffi.sh`, which
   needs a working Rust toolchain with a host linker — see
   `docs/adr/0017`'s MinGW-w64/GNU-toolchain note if `cargo install`/
   `cargo build` fails with a `link.exe` error on Windows.
6. **For the Strands reference agent**: `scripts/setup-strands-agent.sh`
   (installs Ollama + pulls `qwen2.5:0.5b-instruct` + installs
   `strands-agents`) — consider using a venv this time, see §4a/§5.
7. **For the on-device LLM assistant to generate real answers** (not just
   extractive fallback): side-load the model file per README's "Running
   it" section — this is separate from everything above.
