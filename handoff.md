# Handoff: Sankat Setu

Written 2026-09-18, for the next coding agent picking this up. Read this
fully before touching code. Every claim here reflects actual tested state,
not aspiration — where something is untested or broken, it says so.

## 1. Hackathon context

- Event: "Bharat Builds Tour" / "First Commit" hackathon, AWS "Build It" track.
- Project name: **Sankat Setu** ("crisis bridge" — Hindi/Sanskrit).
- Platform: native Android (Kotlin, Jetpack Compose), no backend, no cloud
  dependency by design — the entire pitch is offline-first crisis response.
- Repo root: `C:\Users\Divyam Kulshrestha\Desktop\SankatSetu`
- Local git: 5 commits on `master` (`238c9b5` Day1 → `df6c4c9` Day2 3/3),
  **no remote configured yet**. A large amount of uncommitted work exists on
  top of that (see §7).

## 2. The pitch / what we're building

A phone-to-phone crisis app for disaster scenarios where cellular/internet
is down but phones still have Bluetooth and a UPI-linked SIM. Three pillars:

1. **Bluetooth mesh chat** — phones relay encrypted messages hop-by-hop with
   no internet and no central server, so people can reach others beyond
   direct BLE range (~30-100m) via multi-hop relay through other phones
   running the app.
2. **Offline payments** — two mechanisms:
   - Real UPI money movement via **USSD `*99#` / IVR 123Pay**, which rides
     the SIM's voice/USSD channel and works with zero data connectivity
     (this is a real, bank-backed rail, not invented — India's NPCI runs
     this specifically for feature phones/no-data scenarios).
   - **Mesh IOU vouchers** — a signed "I owe you ₹X" promise sent over the
     BLE mesh when neither party has signal at all (not real money movement,
     see §5 for exactly what this is and isn't).
3. **On-device AI assistant** — a local LLM (MediaPipe LLM Inference API)
   answering first-aid/survival questions from a bundled knowledge base,
   entirely on-device, no network call ever.

Target user: someone in a flood/earthquake/cyclone-affected area in India
where cell towers are down or congested but phones have battery and are
physically near each other.

## 3. Current architecture

```
app/src/main/java/com/sankatsetu/app/
├── mesh/
│   ├── transport/    BLE GATT client+server, MeshForegroundService
│   ├── protocol/     wire format: packet framing, fragmentation, IouPacket
│   ├── crypto/       Identity (ECDSA keypair), NoiseSession (Noise protocol encryption)
│   └── router/       MessageRouter — hop relay, dedup, TTL
├── data/              Room DB: MessageEntity/Dao, PeerEntity/Dao, IouEntity/Dao, AppDatabase
├── assistant/         KnowledgeBaseLoader, KnowledgeDocumentParser, KnowledgeChunk,
│                       KnowledgeRetriever (BM25+TF-IDF), AssistantEngine (prompt building),
│                       MediaPipeLlmAssistant (actual LLM inference wrapper)
├── payments/          UssdDialer (opens system dialer w/ USSD code), IouManager
├── di/                AppContainer — hand-rolled DI (no Hilt/Dagger), owns singletons
└── ui/
    ├── chat/          ChatListScreen, ChatThreadScreen, ChatViewModel
    ├── pay/           PayScreen, PayViewModel (USSD buttons + IOU composer/list)
    ├── assistant/      AssistantScreen, AssistantViewModel
    ├── theme/          Color.kt, Theme.kt (Material 3)
    └── MainActivity.kt  bottom-nav shell, 3 tabs
```

**Key non-obvious facts a new agent needs:**

- `compileSdk`/`targetSdk` pinned to **34, not 35** — build-tools 35.0.0's
  `aapt2` fails on this machine's toolchain. Documented in
  `docs/adr/0006-jdk11-toolchain-downgrade.md`. Do not "helpfully" bump SDK
  versions without re-reading that ADR first; it cost real debugging time.
- **JDK 11**, not a newer JDK — same toolchain fragility. Check
  `docs/adr/0006` before changing `build.gradle.kts` toolchain config.
- No Hilt/Dagger/Koin — DI is a single hand-written `AppContainer.kt`
  ("Day 1" decision, `docs/adr/0002`). Don't introduce a DI framework
  mid-hackathon; it's not broken, just simple.
- `minSdk = 29` (Android 10) — chosen for BLE API stability, not arbitrary.
- No backend server exists or is planned. Nothing in this app calls the
  internet except the on-device LLM's own local file I/O (no network calls
  anywhere — verify this stays true; it's a stated demo/pitch guarantee).
- LLM model file itself is **not committed to git** (`docs/adr/0005`) — it's
  a multi-hundred-MB `.task` file, pushed to the phone separately via `adb
  push`. **The next agent must re-establish how that file gets onto a fresh
  device/emulator** — check `docs/adr/0005` and `docs/adr/0011` for the
  exact filename/source and push path, because the repo alone will not run
  the Assistant tab.

## 4. What is actually built and verified working (on real hardware)

All of the below was tested on a real Android phone (adb serial
`ZN5224PDZ8`, referred to as "Phone A" in commit history/ADRs), not just
compiled. Two-phone mesh testing (multi-hop, actual peer-to-peer over BLE)
was **deferred and has NOT been done yet** — see §6.

- **BLE mesh chat, single phone verified**: BLE GATT transport, wire
  protocol with fragmentation for messages larger than one BLE MTU write,
  Noise-protocol encrypted sessions per peer pair, `MessageRouter` with
  hop-count/TTL and de-duplication, sender-side outbox so a message composed
  while no peer is connected waits and sends once a peer appears instead of
  being silently dropped. Foreground service (`MeshForegroundService`) keeps
  BLE scanning/advertising alive.
- **Chat UI**: peer list (`ChatListScreen`) showing connection state
  (offline/connecting/ready), hop-count badge; per-peer thread
  (`ChatThreadScreen`) with WhatsApp-style delivery ticks (queued → sending
  → sent ✓ → delivered ✓✓ → read ✓✓ blue). Long-press a peer to forget it
  (deletes local history + Room row) — added this session to fix a real bug
  where reinstalling the app during testing left duplicate/stale peer
  entries with no way to clear them.
- **USSD/IVR payment buttons**: `UssdDialer.openDialer()` opens the system
  phone dialer pre-filled with `*99#` — the user must tap the actual call
  button themselves (Android does not allow apps to auto-dial USSD; this is
  intentional and correct, not a shortcut we're missing). **User
  (Divyam) manually tested this on his own real bank/SIM this session**:
  dialing worked, PIN entry worked, but **after entering the PIN only the
  bank balance was shown — the expected UPI payment menu/flow beyond
  balance check did not appear.** This is an open, unexplained issue — see
  §5.
- **Mesh IOU**: `IouManager` + `IouPacket` (signed voucher wire format) +
  `IouDao`/`IouEntity` (Room) + Pay-tab composer/list UI. Functional in the
  sense that it compiles, has unit tests (`IouPacketTest.kt`), and the UI
  flow (pick peer → amount → memo → send → appears in "You owe"/"Owed to
  you"/"Settled" lists → mark-settled) is wired end-to-end. **Not yet tested
  phone-to-phone** (needs the same 2-device mesh test as chat — see §6). See
  §5 for what this feature is conceptually and its real limitations.
- **On-device LLM Assistant**: MediaPipe `LlmInference`/`LlmInferenceSession`
  wrapper (`MediaPipeLlmAssistant.kt`), hybrid BM25+TF-IDF retrieval over a
  20-file plaintext knowledge base (`app/src/main/assets/kb/docs/`, listed
  in §8) with section-heading boosting, structured prompt format ("Situation
  / numbered steps max 3 / Avoid / call 112"), length-gated retry (rejects
  too-short or too-long/rambling generations, retries with a different
  seed), multi-turn conversation support (last 2 turns re-sent as context,
  explicitly instructed to be used ONLY for pronoun resolution, never as a
  fact source — this was a real bug, see §5), warm-up at app startup to hide
  model load latency. Verified multiple real generations on-device with
  full latency instrumentation; typical response now 12-20s (down from a
  worst case of 43.8s — root cause was prompt-length/rambling, not the
  model itself, so this was fixed as a correctness fix, not a
  quality-for-speed tradeoff).
- **UI/UX pass** (this session): replaced emoji bottom-nav/status icons with
  real Material icons; moved `CrisisRed` off `primary` (was used for every
  button/focus-border, diluting its meaning) onto Material's `error` role
  only, introduced calm `OperateBlue` as `primary`; added `TopAppBar` to all
  3 tab screens; consolidated Pay screen's 3 identical full-width cards into
  a compact 2-button row + 1 highlighted IOU card; Assistant answers now
  show a small icon (not a colored border) distinguishing a real LLM
  generation from a direct knowledge-base excerpt fallback. Full detail and
  rationale in `docs/adr/0013-operate-mode-color-and-icons.md`. Used the
  `impeccable` design skill (https://impeccable.style, Apache-2.0,
  `npx impeccable install`) as the guiding reference — its `craft-floor.md`
  and `android.md` files, not fully reproduced here; re-read them from
  `~/.claude/skills/impeccable/` or reinstall if continuing UI work.

## 5. Known problems, open questions, things that don't fully work

- **USSD PIN-then-balance-only issue (OPEN, unexplained)**: after dialing
  `*99#` and entering PIN on a real SIM, only the bank balance screen
  appeared — not the full UPI USSD menu (send money / check balance / etc.)
  a `*99#` session normally offers. Possible causes, **none confirmed**:
  bank/carrier-specific USSD menu variant, PIN entered in the wrong USSD
  prompt step, or the phone's default USSD handling intercepting the
  session differently than expected. **This was tested by the user himself
  on his own real device/SIM/bank** per his explicit privacy instruction
  ("I will follow steps myself, you just check logs, don't see any
  credentials") — no session content or credentials were captured or
  logged by the assistant. Next agent: do not assume this is an app bug
  before checking whether `*99#`'s menu is bank-specific; this may require
  testing with a different bank or reading NPCI's `*99#` menu-tree docs.
- **Mesh IOU is NOT a payment or money-movement feature** — this needs to be
  crystal clear to the next agent and to any judge/demo audience. It is a
  **signed, offline promise-to-pay record** exchanged over Bluetooth: Party
  A cryptographically signs "I owe Party B ₹X for [memo]", it's stored
  locally on both phones, and someone manually marks it "settled" later
  (e.g., after they regain signal and actually pay via UPI, or pay cash).
  No bank, no ledger, no funds ever move as part of this feature — it is a
  trust/bookkeeping aid for exactly the scenario where two people transact
  in a disaster zone with no way to actually move money yet, so they don't
  forget or dispute it later. It IS functional as a signed-record feature;
  it is NOT a payment rail. Misrepresenting this to judges as "we built
  offline payments" (beyond the real USSD rail) would be inaccurate.
- **Two-phone mesh has never been tested.** Everything above (chat routing,
  hop-count, fragmentation, Noise handshake between two *different*
  physical Noise identities, IOU send/receive) has only run against
  single-phone / self-loop or compiled-and-assumed testing. The user
  explicitly deferred this: *"This is fine, we will test ourselves this
  later."* This is very likely the single highest-risk untested surface in
  the whole app — the mesh transport and router logic all have unit tests
  with mocked transports, but real BLE GATT behavior between two different
  Android devices/OEMs can differ from any single-phone or mocked test.
- **Retrieval/heading-boost fix was found via a real bug, not
  proactively**: a "snake bite" query originally retrieved the wrong KB
  section ("Dog Bites and Rabies") because `KnowledgeRetriever` scored only
  chunk body text, not section headings. Fixed via `HEADING_BOOST_REPEATS`
  (heading text repeated 3x before tokenizing) — but this is a heuristic,
  not a principled fix. If a similarly-titled/overlapping KB doc pair causes
  wrong retrieval again, this is the first place to look, and a real
  embedding-based retriever (vs BM25/TF-IDF) may eventually be needed if the
  KB grows past ~20 files.
- **History contamination in the Assistant** was initially misdiagnosed as
  a prompt-engineering problem (fixed via "history is for pronouns only"
  language) but the actual root cause was the retrieval bug above. Both
  fixes are in place; if cross-topic contamination reappears, suspect
  retrieval first, not the history-handling prompt logic.
- **No dedicated SOS/emergency button** exists in the app shell — flagged in
  `docs/adr/0013` as explicitly out of scope for the UI-polish pass, not
  forgotten. Worth strongly considering for a crisis app given genre
  convention (every reference disaster app — Red Cross, FEMA — has one).
- **No formal `impeccable audit`/`critique` score** was generated — UI fixes
  came from direct visual inspection, not a scored report. If more UI time
  exists, running `impeccable audit` for a full P0-P3 findings list could
  surface more than the 4 issues fixed this session.
- **`docs/adr/0010` is missing** — ADR numbering jumps 0009 → 0011. Not
  investigated; either a number was skipped intentionally or a file was
  lost. Worth a `git log -p` check if ADR continuity matters.
- **material-icons-extended dependency risk**: added
  `androidx.compose.material:material-icons-extended` this session given
  this project's documented history of fragile JDK11/AGP7.4.2/D8 toolchain
  issues (see `docs/adr/0006`). It built clean, but flag this dependency
  specifically if a future toolchain upgrade breaks the build — it's the
  newest addition to the dependency graph.

## 6. Immediate next steps, in priority order

1. **Two-phone mesh test** (chat + IOU) — the single biggest untested risk.
   Needs 2 physical Android devices (or 1 device + 1 emulator with BLE,
   though BLE-on-emulator is often unreliable — prefer 2 real phones).
2. **Resolve the USSD PIN/balance-only issue** — or, if it turns out to be
   bank-menu-specific and not fixable app-side, adjust the demo script to
   route around it honestly rather than claim it works end-to-end.
3. **Decide on and possibly build an SOS/emergency entry point** — highest
   leverage feature-shaped gap for a crisis app, not yet scoped in the PRD.
4. **Re-verify the LLM model file push/setup path on a clean checkout** —
   since the `.task` file isn't committed, a fresh clone + fresh device will
   NOT have Assistant working until this is redone; document it in a new
   ADR or in this file's successor once confirmed.
5. Consider running `impeccable audit` for a fuller UI findings list if time
   allows.

## 7. Uncommitted work — read before running `git status`

As of this handoff, `git status` on `master` shows a large amount of
**modified-but-uncommitted** and **untracked** work — essentially this
entire session's worth of changes (LLM overhaul, retrieval fix, UI/UX pass,
Mesh IOU feature, the `payments/` package, ADRs 0011-0013) is sitting
uncommitted on top of the last real commit (`df6c4c9`, "Day 2 (3/3)").
**This handoff process itself commits and pushes all of it** — see §9 — so
by the time the next agent reads this from GitHub, it should already be
part of the initial commit history there. If it isn't (e.g., this file was
read from local disk instead), run `git status` immediately and do not
discard anything — it's the majority of the app's current functionality.

## 8. Knowledge base contents (for the Assistant feature)

20 plaintext files in `app/src/main/assets/kb/docs/`, one topic each:
bleeding & wounds, burns & electrical injury, CPR & choking, cyclone & high
wind, disaster preparedness kit, drowning & water rescue, earthquake safety,
emergency contacts (India-specific), fire safety & evacuation, flood safety,
fractures/sprains/spinal injury, gas leak & chemical hazards, heat stroke &
dehydration, hypothermia & cold exposure, missing person & reunification,
poisoning & emergency childbirth, psychological first aid, road traffic
accident response, shelter & evacuation planning, snake bite & animal
attacks. Parsed by `KnowledgeDocumentParser.kt` into `KnowledgeChunk`s keyed
by section heading — see §5's retrieval note before adding more files.

## 9. Repository / handoff mechanics

- This repo (`SankatSetu`, local only) is being pushed to
  **https://github.com/divyamX700/AWS-Hackathon** as part of this handoff,
  including this file and all uncommitted work described in §7.
- Every markdown file generated during development (`README.md`,
  `NOTICE.md`, everything under `docs/`, including all ADRs 0001-0013 and
  `docs/PLAN.md`/`docs/PRD.md`/`docs/concepts/*`) is preserved — nothing was
  deleted as part of this handoff.
- Read `docs/PRD.md` and `docs/PLAN.md` first for the original scoping and
  day-by-day plan; read the ADRs in numeric order for the decision history
  and the *why* behind non-obvious choices (several of which cost real
  debugging time when second-guessed — see the toolchain warnings in §3).
