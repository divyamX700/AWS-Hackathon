# First Commit — Build Plan (v2, after protocol research)

**Working name:** Sankat Setu (crisis bridge)
**Hackathon:** First Commit / Bharat Builds Tour · Sept 17–20, 2026
**Track:** **Build It** (open-source AWS stack, local, no deployed URL)

**Change log from v1:** Reworked BLE mesh section against Bitchat's actual whitepaper (v2.0, July 6 2026) with real numeric parameters instead of hand-waving. Added Nostr as the "connectivity-returns" global fallback (Bitchat's own dual-transport idea). Folded in the BlueMesh paper's Proximity-Aware Routing Algorithm as an optional experimental layer with honest attribution. Split the agent layer into an on-device tier (MediaPipe) and a gateway tier (Strands) so we can honestly claim to use Strands. Rejected MERN with a reason. Sharpened the mesh-IOU into a signed voucher with auto-settle semantics.

---

## 1. The idea in one line

**A single Android app that keeps a village or city block functioning during a crisis when the internet is dead** — a Bitchat-modelled Bluetooth-mesh chat network, an offline UPI payment channel (Flowpay-style) plus a signed mesh IOU for the case where even USSD/IVR are down, and a small on-device LLM that answers "what do I do now" from a first-aid / disaster-response knowledge base — all glued together with the open-source AWS stack (Strands Agents SDK, OpenSearch, Cedar, SAM Local) so it also scores on Build It. When connectivity eventually returns to any phone in the mesh, queued SOS reports and IOUs settle: payments over UPI, messages via Nostr relays for global reach.

Judge-facing sentence: **"Every Indian smartphone already has everything it needs to save lives during a blackout. Nobody has put those pieces in one app."**

---

## 2. What the research changed

| Source | What it gave us | How we're using it |
|---|---|---|
| **Bitchat whitepaper v2.0** | The definitive spec for a working BLE mesh: TTL, LRU dedup, jitter windows, Noise XX/X patterns, courier envelopes with rotating recipient tags, spray-and-wait, gossip sync with GCS filters, dual transport with Nostr fallback. All with exact numeric parameters. | We reimplement in Kotlin against the same parameters (not a Swift copy — clean-room). Nostr fallback becomes our "back online" story. Everything is a well-cited derivative, not a research bet. |
| **Bitchat deepwiki / repo README** | Architecture picture: dual transport (`MessageRouter` prefers BLE, falls back to Nostr, engages couriers when neither works), IRC-style commands, emergency triple-tap wipe. | Same layered dispatcher model. Same panic-wipe UX. |
| **BlueMesh paper (Chouhan, ISJEM 2026)** | Introduces **Proximity-Aware Routing Algorithm (PARA)** — RSSI + mobility + battery/reliability-scored relay selection instead of naive flood. Nice sequence diagram of the store-and-forward flow. **But**: no code, no benchmarks, and proposes MERN on-device (a poor fit — Node+Mongo on a phone is not sensible for BLE work). | We *credit* PARA as an inspiration for an optional experimental routing mode (RSSI-weighted subset choice on top of Bitchat's flood), and *reject* MERN in our writeup with a reason. That contrast makes a strong Learning-criterion paragraph. |
| **Nostr protocol** | Event-based signed-JSON protocol with dumb WebSocket relays. Global reach without a server of our own. Kind 1 (text notes), kind 1059 (gift wrap), kind 4/14 (private messages). Anyone can spin up a relay; ~440 public relays exist. | Nostr becomes our "connectivity returned" transport. Any phone that touches Wi-Fi/cellular publishes queued SOS reports as signed events; a coordinator elsewhere in India subscribes to a geohash filter and sees them within seconds. No server to run. |
| **Briar (Java/Kotlin, GPLv3)** | Reference implementation of an Android messaging app with three transports (Tor, Bluetooth classic, Wi-Fi). Contact addition via QR handshake. Well-documented approach to Android background BLE execution and permission handling. GPL-infected, so no code-lifting. | We *look* at Briar's Android background-execution and QR-handshake handling as a design reference, cite it as prior art, and don't copy code. Their transport plugin abstraction is a good pattern we'll adapt into our own `Transport` interface (same idea Bitchat calls out). |
| **Bridgefy SDK** | Mesh app used at scale (Hong Kong 2019, Cuba 2021 protests). BLE for discovery, upgrade to Wi-Fi Direct for larger transfers. Commercial SDK now, but their public architecture writeups are useful. | We adopt the "BLE discovers, Wi-Fi Direct carries bulk" pattern for the optional file/photo-of-injury feature. Cited as prior art. |

---

## 3. Why this hits First Commit's judging criteria

Same table as v1 but now with concrete backing:

| Criterion | Why we hit it |
|---|---|
| **Idea & Impact** | Real, felt problem — every recent Indian disaster (Wayanad landslide, Chennai floods, Kerala floods) has "cell towers down, ATMs down, no way to reach family, no way to pay for transport" as a recurring pattern. Theme is fully open ("solve a real problem" — hackathon's own words). |
| **Built on AWS** | Genuine, non-shoehorned use of **4 of 5** Build It items: Strands (gateway agent), Cedar (on-device authorization for priority channels), OpenSearch (RAG index for the LLM's knowledge base), SAM Local + LocalStack (backhaul Lambdas). See §7. |
| **Learning** | Nobody on the team has shipped BLE mesh, Noise Protocol, on-device LLMs, Cedar, or Strands before. Our writeup gets a genuine "here's what we learned" paragraph with three sub-bullets: BLE mesh design against Bitchat's spec, MediaPipe on-device inference, and Cedar policy authoring. Hackathon explicitly scores this. |
| **Execution** | We commit to one working demo path on 3 phones + 1 laptop, everything peripheral stubbed. Every extra feature has to earn its way in on Day 4. |
| **Demo video (3 min)** | 6 beats × 30 sec: problem clip → mesh chat with airplane mode on → offline LLM Q&A → offline UPI payment → mesh IOU voucher → gateway dashboard. Shot list in §11. |

---

## 4. Features, ranked

### Primary (must ship, or the project is nothing)
1. **BLE mesh chat** — Bitchat-modelled, Kotlin clean-room implementation. Parameters lifted from the whitepaper (§5.1). Two phones talking with airplane mode on, three phones showing multi-hop.
2. **On-device LLM emergency assistant** — Gemma 3 1B int4 via MediaPipe LLM Inference, grounded in a packaged NDMA/WHO/Sphere knowledge base. Answers first-aid and evacuation questions offline.

### Secondary (must ship — this is the Flowpay-parity feature the user asked for)
3. **Offline UPI payments** — import Flowpay's Apache-2.0 payment module wholesale; expose it in our UI:
   - `*99*1*3#` USSD scan-to-pay (works on non-Jio carriers)
   - UPI 123Pay IVR — `tel:08045163666,,1,<phone>,,<amount>,,1` DTMF format (works on Jio too)
   - Bank SMS confirmation via Flowpay's paise-exact parser
4. **Mesh IOU voucher** — the novel bit, when even USSD/IVR are down. Sender signs a payload → mesh delivers → receiver stores signed proof → **either device coming online auto-triggers UPI settlement**. Details in §5.3.

### Tertiary (nice to have, stubs OK)
5. **Priority channels gated by Cedar** — `#sos` open to all, `#official` gated to district-officer-signed identities.
6. **Nostr "back online" bridge** — when any phone touches connectivity, queued SOS reports publish to global Nostr relays as signed events. Any Strands agent (anywhere in India) subscribing to a geohash filter can see them within seconds.
7. **Gateway coordinator dashboard** — laptop-side Strands agent that ingests either Nostr or LAN-relayed reports, groups by geohash, drafts prioritised dispatch.

### Deliberately cut
- **iOS.** Android only. Flowpay is Kotlin and BLE quirks are per-OS.
- **Satellite.** Android's built-in emergency-SOS-via-satellite is OEM-locked (Pixel 9+, Galaxy S25+); no public API. Flag as future work.
- **User accounts / phone auth / central servers.** Nobody. Ephemeral identity like Bitchat.
- **Any deployed Ship-It AWS.** Stay pure on Build It.
- **MERN.** BlueMesh paper's Node+Mongo-on-device approach is a mismatch for BLE work — we say so explicitly in the writeup, which gives us a strong Learning-criterion paragraph.

---

## 5. Protocol details (grounded in Bitchat whitepaper v2.0)

### 5.1 BLE mesh — exact parameters

Model = Bitchat's v2 protocol, reimplemented clean-room in Kotlin. Whitepaper is public-domain (Unlicense), so we can implement to spec without infecting our license.

**Transport interface.** Every device runs both GATT central and peripheral roles, discovering peers via BLE advertising on a custom service UUID. Same `Transport` abstraction Bitchat uses, so we can add Nostr as a second `Transport` later without rewriting.

**Packet format** (Bitchat v2, adapted):
- Compact binary header: version (1B), type (1B), TTL (1B), timestamp (8B), flags (1B)
- 8-byte sender ID (first 8 bytes of SHA-256(Noise static pubkey))
- Optional 8-byte recipient ID
- Payload (variable)
- Optional Ed25519 signature (excludes TTL byte so relays don't invalidate it)
- Padding on Noise-encrypted frames only, to 256/512/1024/2048-byte buckets (PKCS#7-style)

**Flood control** (all numbers from Bitchat whitepaper §4.2):
- **TTL**: origin default 7. Relays clamp: dense graphs (≥6 links) cap broadcast TTL at 5; thin chains (≤2 links) relay at full incoming depth.
- **Deduplication**: LRU seen-set (1000 entries, 5-min expiry) keyed by (sender, timestamp, type, payload-digest). Cancels scheduled relays if a duplicate arrives first.
- **Jitter**: relays wait a random 10–220 ms (wider window when dense).
- **Fanout subsetting**: broadcasts go out to a message-ID-seeded subset of ~log₂(degree) links, not all of them. Announces, fragments, and sync packets get full fanout. Ingress link excluded (split horizon).
- **Directed traffic** (handshakes, private messages, courier envelopes): relays deterministically with TTL−1, tight jitter, never subset.

**Announcements** (§4.5): signed presence beacons every 4s while isolated, backing off to 15–30s jittered when connected. A verified announce keeps a peer marked reachable for 60s.

**Fragmentation** (§4.4): packets over MTU split into ~469-byte fragments (8-byte fragment ID, index/total). Reassemble at each receiving node. 128 concurrent assemblies, 30s timeout, 1 MiB cap.

**Encryption** (§5):
- Live sessions: Noise `XX` pattern (Curve25519 / ChaCha20-Poly1305 / SHA-256). Mutual authentication + forward secrecy. All private payloads ride as typed ciphertext.
- Offline mail: Noise `X` pattern for courier envelopes. **No forward secrecy** — Bitchat acknowledges this as a limitation; we inherit it and say so.
- Kotlin implementation: `noise-java` (fork onto Android) or `com.rfksystems:noise-java`.

**Store-and-forward** (§6): four mechanisms, all panic-wiped:
1. **Sender outbox** — 100 messages/peer, 24h TTL, 8-attempt cap. Persisted to disk sealed with ChaChaPoly.
2. **Couriers** — sealed envelopes handed to up to 3 connected peers. 16-byte rotating recipient tag = HMAC(recipient_static_key, UTC_day) — couriers can't correlate mail across days. 5 slots for mutual favorites, 2 for verified peers. Spray-and-wait: initial copy budget 4, cap 8, half-split when couriers meet.
3. **Public history gossip** — 1000-packet cache, 15s sync interval using GCS filters. 6h retention. Persists across restarts.
4. **Nostr mailboxes** — for mutual favorites, private envelopes rest on Nostr relays with 24h lookback subscription on reconnect.

**PARA extension (optional, from BlueMesh paper).** Under a `experimental.smart_routing = true` flag, we replace the fanout subset choice with an RSSI + reliability-weighted selection: each neighbor gets a score = (α · RSSI + β · uptime + γ · battery + δ · past-forwarding-rate). We keep the size of the subset the same (~log₂(degree)) but pick the top-K by score. Credited to Chouhan 2026 in the code and writeup. If we don't get to this by Sunday, we cut it and mention it as future work — the baseline flood already works.

### 5.2 Offline UPI payments — from Flowpay

Straight import of Flowpay's payment module (Apache 2.0, allowed under Rule 03 as an open-source library):
- **`*99*1*3#`** dialed via `Intent.ACTION_CALL` with `Uri.encode` preserving `#`. Rides GSM signalling, not IP. Doesn't work on Jio.
- **UPI 123Pay IVR**: `tel:<service>,,1,<phone>,,<amount>,,1` DTMF format built by `Upi123CallStringBuilder`. Each `,` is a 2-second pause. Service number `08045163666`. Amount cap ₹4,999 (NPCI-set for IVR).
- **Outcome truth**: bank SMS parsing via `SmsTransactionParser` — paise-exact match, 10-minute deadline, delete-pending-on-timeout. Never trust call state.
- Reused as-is; Flowpay credited in `README.md`, `NOTICE.md`, and the demo video.

### 5.3 Mesh IOU voucher — our novel bit

The scenario: A wants to pay B ₹200 in cash-drought conditions where even USSD/IVR are unreachable (dead cell tower, exhausted mobile balance). Both phones have Bluetooth.

**Envelope** (signed with A's Ed25519 identity key):
```
{
  version: 1,
  type: "iou",
  from_pubkey:   <A's Noise static pubkey>,
  from_upi_id:   <A's UPI ID — hashed, not plaintext>,
  to_pubkey:     <B's Noise static pubkey>,
  amount_paise:  20000,
  nonce:         <16 random bytes>,
  created_at:    <UTC ms>,
  expires_at:    <UTC ms + 72h>,
  signature:     <Ed25519 sig over the above>
}
```

**Delivery**: mesh transport (same courier system as private messages).

**Storage**: both A and B store the signed record in their local Room DB (SQLCipher, reusing Flowpay's schema with a new `Iou` entity). B sees it in a separate "Mesh IOUs" tab of their transaction history.

**Settlement** (whichever side comes online first):
- **A comes online first**: A's app auto-fires a UPI 123Pay call or a standard UPI transfer (data path) to B's UPI ID for ₹200, adding the IOU nonce as a reference. Bank SMS confirms. Both apps mark the IOU as `SETTLED`.
- **B comes online first, A still offline**: nothing happens automatically — B can't pull money from A without A's PIN. B waits. When A comes online, A's app sees B's still-open IOU (broadcast via Nostr with A's pubkey as filter) and fires the settlement.

**Honest failure modes** we don't hide:
- If A never comes online, or A's bank balance is insufficient, the IOU stays `UNSETTLED`. B has cryptographic proof of A's promise for later dispute, but no money.
- The IOU is a *promise*, not a bearer instrument. We don't pretend otherwise.

This design is faithful to Flowpay's honesty-by-default philosophy: don't guess, don't lie about outcomes.

### 5.4 Nostr as the "connectivity returns" transport

Bitchat's own dual-transport idea, adapted to our disaster context:

- When any phone in the mesh touches Wi-Fi or cellular data (even a single passing car with signal), it flushes queued SOS reports and IOU settlements to Nostr relays as signed events.
- Event kinds:
  - Public SOS report → kind 1 (short text note) with a `#sos` and `#geohash-XX` tag
  - Private IOU broadcast for settlement → kind 1059 gift-wrap with our own inner content (v2 XChaCha20-Poly1305, same construction Bitchat uses — not NIP-44 compatible, and we say so)
- A gateway/coordinator anywhere in India subscribes with a `REQ` filter `{"kinds":[1], "#t":["sos"], "#geohash":["dr5rs"]}` and sees SOS reports within seconds of any phone reaching connectivity.
- **This gives us global reach without running a server.** Nostr's ~440 public relays do it for us.

The Strands gateway agent (§7) uses Nostr as its input source alongside LAN uploads.

### 5.5 On-device LLM emergency assistant

- **Runtime**: MediaPipe LLM Inference for Android (Apache 2.0, official Google library). Handles model loading, GPU/NPU acceleration, streaming inference.
- **Model**: Gemma 3 1B int4 (~529 MB `.task` file). Falls back to Gemma 2 2B int4 (~1.4 GB) on higher-end devices if we detect ≥8 GB RAM.
- **Prompt shell**: pinned system prompt tells the model it's a rural-India first-response assistant, answer only from provided context, always include emergency phone `112`, output ≤3 short paragraphs.
- **RAG**: local retrieval from a SQLite index shipped in the APK. Index built at dev time by embedding the knowledge corpus (NDMA guidelines, IFRC first-aid, WHO psychological first-aid, Sphere handbook subset) with `all-MiniLM-L6-v2` and exporting the top-k neighbor structure. On-device: query → embed with the same model (~22 MB ONNX) → ANN lookup → top-3 chunks → prompt → Gemma → answer.
- **Latency budget**: Gemma 3 1B int4 gives ~15–20 tok/s on a Pixel 7. First-token <1 s, full 100-token answer <8 s. Acceptable for a demo.
- **Tools the agent can invoke** (Kotlin agent loop, Strands-patterned):
  - `search_knowledge_base(query)` → RAG hit
  - `broadcast_sos(injury_type, victim_count, geohash)` → composes an SOS mesh envelope
  - `list_shelters(geohash)` → static packaged geojson lookup

---

## 6. Architecture (revised)

```
                        ANDROID APP (per phone)
┌──────────────────────────────────────────────────────────────────┐
│  UI (Jetpack Compose, Material 3)                                │
│  ├─ Chat  ├─ Assistant  ├─ Payment  ├─ SOS  ├─ IOU tab           │
├──────────────────────────────────────────────────────────────────┤
│  Cedar Policy Engine  (cedar-java + JNI to Rust FFI via NDK)     │
│  Every inbound message → isAuthorized(principal, action, channel)│
├──────────────────────────────────────────────────────────────────┤
│  Agent loop (Kotlin, Strands-patterned)                          │
│  ├─ tools: search_kb, broadcast_sos, list_shelters, compose_iou  │
│  └─ model backend: MediaPipe LLM Inference (Gemma 3 1B int4)     │
├──────────────────────────────────────────────────────────────────┤
│  Local RAG index (SQLite + MiniLM ONNX embeddings) — 20 MB       │
├──────────────────────────────────────────────────────────────────┤
│  Message layer                                                   │
│  ├─ Chat (text) ├─ SOS ├─ IOU voucher ├─ Payment session         │
│  └─ Every outbound envelope signed with per-device Ed25519 key   │
├──────────────────────────────────────────────────────────────────┤
│  MessageRouter (dispatch, Bitchat-style)                         │
│  Prefer live mesh → fall back to Nostr → engage courier system   │
├──────────────────────────────────────────────────────────────────┤
│  Transports                                                      │
│  ├─ BLE mesh (Noise XX live, Noise X sealed)                     │
│  │    TTL 7 clamped, LRU 1000/5min dedup, 10–220ms jitter,       │
│  │    log₂(degree) fanout subset, GCS gossip sync                │
│  ├─ Nostr client (kind 1 SOS, kind 1059 private) — over Tor      │
│  │    if enabled and available, plain WebSocket otherwise        │
│  └─ UPI dialer (USSD + 123Pay IVR) — Flowpay's CallManager       │
├──────────────────────────────────────────────────────────────────┤
│  Persistence: Room + SQLCipher (Flowpay's data layer, extended)  │
│  Entities: Transaction, IouVoucher, MeshMessage,                 │
│            CourierEnvelope, PublicHistoryCache                   │
└──────────────────────────────────────────────────────────────────┘
                             ↓ opportunistic ↓
   ┌────────────────────────────────────────────────────────────┐
   │              GATEWAY (dev laptop, for demo)                │
   │  ┌──────────────────────────────────────────────────────┐  │
   │  │  Strands Agents SDK (Python, Apache 2.0)             │  │
   │  │  ├─ subscribe to Nostr relays on #sos + geohash tags │  │
   │  │  ├─ subscribe to LAN Lambda invocations from phones  │  │
   │  │  ├─ tools: query_opensearch, cluster_by_geohash,     │  │
   │  │  │          score_priority, draft_dispatch           │  │
   │  │  └─ model backend: Ollama (Gemma 2 9B / Llama 3.2 3B)│  │
   │  ├──────────────────────────────────────────────────────┤  │
   │  │  SAM CLI + LocalStack                                │  │
   │  │  ├─ Lambda: IngestSosLan, IngestIou, NotifyOfficials │  │
   │  │  ├─ DynamoDB Local: incident_store, iou_ledger       │  │
   │  │  └─ SNS Local: fake pager to district office         │  │
   │  ├──────────────────────────────────────────────────────┤  │
   │  │  OpenSearch (Docker, single node)                    │  │
   │  │  └─ Vector index of NDMA / IFRC / WHO / Sphere docs  │  │
   │  └──────────────────────────────────────────────────────┘  │
   └────────────────────────────────────────────────────────────┘
```

---

## 7. AWS Build It stack — mapped, honestly

Rule: "Using an AWS open-source project or AWS services is **mandatory** to win a prize." One is required; more strengthens the story.

| AWS OSS | Where it lives | What it actually does | Real, or shoehorned? |
|---|---|---|---|
| **Strands Agents SDK** (Python, Apache 2.0) | Gateway laptop | Drives the coordinator agent: subscribes to Nostr relays + LAN Lambda queue, groups incoming SOS reports by geohash, calls tools to query OpenSearch and draft prioritised dispatch, streams traces to the terminal (visible in the demo video). | Real. Model-agnostic Python agent framework — this is exactly what Strands is for. |
| **OpenSearch** (Docker single node) | Gateway laptop | Hosts the disaster-response corpus (NDMA guidelines, IFRC first-aid, WHO psychological first-aid, Sphere handbook subset, IMD alert taxonomy, state SOPs where public). BM25 + k-NN indexes. Used both by the gateway agent at demo time and — via export at build time — as the source for the on-device SQLite index shipped in the APK. | Real. OpenSearch is the correct tool for RAG at this scale. |
| **Cedar** (cedar-policy, Rust core + cedar-java bindings) | On-device Android (cross-compiled `.so`) | Authorizes every mesh message. Policies:<br>1. anyone can `read` any channel<br>2. anyone can `write` `#chat` and `#sos`<br>3. only principals whose identity key is signed by the preloaded District Officer key can `write` `#official`<br>4. rate-limit (max 30 msgs/min per principal on `#chat`, 5 on `#sos`) | Real. Cedar is built exactly for this "policies live outside the code" problem. Having it on-device means enforcement holds when the app is offline — the entire point. |
| **SAM CLI + LocalStack** | Gateway laptop | Coordinator side runs as SAM-templated Lambdas (`IngestSosLan`, `IngestIou`, `NotifyOfficials`) against LocalStack (DynamoDB, SNS, EventBridge). Provable via `sam local invoke` — no AWS account, no bill. | Real. Intended use. |
| PartyRock | — | Skipped. It's a browser-based no-code AI app builder; no fit for a native Android disaster app. Better to use four items well than five badly. | Correctly skipped. |

Coverage: **4 of 5** Build It items, each with a concrete non-shoehorned reason. Combined with the demo video showing them working, this is the strongest "Built on AWS" story we can honestly tell.

---

## 8. Threat model (compact, honest)

Directly inherited from Bitchat whitepaper §8, adapted to our disaster context:

| Threat | Our answer |
|---|---|
| Relay nodes reading private traffic | Cannot — Noise XX means relays see only opaque ciphertext. |
| Malicious courier dropping mail | Bounded by redundant copies (spray-and-wait budget) and deposit-retry. Cannot amplify — copy budgets are capped. |
| Flooding / DoS via crafted broadcasts | TTL clamps, LRU dedup, per-depositor quotas, connect-rate limits, Cedar rate-limit policies. |
| Replay of public broadcasts | 6-hour acceptance window + LRU dedup. Private payloads protected by Noise nonces. |
| Passive metadata leakage on-air | **Weakest area, and we say so.** Bitchat's own limitation: peer IDs are stable (derived from static key), announces publish nicknames and neighbor lists in cleartext. We inherit this. Future work: epoch-rotating peer IDs. |
| Spoofing / impersonation of an "official" | Cedar policy on `#official` requires signature by the District Officer's key, which is preloaded via QR code in setup. Attacker without that private key cannot write to the channel. |
| No forward secrecy for sealed courier mail | Inherit Bitchat's limitation. Documented. Future work: prekey scheme. |
| Fake IOU vouchers | Ed25519-signed with sender's identity key. Verified before storage. An attacker can't forge without the private key; a malicious sender can promise money they don't have (settlement will fail, receiver has proof for dispute). |

BlueMesh paper listed spoofing, replay, and unauthorized access as unresolved concerns. Our answer: Noise handshake, LRU dedup, and Cedar authorization respectively.

---

## 9. Tech stack (concrete)

**Android app**
- Kotlin 2.1, Android Studio Ladybug+, Jetpack Compose (Material 3)
- Min SDK 29 (Android 10), target SDK 35 (matches Flowpay)
- Room + SQLCipher — reuse Flowpay's data layer, extended with `IouVoucher`, `MeshMessage`, `CourierEnvelope`, `PublicHistoryCache` entities
- BLE via native `BluetoothLeScanner` / `BluetoothGattServer` APIs
- Noise Protocol: fork `noise-java` for Android (Curve25519, ChaCha20-Poly1305, SHA-256)
- MediaPipe: `com.google.mediapipe:tasks-genai:0.10.16+` for the Gemma runtime
- ONNX Runtime Mobile for the MiniLM embedding model
- Cedar: `cedar-java` uber-jar + cross-compiled `libcedar_java_ffi.so` for `arm64-v8a`, `armeabi-v7a` (`cargo-ndk`)
- QR (setup handshake + officer key exchange): ZXing + CameraX
- Nostr client: Kotlin coroutine wrapper over OkHttp WebSocket; secp256k1 via `bitcoinj` or `secp256k1-kmp`

**Gateway (dev laptop)**
- Docker Desktop
- `opensearchproject/opensearch:latest` single node
- `localstack/localstack` CE
- SAM CLI (v1.130+)
- Python 3.11 + `strands-agents` + `strands-agents-tools` + `boto3` (pointed at LocalStack) + `nostr-sdk` Python client
- Ollama with `gemma2:9b` (or `llama3.2:3b` if the demo laptop is thermally constrained)

**Dev tooling**
- Git, GitHub public repo (Rule 04)
- OBS Studio + kdenlive / DaVinci Resolve for the 3-min demo cut
- AWS Builder Center account for the writeup / blog

---

## 10. 4-day schedule (Thu Sept 17 – Sun Sept 20)

**Day 0 — Wed evening, pre-clock (allowed by rules)**
Learning and practice only. No repo, no project code.
- Get MediaPipe LLM Inference sample running end-to-end on a test Pixel
- Get `cedar-java` hello-world compiling and running an `isAuthorized` call
- Get the docker-compose stack up (OpenSearch + LocalStack + SAM CLI)
- Read Bitchat whitepaper end-to-end (v2.0). Read Flowpay's `Upi123CallStringBuilder.kt` and `SmsTransactionParser.kt` end-to-end.
- Cross-compile `cedar-ffi` `.so` for `arm64-v8a` with `cargo-ndk` (this is the hardest single yak-shave — do it Wednesday)

**Day 1 — Thu (kickoff, BLE mesh baseline)**
- `git init`, first commit, Compose scaffold
- BLE peripheral advertising + central scanning on a custom service UUID
- Two-device handshake: exchange peer IDs, send one plaintext "hello" over GATT
- Noise XX handshake wired in (via `noise-java` fork)
- Two devices exchange one encrypted "hello"
- **End-of-day gate**: two-phone one-hop encrypted chat working.

**Day 2 — Fri (mesh depth + LLM)**
- Multi-hop relay with TTL, LRU dedup keyed by (sender, timestamp, type, digest)
- Announce packets every 4s / 15–30s per whitepaper, signed
- Sender outbox for offline recipients (100/peer, 24h)
- Courier envelope layer (spray-and-wait budget 4, 16-byte HMAC-day recipient tag)
- MediaPipe wired up, one-shot answer to a hardcoded prompt
- Ship the pre-built RAG SQLite as an APK asset; wire retrieval
- **End-of-day gate**: three-phone mesh chat + on-device LLM answers a first-aid question offline.

**Day 3 — Sat (payments + Cedar + gateway; optional Bangalore day for one teammate)**
- Import Flowpay payment module as a git submodule, credited in NOTICE
- Wire USSD + 123Pay flows into our UI
- Layer the mesh-IOU voucher on top of Flowpay's `PaymentSessionManager`
- Cedar `.so` loaded via `System.loadLibrary`; policy set authored in `assets/policies.cedar`; every inbound mesh message goes through `isAuthorized`
- Docker-compose up: OpenSearch + LocalStack; SAM template with 3 Lambdas
- Strands agent that reads DynamoDB Local queue + Nostr subscription, calls OpenSearch, prints prioritised dispatch
- **End-of-day gate**: full end-to-end path working on two phones + one laptop.

**Day 4 — Sun (polish, video, writeup, submit)**
- Record the 3-min demo (§11)
- Repo cleanup: README, ARCHITECTURE.md, NOTICE, LICENSE (Apache 2.0)
- Writeup (Rule 04): problem, build, where AWS fits, AI tools disclosure, learning paragraph
- Blog for AWS Builder Center (top-5-blogs prize eligibility)
- Submit before Sunday deadline

---

## 11. Demo video shot list (3 min, 6 beats × 30 s)

Rule 04 is brutal: **"Judges score what you submit and nothing else. There is no live demo. If the video does not show it, it does not count."** So the video *is* the project.

1. **00:00–00:20 — The problem, real footage.** News clip of Wayanad landslide / Chennai flood. Voice: "When the internet dies, everything else dies with it. Payments, calls, ambulances, family."
2. **00:20–00:50 — BLE mesh chat.** Two phones on a table, cameras confirm airplane mode. Message goes from one to the other. Add a third phone as a middle relay, show hop count in the debug UI.
3. **00:50–01:30 — Offline LLM.** Same phones, still offline. "How do I treat a bleeding wound with no first-aid kit?" Answer streams in on-screen, cites the packaged handbook.
4. **01:30–02:00 — Offline UPI payment.** Show 123Pay DTMF call composing on-screen. Bank confirmation SMS arrives. "Paid ₹100 via mesh" appears in history.
5. **02:00–02:30 — Mesh IOU voucher.** Both phones offline, no cell. A sends B ₹200 as a signed IOU over mesh. B's screen shows the pending voucher within seconds. Cut to A restoring connectivity → auto-settlement fires → both apps show `SETTLED`.
6. **02:30–03:00 — Gateway dashboard + close.** Cut to laptop terminal: Strands agent's trace scrolls as a Nostr-relayed SOS lands. Dashboard groups by geohash and prioritises. Voice: "One APK. No accounts. No servers. Every Indian smartphone already has the radios. All open-source, on the AWS Build It stack." Repo URL card.

---

## 12. What the writeup must say (Rule 04)

- **Problem**: cell towers go down first in every disaster; UPI Lite/123Pay exist but nobody uses them. Cite Wayanad, Chennai, Kerala data.
- **The build**: architecture diagram, protocol details, what we imported vs what we wrote.
- **Where AWS fits**: name Strands, Cedar, OpenSearch, SAM Local, one paragraph each.
- **AI coding tools disclosure** (Rule 03): "We used Claude Code for scaffolding and code review."
- **Credits**: Flowpay (Apache 2.0, imported wholesale), Bitchat whitepaper (public domain, protocol reference), MediaPipe (Apache 2.0), noise-java, all corpus sources with per-doc licenses in `assets/kb/NOTICES.md`. **Explicit clean-room statement**: we implemented Bitchat's protocol from the whitepaper without copying its Swift source.
- **Learning paragraph** (scored criterion): honest, three sub-bullets:
  - Implementing Bitchat's mesh protocol from scratch in Kotlin — Noise handshake, TTL/dedup/jitter, courier envelopes with rotating tags.
  - Running Gemma 3 1B on-device with MediaPipe — first time any of us had touched on-device LLMs.
  - Cedar policy authoring and getting the Rust FFI cross-compiled for Android arm64.

---

## 13. Risks and honest mitigations

| Risk | Mitigation |
|---|---|
| BLE mesh takes longer than one day on Android (OEM background-execution kills) | Demo phones are stock Pixels, kept awake with screen on. Production robustness is out of scope; we say so. Also Briar's docs and Nordic Semiconductor's Android BLE cookbook are references. |
| Cedar's Rust FFI is a pain to cross-compile for Android | Plan B: run Cedar on the gateway laptop, phone calls it over LAN for demo. Weaker story but still counts. Try Plan A first — the yak-shave is Wednesday, before the clock starts, so no cost to project time. |
| MediaPipe model file too big for APK | Side-load the `.task` file on demo phones ahead of time. First-run bundle limitation, mentioned in the video. |
| Noise-java is JVM, not Android-optimized | Fork it, replace `java.security.SecureRandom` with `android.security.keystore` for key material. Small change. |
| Nostr relay flakiness during demo | Preselect 5 reliable public relays (`wss://relay.damus.io`, `wss://nos.lol`, `wss://relay.nostr.band`, etc.). Redundancy is the point. |
| Flowpay's payment code isn't ours | It isn't. We say so, prominently, in README, video credits, and writeup. What we built is the mesh + IOU + LLM + Cedar + AWS + Nostr integration around it. |
| Judges don't recognize BlueMesh paper's PARA as a legitimate reference | It's published (ISJEM, April 2026). We cite it once, credit it fairly, and don't overclaim. If the paper's weakness comes up in evaluation, it doesn't hurt us — we credited the *idea*, not the paper's engineering. |
| Sunday deadline hits mid-recording | Video recording starts Saturday night at the latest. Sunday is buffer + writeup only. |
| Bangalore day pulls a teammate out of coding | The day is workshops/feedback/mentors — net-positive time. That person brings back mentor feedback + refined pitch for the video script. |

---

## 14. Attribution and IP boundaries (Rule 05 anti-cheating gate)

- **Flowpay** (`payment/*`): Apache 2.0. Imported as a git submodule. Credited in README, NOTICE, video.
- **Bitchat**: public domain (Unlicense). Whitepaper cited as protocol reference. **No Swift source copied** — Kotlin implementation is clean-room from the spec.
- **BlueMesh paper (Chouhan, ISJEM 2026)**: cited as inspiration for the optional PARA extension. No code was ever released, so nothing to copy.
- **MediaPipe**: Apache 2.0. Standard dependency.
- **noise-java**: Apache 2.0. Forked, changes documented.
- **cedar-java + cedar-policy**: Apache 2.0. Standard dependency.
- **Briar**: GPLv3. **Referenced as prior art only** — no code copied, so no GPL infection.
- **Bridgefy SDK**: commercial license today. **Referenced as prior art only** for BLE-discovers/Wi-Fi-P2P-carries pattern.
- **Corpus documents**: per-doc licenses tracked in `assets/kb/NOTICES.md`. All CC-BY, public domain, or open-access.
- **Repo history**: every commit after Thursday's kickoff. This plan file lives in a separate scratch repo. Rule 05 disqualifies mismatched repo history.

---

## 15. What to do RIGHT NOW (before Thursday)

- [ ] Everyone: WeMakeDevs account → register First Commit → AWS Builder Center profile → SheerID student verification
- [ ] One person: claim the $100/team AWS credit (harmless even though we're on Build It)
- [ ] Buy/borrow **3 Android phones** — Pixel 6 or later ideal. Fourth is a bonus for the multi-hop shot.
- [ ] Install Android Studio Ladybug+ everywhere
- [ ] Get docker-compose up with OpenSearch, LocalStack, SAM CLI
- [ ] **Cross-compile cedar-policy-ffi for `arm64-v8a`** — the hardest thing, do it now
- [ ] Read Flowpay's `docs/ARCHITECTURE.md` + the two payment files end-to-end
- [ ] Read Bitchat whitepaper v2.0 end-to-end
- [ ] Get one Strands agent running locally against Ollama
- [ ] Get one `cedar-java` hello-world running an authorization request
- [ ] Get MediaPipe LLM Inference sample running with Gemma 3 1B on a test phone
- [ ] Preselect 5 Nostr relays and confirm we can publish + subscribe from a laptop
- [ ] Draft demo-video script (30s per beat, §11). Rest of the team edits before Thursday.

---

## 16. One-paragraph pitch

**Sankat Setu** is a single Android app that lets a village stay in touch, get first-aid guidance, and move money when the internet is dead. It runs a Bitchat-protocol-compliant Bluetooth LE mesh (Noise XX for live sessions, sealed courier envelopes with rotating recipient tags for offline mail, gossip-synced public history) reimplemented clean-room in Kotlin against the published spec, with an optional RSSI-weighted routing mode inspired by the BlueMesh paper's Proximity-Aware Routing Algorithm. An on-device Gemma 3 1B model with a packaged NDMA/WHO/Sphere knowledge base answers "what do I do now" offline. Flowpay's proven `*99#`-USSD and UPI-123Pay-IVR paths handle payments where a cell signal exists — and a novel signed mesh-IOU voucher covers the case where even those are down, auto-settling when either party comes online. Cedar policies on-device enforce who can broadcast on official channels. When any phone touches connectivity, queued SOS reports publish to global Nostr relays; a Strands agent running on a district-office laptop, backed by OpenSearch RAG and SAM Local + LocalStack Lambdas, subscribes by geohash and shows a prioritised dispatch. Four of the five Build It stack items in real use, with a clean-room protocol build we can defend line by line.
