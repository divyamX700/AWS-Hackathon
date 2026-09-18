# Handoff: Sankat Setu

Written 2026-09-19 (supersedes the 2026-09-18 version — that one is still
readable in git history at commit `000357b` if you want the earlier
snapshot). Read this fully before touching code. Every claim here reflects
actual tested state, not aspiration — where something is untested,
partially done, or broken, it says so plainly.

**GitHub**: https://github.com/divyamX700/AWS-Hackathon — everything
described here is pushed and current as of commit `f052d13`.

## 1. Hackathon context — confirmed against the real rules page

- Event: "Bharat Builds Tour" / "First Commit", wemakedevs.org/aws/first-commit.
- Project name: **Sankat Setu** ("crisis bridge" — Hindi/Sanskrit).
- Platform: native Android (Kotlin, Jetpack Compose), offline-first by
  design — the whole pitch is crisis response with zero connectivity.
- Competing in the **Build It** track: "open source, on your machine — no
  AWS account, no card, no bill."
- **The real Build It tool list** (verified directly against the
  hackathon's own page, not the original draft PRD's guess): **Strands
  Agents SDK, PartyRock, SAM CLI, LocalStack, Firecracker, Corretto,
  OpenSearch, Cedar** — 8 tools across 6 categories (Agents/AI,
  Containers/K8s, Serverless, Servers/runtimes, Data/search, Auth/policy).
- **The actual judging rule**: *"Using an AWS open-source project or AWS
  service is mandatory to win a prize."* That's **one**, not all eight —
  lighter than the original draft PRD's self-imposed "demonstrate 4 of 5"
  goal, which was never an actual competition rule.
- **Current status: zero of the eight tools are integrated into the repo**
  — see §4a, this is the single most urgent gap heading into judging.

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
   from a bundled knowledge base, now a real 3-stage agent (triage,
   action-suggestion, message-drafting) — see §4.

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
│   └── router/       MessageRouter — hop relay, dedup, TTL (the one real
│                       choke point every packet passes through — see §4a's
│                       Cedar note for why this matters)
├── data/              Room DB: MessageEntity/Dao, PeerEntity/Dao, IouEntity/Dao, AppDatabase
├── assistant/         KnowledgeBaseLoader, KnowledgeDocumentParser, KnowledgeChunk,
│                       KnowledgeRetriever (BM25+TF-IDF), AssistantEngine (now a
│                       3-stage on-device agent, see §4), MediaPipeLlmAssistant
├── payments/          UssdDialer, IouManager
├── di/                AppContainer — hand-rolled DI, owns singletons + knowledgeBase
└── ui/
    ├── chat/          ChatListScreen (channel-roster style, "I'm Safe" broadcast,
    │                    editable nickname), ChatThreadScreen, ChatViewModel
    ├── pay/           PayScreen, PayViewModel
    ├── assistant/      AssistantScreen (agent UI + Docs browser), AssistantViewModel
    ├── components/     SignalBars (drawn 4-bar connection glyph)
    ├── theme/          Color.kt, Theme.kt (Material 3, IMD alert-color system), Type.kt
    └── MainActivity.kt  bottom-nav shell, 3 tabs, app-wide Back handling
```

Also new at the project root: **`PRODUCT.md`** and **`DESIGN.md`** (written
this session per the `impeccable` design skill's convention — read these
for product truth and the visual-system tokens/components respectively,
faster than re-deriving them from code).

**Key non-obvious facts a new agent needs:**

- `compileSdk`/`targetSdk` pinned to **34, not 35** — `docs/adr/0006`.
- **JDK 11 pinned**, and as of this session specifically **Amazon
  Corretto 11**, not Temurin — see §4a. **This pin lives in the
  machine-local `~/.gradle/gradle.properties`, NOT in the repo** (by
  design, per that file's own comment — it's machine-specific). **On a
  new machine/environment, you must re-pin `org.gradle.java.home` to a
  JDK 11 install yourself** or the build may hit a real, previously-hit
  bug: on at least one Windows environment, JDK 17+ cannot open an NIO
  Selector (broken AF_UNIX loopback connect), which breaks Gradle's
  daemon IPC entirely with a cryptic "Unable to establish loopback
  connection" error. If you hit that exact error, this is why — see
  `docs/adr/0006`.
- No Hilt/Dagger/Koin — hand-written `AppContainer.kt` (`docs/adr/0002`).
- `minSdk = 29`.
- No backend server. Nothing calls the internet except optional future
  connectivity-return features (Nostr, PRD-only, not built; the Bedrock
  "expanded guidance" bonus feature discussed but also not built yet).
- LLM model file **not committed to git** (`docs/adr/0005`) — side-loaded
  via `adb push`. A fresh device will not have the Assistant tab's
  generation working until this is redone.
- **Font**: `res/font/jetbrains_mono_*.ttf` (JetBrains Mono, OFL 1.1) —
  the "instrument panel" register (peer IDs, hop counts, timestamps,
  status words). Never used for body prose — see `docs/adr/0014`.

## 4. What is actually built and verified working (on real hardware)

All tested on Phone A (adb serial `ZN5224PDZ8`). Two-phone mesh testing
still **has not been done** — see §6, this remains the single highest-risk
untested surface in the app.

- **BLE mesh chat**: as before (transport, Noise encryption, router,
  outbox), plus this session: **editable nickname** (`NicknameStore`,
  `docs/adr/0015`) — defaults to an anonymous `builder-xxxx` id, a pencil
  icon on the Chat tab opens a rename dialog that *offers* (never
  silently applies) the phone's real Bluetooth device name as a one-tap
  suggestion; **"I'm Safe" broadcast** — one tap sends "I'm safe." to
  every peer with an established session, reusing the existing 1:1
  encrypted send path (not a new wire message type).
- **Chat UI redesign** ("Field Radio + IMD Alert Colors", `docs/adr/0014`):
  peer rows read as a channel roster — a drawn 4-bar signal glyph
  (`SignalBars.kt`) instead of a colored dot, monospace status line
  ("2 HOPS · RELAYED"). Color system now uses India's real IMD four-stage
  disaster-alert scale (Green/Yellow/Orange/Red) instead of an invented
  palette — `primary` is IMD Green (the mesh's own "all clear" color),
  `error` is IMD Red (reserved for genuine danger only, never decorative).
- **USSD/IVR payment buttons**: unchanged from last session. **Still
  open**: after PIN entry only bank balance showed, not the full UPI
  menu — see §5.
- **Mesh IOU**: unchanged functionally; status pills now recolored to the
  same IMD scale as peer status (settled=green, pending=orange,
  rejected=red) — one vocabulary across tabs.
- **On-device LLM Assistant — now a real 3-stage agent**
  (`docs/adr/0016`), not just single-shot Q&A:
  1. **Triage + action-suggestion**, merged into the existing answer
     generation (one extra line: `Action: NONE|BROADCAST_SAFE|OPEN_PAY`,
     placed at the **front** of the required format — this matters, see
     the bug note below). Parsed out before display; renders as a tappable
     `AssistChip` under the answer ("Broadcast \"I'm safe\" now" / "Open
     Pay tab") — **never fires automatically**, always requires a tap.
     Only two possible actions, both real, already-built app features —
     no fabricated SOS/dispatch capability.
  2. **Message-drafting**, a genuinely separate, on-request second LLM
     call ("Draft a message to share" button) — not run eagerly on every
     question, since most questions never use it and it would double
     their latency.
  3. Neither stage needs a network call — fully offline, same on-device
     Gemma model as before.
  - **Three real bugs found via actual on-device testing this session**
    (not caught by unit tests alone — worth remembering as a pattern):
    the Action line initially placed at the *end* of the format got
    silently truncated off by the model's tight token budget; an early
    version added a *second, outer* retry for a missing Action line that
    compounded with `MediaPipeLlmAssistant`'s own internal retry loop into
    up to **4 total generations, 86 seconds, for one query** — removed,
    the front-loading fix alone was sufficient; the model at one point
    echoed the prompt's own "Action rule:" heading as a literal visible
    line in the answer — reworded the prompt + added a defensive strip.
    Verified back to a single generation, ~15s typical, after all three
    fixes.
- **Docs browser** (Assistant tab, book icon in the top bar): lets the
  person read the raw 20-file knowledge base directly, not just through a
  generated answer — a list of documents, tap into one, scrollable full
  text. Two-level, both steppable via system Back.
- **App-wide Back/gesture navigation fixed**: previously any system Back
  press exited the app outright, mid-navigation. Now steps back one level
  at a time via `BackHandler`s in `MainActivity` and `AssistantScreen` —
  chat thread → peer list, docs reader → docs list → Assistant home,
  non-Chat tab → Chat tab, only *then* the system default (exit).
- **Message timestamps**: HH:mm, monospace, next to delivery ticks —
  previously absent from the chat UI entirely.
- **Real bugs found and fixed this session, unrelated to the redesign
  itself** (all from actually screenshotting the running app, not
  assumed): Material's own stock demo-app purple was leaking onto the
  "Send Mesh IOU" card because only top-level color roles were overridden,
  not the `*Container` roles Material falls back to; the Assistant tab's
  question field and send button were rendering **entirely hidden behind
  the bottom nav bar** on first launch (an empty-state `Column` used
  `fillMaxSize()` instead of `weight(1f)`, present only when the turn list
  was empty — meaning a fresh install's very first screen was actually
  unusable until caught here); the keyboard wasn't dismissing after
  sending an Assistant question; two `Spacer(Modifier.width(...))` calls
  inside vertical `Column`s did nothing (width has no effect on vertical
  spacing) — question/answer bubbles and answer/label rows were visually
  touching until fixed to `.height(...)`.

## 4a. AWS Build It integration — the honest, current status

**This is the most important section for whoever picks this up next.**
As of this handoff, **none of the 8 required tools are integrated into
the app's actual code**, despite a large amount of design/architecture
work this session. Judging requires at least one, mandatory to win
anything. Status per tool:

| Tool | Status | Detail |
|---|---|---|
| **Corretto** | 🟡 Partially done, **not portable** | Amazon Corretto 11 downloaded, extracted to `C:\JDKs\jdk11.0.32_10` on *this* machine, and pinned via `org.gradle.java.home` in the **machine-local** `~/.gradle/gradle.properties` (confirmed via `java -version` → `OpenJDK Runtime Environment Corretto-11.0.32.10.1`). This is real, but **it is not committed anywhere in the repo** because the JDK pin is deliberately machine-specific (see `docs/adr/0006`). **On a new machine: download Corretto 11 yourself** (https://corretto.aws/downloads/), extract, pin `org.gradle.java.home` in your own user-level `gradle.properties`, verify with `./gradlew -version` shows "Corretto" not "Temurin"/other. |
| **Cedar** | 🔴 Designed, not built — **real blocker found** | Exact integration point identified and is still correct: `MessageRouter.handleInboundBytes()` (the one choke point every mesh packet passes through) should gate on a per-sender rate/flood policy before accepting or relaying. **But**: investigated two real implementation paths and both need a Rust toolchain cross-compiled for Android (cargo-ndk, protoc for gRPC, UniFFI codegen) — (1) raw `cedar-java` (real Maven coords: `com.cedarpolicy:cedar-java:4.3.1`, **not** `4.10.0` as an earlier draft plan hallucinated) ships a JNI native lib built for desktop JVMs (Linux/Mac/Windows x86_64), not Android ARM ABIs, so it needs the same cargo-ndk cross-compile the original 2024 PRD already flagged as painful and never finished; (2) **Cedarling** (Janssen Project, initially thought to be the easier Android-native path) turns out to require building from Rust source (`jans-cedarling` on GitHub) with the full Rust toolchain + protoc + UniFFI — not a drop-in Maven/AAR dependency, no prebuilt Android artifact found. **This needs a real decision from whoever picks this up**: either commit to the Rust/NDK cross-compile toolchain (real work, real risk given this project's already-fragile Android toolchain history), or find/vendor a pure-JVM Cedar-compatible policy evaluator (may not exist for Cedar specifically — worth checking), or drop Cedar and pick a different tool from the list to satisfy the "at least one" rule. |
| **Strands Agents SDK** | 🔴 Not started, plan agreed | Python-only, cannot run inside the Android APK. Agreed approach with the user: a real Strands agent using **Ollama** as the model provider (fully local, no AWS account, offline) as a standalone Python reference implementation in the repo, mirroring the exact 3-stage pipeline already built on-device in Kotlin (§4). This is honest, real usage of the actual SDK — not claimed to run on the phone, but a genuine artifact demonstrable in the submission video. **Ollama installer was downloading when this session ended — not confirmed installed, not confirmed working, no model pulled yet.** Next step: finish the Ollama install (`https://ollama.com/download/OllamaSetup.exe`), pull a model, `pip install strands-agents`, build the reference agent script. |
| **PartyRock** | 🔴 Not done — needs the user | No billed AWS account needed, just the free Builder Center profile (user confirmed they have one). This was meant to be the user's own step (prototype the 3-stage agent flow in the PartyRock playground) *before* the on-device Kotlin port — that ordering got skipped; the Kotlin port happened first, directly. Still open, still needs the user in a browser at partyrock.aws. |
| **SAM CLI** | 🔴 Blocked | `sam local start-api`/`sam local invoke` need Docker for Lambda-runtime emulation. **No Docker on this machine** (confirmed: `docker --version` → not found). Installing Docker Desktop is a heavy, admin-rights, possible-reboot operation — flagged to the user, not started without explicit confirmation given the footprint. |
| **LocalStack** | 🔴 Blocked | Same Docker dependency as SAM CLI, same status. |
| **Firecracker** | ⛔ Not applicable on this machine | Linux/KVM-only microVM tool. This dev environment is Windows. Categorically cannot run here — not "not done yet," a hard platform mismatch. Worth dropping from the plan entirely unless building/testing happens on a Linux box. |
| **OpenSearch** | 🔴 Not started, feasible without Docker | The standalone OpenSearch distribution (tarball/zip) runs directly via its own bundled JVM launcher, no Docker required — just needs a real download (~600MB-1GB). Not yet downloaded or run this session. This is the most tractable *remaining* option along with Strands+Ollama. |

**Bottom line**: Corretto is the closest to "real and working" but needs
manual re-setup on any new machine since it's intentionally not
repo-committed. Cedar has a genuinely hard blocker (native Rust
cross-compile) that needs a real decision, not just more effort. Strands
(via Ollama) and OpenSearch are the two most promising *next* targets —
both feasible without Docker or a billed AWS account, both fit the
offline-first constraint, and Strands+OpenSearch together could form one
coherent "real RAG agent, fully local" deliverable.

## 5. Known problems, open questions, things that don't fully work

- **USSD PIN-then-balance-only issue (OPEN, unexplained)** — unchanged
  from last handoff. See prior section for detail; not touched this
  session.
- **Mesh IOU is NOT a payment feature** — still true, still worth
  restating to avoid misrepresenting it to judges. See prior handoff's
  full explanation (unchanged).
- **Two-phone mesh has never been tested.** Still the single highest-risk
  untested surface. Nothing this session touched the mesh transport
  itself, so this remains exactly as risky as before.
- **LLM occasionally produces a wrong/hallucinated fact** — flagged but
  not fixed this session: a "snake bite" query at one point generated
  "washing the wound reduces the risk of rabies transmission," which is
  incorrect and unrelated (rabies isn't a snake-bite concern). This is a
  prompt/generation quality issue distinct from the format bugs fixed in
  §4 — worth a dedicated pass if time allows, flagged to the user, not
  yet actioned.
- **No dedicated SOS/emergency button** — still out of scope, still worth
  considering given genre convention.
- **`docs/adr/0010` is missing** — still unexplained, ADR numbering jumps
  0009 → 0011.
- Everything else from the prior handoff's §5 (retrieval heading-boost
  heuristic, history-contamination root cause) still applies unchanged.

## 6. Immediate next steps, in priority order

1. **Pick a real path to satisfy the AWS Build It requirement** — this is
   now the most time-pressured item given hackathon judging. Realistic
   ranked options: (a) finish Ollama install + build the Strands reference
   agent (no Docker, no account, offline, plan already agreed) — probably
   fastest to a genuinely working, honest deliverable; (b) stand up
   OpenSearch locally (no Docker needed) and wire either the Kotlin app or
   the Strands reference agent to query it for real; (c) make a final
   call on Cedar given the real Rust/NDK blocker in §4a — commit to the
   cross-compile toolchain, find a pure-JVM alternative, or drop it.
2. **Two-phone mesh test** — still the biggest untested functional risk,
   independent of the AWS work.
3. **Resolve or route around the USSD PIN/balance-only issue.**
4. **Re-verify the LLM model file push/setup path on a clean checkout.**
5. Consider the LLM hallucination issue (§5) if time allows after the AWS
   work — it's a real quality issue in a safety-critical answer.
6. Consider a dedicated SOS/emergency entry point.

## 7. Repository / handoff mechanics

- Everything through commit `f052d13` is pushed to
  **https://github.com/divyamX700/AWS-Hackathon**, `master` branch.
  Working tree was clean (nothing uncommitted) at the time this handoff
  was written — unlike the prior handoff, there is no pending uncommitted
  batch to worry about.
- Read `PRODUCT.md` and `DESIGN.md` at the repo root first — written this
  session, faster than re-deriving product truth and the visual system
  from code. Then `docs/PRD.md`/`docs/PLAN.md` for original scoping, then
  ADRs in numeric order (0001 through 0016) for decision history — several
  cost real debugging time when second-guessed, especially the toolchain
  ones (0002, 0006) and the AWS/Cedar investigation in this file's §4a.
- The `impeccable` design skill (https://impeccable.style, Apache-2.0) was
  used for the visual redesign this session — its reference files aren't
  reproduced in this repo; re-read from `~/.claude/skills/impeccable/` or
  reinstall (`npx impeccable install`) if continuing UI work.
- Nothing was deleted from any prior handoff's file list — all markdown,
  all ADRs, all knowledge-base docs remain in place.
