# Handoff: Sankat Setu

Written 2026-09-19, last updated 2026-09-20. This is a complete,
standalone handoff — read this file alone and you should understand the
whole project, not just the latest session's diff. It supersedes every
earlier version (still readable in git history, e.g. `9201b31`, `78f4744`,
`4fdf572`, `e73a3bd` if you want prior snapshots, but you shouldn't need
them). Every claim here reflects actual tested state, not aspiration.
Where something is untested, partially done, or broken, it says so
plainly, because that distinction has mattered more than once in this
project's own history.

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

**A 4th, deliberately separate section was added 2026-09-20**: offline
maps. Unlike the three pillars, it's not a zero-internet feature — it
needs a real internet connection once, in advance, to download a 2km
radius around the person, framed explicitly as preventive (before a trip
to a remote or high-risk area, not reached for during a crisis, since
there's no internet during one to download anything with). See §9.

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
scripts/              Setup scripts for a new machine, see §11.
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
- **Real bug found and fixed 2026-09-20, not just a testing gap**:
  `ChatViewModel.handleAnnounce` only auto-initiated the Noise handshake
  for peers exactly one hop away (`hopCount <= 1`), a genuine Day 1 scope
  decision (docs/adr/0001 named "the two-phone one-hop encrypted chat
  gate" as the Day 1 bar), but it was never widened afterward despite the
  router already supporting multi-hop directed relay (proven by the
  existing `three-router chain relays a message` test). A peer 2+ hops
  away would show up correctly in the peer list with the right hop count,
  but no session ever formed and the send button would silently never
  become usable — the app could show you a multi-hop friend without ever
  letting you actually message them, directly undercutting the product's
  core claim. Fixed by removing the hop-count condition; the
  `sessions[peerIdB64] == null` guard still bounds it to one handshake
  attempt per peer. Added a new router test,
  `NOISE_HANDSHAKE round-trips across a three-router chain, both
  directions`, proving the exact mechanism this now depends on (a
  directed packet flooding 2 hops and a reply making it all the way back)
  using the real message type, not just the generic `MESSAGE` type the
  existing test used. Still not verified on real BLE hardware beyond one
  hop — same limitation as everything else in this section, but the logic
  itself is no longer artificially capped at one hop.

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
§11), the extractive fallback returns the best-matched passage verbatim,
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
GitHub's 100MB limit) — side-loaded via `adb push`, see §11.

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
  message, to every peer with a completed handshake). The encrypted
  handshake now auto-initiates for a peer at **any** hop count, not just
  one hop (fixed 2026-09-20, see §2's mesh-protocol section) — a real
  logic fix, verified by a dedicated router-level test, not just a
  hardware-testing gap. **Two-phone multi-hop has still never been
  field-tested over real Bluetooth** — the single biggest outstanding
  risk, see §6.
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
- **SOS broadcast**: send path and local log verified on real hardware
  (see §8) — same single-device limitation as the rest of the mesh for
  an actual received alert, though the *decode → store → render* half of
  that path is verified via `SosSimulator` (§10), just not the real BLE
  transport underneath it.
- **Offline maps**: full cold-cache download verified on real hardware —
  real GPS fix, real MapTiler tiles, then confirmed genuinely usable with
  Wi-Fi and mobile data both disabled (panning inside the downloaded area
  works, panning outside it correctly shows nothing cached). See §9.
- **SOS location + 1-hop peer location on the map**: real GPS coordinates
  now travel with an SOS broadcast and with a peer's announce (when a
  fix is cached), rendered as standardized pins on the Map screen. See
  §10 for the two ADRs, the real bugs found, and what's still simulated
  vs. field-verified.
- Full unit test suite: **72/72 passing** as of this handoff
  (`./gradlew testDebugUnitTest`).

## 4. AWS Build It integration status

The judging rule (confirmed against the real event page, not the
original draft PRD): *"Using an AWS open-source project or AWS service is
mandatory to win a prize"* — one tool, not all eight.

| Tool | Status | Detail |
|---|---|---|
| **Corretto** | 🟢 Verified | Amazon Corretto 11 is the actual JDK building this app. Pinned via machine-local `~/.gradle/gradle.properties`, not the repo — see §11's setup notes, including a real gotcha about the Gradle launcher process needing `JAVA_HOME` itself set, not just the daemon property. |
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

**Explicitly considered and declined for the offline-maps feature
(2026-09-20)**: SAM CLI + LocalStack for a demonstration Lambda/S3
"map-extraction service" pipeline. Correctly rejected once the user
clarified the actual constraint: SAM CLI/LocalStack is inherently a
laptop-side dev-time tool (Docker containers emulating AWS), so using it
at all means a second component existing outside the phone app — exactly
the "separate laptop/coordinator" pattern the user had just said they
didn't want repeated (see the Strands `gateway/` caveat above). No AWS
tool touches the maps feature; it doesn't need one, and 3/8 already
satisfies the rule.

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
- **`gateway/`'s Python dependencies are in the system Python, not a
  venv** — works, but a fresh machine should use
  `python -m venv gateway/.venv` from the start.
- **`docs/adr/0010` is missing.** ADR numbering jumps 0009 → 0011,
  unexplained, harmless.
- **Light theme, TalkBack, and 200% font scale** are not verified against
  the current (or any prior) build on real hardware.
- **Offline maps only cache one area at a time** — re-downloading
  overwrites the previous record (`MapAreaStore` holds a single entry).
  A tourist visiting several high-risk stops on one trip needs to
  re-download at each one, not a running multi-area cache. See §9.
- **No live "you are here" tracking on the map** — SOS/peer/download pins
  are all last-known-position snapshots, never continuously updated. See
  §10.
- **A second real data-wipe mistake happened this session**: `adb pm
  clear` was used mid-session to force a clean cold-cache test for the
  maps feature, wiping the same kind of real test-phone data the earlier
  `adb uninstall` incident (§7) already flagged as something to never
  repeat. Recorded here plainly rather than only in chat history.

## 6. Immediate next steps, in priority order

1. **Two-phone mesh test.** Still the single biggest untested functional
   risk, independent of everything else. Needs a second physical device —
   this is also the only way to verify SOS's receive path (§8) and the
   now-fixed multi-hop handshake (§2) for real, not just at the router
   level.
2. **Record the demo video.** The app is visually stable for this; the
   two-phone gap above is the main risk to a strong demo.
3. **Verify Cedar's flood-denial on a real BLE link**, ideally as part
   of the two-phone test.
4. **Reconcile the Kotlin/Strands parity gap** (§4) before finalizing
   any submission writeup that claims the two agents match.
5. Resolve the USSD PIN/balance-only issue if time allows.
6. Consider the LLM hallucination issue if time allows.
7. Consider an `SOS_ACK` reply type and a local SOS send-rate limit if
   time allows (§8).
8. **Design and build the SOS/offline-maps integration** the user has
   already signaled intent for (a real location attached to an SOS report,
   now that real location exists) — a genuine design conversation of its
   own, not started yet. See §9.
9. OpenSearch remains the most tractable *additional* AWS tool if ever
   wanted, though not required.

## 7. Session log

For context on what actually happened, session by session, on top of
everything described above.

### 2026-09-19 — ledger redesign, assistant fixes, first SOS ideation

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
- Ideated (not built) an SOS broadcast feature, parked in `docs/TODO.md`.

### 2026-09-20 — SOS broadcast built, a real multi-hop bug fixed, cleanup

- **Built the SOS broadcast feature** end to end from the prior session's
  ideation — see §8 for the full detail (files, what's verified, what
  isn't). Refined twice more the same session on direct user feedback:
  once against real AI-slop patterns after reading
  [impeccable.style/slop/](https://impeccable.style/slop/), once to
  shrink the log further (3 rows → 2) and add timestamps.
- **Found and fixed a real bug undercutting the app's core claim**: the
  Noise handshake only ever auto-started for peers exactly one hop away,
  meaning a peer further out showed up correctly in the peer list but
  could never actually be messaged — the send button would silently
  never activate. See §2's mesh-protocol section for the full writeup and
  the new router test that proves the fix's mechanism.
- **Removed real dead weight found by asking "why does this app want
  camera access?"**: a whole `zxing-android-embedded` + CameraX
  dependency block for a "QR setup handshake" feature that was scoped in
  a comment but never built, plus seven manifest permissions
  (`RECEIVE_SMS`, `CALL_PHONE`, `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`,
  `READ_CONTACTS`, `SYSTEM_ALERT_WINDOW`, `MODIFY_AUDIO_SETTINGS`) with
  zero references anywhere in the code. `INTERNET`/`ACCESS_NETWORK_STATE`
  were kept, at the user's call, since ADR 0003 documents a real planned
  use (the Nostr bridge) even though nothing calls them yet.
- **A real mistake worth flagging for whoever picks this up next**: mid-session,
  `adb uninstall` was used twice to clear leftover test data, which wipes
  the entire encrypted local database — not just the rows being cleaned
  up. This deleted the user's real peer list, chat history, and device
  identity on their own physical test phone. **Never use `adb uninstall`
  for test cleanup on a device with real data** — use `adb install -r`
  (keeps data) and scoped deletes through the app's own DAOs (or a
  temporary debug hook calling them) instead. This was actually
  demonstrated safely later the same session: a synthetic peer + chat
  thread was inserted directly via the real DAOs to show the user how an
  out-of-range-but-previously-met peer renders, then removed with
  `PeerDao.delete` + `MessageDao.deleteThread` — no data lost that time.
- **Replaced SOS's full-screen blocking interrupt dialog** with a
  non-blocking pulsing log-row animation plus a small "SOS" nav-icon tag,
  after direct user feedback that a modal interrupt was too disruptive.
  Found and fixed a real bug in the same pass: the "already animated"
  flag lived in per-row Compose `remember` state, which reset every time
  a row scrolled out of the lazy list's window and back in, replaying the
  pulse — moved to a plain `Set` on `SosViewModel`, which outlives any
  single row's composition.
- **Built the offline-maps feature end to end** (a new, 4th section — see
  §9 for the full detail). Real bugs found and fixed by actually testing
  on-device, not by inspection: the public OSM tile server's own
  `TileSourcePolicyException` on bulk download (led to sourcing a real
  MapTiler API key instead of routing around the policy), a `CacheManager`
  built from the visible `MapView` visibly dragging that MapView's own
  camera across zoom levels during download (looked exactly like a
  runaway multi-hundred-km download; fixed by using the `MapView`-free
  `CacheManager` constructor), and a progress percentage that legitimately
  exceeds 100% (a real `osmdroid` tile-count-estimate quirk, fixed by
  clamping the display). Verified with Wi-Fi and mobile data both
  disabled: the downloaded area renders and pans correctly, and panning
  outside the 2km radius correctly shows nothing cached. A second
  `adb pm clear` data-wipe mistake happened while testing this — see §5.
- **Fixed two more real maps bugs the same day**, both found only by the
  user actually using the feature after it shipped: the live map
  streaming tiles from the network anywhere panned to once online (not
  just the downloaded area), and a real, visible delay before the map
  displayed anything right after a download hit 100%. Also raced
  `GPS_PROVIDER`/`NETWORK_PROVIDER` instead of GPS-only, cutting a 30s
  location timeout down to single-digit seconds. See §9.
- **Added real location to SOS and to 1-hop peers, plus a map redesign**
  (§10) — the user's own explicit next step after maps, plus a demo-value
  ask ("both phones show up on the map"). Built and tested via a new
  debug-only `SosSimulator` tool on a single device (still no second
  phone available this session); found and fixed a real camera-framing
  bug where markers outside the download area's box were added correctly
  but sat invisibly off-screen.

## 8. SOS broadcast

Ideated in one session, built in the next (2026-09-20), per `docs/TODO.md`'s
now-updated entry. It is **not** a copy of "I'm Safe": "I'm Safe" is a
private, directed send to peers you've already handshaken with
(`ChatViewModel.broadcastImSafe`), while SOS is flooded, unencrypted,
unsigned, maximum-reach broadcast to everyone in range regardless of any
prior handshake — the point is a stranger relaying it can still read it.

Real files: `mesh/protocol/SosPacket.kt` (a `SosCategory` 5-value enum —
Medical/Trapped/Fire/Flood-water/Other, never free text, never touches the
LLM), `mesh/emergency/SosManager.kt` (mirrors `IouManager`'s own pattern:
an independent collector on `MessageRouter.inboundApplicationPackets`
rather than routing through `ChatViewModel`), a `sos_alerts` Room table
(migration 3→4, see `AppDatabase.kt`), and Chat-tab UI
(`ChatScreen.kt`'s `SosReportSection`/`HoldToSendRow`/`SosLogRow`,
`ui/components/HazardEdge.kt` for the visual break from the ledger's calm
vocabulary — see `DESIGN.md`). Sending is a collapsed row above "I'm Safe"
that expands into a category picker, each category needing a genuine 1.1s
press-and-hold (a filling bar, not a circular countdown ring — a
deliberate simplification for reliable implementation under real testing
time) rather than a single tap, since a false SOS is costly in a way a
false "I'm Safe" isn't. Wire uses `MessageType.SOS_BROADCAST` (`0x40`),
already Cedar-gated at 5/minute per sender before this session
(`assets/cedar/policies.cedar`) — no router or policy changes were needed,
only the payload format and the two ends that were missing.

**Receiving was originally a non-dismissible full-screen interrupt
dialog** — replaced later the same session, direct user feedback, with a
2-3s pulsing animation on the alert's own card in the log plus a small
"SOS" nav-bar tag when you're on another tab (see §7's 2026-09-20 entry).
A per-row `LazyColumn`-recycling bug (the pulse replaying on scroll) was
found and fixed by moving "have I already pulsed this" tracking off
per-row `remember` and onto the ViewModel (`SosViewModel.pulsedSosIds`),
which survives a row scrolling out of the visible window and back.

**Real GPS location was added 2026-09-20** (see §10) — an SOS now carries
the sender's coordinates when a quick fix is available, shown on the
card in place of the old hop-count line, with a button that opens the Map
tab centered on that exact report.

**Verified on real single-device hardware this pass**: send → appears in
the local log with the right category and "sent to everyone in range" →
confirmation text shows and clears → survives an app force-stop and
relaunch (proves the Room migration and insert both actually committed,
not just in-memory state) → a quick tap does not send, only a completed
hold does → multiple sends stack newest-first with distinct IDs. Also
verified the **receive path**, without a second phone: a synthetic
incoming packet fed through the real `MessageRouter.handleInboundBytes`
entry point (see §10's `SosSimulator` — the exact real decode → Cedar
gate → Room → Compose pipeline a real BLE arrival would use, not a UI
mockup) correctly pulsed the alert's card and populated the log with the
right sender/category/hop-count text, and acknowledging it cleared the
pulse. **Still not verified**: an actual over-the-air delivery end to end
(needs the second phone, same limitation as every other mesh feature in
this app), and the Cedar 5/minute cap actually throttling a spam attempt
at a real relay hop. Also not built: an `SOS_ACK` reply type, and any
local send-rate limit (Cedar's cap only throttles a receiving node's
*relay* of a flood, not this device's own repeated local sends).

**Refined after the initial build, same session**, following direct
user feedback plus a deliberate pass against
[impeccable.style/slop/](https://impeccable.style/slop/)'s AI-slop
catalog: the emergency log used to give every row its own red-bordered
card — exactly their cataloged "a colored stripe decorates every card"
pattern, diluting the alarm instead of reinforcing it. Log rows are now
plain, divider-separated lines; the hazard-stripe border survives only
on the single "Report emergency" control itself, where it's a genuine
warning, not decoration (see `DESIGN.md`'s own note on this). The
peer-count summary was cut from a full `Card` with a display-size digit
(a "hero metric" treatment for a number that isn't actually important)
down to a single quiet text line. The log and the peer/chat list are now
separated by an explicit divider, and the log caps at **2** visible rows
(down from an initial 3, per direct user feedback that it was still
taking too much space) — past that it scrolls in its own bounded region
so a long alert history can never push the peer list, or an open chat
thread, off screen. Each row also got a trailing `HH:mm` timestamp
(reusing the same formatter chat bubbles already use).

## 9. Offline maps

A new, 4th section (`ui/map/MapScreen.kt`, `MapViewModel.kt`), built
2026-09-20 — see `docs/adr/0020-offline-maps.md` for the full decision
record. Deliberately **not** framed as a fourth offline pillar: it needs
real internet once, in advance, and is explicitly preventive (before a
trip to a remote or high-risk area), not something used during a crisis.

**Library**: `osmdroid` (`org.osmdroid:osmdroid-android:6.1.20`), chosen
over `MapLibre` GL Native specifically to avoid a third round of native
`.so` toolchain pain (Cedar, `docs/adr/0017`; MediaPipe,
`docs/adr/0006`/`0011`) — `osmdroid` is pure Kotlin/Java, verified to dex
cleanly on this project's pinned toolchain before any feature code was
written. Real trade-off: `osmdroid` itself is archived upstream (frozen
at 6.1.20, no further releases) — still fully functional; Mapsforge
(actively maintained, also pure Java, genuinely vector/offline-first) is
the honest upgrade path if there's ever time.

**Flow**: request location once (`maps/LocationProvider.kt`, plain
`LocationManager`, no Play Services) → compute a 2km-radius bounding box
(`maps/GeoMath.kt`, unit-tested, accounts for real longitude compression
at latitude) → download raster tiles for zoom 12-17 via `osmdroid`'s
`CacheManager` → persist the downloaded area's metadata
(`maps/MapAreaStore.kt`, SharedPreferences, same tier as `NicknameStore`)
so reopening the app shows the map immediately without re-downloading.

**Real bugs found by testing on-device, not by inspection**:
1. The public OSM tile server (`TileSourceFactory.MAPNIK`) throws
   `TileSourcePolicyException` on any bulk download — a real, deliberate
   `osmdroid` guardrail mirroring that server's own usage policy against
   bulk/app-embedded fetching. Fixed with a different, honest tile
   provider (MapTiler, free tier, no card) — never by silencing the
   guardrail on the same restricted server. The API key lives in
   `local.properties` (gitignored) → `BuildConfig.MAPTILER_API_KEY`,
   never committed. See `maps/MapTileSource.kt`.
2. Building `CacheManager` from the visible, on-screen `MapView` made
   that MapView's own camera visibly drag across zoom levels while the
   download ran internally — looked exactly like the download expanding
   to cover hundreds of kilometers, even though the actual downloaded
   `BoundingBox` (independently logged and verified) was a correct 2km
   radius the whole time. Fixed by constructing `CacheManager` from the
   `MapTileProviderBase`/`ITileSource` + `SqlTileWriter()` overload
   instead, which has no `MapView` at all and so cannot touch any camera.
3. The download progress percentage legitimately exceeds 100% (observed
   past 290%) — `osmdroid`'s own upfront tile-count estimate can run low
   against the real count at the finest zoom. The download isn't stuck;
   fixed by clamping the *displayed* value, not the estimate.

**Verified on real hardware**: a full cold-cache download (real GPS fix
in Maligaon, Guwahati, Assam — not simulated; real MapTiler tiles;
complete in under two minutes over a real connection, camera stable
throughout after fix #2). Then, with Wi-Fi and mobile data both
disabled: the downloaded area still renders and pans correctly with zero
network, and panning outside the 2km radius correctly shows `osmdroid`'s
blank placeholder — proof the download is honestly scoped, not secretly
caching more.

**Known gaps, stated plainly**: only one area can be cached at a time
(re-download overwrites the previous record); no staleness check on old
downloads.

**Two more real bugs found later the same day, by the user actually
watching the download and waiting through it**:
4. With internet on, the *live* map (not the download) silently streamed
   tiles from the network for anywhere panned to, not just the downloaded
   2km — `osmdroid`'s default tile provider falls back to network for any
   tile not already cached, quietly making the entire "offline map" claim
   false the moment there's signal. Fixed with
   `tileProvider.setUseDataConnection(false)` on the live `MapView` — the
   only place tiles are now allowed to come from is an explicit
   download, never ambient panning.
5. Right after a download hit 100%, the live map sat blank for a real,
   visible delay before showing anything — `osmdroid`'s in-memory tile
   cache remembers "no tile available" for every tile it tried (and
   failed) to fetch while nothing existed on disk yet, and those negative
   entries don't clear themselves. Fixed by calling
   `tileProvider.clearTileCache()` the moment the download completes.
   A related but separate finding: the progress bar itself would freeze
   at a static "100%" for 20-60s while `osmdroid`'s raw percentage kept
   climbing past its own optimistic estimate (see bug #3 above) —
   switched to an indeterminate "Finishing up…" spinner in that window
   instead of a number that looks stuck.

Also fixed: raw `GPS_PROVIDER`-only location could take 30+ seconds for
a first fix (worse indoors, worse with no internet for A-GPS assistance
data) even though the phone's own Maps app looks instant — because Maps
blends in `NETWORK_PROVIDER` (WiFi/cell-tower) fixes, which resolve in
a couple seconds. `LocationProvider.getCurrentFix` now races both stock
`LocationManager` providers (still no Play Services) and takes whichever
answers first.

**AWS**: none used, none needed for this feature — see §4's own note on
SAM CLI/LocalStack being considered and explicitly declined once the
user clarified they didn't want any laptop-side companion component,
even a demo-only one.

## 10. SOS and peer location on the map

Built 2026-09-20, same day as offline maps itself — the user's own
explicit next step once real device location existed in the app for the
first time (deferred at the end of §9's own work: "this feature first,
later we'll see the integrations"). See `docs/adr/0021-sos-location.md`
and `docs/adr/0022-peer-location.md` for the full decision records.

**SOS location** (`docs/adr/0021`): `SosPacket` gained two optional TLVs
(raw IEEE-754 double bits, same pattern as everything else in this
wire format) carrying latitude/longitude — sent as part of the flooded
broadcast itself, so anyone receiving it gets the coordinates too, not
just the sender's own local record. `SosManager.broadcastSos` waits up
to **5 seconds** (not `LocationProvider`'s own 30s default) for a fix
before sending regardless: this is a one-handed emergency action, and
blocking it on a slow or missing GPS fix would be worse than sending
without coordinates. A cached recent fix (e.g. from already having
opened the Map tab) still resolves near-instantly either way.
`SosEntity` gained matching nullable columns (migration 5→6). The
emergency-log card (`ChatScreen.kt`'s `SosLogRow`) now shows
`26.1445°N, 91.7362°E` in place of the old hop-count line when a fix
was attached (falling back to the old text when it wasn't — a fix isn't
guaranteed), plus a small map-icon button that switches to the Map tab
with that specific report highlighted.

**Peer location** (`docs/adr/0022`): explicitly requested for demo
value — "it would make it look good in the demo when our phones both
show up on the map," not organic feature growth, and worth stating
plainly since it changes the privacy calculus (see the ADR's own
discussion). `AnnouncementPacket` gained the same lat/lon TLV pattern.
Critically, `ChatViewModel.sendAnnounce()` attaches only a **cached**
fix (`LocationProvider.cachedFixOrNull()`, a new synchronous,
no-GPS-request method) — announces fire every 4-30s, far too often to
block on a live fix. A peer only shows up on the map once *their* phone
has a fix cached from doing something else real (opening the Map tab,
sending an SOS) — this is never continuous tracking. Only **1-hop**
peers are shown, deliberately: a relayed hop's last-known position could
be stale by an unbounded amount by the time it reaches you, unlike a
1-hop peer's own fresh announce. `PeerEntity` gained matching nullable
columns (migration 6, same as SOS's own version bump would have been —
see `AppDatabase.kt` for the exact sequencing).

**Map screen redesign, same pass**: every marker (the download-center
"Your location" pin, 1-hop peers, SOS reports) now uses one standardized
hand-drawn teardrop pin (`MapScreen.kt`'s `pinDrawable`) instead of
osmdroid's generic default marker or a plain filled circle — blue/green/
red respectively, with the tapped-from-a-card SOS report rendered larger
and amber. Each pin's name is baked directly into the same bitmap above
the pin head, not left to osmdroid's tap-to-open `Marker.title` bubble —
a name that only appears after tapping every pin individually defeats
the point of a "who's around me" map.

**Real bug found while testing this**: the camera only ever fit the
downloaded area's own fixed 2km box, so a peer or SOS marker outside
that box was added to the map correctly but sat off-screen with nothing
visibly wrong — indistinguishable from "peers aren't showing" even
though they were. Fixed by computing a bounding box across every marker
actually present (falling back to the fixed 2km box only when nothing
else is around to frame against).

**How this was tested without a second phone**: `debug/SosSimulator.kt`,
registered only when `BuildConfig.DEBUG` is true (never in a release
build), listens for three `adb shell am broadcast` actions
(`SIMULATE_INCOMING_SOS`, `SIMULATE_INCOMING_PEER`, `CLEAR_SOS_LOG`) and
feeds a real, correctly wire-encoded packet into
`MessageRouter.handleInboundBytes` — the exact entry point real BLE
arrival uses. This exercises the genuine decode → Cedar gate → Room →
Compose pipeline end to end, just injected at the transport boundary
instead of over an actual radio; it does not and cannot prove two real
phones can exchange these packets over BLE, which remains the
project-wide untested surface described in §5. One incidental, real data
point *for* that surface: a second physical phone ("CMF by Nothing
Phone 1") came into range organically during this session's testing and
reached genuine 1-hop `READY` handshake status in the live peer list —
observed, not deliberately re-verified with an actual two-way chat/SOS
exchange this session, so still short of a full field-test claim.

**Known gaps, stated plainly**: no live "you are here" dot — the pin is
where you were at your last download/SOS/announce, not a continuously
updating position; no opt-out for peer location beyond simply never
using the Map tab or sending an SOS (there's no cached fix to attach
otherwise, but also no explicit consent toggle); a real, minor UI lag
found during testing — an incoming SOS can need the app brought back to
the foreground before it appears in the log if the phone's screen went
idle in between (Compose pauses recomposition while backgrounded, then
catches up on resume) — not a data-loss bug, just worth knowing before a
demo (keep the screen awake).

## 11. Setting up on a new machine

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
8. **For the offline-maps tab to actually download anything**: get a
   free MapTiler API key (maptiler.com, no card, Account → API keys) and
   add it to your own `local.properties` (gitignored) as
   `maptiler.api.key=YOUR_KEY`. Without it, the download fails with an
   honest network/auth error rather than a silently broken map — see
   §9 and `docs/adr/0020`.
