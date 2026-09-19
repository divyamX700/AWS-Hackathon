# Handoff: Sankat Setu

Written 2026-09-19. This is a complete, standalone handoff — read this
file alone and you should understand the whole project, not just the
latest session's diff. It supersedes every earlier version (still
readable in git history, e.g. `9201b31`, `78f4744`, `4fdf572`, `e73a3bd`
if you want prior snapshots, but you shouldn't need them). Every claim
here reflects actual tested state, not aspiration. Where something is
untested, partially done, or broken, it says so plainly, because that
distinction has mattered more than once in this project's own history.

**GitHub**: https://github.com/divyamX700/AWS-Hackathon — everything
described here is pushed and current as of the commit this handoff itself
is committed in.

## 1. What this project is

**Sankat Setu** ("crisis bridge," Hindi/Sanskrit) is a native Android app
(Kotlin, Jetpack Compose) for the **"Bharat Builds Tour" / "First Commit"
hackathon** (wemakedevs.org/aws/first-commit), competing in the **Build
It** track ("open source, on your machine, no AWS account, no card, no
bill").

The pitch: a phone-to-phone crisis app for disaster scenarios (flood,
earthquake, cyclone) where cell towers and internet are down but phones
still have Bluetooth and battery. Three pillars:

1. **Bluetooth mesh chat** — multi-hop relay through nearby phones, no
   internet, no central server, no coordinator of any kind. This is the
   product's real, defensible mechanism: a message reaches someone beyond
   direct radio range by hopping through other phones running the app,
   which no thin-client messaging app (WhatsApp, GPay) can do without
   rebuilding its transport layer from zero.
2. **Offline payments** — real UPI money movement via USSD `*99#` / UPI
   123Pay's IVR (a genuine bank-backed rail, NPCI-standard), plus a
   signed **mesh IOU voucher** for when even that's unreachable. The IOU
   is explicitly **not** money movement — a bookkeeping promise, manually
   marked settled later. This distinction is load-bearing; see §3's
   Payments section for why.
3. **On-device AI assistant** — a local LLM (no network call, ever)
   answering first-aid/survival questions from a bundled knowledge base,
   with real retrieval grounding so it doesn't fabricate medical facts.

Target user: someone in a disaster-affected area in India, phone in
hand, possibly frightened, injured, or moving, with other phones running
the same app somewhere nearby.

**Explicit product constraints from the user, still in force**: no
central coordinator or laptop-side gateway of any kind (superseding the
original PRD's coordinator concept) — every feature runs standalone on
the phone. The three offline pillars above are the core; the mesh IOU is
real but secondary, never given equal billing with actual UPI payment.

## 2. Architecture

```
app/src/main/java/com/sankatsetu/app/
├── mesh/
│   ├── transport/    MeshTransport (BLE advertise+scan+GATT client/server,
│   │                   one link per peer), MeshForegroundService
│   ├── protocol/     BinaryProtocol, MeshPacket, AnnouncementPacket,
│   │                   PrivateMessagePacket, IouPacket, FragmentPacket, MessageType
│   ├── crypto/       Identity (ECDSA/P-256 signing key + Curve25519 for Noise,
│   │                   Keystore-backed), NoiseSession (Noise XX), NicknameStore
│   ├── authz/        CedarAuthorizer + MeshAuthorizer — real on-device Cedar
│   │                   policy evaluation
│   └── router/       MessageRouter — TTL, dedup, jitter, fanout, the sender
│                       outbox, and the Cedar flood/blocked-peer gate, all at
│                       the one choke point every packet passes through
├── data/             Room DB (SQLCipher): MessageEntity/Dao, PeerEntity/Dao,
│                       IouEntity/Dao, AppDatabase
├── assistant/        KnowledgeBaseLoader, KnowledgeDocumentParser, KnowledgeChunk,
│                       KnowledgeRetriever (hybrid BM25+TF-IDF), AssistantEngine
│                       (a 2-stage on-device agent: triage+action-suggestion
│                       merged into the main answer call, plus a genuine
│                       fallback to plain conversation when nothing in the
│                       knowledge base matches), MediaPipeLlmAssistant
├── payments/         UssdDialer (opens the system dialer, never auto-dials),
│                       IouManager
├── di/               AppContainer — hand-rolled DI (no Hilt/Dagger/Koin),
│                       owns every singleton including bluetoothOn (a real
│                       live Bluetooth-adapter-state flow)
└── ui/
    ├── chat/         ChatListScreen (peer register, "I'm Safe" broadcast,
    │                   editable nickname), ChatThreadScreen (per-thread
    │                   Clear-chat action), ChatViewModel
    ├── pay/          PayScreen, PayViewModel
    ├── assistant/    AssistantScreen (agent UI, offline docs browser,
    │                   Clear-conversation action), AssistantViewModel
    ├── components/   SignalBars, StampMark, CounterfoilEdge, StatusPill —
    │                   see §4's Design section
    ├── theme/        Color.kt, Theme.kt, Type.kt, Shapes.kt, Motion.kt
    └── MainActivity.kt  bottom-nav shell (Chat/Pay/Assistant), app-wide
                          Back handling (steps back one level, never exits
                          the app on the first press)

gateway/              Strands Agents SDK + Ollama reference agent (Python,
                       standalone, a laptop CLI tool — not part of the
                       Android app, no code path connects them). See §5's
                       AWS section and gateway/README.md.
scripts/              Setup scripts for a new machine, see §9.
docs/
  PRD.md, PLAN.md     Original product spec and day-by-day build plan —
                       historical, several features scoped there were
                       deliberately descoped, see this file for what's real.
  adr/                Architecture decision records, numeric order, the
                       real decision history. 0006, 0007, 0011, 0017 are
                       the most load-bearing for anyone touching the build
                       toolchain, crypto, the LLM, or Cedar respectively.
  concepts/           Protocol/crypto deep-dives (ble-mesh-protocol.md,
                       noise-encryption.md).
  knowledge-base/     22 plain-text first-aid/disaster-response documents —
                       the Assistant's actual grounding corpus.
  TODO.md             Parked decisions raised in conversation but not yet
                       built — read this, it has real, current design
                       thinking on the next feature (SOS broadcast, §8).
```

Also at the project root: **`PRODUCT.md`** (product truth: users,
positioning, principles, what's real vs. aspirational) and **`DESIGN.md`**
(the current visual system's tokens and components in detail). Both are
kept current and are faster to read than re-deriving the same facts from
code.

### Mesh protocol, in detail

Ported behaviorally from Bitchat (Swift, public domain) — see
`docs/adr/0002` for exactly what was ported line-for-line (the wire
format and fanout/TTL algorithms) versus referenced-only (Briar's
foreground-service pattern, not copied because Briar is GPLv3).

- **Wire format** (`BinaryProtocol.kt`, `MeshPacket.kt`): a packet header
  (type, TTL, timestamp, sender ID, optional recipient ID, optional
  signature) plus a payload. TTL defaults from `MeshPacket.DEFAULT_TTL`
  and decrements on every hop; `hopCount` anywhere in the UI is computed
  as `DEFAULT_TTL - packet.ttl`, a real fact about how many phones
  relayed a given packet, not a cosmetic number.
- **Relay mechanics** (`MessageRouter.kt`): every inbound packet is
  deduplicated (`SeenMessageCache`, LRU), then either delivered locally
  (if addressed to us or a public broadcast), or relayed onward with
  jittered timing and a deterministic fanout subset (`FanoutSelector`) to
  avoid an entire dense mesh re-broadcasting every packet to every
  neighbor. Broadcasts (chat, announces, SOS) flood; directed traffic
  (private messages, handshakes, IOUs) still floods too in the current
  build, since there's no source-routing table yet — Day 2/3 scope that
  was never built, tracked as a known gap, not silently missing.
- **Fragmentation**: a payload over one BLE write's size limit splits into
  `FragmentPacket`s, each relayed and deduplicated independently, then
  reassembled at the far end and fed back into the same inbound handling
  path as if it arrived whole.
- **Sender outbox** (`SenderOutbox.kt`): if you send while there's no
  mesh link to anyone at all, the message queues (100/peer cap, 24h TTL,
  8 retry attempts, Bitchat's own parameters) instead of vanishing, and
  retries automatically once a link reappears or that peer's next
  announce is seen. Real and unit-tested, including a zero-link-at-send
  integration test.
- **Deliberately not built**: courier envelopes (a third party's phone
  carrying a sealed message for two devices that are never simultaneously
  in range) — this needs one-way sealed encryption (Noise `X`, not the
  `XX` pattern chat uses) and Bitchat's quota/spray-and-wait logic, both
  substantial. The sender outbox above covers the primary "wait for the
  recipient" behavior people would actually notice; courier delivery is a
  secondary mesh property, real but unbuilt. Don't claim it exists.
- **Encryption** (`NoiseSession.kt`, `docs/concepts/noise-encryption.md`):
  real Noise `XX` (mutual authentication, forward secrecy), X25519 +
  ChaCha20-Poly1305, via `rweather/noise-java`. Established per peer pair
  before either side can send an encrypted chat message. `MessageRouter`
  only ever relays the ciphertext bytes for `NOISE_ENCRYPTED` packets — it
  never decrypts, so intermediate relay hops cannot read message content.
  **They can** see packet header metadata (sender ID, recipient ID, TTL,
  message type) in the clear, since routing depends on it — a real,
  stated privacy nuance, not "nobody but the recipient sees anything."
- **Identity/signing** (`Identity.kt`, `docs/adr/0007`): ECDSA/P-256
  (`SHA256withECDSA`), Keystore-backed, **not Ed25519** — Ed25519 signing
  was found unavailable from `AndroidKeyStore` on a real device even on
  API 34 (a genuine on-device finding, not a theoretical risk), so the
  identity key uses a curve that's been Keystore-supported since API 18.
  This forced the wire format's signature field from fixed-64-byte to
  length-prefixed, since ECDSA/P-256 signatures are DER-encoded and vary
  68-72 bytes.
- **Multi-hop has never been tested over real Bluetooth.** The routing
  *logic* is unit-tested with 2-3 `MessageRouter`s wired together
  in-memory, proving encode → relay → reassemble → dedup → deliver
  end-to-end including genuine multi-hop relay through a middle node. But
  that's a simulated environment. This remains the single highest-risk
  untested surface in the entire app — see §6.

### Cedar authorization

Real, on-device, native — not a JVM mock. `CedarAuthorizer` wraps a
Rust-cross-compiled `libcedar_java_ffi.so` (committed for arm64-v8a and
armeabi-v7a, no need to rebuild it, see `docs/adr/0017` if you ever do)
and evaluates a real Cedar policy (`assets/cedar/policies.cedar`) at the
one choke point every packet passes through:
`MessageRouter.handleInboundBytes()`. It gates per-sender flood/rate
limits by message kind: public chat capped at 30/minute, SOS at 5/minute
(tighter, since it's not a chat channel, but with real headroom for a
person retrying), IOU envelopes at 10/minute. A denial drops the packet
silently, the same as a malformed one — and Cedar being unavailable for
any reason fails open (never blocks traffic), so a missing native lib
can't turn into a mesh-wide outage. Verified on-device with a real
32-call self-test: calls 1-30 allowed, call 31 denied, matching the
policy text exactly.

Getting the native `.so` building at all took three real, documented
fixes (`docs/adr/0017`): no host linker on the dev machine (needed
MinGW-w64 + a GNU Rust toolchain), an upstream R8/D8 crash on
`cedar-java-4.3.1.jar`'s `MethodParameters` attribute (fixed with a
committed, patched jar at `app/libs/`), and a Guava Gradle-variant bug
that crashed the app on first real-device launch (fixed via a
`settings.gradle.kts` repository override — **do not remove this**, it
will silently reintroduce a real on-device crash).

### On-device Assistant

Retrieval-augmented, not free generation: `KnowledgeRetriever` does a
real hybrid BM25 + TF-IDF search over the 22-document knowledge base
before the LLM ever runs. If retrieval finds a match, the LLM is asked to
phrase a short structured guide (situation, up to 3 numbered steps, what
to avoid, a 112 reminder) strictly from the retrieved passages, and the
same call carries a triage line (`Action: NONE|BROADCAST_SAFE|OPEN_PAY`)
letting the agent suggest — never auto-fire — one of two genuinely
real app actions (see `docs/adr/0016` for why only two, and why one
generation call instead of three round-trips: a 1B-class model on this
hardware already takes 12-20 seconds for one answer, and three chained
calls would mean up to a minute before a frightened person sees anything).

If retrieval finds **no** match, the assistant now falls through to a
plain conversational reply from the LLM instead of a canned refusal —
fixed this session after discovering a real "hello" produced only "I
don't have specific guidance," because the old code never called the
model at all when nothing matched. General-chat answers are marked
distinctly (empty `sources`) so the UI never implies knowledge-base
grounding for them, and they're deliberately excluded from the
conversation history used for *later* grounded questions, since mixing
casual chat into a crisis prompt's context was found to cause real
latency regressions (prompt bloat pushing the model toward its own
documented worst-case rambling/retry behavior).

If no model is side-loaded at all (the common case on a fresh clone — see
§9), the extractive fallback returns the best-matched passage verbatim,
never fabricated, and the UI marks this state visibly.

**Model**: `Qwen2.5-0.5B-Instruct`, int8, MediaPipe LLM Inference API
(`.task` bundle). Chosen after directly verifying a "Qwen3-0.6B is good
enough" claim didn't hold for this runtime — Qwen3 only ships in the
newer `.litertlm` container, which has no ready-made Android AAR
comparable to what this app already uses (`docs/adr/0011` has the full
investigation, plus two further real on-device findings: the exact
`tasks-genai` version had to be bisected to `0.10.20` for the model to
both dex and load, and wrapping the prompt in Qwen's own ChatML turn
markers made this specific quantized conversion emit zero tokens — a
plain unstructured prompt works, ChatML markers don't).

The model file (~521MB) is **not committed to git** (`docs/adr/0005`,
GitHub's 100MB limit) — side-loaded via `adb push`, see §9.

The on-device agent used to have a third stage (an on-request
message-drafting call, turning a Q&A into a short shareable summary).
**This was removed entirely this session** at the user's request. The
Python reference agent in `gateway/` (used for the AWS Strands
integration, see §5) still has all three stages — **the two are no
longer a matched pair**, which matters if any AWS submission material
claims parity between them. See §7.

### Payments

`PayScreen`'s two USSD/IVR cards open the system dialer pre-filled with
`*99#` via `Intent.ACTION_DIAL` and stop there — the person must tap the
call button themselves. The app never requests `CALL_PHONE` and never
uses `Intent.ACTION_CALL`, which would dial with no further confirmation.
This is a considered, permanent boundary (`docs/adr/0012`): this codebase
will never initiate a real transfer or phone call against someone's
actual bank account or SIM without a fresh, physical action from that
specific person, hackathon deadline or not.

The **mesh IOU voucher** (`IouPacket`, `IouManager`) is a cryptographically
signed promise-to-pay (using the same ECDSA identity key chat uses),
carried over the identical mesh transport as chat — same TTL/dedup/relay.
It is **not Noise-encrypted** (a stated, deliberate gap: encrypting it
would require an established chat session first, adding a cross-feature
dependency for Day 3 scope) — amount and memo travel in the clear but
signed and tamper-evident. State machine: `pending → settled` (sender
marks paid, notifies the receiver) or `pending → rejected` (receiver-only,
local). `IouManager.markSettled()` never verifies a payment actually
happened or moves money — it's a manual acknowledgment for when the two
parties already settled up through some real channel.

An open, unexplained issue from real testing: after PIN entry on the USSD
flow, only the bank balance shows, not the full UPI menu. Not touched or
diagnosed in this or the redesign session.

### Design system

The app has shipped **three distinct visual worlds** across its history,
documented in full in `DESIGN.md` (always read that file fresh, it
describes the *current* one) and their own ADRs:

1. **"Field Radio + IMD Alert Colors"** (`docs/adr/0014`) — India's own
   four-stage disaster-alert color scale as the primary palette, a
   channel-roster peer list, signal-bar glyphs. Superseded after direct
   user feedback that it still read as "a very basic, wireframe kind of
   build" — a color change alone didn't fix a structural problem.
2. **"Apple-craft instrument panel"** (`docs/adr/0018`) — a restrained
   dark-first canvas, Inter typography, one signal-blue accent, a
   `MeshRadar` pulsing sweep for the searching state. Sound execution,
   but the next session's brief was explicit: research and redesign
   without being constrained by what's already shipped.
3. **"Post Office Passbook / Ledger Register"** (`docs/adr/0019`, current)
   — the app reads as a bound ledger: ruled rows instead of floating
   rounded cards, a drawn ink-stamp seal (`StampMark`) for confirmed/
   settled state, a dashed counterfoil edge (`CounterfoilEdge`) for
   anything still pending, tabular monospace for every number. Chosen
   through the `impeccable` design skill's full direction-seed process
   (real research into India's disaster-relief visual culture, a
   dice-assigned pick specifically to break the model's own bias toward
   the obvious choice, then reframed by the user's own constraint that
   the three offline pillars are primary and the mesh IOU stays visually
   secondary). `MeshRadar.kt` is deleted; a ledger doesn't have a radar.

Typography has stayed constant across all three: **Inter** for all prose,
**JetBrains Mono** reserved narrowly for the instrument/ledger-numerals
register (hop counts, timestamps, amounts) — never body prose.

## 3. What is genuinely built and verified working

- **BLE mesh chat, single-device and self-loop verified**: transport,
  Noise encryption, router, sender outbox, editable nickname, "I'm Safe"
  one-tap broadcast (reuses the same encrypted-send path as a typed
  message, to every peer with a completed handshake). **Two-phone
  multi-hop has never been field-tested** — the single biggest
  outstanding risk, see §6.
- **On-device LLM Assistant**: real retrieval-grounded answers, a real
  fallback to plain conversation when nothing matches, a real extractive
  fallback when no model is loaded. Verified live with actual generation
  timing (a healthy single generation lands in the 5-20 second range).
- **Cedar authorization**: verified on real hardware with a genuine
  32-call self-test against the native engine, not a synthetic mock.
  **Not yet tested against a real flood scenario over an actual BLE
  link with a second device** — only the synthetic self-test loop.
- **Mesh IOU**: protocol logic unit-tested, UI verified on-device. A live
  two-phone IOU send/receive/verify pass is still pending.
- **USSD/IVR dialer buttons**: real, tested by the user on their own
  bank/SIM, with the PIN/balance-only issue above still open.
- Full unit test suite: **63/63 passing** as of this handoff
  (`./gradlew testDebugUnitTest`).

## 4. AWS Build It integration status

The judging rule (confirmed against the real event page, not the
original draft PRD): *"Using an AWS open-source project or AWS service is
mandatory to win a prize"* — one tool, not all eight.

| Tool | Status | Detail |
|---|---|---|
| **Corretto** | 🟢 Verified | Amazon Corretto 11 is the actual JDK building this app. Pinned via machine-local `~/.gradle/gradle.properties`, not the repo — see §9's setup notes, including a real gotcha about the Gradle launcher process needing `JAVA_HOME` itself set, not just the daemon property. |
| **Cedar** | 🟢 Verified, real hardware | See §2's Cedar section above. |
| **Strands Agents SDK** | 🟢 Verified, but see the caveat below | `gateway/agent/` — a real `strands.Agent` + `OllamaModel`, fully offline, mirroring (mostly — see below) the Kotlin engine's pipeline. This is a **standalone Python CLI script on a laptop**, not part of the Android app, and there is no code path — none, under any connectivity condition — from the phone app to it. A user of the actual app can never reach it. It exists purely to demonstrate genuine Strands SDK usage for judging. **Caveat**: it still has all 3 original agent stages (including message-drafting), while the Kotlin app dropped its drafting stage this session — they are no longer a matched pair. Worth a decision before finalizing any submission material that claims parity. |
| **PartyRock** | 🔴 Not done | Needs the user's own 10 minutes in a browser at partyrock.aws — no code work possible on this end. |
| **SAM CLI** / **LocalStack** | 🔴 Blocked | Both need Docker, not installed on any dev machine used so far. |
| **Firecracker** | ⛔ Not applicable | Linux/KVM-only; every dev machine used has been Windows. Not "not done yet" — a hard platform mismatch, drop it from the plan. |
| **OpenSearch** | 🔴 Not started | The standalone distribution runs without Docker, so it's feasible, but not begun. Most tractable *additional* tool if a 4th angle is ever wanted — not required, since 3 already satisfies the rule. |

**Bottom line**: 3 of 8, genuinely verified on real hardware, comfortably
satisfies the "at least one" rule. Given the product's explicit
no-central-coordinator constraint, most of the remaining tools (SAM CLI,
LocalStack, OpenSearch as a server) don't have a natural place to live —
they're server/backend tools for an app with no backend. Treat the AWS
requirement as closed unless there's a specific reason to add a 4th.

## 5. Known problems and open questions

- **Two-phone mesh has never been tested.** The single highest-risk
  untested surface in the entire app, unchanged across every session so
  far. Needs two physical Android devices with Bluetooth on.
- **Cedar's flood-denial has not been tested over a real BLE link**,
  only the synthetic self-test loop.
- **The Kotlin app and the Strands reference agent are no longer a
  matched 3-stage pair** — see §4.
- **USSD PIN-then-balance-only issue** — open, unexplained, real
  (encountered on the user's own bank/SIM).
- **LLM occasionally hallucinates a fact unrelated to the question**
  (a documented example: a snake-bite query once generated an incorrect
  claim about rabies transmission). Not fixed.
- **No dedicated SOS/emergency broadcast exists.** Real protocol
  groundwork is already sitting unused: `MessageType.SOS_BROADCAST`
  (`0x40`) is a reserved wire byte, and Cedar's policy already has a
  tighter-capped `"sos"` resource kind — but no packet payload format or
  UI was ever built. Real ideation is parked in `docs/TODO.md`, not yet
  decided or built. See §8.
- **`gateway/`'s Python dependencies are in the system Python, not a
  venv** — works, but a fresh machine should use
  `python -m venv gateway/.venv` from the start.
- **`docs/adr/0010` is missing.** ADR numbering jumps 0009 → 0011,
  unexplained, harmless.
- **Light theme, TalkBack, and 200% font scale** are not verified against
  the current (or any prior) build on real hardware.

## 6. Immediate next steps, in priority order

1. **Two-phone mesh test.** Still the single biggest untested functional
   risk, independent of everything else. Needs a second physical device.
2. **Decide on and, if there's time, build the SOS broadcast** —
   `docs/TODO.md` has the full design thinking; real protocol/Cedar
   groundwork already exists for it.
3. **Record the demo video.** The app is visually stable for this; the
   two-phone gap above is the main risk to a strong demo.
4. **Verify Cedar's flood-denial on a real BLE link**, ideally as part
   of the two-phone test.
5. **Reconcile the Kotlin/Strands parity gap** (§4) before finalizing
   any submission writeup that claims the two agents match.
6. Resolve the USSD PIN/balance-only issue if time allows.
7. Consider the LLM hallucination issue if time allows.
8. OpenSearch remains the most tractable *additional* AWS tool if ever
   wanted, though not required.

## 7. This session's work, specifically

For context on what just happened, on top of everything above:

- Ran a full design-skill redesign producing the current "Post Office
  Passbook / Ledger Register" visual world (§2's Design section,
  `docs/adr/0019`), replacing the prior "Apple-craft" pass.
- Fixed a real logic bug where the Assistant never called the LLM at all
  for a query with no knowledge-base match (e.g. "hello"), and a related
  latency regression that same fix introduced and then fixed within the
  same session (see §2's Assistant section).
- Fixed a real, previously-dead Bluetooth-adapter-state bug: the "off"
  banner was hardcoded to never fire.
- Removed the on-device agent's message-drafting stage entirely at the
  user's request — see §4's Strands caveat for the resulting divergence
  from the Python reference agent.
- Added Clear-chat (per thread) and Clear-conversation (Assistant, also
  clears the model's own memory of it) actions.
- A handful of smaller, low-stakes fixes (a delivery-tick color that had
  become invisible against its own background, a peer-rename not
  persisting to the database, a top-bar title alignment inconsistency) —
  all fixed, all low-importance enough not to need detail here; check
  git log around this handoff's own commit if you need the specifics.
- Rewrote user-facing copy across Chat/Pay/Assistant to avoid em dashes
  and negative-parallelism constructions, at the user's explicit request,
  after reading Wikipedia's "Signs of AI Writing" — except the "I'm Safe"
  button's copy, which was deliberately reverted back to its original
  phrasing after the user reviewed the plain-language version and
  preferred the original.
- Ideated (not built) an SOS broadcast feature, parked in full in
  `docs/TODO.md` — see §8.

## 8. SOS broadcast — parked ideation, not yet built

Full detail lives in `docs/TODO.md`, kept there because it's a live
"decide this before building" item, not settled history. Summary: it
should **not** be built as a copy of "I'm Safe" — "I'm Safe" is a
private, directed send to peers you've already handshaken with, while an
SOS needs flooded, maximum-reach broadcast to everyone in range
regardless of any prior handshake, probably unencrypted for that reason.
Content should be a category picker (Medical / Trapped / Fire /
Flood-water / Other), never free text, and must never route through the
LLM (instant, deterministic, works with no model side-loaded). Sending
should have deliberately *more* friction than "I'm Safe" (a false SOS is
costly in a way a false "I'm Safe" isn't) — a press-and-hold with a
countdown, matching how iOS/Android's own emergency SOS already works.
Receiving needs its own interrupting surface and a reviewable log, not a
chat-thread row. No GPS/location exists anywhere in this app, so an SOS
can say what's wrong but not where, beyond hop count. Placement (a
separate tab vs. a persistent small affordance reachable from every tab)
is undecided — leaning against stacking a second emergency-colored button
next to "I'm Safe" on the Chat tab, since two competing high-alert
actions on one screen is a real cognitive-load risk for a frightened user.

## 9. Setting up on a new machine

1. **JDK**: install Amazon Corretto 11 (`scripts/setup-corretto.sh`), pin
   `org.gradle.java.home` in your own `~/.gradle/gradle.properties` (not
   the repo's, see `docs/adr/0006`). **Also set `JAVA_HOME` itself** to
   that same Corretto install for any shell you run `./gradlew` from —
   the daemon-only pin is not sufficient on a machine with the JDK 17+
   loopback bug `docs/adr/0006` documents, since the Gradle wrapper's own
   launcher process runs under whatever `java` is on `PATH`/`JAVA_HOME`,
   separate from the daemon it spawns.
2. **Android SDK**: `scripts/setup-android-sdk.sh` — installs command-line
   tools, platform 34, build-tools 34.0.0, platform-tools, and the NDK;
   writes `local.properties`.
3. **Verify the base build**: `./gradlew assembleDebug testDebugUnitTest`
   — should pass using the already-committed Cedar `.so` and patched jar
   (no Rust/NDK setup needed for this step).
4. **If a device is connected**: `adb install -r
   app/build/outputs/apk/debug/app-debug.apk`, launch, confirm no crash
   (the Guava fix in `settings.gradle.kts` is what prevents a real crash
   here, see §2's Cedar section).
5. **Only if you need to rebuild the Cedar `.so` from scratch**
   (normally you don't): `scripts/build-cedar-ffi.sh`, needs a working
   Rust toolchain with a host linker, see `docs/adr/0017`.
6. **For the Strands reference agent**: `scripts/setup-strands-agent.sh`
   — consider a venv (`python -m venv gateway/.venv`) from the start.
7. **For the on-device LLM to generate real answers** (not just the
   extractive fallback):
   ```bash
   curl -L -o qwen2.5-0.5b-instruct-q8.task \
     "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
   adb push qwen2.5-0.5b-instruct-q8.task \
     /sdcard/Android/data/com.sankatsetu.app/files/models/qwen2.5-0.5b-instruct-q8.task
   ```
   See `docs/adr/0011` for why this exact model/file variant and not any
   other on that page.
