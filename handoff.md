# Handoff: Sankat Setu

Written 2026-09-19 (supersedes the earlier 2026-09-19 version, readable in
git history at commit `4fdf572` if you want that snapshot, and the one
before that at `e73a3bd`). Read this fully before touching code. Every
claim here reflects actual tested state, not aspiration. Where something
is untested, partially done, or broken, it says so plainly.

**GitHub**: https://github.com/divyamX700/AWS-Hackathon — everything
described here is pushed and current as of the commit this handoff itself
is committed in. Working tree was clean at the time this was written.

## 1. Hackathon context — confirmed against the real rules page

- Event: "Bharat Builds Tour" / "First Commit", wemakedevs.org/aws/first-commit.
- Project name: **Sankat Setu** ("crisis bridge," Hindi/Sanskrit).
- Platform: native Android (Kotlin, Jetpack Compose), offline-first by
  design. The whole pitch is crisis response with zero connectivity.
- Competing in the **Build It** track: "open source, on your machine, no
  AWS account, no card, no bill."
- The real Build It tool list: Strands Agents SDK, PartyRock, SAM CLI,
  LocalStack, Firecracker, Corretto, OpenSearch, Cedar. Eight tools.
- The actual judging rule: *"Using an AWS open-source project or AWS
  service is mandatory to win a prize."* One tool, not all eight.
- **Status, unchanged this session: 3 of 8 tools (Corretto, Cedar,
  Strands) are genuinely verified working on real hardware.** This
  comfortably satisfies the "at least one" rule. See §4a. Nothing in
  this session touched the AWS integration work; it's carried over as-is
  from the prior handoff.

## 2. The pitch / what we're building

A phone-to-phone crisis app for disaster scenarios where cellular and
internet are down but phones still have Bluetooth. Three pillars, all
real and working (see §4):

1. **Bluetooth mesh chat.** Multi-hop relay through nearby phones. No
   internet, no central server.
2. **Offline payments.** Real UPI money movement via USSD `*99#`/IVR
   123Pay (a genuine bank-backed rail), plus a mesh IOU voucher: a signed
   offline promise-to-pay, explicitly not money movement. See §5.
3. **On-device AI assistant.** A local LLM answering first-aid questions
   from a bundled knowledge base, and, as of this session, real
   general-conversation ability too, not just crisis Q&A. See §4.

All three are gated at the mesh-router level by real, on-device Cedar
authorization (flood and blocked-peer control). See §4a.

Target user: someone in a flood, earthquake, or cyclone-affected area in
India where cell towers are down but phones have battery and are
physically near each other.

## 3. Current architecture

```
app/src/main/java/com/sankatsetu/app/
├── mesh/
│   ├── transport/    BLE GATT client+server, MeshForegroundService
│   ├── protocol/     wire format: packet framing, fragmentation, IouPacket
│   ├── crypto/       Identity (ECDSA keypair), NoiseSession, NicknameStore
│   ├── authz/         CedarAuthorizer + MeshAuthorizer interface, real
│   │                    on-device Cedar policy evaluation (docs/adr/0017)
│   └── router/       MessageRouter — hop relay, dedup, TTL, and the
│                        Cedar flood/blocked-peer gate, all at the one real
│                        choke point every packet passes through
├── data/              Room DB: MessageEntity/Dao, PeerEntity/Dao, IouEntity/Dao, AppDatabase
├── assistant/         KnowledgeBaseLoader, KnowledgeDocumentParser, KnowledgeChunk,
│                       KnowledgeRetriever (BM25+TF-IDF), AssistantEngine (now also
│                       handles plain conversation, not just KB-grounded answers,
│                       see §4), MediaPipeLlmAssistant
├── payments/          UssdDialer, IouManager
├── di/                AppContainer — hand-rolled DI, owns singletons + knowledgeBase +
│                        cedarAuthorizer + bluetoothOn (new — a real live adapter-state
│                        flow, see §4)
└── ui/
    ├── chat/          ChatListScreen (a ledger register: peer rows, "I'm Safe"
    │                    broadcast, editable nickname), ChatThreadScreen (now with
    │                    a Clear-chat action), ChatViewModel
    ├── pay/           PayScreen, PayViewModel
    ├── assistant/      AssistantScreen (agent UI, docs browser, Clear-conversation
    │                    action), AssistantViewModel
    ├── components/     SignalBars, StampMark (new — a drawn ink-stamp seal),
    │                    CounterfoilEdge (new — a dashed pending-row border),
    │                    StatusPill
    ├── theme/          Color.kt, Theme.kt, Type.kt (Inter + JetBrains Mono),
    │                    Shapes.kt, Motion.kt
    └── MainActivity.kt  bottom-nav shell, 3 tabs, app-wide Back handling

gateway/               Strands Agents SDK + Ollama reference agent (Python,
                       standalone, mirrors AssistantEngine's pipeline, see
                       gateway/README.md) — untouched this session
scripts/               setup scripts for a new machine, see §8
```

Also at the project root: **`PRODUCT.md`** (product truth, unchanged this
session) and **`DESIGN.md`** (visual-system tokens/components, **rewritten
this session, a third distinct visual world** — read it fresh even if
you've read a prior version).

**Key non-obvious facts a new agent needs:**

- `compileSdk`/`targetSdk` pinned to **34, not 35**, `docs/adr/0006`.
- **JDK 11 pinned**, specifically Amazon Corretto 11. This pin lives in
  the machine-local `~/.gradle/gradle.properties`, not the repo. On a new
  machine you must re-pin this yourself, see §8. **A real, separate gotcha
  found this session**: pinning `org.gradle.java.home` alone is not
  enough. The Gradle wrapper's own launcher process (the one that
  connects to the daemon) runs under whatever `java` is on your `PATH`/
  `JAVA_HOME`, and on a machine with the JDK 17+ AF_UNIX loopback bug
  (`docs/adr/0006`), that launcher itself will hit the same "Unable to
  establish loopback connection" error even with the daemon correctly
  pinned to JDK 11. You need `JAVA_HOME` itself pointed at Corretto 11 for
  the shell you run `./gradlew` from, not just the Gradle property.
- No Hilt/Dagger/Koin. Hand-written `AppContainer.kt`, `docs/adr/0002`.
- `minSdk = 29`.
- No backend server. Nothing calls the internet.
- LLM model file **not committed to git** (`docs/adr/0005`), side-loaded
  via `adb push`. See README's "Running it" section for the exact command.
- **Typography**: Inter for all prose, JetBrains Mono for the
  instrument/ledger-numerals register only. See `DESIGN.md`.
- **The Cedar native `.so` is committed** (`app/src/main/jniLibs/`). You
  do not need to redo the Rust cross-compile to build/run the app.
- **A patched `cedar-java` jar is committed** at
  `app/libs/cedar-java-4.3.1-methodparams-stripped.jar`, see
  `docs/adr/0017`.
- **`settings.gradle.kts` has a load-bearing Guava fix.** Without it the
  app crashes on real-device launch. Do not "simplify" this away, see
  `docs/adr/0017`.

## 4. What is actually built and verified working (on real hardware)

This session was a UI/UX redesign plus a pass of real bugs found by
actually using the app, not new mesh/payment functionality. Everything
below was tested live on `ZN5224PDZ8` (a Moto G57 Power). Two-phone mesh
testing still has not been done, see §6 — the single highest-risk
untested surface, unchanged by this session.

- **A third full visual world: "Post Office Passbook / Ledger Register"**
  (`docs/adr/0019-ledger-register-redesign.md`), replacing the prior
  "Apple-craft instrument panel" pass. Ran the `impeccable` design skill's
  full direction-seed process (real research into India's disaster-relief
  visual culture, a dice-assigned pick to break the model's own bias
  toward the obvious choice, then the user's own constraint — the three
  offline pillars are primary, the mesh IOU is secondary — decided the
  final direction). The app now reads as a bound ledger: ruled rows, a
  drawn ink-stamp seal (`StampMark`) for confirmed/settled state, a
  dashed counterfoil edge (`CounterfoilEdge`) for anything still pending,
  tabular monospace for every number. `MeshRadar.kt` (the previous pass's
  radar-sweep component) is deleted, since a ledger doesn't have a radar.
  Full token/component reference in `DESIGN.md`.
- **A real, on-device-verified layout bug found and fixed during that
  redesign**: an experimental `PerforationMargin` decoration (since
  removed entirely, see below) was called with `Modifier.fillMaxSize()`
  at its call sites. Compose's `fillMaxSize()` forces an exact incoming
  constraint range; the component's own internal `.width(10.dp)`, applied
  after that, doesn't override an already-exact range, it gets coerced
  into it. The decoration silently ate the entire Chat tab's width,
  leaving its sibling (every other visible element on the screen) zero
  space. No crash, no log line. Found only by actually screenshotting the
  running app.
- **The `PerforationMargin` decoration itself was removed** after direct
  user feedback: it read as visual noise, was inconsistent (list screens
  only, not reading screens), and — tellingly — even the person who added
  it briefly mistook it for a rendering bug during testing. If a
  decorative element can't be told apart from a defect by the person who
  built it, it fails the "clarity beats flair" bar this product is
  supposed to hold itself to. Cut, not iterated on.
- **A real, live-tested Bluetooth-state bug fixed**: `ChatViewModel`'s
  `_bluetoothOn` flag was hardcoded `true` at init and never updated by
  anything. The "Bluetooth is off" banner had been dead code since it was
  written; it never fired even when Bluetooth genuinely was off. Fixed by
  registering a real `BroadcastReceiver` on `BluetoothAdapter.ACTION_STATE_CHANGED`
  in `AppContainer`, exposed as `bluetoothOn: StateFlow<Boolean>`, threaded
  through to the ViewModel.
- **A related honesty fix**: the Chat tab's "N phone(s) in range" header
  counted every peer ever saved locally, connected or not, which is a
  real, misleading claim. Relabeled to "N known device(s)" (accurate: the
  register's total) and left the `READY` stamp as the one number that
  actually means reachable right now.
- **The on-device Assistant can now hold a plain conversation, not just
  answer crisis questions.** Before this session, `AssistantEngine.answer()`
  returned a canned "I don't have specific guidance" line, without ever
  calling the LLM, whenever the knowledge-base retrieval found zero
  matches — meaning a plain "hello" could never reach the model at all,
  discovered by literally asking the app "hello." Fixed: a zero-match
  query now falls through to the LLM on a plain conversational prompt
  (never the structured crisis-answer format, and `sources` stays empty so
  the UI never implies KB grounding). Verified live: "hello" got a real
  reply in 4.4 seconds.
- **A real latency regression, introduced and then fixed within this same
  session**: the "hello" fix above made general-chat answers
  `wasGenerated = true` (correctly — they're real generations), but
  `AssistantViewModel.ask()`'s conversation-history filter only checked
  `wasGenerated`, so a casual "hello" started getting threaded into the
  *next* question's prompt as fake "conversation so far" context. That's
  exactly the kind of prompt bloat this model's own existing code comments
  already document as the trigger for its worst-case rambling/retry
  behavior. Fixed by also requiring `sources.isNotEmpty()` before a turn
  counts as history, so only real KB-grounded exchanges get carried
  forward. Verified with actual timing: "hello" (4.4s) then immediately
  "how do I treat a burn" (20s, single generation, no retry, normal
  prompt size) — the healthy range, not the 44-86s pathological one.
- **The message-drafting feature (the on-device agent's third stage) was
  removed entirely at the user's request** ("I don't need that draft
  message button"). Deleted: the UI button and drafted-message card,
  `AssistantViewModel.requestDraft`, `AssistantEngine.draftShareableMessage`,
  the now-dead `draft`/`isDrafting` fields on `AssistantTurn`, and the two
  unit tests that only existed to cover it. **This is a real, disclosed
  divergence from `docs/adr/0016`'s "3-stage agent" description and from
  `gateway/README.md`'s claim that the Strands reference agent "mirrors
  AssistantEngine.kt's pipeline"** — the Kotlin app is now a 2-stage agent
  (triage/action-suggestion + answer), and the Python reference agent
  still has all 3 stages. If the AWS submission material calls this a
  matched 3-stage pair, that claim is now stale and needs updating or the
  gateway agent's own drafting call needs removing too for parity.
- **A real, unrelated color bug found and fixed**: the "read" delivery
  tick (the second blue WhatsApp-style checkmark) used a `ReadBlue` color
  this session's own redesign had set to the exact same hex as `primary`,
  which now fills the outgoing message bubble itself. A same-color tick on
  a same-color bubble is invisible. Fixed by reusing the stamp-ink green
  (`StatusSafe`) instead, which is also more correct for the ledger
  world's own color logic (green already means "confirmed" everywhere
  else in the app).
- **Nickname-rename persistence, a real gap, fixed**: when a peer renames
  themselves and re-announces, `PeerDao.touch()` only ever updated
  `lastSeen`/`hopCount`, never `nickname` — the rename showed up live via
  an in-memory map but was never actually saved, so a cold app restart
  before that peer's next announce would show their stale, pre-rename
  name again. `touch()` now persists the nickname too.
- **New: Clear-chat and Clear-conversation.** A trash icon in a chat
  thread's top bar clears that thread's local message history only (a
  confirm dialog first, same `AlertDialog` pattern as "Forget peer"). A
  trash icon on the Assistant tab (shown only when there's a conversation
  to clear) resets the visible turns and, just as importantly, the
  model's own memory of them in the same action, since `ask()` builds
  the LLM's history directly from that same turns list.
- **A real top-bar alignment bug fixed**: the Chat tab's title was a
  two-line `Column` ("Chat" + "You: nickname"), while Pay and Assistant
  had a plain one-line title. Material centers the whole title block, so
  a taller block's first line sits above where a one-line title centers,
  meaning "Chat" rendered visibly higher than "Pay"/"Assistant" on their
  own app bars. Fixed by moving the nickname (and its rename pencil icon,
  which now sits directly next to it instead of stranded in the top-right
  corner) into its own row below the app bar. Confirmed with a pixel
  comparison: identical baseline now.
- **User-facing copy pass, at the user's explicit request**: read
  Wikipedia's "Signs of AI Writing" page (specifically the "Negative
  parallelisms" and em-dash sections) and rewrote every em-dash-joined
  user-facing string across Chat, Pay, and Assistant into plain sentences.
  The Mesh IOU section header used to read "a pending promise, not a
  payment" (a negative-parallelism construction); it's now just "Mesh
  IOU," with the clarifying fact moved into the card's own plain-sentence
  description. The "I'm Safe" button's own copy was deliberately reverted
  back to its original em-dash phrasing ("I'm safe — notify N peers") at
  the user's explicit direction after reviewing the plain-language
  version; every other string stayed on the plain-sentence rewrite.

## 4a. AWS Build It integration — unchanged this session

| Tool | Status | Detail |
|---|---|---|
| **Corretto** | 🟢 Verified | Amazon Corretto 11.0.32, pinned via machine-local `~/.gradle/gradle.properties` (`docs/adr/0006`). See §3's new note on the launcher-vs-daemon JDK gotcha found this session. |
| **Cedar** | 🟢 Verified, real hardware | Native `libcedar_java_ffi.so`, committed for both ABIs, wired into `MessageRouter.handleInboundBytes()`. Full diagnosis in `docs/adr/0017`. |
| **Strands Agents SDK** | 🟢 Verified | `gateway/agent/` — a real `strands.Agent` + `OllamaModel` mirroring the Kotlin engine's pipeline. **Note the new divergence from §4**: the Kotlin app dropped its drafting stage this session, the Python agent still has it. Worth a decision before submission. |
| **PartyRock** | 🔴 Not done, needs the user in a browser at partyrock.aws. |
| **SAM CLI** / **LocalStack** | 🔴 Blocked on Docker. |
| **Firecracker** | ⛔ Not applicable, Linux/KVM-only. |
| **OpenSearch** | 🔴 Not started, most tractable remaining option if a 4th tool is ever wanted. |

## 5. Known problems, open questions, things that don't fully work

- **Two-phone mesh has never been tested.** Still the single highest-risk
  untested surface in the entire app.
- **Cedar has not been tested on a second connected device** over a real
  BLE link, only a synthetic self-test loop.
- **Mesh IOU is NOT a payment feature.** Still true, still worth
  restating so it's never misrepresented.
- **USSD PIN-then-balance-only issue.** Unchanged, open, unexplained.
- **LLM occasionally produces a wrong/hallucinated fact** (a prior
  session's snake-bite/rabies example). Not touched this session.
- **No dedicated SOS/emergency broadcast.** Real ideation done this
  session, not yet built, parked in `docs/TODO.md` — see that file for
  the actual design questions (flooded/unencrypted broadcast vs. directed
  send, a category picker instead of free text, deliberately more
  friction to send than "I'm Safe," a receiving-side surface that
  actually interrupts, no GPS/location exists anywhere in this app). Real
  protocol groundwork already exists and is unused: `MessageType.SOS_BROADCAST`
  (`0x40`) and a Cedar `"sos"` policy kind are both already defined, but
  no packet payload format or UI was ever built.
- **The Kotlin app and the Strands reference agent are no longer a
  matched 3-stage pair** — see §4's note on removing the drafting stage.
  If any AWS submission material claims parity between them, it needs
  updating.
- **`gateway/`'s Python dependencies are in the system Python, not a
  venv.** Unchanged.
- **`docs/adr/0010` is missing.** ADR numbering jumps 0009 → 0011,
  unexplained, unchanged from prior handoffs.

## 6. Immediate next steps, in priority order

1. **Two-phone mesh test.** Still the single biggest untested functional
   risk, independent of everything else. Needs a second physical Android
   device.
2. **Decide on the SOS broadcast** (`docs/TODO.md` has the full ideation)
   and, if there's time, build it — real protocol/policy groundwork
   already exists for it.
3. **Record the demo video.** The app is visually stable for this now;
   the two-phone gap above is the main remaining risk for a strong demo.
4. **Verify Cedar's flood-denial behavior on a real BLE link**, ideally
   as part of the two-phone test.
5. **Reconcile the Kotlin app / Strands agent parity gap** (§4a, §5)
   before finalizing any submission writeup that claims they match.
6. Resolve or route around the USSD PIN/balance-only issue if time
   allows.
7. Consider the LLM hallucination issue if time allows.
8. OpenSearch remains the most tractable *additional* AWS tool if a 4th
   angle is ever wanted, though 3 already satisfies the judging rule.

## 7. Repository / handoff mechanics

- Everything through this handoff's own commit is pushed to
  **https://github.com/divyamX700/AWS-Hackathon**, `master` branch.
  Working tree is clean.
- Read `DESIGN.md` fresh even if you've read a prior version — the
  entire visual system changed again this session (see §4). `PRODUCT.md`
  is unchanged and still accurate. Then `docs/adr/` in numeric order —
  0017, 0018, and the new 0019 are the most load-bearing for anyone
  continuing UI or AWS-tool work.
- `docs/TODO.md` is a real, current parking lot for decisions raised in
  conversation but not yet built — read it before assuming a feature
  gap hasn't been thought through.
- `gateway/README.md` documents the Strands reference agent's own setup
  and known limitations separately from this file.

## 8. Setting up on a new machine — start here

1. **JDK**: install Amazon Corretto 11 (`scripts/setup-corretto.sh`), pin
   `org.gradle.java.home` in your own `~/.gradle/gradle.properties` (not
   the repo's, see `docs/adr/0006`). **Also set `JAVA_HOME` itself to that
   same Corretto install for any shell you run `./gradlew` from** — see
   §3's note; the daemon-only pin isn't sufficient on a machine with the
   JDK 17+ loopback bug.
2. **Android SDK**: `scripts/setup-android-sdk.sh`, installs command-line
   tools, platform 34, build-tools 34.0.0, platform-tools, and the NDK;
   writes `local.properties`.
3. **Verify the base build works**: `./gradlew assembleDebug
   testDebugUnitTest`, using the already-committed Cedar `.so` and
   patched jar (no Rust/NDK setup needed for this step).
4. **If a device is connected**: `adb install -r
   app/build/outputs/apk/debug/app-debug.apk`, launch, confirm no crash.
5. **Only if you need to rebuild the Cedar `.so` from scratch**
   (normally you don't, it's committed): `scripts/build-cedar-ffi.sh`,
   needs a working Rust toolchain with a host linker, see `docs/adr/0017`.
6. **For the Strands reference agent**: `scripts/setup-strands-agent.sh`,
   consider using a venv this time (§4a/§5).
7. **For the on-device LLM assistant to generate real answers**:
   side-load the model file per README's "Running it" section.
