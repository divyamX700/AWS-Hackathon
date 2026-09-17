# Sankat Setu — Product Requirements Document

**Document owner:** Team lead
**Document status:** Draft v1, pre-kickoff
**Product name:** Sankat Setu (working title)
**Platform:** Android (min SDK 29, target 35)
**Hackathon:** First Commit / Bharat Builds Tour, Sept 17–20, 2026
**Competition track:** Build It (AWS open-source stack, no deployed URL)

> **Sourcing policy for this build:** any open-source repo referenced in this document may be lifted whole or in part. Treat Bitchat's Swift source, Briar's Kotlin/Java modules, Bridgefy's older Android SDK, Flowpay's payment module, MediaPipe's Android samples, cedar-java's authorization engine, and the Strands Agents SDK samples as **hackathon inputs, not read-only references**. Copy files, port line-by-line, keep variable names, whatever gets the demo working by Sunday. Attribution goes in `NOTICE.md` but does not block a merge.

---

## 1. Executive summary

Sankat Setu is a single Android app that keeps essential communication, information, and money movement working when the internet and cell networks are down or degraded — the failure pattern that defines every recent Indian natural disaster (Wayanad landslide 2024, Chennai floods 2023, Kerala floods 2018).

The app bundles four capabilities behind one UI:

1. **A Bluetooth-LE mesh chat network** — reimplements the Bitchat v2.0 protocol on Android: Noise-encrypted, multi-hop, store-and-forward, gossip-synced public history, courier envelopes for offline recipients.
2. **An on-device emergency assistant** — a Gemma 3 1B model running via MediaPipe LLM Inference, grounded in a packaged first-aid and disaster-response knowledge base, so users can get evacuation and medical guidance with zero connectivity.
3. **Offline UPI payments** — Flowpay's `*99#` USSD and UPI 123Pay IVR paths, wholesale, so a user can pay a shopkeeper for water or a bus fare without internet, on any Indian SIM.
4. **A mesh-signed IOU voucher** — for the case where even USSD/IVR are unreachable, the sender signs a promise-to-pay, the mesh delivers it, and settlement fires automatically when either side reaches connectivity.

An optional **gateway service** on a laptop — running Strands Agents SDK, OpenSearch, and SAM Local + LocalStack Lambdas — plays district emergency coordinator: subscribes to SOS reports over Nostr relays and LAN uploads, groups them by geohash, drafts prioritised dispatch.

The primary success criterion is a working 3-minute demo video showing all four capabilities on real devices with real airplane mode, plus a public GitHub repo and a writeup on AWS Builder Center. The secondary success criterion is a fast-track interview at Amazon's University Talent Acquisition Team.

---

## 2. Problem statement

### 2.1 The failure pattern

Every mainstream UPI and messaging app (GPay, PhonePe, WhatsApp, Signal, Telegram) is a thin client over an IP network. The moment cellular data or Wi-Fi is unavailable — a landslide has flattened a tower, a flood has taken out the fibre backhaul, an all-IP network (Jio) has any signal issue at all — those apps are dead.

Published UPI decline rates (~0.7–0.8%) count only transactions that reached NPCI's switch and were rejected. They do not count the much larger invisible category: transactions that never started because the client could not reach the network. Same shape for messaging: message-failed telemetry counts sent messages that failed delivery, not the messages users never typed because the "no internet" banner was up.

In a genuine disaster, the failure population is *everyone in the affected area*, and it lasts hours to days. During that window:
- Families cannot confirm each other are alive.
- Payments (bus fare out, bottled water, mobile top-up for the one working SIM) fail.
- First aid is administered from memory or from a laminated card, if there is one.
- Coordination with emergency services depends on a single person finding signal to make one call.

### 2.2 What already exists but doesn't work

- **UPI Lite / Lite X**: on paper offline; in practice Lite is debit-only wallet with no merchant flow, Lite X is NFC-only and hasn't shipped at consumer scale.
- **UPI 123Pay**: designed for feature phones — voice IVR menu that nobody with a smartphone will tolerate.
- **Bluetooth Chat apps (Bitchat, Bridgefy, Briar)**: exist, work, but are single-purpose messaging tools. iOS-only (Bitchat) or activist-focused (Briar) or commercial (Bridgefy). None have India-specific payment flows or a disaster-response knowledge assistant.
- **NDMA advisories, IFRC first-aid handbook**: exist, are excellent, are PDFs on a website users cannot reach when they most need them.
- **Emergency-SOS-via-satellite**: OEM-locked (Pixel 9+, Galaxy S25+), no public API, useless as a hackathon build target.

Nobody has put these pieces in one app. That's the gap.

### 2.3 Scope of the problem we're solving

Not: a general-purpose offline chat network. Not: a competitor to WhatsApp. Not: a full disaster-management platform.

**In scope:** the 48 hours after a disaster where cellular/data are down and a person with a smartphone needs to (a) find their family, (b) know what to do about a wound or an evacuation, (c) pay for a resource that helps them survive, (d) get an SOS to someone who can help.

Everything about the product design flows from those four immediate needs.

---

## 3. Goals and non-goals

### 3.1 Goals

**G1.** Demonstrate a working offline BLE mesh chat between three phones on stage in a 3-minute demo video, with a hop count visible in the UI.

**G2.** Demonstrate an on-device LLM answering an emergency question offline, citing a packaged handbook.

**G3.** Demonstrate an offline UPI payment completing end-to-end on a real bank account, with the confirmation SMS parsed and shown.

**G4.** Demonstrate the mesh IOU voucher flow with two offline phones and auto-settlement when one comes online.

**G5.** Demonstrate at least four of the five Build It open-source AWS items in the demo video (Strands agent trace, Cedar policy evaluation logs, OpenSearch RAG query, SAM Local Lambda invocation).

**G6.** Ship a public GitHub repo with clean README, ARCHITECTURE.md, LICENSE (Apache 2.0), and NOTICE.

**G7.** Publish an AWS Builder Center blog post covering the build (eligibility for the top-5-blogs prize).

**G8.** Submit before the Sunday deadline.

### 3.2 Non-goals

**NG1.** Production-grade robustness. Xiaomi/Realme background-execution quirks, iOS parity, low-end phone RAM constraints — out of scope.

**NG2.** True offline cryptocurrency or bearer-instrument money. The IOU voucher is a *promise*, not a bearer note; we don't pretend otherwise.

**NG3.** Any deployed AWS service. Ship It track is explicitly not what we're competing on.

**NG4.** Multi-language UI at launch. English + Hindi for the demo; other Indian languages are future work.

**NG5.** Anti-abuse hardening beyond Cedar rate limits. Real deployment would need spam and flood-attack defenses we cannot land in 4 days.

**NG6.** iOS build.

**NG7.** Play Store distribution. GitHub source + APK artifact only.

---

## 4. Target users and personas

### 4.1 Persona P1 — The Disaster-Affected Resident

**Meera, 34, lives in a Chennai flat that just lost power and cell service.** Her elderly mother is stuck two blocks away. Meera needs to (a) know her mother is OK, (b) find out if she should try to walk to her, (c) pay the auto that offered to take her when she does reach the street.

Success looks like: she opens Sankat Setu, sees her mother's phone as an in-range peer (both are on BLE), sends "u ok?" and gets "yes come" back. She asks the assistant "is it safe to walk in flood water above knee height?" and gets a two-paragraph answer with the citation. She pays the auto ₹120 by dialing 123Pay IVR from inside the app.

### 4.2 Persona P2 — The Concerned Relative

**Karthik, 41, in Bangalore, cannot reach his family in Wayanad after the landslide.** He downloads Sankat Setu and joins the `#wayanad` geohash channel on Nostr (via internet — he has connectivity). He can see SOS reports coming out of the affected area as any phone there touches passing signal.

Success looks like: he sees an SOS report from a phone in his family's neighborhood — not necessarily his family, but confirming people in the area are alive and organized. He can post a "father: Balan Menon, house 47, Pothukallu, missing" reply that will be broadcast into the mesh the next time any Wayanad phone touches connectivity.

### 4.3 Persona P3 — The Field Coordinator

**Priya, 29, district emergency coordinator, has a laptop with Wi-Fi at the district office.** She is running the gateway service. She sees a live map of geohash-tagged SOS reports and IOUs, sorted by priority (injury severity from the LLM classifier, elapsed time, cluster density).

Success looks like: her laptop shows a prioritised dispatch draft the Strands agent generated ("6 injuries reported in geohash `dr5rs7j`, one severe, medical team recommended"). She can click a report to see the raw SOS, the LLM's classification reasoning, and the identity chain (who reported, verified how).

### 4.4 Persona P4 — The Hackathon Judge

**Arkodyuti, AWS Developer Experience Community Manager**, watches the 3-minute demo video from home on a phone screen.

Success looks like: they can tell what the product does in the first 20 seconds, they see all four capabilities working on real devices in the next 130 seconds, they see all four AWS Build It items in the last 30 seconds, they know exactly why India needs this, they leave the video wanting to check the repo.

---

## 5. User stories and journeys

### 5.1 Journey J1 — First-time setup

1. User installs the APK (or the debug build via `adb install`).
2. Splash screen with logo, name, one-sentence tagline.
3. Setup screen 1/4: bank + primary SIM selection (reused from Flowpay's `SetupActivity`).
4. Setup screen 2/4: identity key generation (Ed25519 signing, Curve25519 Noise). No name, no phone number entered. User picks a display nickname.
5. Setup screen 3/4: permission requests (in this order) — Bluetooth, Bluetooth-Advertise, Bluetooth-Scan, Bluetooth-Connect (all Android 12+), Location (BLE-scan-required, explained honestly), Camera (QR), SMS-Receive, Call-Phone, Contacts (optional).
6. Setup screen 4/4: optional QR-code exchange with a local district officer for `#official` channel signing. Skipped in most flows.
7. Home screen appears. First-run tour bubble points at the four tabs: Chat, Assistant, Pay, SOS.

**Acceptance:** first-run completes in under 90 seconds excluding permission dialogs.

### 5.2 Journey J2 — Two-phone offline chat

1. Two users open the app on phones in airplane mode + Bluetooth on.
2. Peer list on the Chat tab shows the other user within 5–10 seconds (BLE advertising + scanning cycle).
3. Tap peer → open thread → type "u ok?" → send.
4. Message appears with a paper-plane icon (sending), transitions to checkmark (delivered) within 2 seconds.
5. Reply arrives, notification fires.

**Acceptance:** end-to-end latency < 3 seconds on one hop, < 8 seconds through one relay.

### 5.3 Journey J3 — Ask the assistant

1. Assistant tab → text input at bottom.
2. User types "how do I treat a bleeding wound with no first-aid kit."
3. Loading state ("thinking…") appears within 500 ms.
4. Answer streams token-by-token, first token < 1 s.
5. Below the answer: source card ("From: IFRC First Aid Handbook, section 4.2") is tappable, opens the source passage in a read-only sheet.
6. Below the source card: two action chips — "Broadcast SOS" and "Nearest shelter".

**Acceptance:** full answer under 8 seconds for a 100-token response on a Pixel 7.

### 5.4 Journey J4 — Offline UPI payment

1. Pay tab → "Pay contact" → enter phone `+91 98…` and amount ₹100.
2. App validates via `Upi123CallStringBuilder` (paise-to-rupees, service number, phone format).
3. "Call your bank to authorise" screen: dial pad ready, overlay explains what will happen.
4. Tap "Dial" → `Intent.ACTION_CALL` fires `tel:08045163666,,1,<phone>,,100,,1`.
5. Call connects, DTMF plays automatically, user enters PIN when prompted by the bank's IVR.
6. Call ends. Waiting-for-verification state, 10-minute deadline countdown.
7. Bank SMS arrives → parser matches paise-exact → status flips to SUCCESS. Notification.

**Acceptance:** full flow in under 90 seconds on a working carrier, correct SUCCESS/FAILED on all 14 supported bank SMS templates.

### 5.5 Journey J5 — Mesh IOU when payments won't work

1. User A tries the payment flow but gets `Timeout: CALL_INITIATION` (no service).
2. App proactively offers "Send as mesh IOU" chip.
3. User A taps it → confirms recipient (already known peer B) and amount.
4. IOU envelope is signed, added to A's local ledger as PENDING, broadcast over mesh.
5. B's phone (offline) receives within 5–15 seconds; entry appears in B's IOU tab.
6. Later, A gets connectivity. A's app detects, fires the deferred UPI transfer to B's UPI ID.
7. Bank SMS confirms. A's IOU flips to SETTLED. Simultaneously, an "IOU settled" mesh envelope goes out to B (or waits in courier storage if B is still out of range).

**Acceptance:** IOU delivery under 15 seconds on the mesh, auto-settle fires within 60 seconds of connectivity return, no double-send.

### 5.6 Journey J6 — Field coordinator dashboard

1. Priya opens the gateway app on her laptop (a plain terminal + web dashboard localhost:8080).
2. Her laptop is running: OpenSearch, LocalStack (DynamoDB + SNS), SAM Local, the Strands agent.
3. The agent is subscribed to 5 Nostr relays with `REQ` filter `{"kinds":[1], "#t":["sos"], "#geohash":["dr5rs"]}` (Chennai example).
4. As a new SOS event arrives, the agent traces it, calls `query_opensearch(text)` to find the matching response protocol, calls `cluster_by_geohash` to bucket with related reports, calls `score_priority`, produces a dispatch draft.
5. The dashboard's incident table gets a new row within 2 seconds of the Nostr publish.

**Acceptance:** end-to-end SOS-to-dashboard latency under 10 seconds once connectivity is present.

---

## 6. Feature specification

### F1 — BLE mesh chat

**Priority:** P0. Must ship or the product is nothing.

#### F1.1 Sourcing

Take Bitchat's Swift source in `permissionlesstech/bitchat` and port it to Kotlin file by file. Priority modules to lift:
- `BluetoothMeshService.swift` → `MeshTransport.kt` (advertising + scanning + GATT server + GATT client)
- `NoiseSession.swift` → `NoiseSession.kt` (wrap `com.rfksystems:noise-java` for XX and X patterns)
- `BinaryProtocol.swift` → `BinaryProtocol.kt` (compact header packing)
- `MessageRouter.swift` → `MessageRouter.kt` (transport-agnostic dispatcher)
- `StoreForwardService.swift` → `StoreForward.kt` (outbox + courier envelopes)
- `GCSFilter.swift` → `GcsFilter.kt` (Golomb-Coded-Set filter for public gossip sync)

Do not attempt clean-room. Translate line-by-line, use the same variable names where possible so the whitepaper's parameters are traceable.

Also usable: Briar's `bramble-android` module for Android-specific BLE quirks (permission handling, background service pattern, foreground-service notification). Copy the service scaffolding wholesale.

#### F1.2 Protocol parameters (from Bitchat whitepaper v2.0)

| Parameter | Value | Notes |
|---|---|---|
| BLE service UUID | `F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3B4A5F` | Ours, distinct from Bitchat's, so we don't collide with real Bitchat users |
| Advertising interval | 100 ms (fast) → 1000 ms (slow after 30 s isolated) | Adaptive per whitepaper §4.5 |
| Scan window / interval | 200 ms / 2000 ms (idle), 400 ms / 500 ms (active) | |
| MTU request | 517 bytes (Android max) | Fragmentation at ~469 bytes payload |
| Fragment size | 469 bytes | 8-byte fragment ID, index/total header |
| Max concurrent assemblies | 128 | 30 s timeout, 1 MiB reassembly cap |
| Packet TTL default | 7 | Clamped to 5 in dense (≥6 links) graphs, full depth on thin chains |
| LRU seen-set | 1000 entries, 5-min expiry | Key: (sender_id, timestamp, type, sha256(payload)[0:8]) |
| Relay jitter | 10–220 ms uniform, wider window when dense | |
| Fanout subset size | ⌈log₂(degree)⌉ | Message-ID-seeded selection; ingress link excluded |
| Announce interval | 4 s isolated, 15–30 s jittered when connected | Signed Ed25519 |
| Peer reachability window | 60 s after last verified announce | |
| Noise pattern (live) | XX (Curve25519, ChaCha20-Poly1305, SHA-256) | Mutual auth + forward secrecy |
| Noise pattern (sealed mail) | X | No forward secrecy, acknowledged |
| Packet padding | 256/512/1024/2048-byte buckets | PKCS#7-style, Noise-encrypted frames only |
| Sender outbox | 100 messages/peer, 24 h TTL, 8 attempts | Persisted sealed |
| Courier envelope | 16 KiB cap, 24 h TTL, 3 couriers, budget 4 init, 8 cap | Half-split on courier-meets-courier |
| Recipient tag | HMAC-SHA256(recipient_static_key, UTC_day)[0:16] | Opaque, rotates daily |
| Gossip cache | 1000 packets, 6 h retention | GCS filter reconcile every ~15 s |

#### F1.3 Message types

| Type ID | Name | Payload |
|---|---|---|
| 0x01 | `announce` | signed(pubkey, nickname, direct_neighbors[≤10]) |
| 0x02 | `noiseHandshake` | Noise XX handshake bytes |
| 0x03 | `noiseEncrypted` | opaque ciphertext |
| 0x04 | `publicBroadcast` | signed(channel, body) |
| 0x05 | `courierEnvelope` | recipient_tag + Noise-X-sealed(inner) |
| 0x06 | `gcsSyncOffer` | GCS filter of held IDs |
| 0x07 | `gcsSyncRequest` | list of missing IDs |
| 0x08 | `gcsSyncDelivery` | list of packets |
| 0x09 | `fragment` | fragment_id + index/total + bytes |
| 0x0A | `iouEnvelope` | signed(iou payload — see F4) |
| 0x0B | `sosBroadcast` | signed(structured SOS — see F1.4) |
| 0x0C | `iouSettlementAck` | signed(iou_id, upi_txn_ref) |

#### F1.4 Structured SOS payload

```
{
  version: 1,
  reporter_pubkey: <32 bytes>,
  reported_at:     <UTC ms>,
  geohash:         <7-char string, ~150m precision>,
  injury_type:     <enum: bleeding | fracture | unconscious | drowning | trapped | none>,
  severity:        <enum: minor | serious | severe | fatal | unknown>,
  victim_count:    <uint8>,
  note:            <UTF-8, max 200 chars>,
  needs:           <bitmask: medical=1, food=2, water=4, evac=8, shelter=16>,
  signature:       <Ed25519 sig over above>
}
```

#### F1.5 UI surface

- **Chat tab** — peer list (nickname + hop-count badge), unread counter per thread, tap opens conversation view (WhatsApp-like bubble UI).
- **Global room** — `#public` broadcast channel, unread badge, timeline.
- **SOS button** — persistent bottom-right FAB on every tab, opens SOS composer.

#### F1.6 Acceptance criteria

- Two devices in airplane mode + Bluetooth on discover each other in ≤ 10 s.
- One-hop message delivery ≤ 3 s.
- Two-hop (with a third phone as pure relay) delivery ≤ 8 s.
- Killing the relay phone in the middle of a two-hop send: sender's outbox retries, delivery succeeds on relay return within 60 s.
- Panic-wipe (triple-tap the logo) clears identity keys, outbox, courier storage, chat history within 500 ms.

---

### F2 — On-device LLM emergency assistant

**Priority:** P0.

#### F2.1 Sourcing

- **MediaPipe LLM Inference Android sample** in `google-ai-edge/mediapipe-samples/examples/llm_inference/android`. Lift the whole example project as the Assistant module's starting point.
- **Gemma 3 1B int4 task file** from Kaggle (`gemma-3-1b-it-int4.task`, ~529 MB). Side-load onto demo phones via `adb push /sdcard/Android/data/com.sankatsetu.app/files/models/`.
- **MiniLM ONNX embedding model**: `all-MiniLM-L6-v2` ONNX-int8 (~22 MB), from Sentence-Transformers' ONNX exports.
- **Corpus**: NDMA guidelines PDFs (public), IFRC first-aid handbook (CC-BY), WHO psychological first aid (CC-BY-NC), Sphere handbook (open-access), IMD warning taxonomy. Total raw ~120 MB → after chunking and embedding, on-device SQLite index ~20 MB.

#### F2.2 Runtime architecture

```
User query
  → embed with MiniLM ONNX (mean-pool token embeddings, L2-normalize)
  → SQLite ANN lookup (top 3 chunks via IVF-PQ index if we get to it, else brute-force cosine on <5000 chunks)
  → assemble prompt: [system, retrieved chunks, user query, format instruction]
  → MediaPipe LlmInference.generateResponseStream() 
  → stream tokens to UI, save answer + sources to conversation log
```

#### F2.3 Prompt template

```
System: You are a first-response assistant in rural India. Answer only from the CONTEXT below. If the context does not contain the answer, say "I don't have specific guidance for this — call 112 immediately." Keep answers to 3 short paragraphs max. Always end with the emergency phone number 112.

CONTEXT:
[1] {chunk_1.text}
     Source: {chunk_1.source}, section {chunk_1.section}
[2] {chunk_2.text}
     Source: {chunk_2.source}, section {chunk_2.section}
[3] {chunk_3.text}
     Source: {chunk_3.source}, section {chunk_3.section}

USER: {user_query}

ASSISTANT:
```

#### F2.4 Tools the agent can invoke

The Kotlin agent loop implements Strands-style tool calling. When the model emits a specific structured directive, the loop parses and dispatches.

| Tool | Signature | Effect |
|---|---|---|
| `search_kb(query: string)` | | Additional RAG lookup mid-conversation |
| `broadcast_sos(injury: enum, count: int, geohash: string)` | | Composes and enqueues SOS mesh envelope |
| `list_shelters(geohash: string)` | | Returns nearest 3 shelters from packaged geojson |
| `compose_iou(to_upi: string, amount_paise: int)` | | Opens a pre-filled IOU envelope for user confirmation |

Tool call syntax (embedded in model output):

```
<tool name="broadcast_sos" injury="bleeding" count="1" geohash="dr5rs7j"/>
```

The loop intercepts, executes, feeds the tool result back as a synthetic turn, and continues.

#### F2.5 Model performance targets

| Device | Model | First-token latency | Full 100-token answer |
|---|---|---|---|
| Pixel 7 | Gemma 3 1B int4 | < 1 s | < 8 s |
| Pixel 6a | Gemma 3 1B int4 | < 1.5 s | < 12 s |
| Snapdragon 8 Gen 2+ | Gemma 2 2B int4 (upgrade) | < 1.2 s | < 10 s |
| RAM < 6 GB | falls back to a lookup-only "read from source" mode with no generation | | |

#### F2.6 Acceptance criteria

- Given the query "how do I treat a snake bite," answer includes: keep victim still, wash wound with water, do not tourniquet, get to hospital, call 112.
- Given "the river is rising and I have children," answer includes: move to higher ground, avoid flowing water above knee height, keep documents in sealed bag.
- Given a nonsense query ("purple pineapple emergency"), model refuses and outputs the fallback line.
- Source cards render correctly and open the source passage.

---

### F3 — Offline UPI payments

**Priority:** P1.

#### F3.1 Sourcing

Import Flowpay's entire `app/src/main/java/com/flowpay/app/payment/` module as a git submodule at `libraries/flowpay-payment/`. Reused in full:

- `PaymentSessionManager.kt`
- `Upi123CallStringBuilder.kt`
- `sms/SmsTransactionParser.kt`
- `PaymentWindowObserver.kt`
- `InvalidReasonMessages.kt`

Also lift:
- `receivers/SimpleSMSReceiver.kt`
- `receivers/SmsIngestionPipeline.kt`
- `helpers/TransactionDetector.kt`
- `managers/CallManager.kt`
- `states/PaymentState.kt`
- `data/Transaction.kt` and DAOs (extend, don't replace)
- `services/CallOverlayService.kt`

Attribution in NOTICE.md. No functional changes to the payment logic itself.

#### F3.2 Integration surface

New wrapper `PaymentService.kt` in our code, dependency-injected via our existing `AppContainer` (Flowpay pattern):

```kotlin
class PaymentService(
    private val session: PaymentSessionManager,
    private val callManager: CallManager,
    private val iouLedger: IouLedger
) {
    fun payViaUpi123(phone: String, amount: BigDecimal): Flow<PaymentState>
    fun payViaUssd(): Intent  // launches *99*1*3# for QR entry
    fun payViaIou(recipientPubkey: ByteArray, amount: BigDecimal)
    fun history(): Flow<List<Transaction>>
    fun ious(): Flow<List<IouVoucher>>
}
```

#### F3.3 UI additions on top of Flowpay's screens

- Home tab renamed "Pay," carries three primary buttons: Scan QR (USSD), Pay Contact (123Pay), Send Mesh IOU.
- Payment history screen filters by type: All, UPI, IOUs.
- Transaction detail screen shows the same fields as Flowpay + `iou_id` when the transaction settled an IOU.

#### F3.4 Acceptance criteria

Exactly Flowpay's: bank SMS parsing paise-exact across the 14 supported banks, 10-minute deadline enforced, silent discard on timeout, no false SUCCESS from call state.

---

### F4 — Mesh IOU voucher

**Priority:** P1. Our headline novel feature.

#### F4.1 Envelope schema

```
IouEnvelope {
  version:        u8 = 1
  iou_id:         u128 (random)
  from_pubkey:    [u8; 32]           // sender's Ed25519 identity
  to_pubkey:      [u8; 32]           // receiver's Ed25519 identity
  from_upi_hash:  [u8; 32]           // SHA-256(sender's UPI ID + salt), privacy-safe
  from_upi_hint:  string (≤ 32 chars, e.g. "9876****@ybl")  // for receiver display only
  amount_paise:   u64
  currency:       string = "INR"
  memo:           string (≤ 200 chars)
  created_at:     u64 (UTC ms)
  expires_at:     u64 (created_at + 72*3600*1000)
  chain_id:       string = "flowpay-upi-inr"
  nonce:          [u8; 16] (random)
  signature:      [u8; 64]           // Ed25519 sig over serialized above
}
```

#### F4.2 State machine on both devices

```
Sender (A)                                      Receiver (B)
──────────                                      ────────────
CREATED  ─(sign + emit to mesh)→
                                                RECEIVED  ─(verify sig, verify not expired,
                                                            check dedup by iou_id)→
                                                PENDING   ─(store in ledger)
PENDING  ─(wait for connectivity, retry via
            courier if peer went out of range)
   │
   ↓ connectivity returns on A's device
   │
SETTLING ─(fire UPI 123Pay call or standard
            UPI transfer to from_upi_hash's
            plaintext record, using iou_id
            as reference)→
   │
   ↓ Flowpay's PaymentSessionManager runs
   ↓ bank SMS confirms
   │
SETTLED  ─(emit iouSettlementAck to mesh with
            upi_txn_ref)→
                                                SETTLED
```

Alternate paths:
- **Sender fails to settle** (insufficient balance, wrong UPI ID, bank rejects): state → `SETTLEMENT_FAILED`. Receiver sees the same after the ack is broadcast. Receiver retains signed envelope as dispute evidence.
- **Sender never comes online before 72-hour expiry**: state → `EXPIRED` on both sides. Envelope becomes a dispute-only record.
- **Receiver rejects the IOU** (fake identity, blocked contact): the envelope is stored locally but flagged `REJECTED`; a `iouRejected` mesh message is emitted so sender can see.
- **Double-spend attempt** (sender signs two IOUs for the same nonce): receivers detect duplicate `iou_id`, drop the second, retain the first. If both go to different receivers, both are valid promises but the sender's bank balance is the eventual constraint.

#### F4.3 UI

- Pay tab → third card "Send Mesh IOU" → composer with recipient (from peer list) + amount + memo.
- IOU tab → three sections: Owed to you, You owe, Settled.
- Each IOU card shows: counterparty nickname + short pubkey fingerprint, amount, memo, timer to expiry, status pill.

#### F4.4 Acceptance criteria

- Sign-and-send: < 500 ms.
- Delivery over one BLE hop: < 5 s.
- Receiver's UI updates within 1 s of receipt.
- Auto-settle fires within 60 s of sender's connectivity.
- Reject flow works (delivers `iouRejected` back).
- Manual "mark as settled" fallback exists on the receiver if the sender's ack was lost (with an "I trust this counterparty" confirmation).

---

### F5 — Cedar-authorized channels

**Priority:** P2.

#### F5.1 Sourcing

- `cedar-policy/cedar-java` from Maven Central: `com.cedarpolicy:cedar-java:4.2.3:uber`.
- `cedar-policy/cedar` Rust crate — if the uber-jar's bundled `.so` doesn't include Android ABIs, cross-compile `cedar-java-ffi` locally with `cargo-ndk`:

```bash
cargo ndk -t arm64-v8a -t armeabi-v7a build --release
```

Bundle the resulting `libcedar_java_ffi.so` per-ABI in `app/src/main/jniLibs/`.

Fallback path if cross-compile fights us: run Cedar in-process on the gateway laptop, phone calls it via a LAN endpoint for the demo. Weaker but shipable.

#### F5.2 Policy set (`assets/policies.cedar`)

```cedar
// Anyone can read anything.
permit(
  principal,
  action == Action::"read",
  resource
);

// Anyone can post to the general chat.
permit(
  principal,
  action == Action::"write",
  resource == Channel::"#chat"
);

// Anyone can raise an SOS.
permit(
  principal,
  action == Action::"write",
  resource == Channel::"#sos"
);

// Only district-officer-signed principals can write to #official.
permit(
  principal,
  action == Action::"write",
  resource == Channel::"#official"
)
when { principal has officer_signature && principal.officer_signature.valid == true };

// Rate limit on #chat: 30 messages per minute.
forbid(
  principal,
  action == Action::"write",
  resource == Channel::"#chat"
)
when { context.messages_last_minute > 30 };

// Rate limit on #sos: 5 per minute (SOS is not a chat channel).
forbid(
  principal,
  action == Action::"write",
  resource == Channel::"#sos"
)
when { context.messages_last_minute > 5 };

// Blocked principals cannot write at all.
forbid(
  principal,
  action == Action::"write",
  resource
)
when { principal in Group::"blocked" };
```

#### F5.3 Entity schema (`assets/schema.cedarschema`)

```
namespace SankatSetu {
    entity User {
        officer_signature?: OfficerSig,
    };
    entity OfficerSig {
        valid: Bool,
        issuer_pubkey: String,
    };
    entity Channel;
    entity Group;
    
    action read appliesTo {
        principal: [User],
        resource: [Channel]
    };
    action write appliesTo {
        principal: [User],
        resource: [Channel],
        context: {
            messages_last_minute: Long
        }
    };
}
```

#### F5.4 Integration point

Every inbound mesh message runs through `CedarAuthorizer.check(sender_pubkey, "write", channel)` before hitting the message store. Failures are logged (with reason) and the message is dropped. Rate-limit failures produce a "you are being rate limited" mesh reply to the sender.

#### F5.5 Officer key exchange

Setup screen 4/4 (§5.1): user scans a QR code from a district officer's phone containing `{issuer_pubkey, signed_user_pubkey, valid_until}`. Stored as an entity attribute on the local User entity.

#### F5.6 Acceptance criteria

- A message from a non-officer to `#official` is dropped, and a debug log shows Cedar denied it with the exact policy that matched.
- A message from an officer-signed user to `#official` goes through.
- 40 messages in 60 seconds to `#chat` from the same principal → last 10 dropped with rate-limit reason.

---

### F6 — Nostr "connectivity returns" bridge

**Priority:** P2.

#### F6.1 Sourcing

Use `rust-nostr/nostr-sdk` via its Kotlin bindings (`org.rust-nostr:nostr-sdk`), or write a minimal Kotlin implementation using OkHttp WebSocket + `secp256k1-kmp`. The Kotlin binding path is faster; use it.

#### F6.2 Preselected public relays

```
wss://relay.damus.io
wss://nos.lol
wss://relay.nostr.band
wss://nostr.wine
wss://relay.snort.social
```

Configurable in Settings; user can add/remove.

#### F6.3 Event kinds we use

| Kind | Purpose | Body |
|---|---|---|
| 1 | Public SOS report | Serialized SOS payload as JSON |
| 1059 | Private IOU broadcast (gift-wrap) | Encrypted inner IOU envelope |
| 30078 (parameterized replaceable) | Coordinator's dispatch draft | JSON draft |

Tags on kind 1 SOS events:
- `["t", "sos"]`
- `["t", "geohash-dr5rs"]` (5-char precision, city level)
- `["t", "geohash-dr5rs7j"]` (7-char precision, block level)
- `["client", "sankat-setu"]`
- `["v", "1"]`

#### F6.4 Sync logic

On every connectivity change to `HAS_INTERNET`:
1. Flush the "pending Nostr publish" queue: every SOS + IOU envelope generated while offline goes out as a signed Nostr event.
2. Subscribe to a `REQ` for the last 24 h of `#sos` events matching the user's current geohash (5-char precision).
3. Ingest any events not already in local storage (dedup by event id).
4. When connectivity is lost again, close subscriptions gracefully.

#### F6.5 Acceptance criteria

- With airplane mode → data on, a queued SOS publishes to ≥ 3 of the 5 relays within 5 s.
- A test event published from the laptop is received by a phone subscribing to the same geohash within 5 s.

---

### F7 — Gateway coordinator (laptop-side)

**Priority:** P2. Non-blocking for phone demo; enhances the video.

#### F7.1 Composition

```
gateway/
├── docker-compose.yml
│   ├── opensearch:latest (single node)
│   └── localstack:3.x (community edition)
├── infra/
│   └── template.yaml  (SAM: IngestSosLan, IngestIou, NotifyOfficials)
├── agent/
│   ├── main.py         (Strands Agent entry point)
│   ├── tools.py        (Strands tools)
│   └── prompts.py
├── dashboard/
│   └── index.html      (localhost:8080, plain HTML + fetch)
└── seed/
    ├── corpus_index.py  (build OpenSearch index at setup)
    └── corpus/          (NDMA/WHO/Sphere/IFRC docs)
```

#### F7.2 Strands agent tools

```python
@tool
def query_opensearch(text: str) -> list[dict]:
    """Return top-3 disaster-response passages relevant to text."""

@tool
def cluster_by_geohash(reports: list[dict], precision: int = 6) -> dict:
    """Group reports by geohash prefix, return {geohash: [reports]}."""

@tool
def score_priority(cluster: dict) -> int:
    """Return priority score 0-100 based on severity, count, time."""

@tool
def draft_dispatch(cluster: dict, protocol: str) -> str:
    """Produce a 4-line dispatch summary for a human operator."""

@tool
def notify_officials(dispatch: str, channel: str = "district") -> bool:
    """Publish to SNS Local topic 'district_ops'."""
```

Agent loop reads from two input queues in parallel:
1. `nostr_stream` — subscribed to relays, filters kind 1 with `t=sos`.
2. `lan_stream` — polls DynamoDB Local table `incident_ingest`, populated by the `IngestSosLan` Lambda.

#### F7.3 SAM template highlights

```yaml
Resources:
  IngestSosLan:
    Type: AWS::Serverless::Function
    Properties:
      Runtime: python3.11
      CodeUri: functions/ingest_sos/
      Events:
        Api: {Type: Api, Properties: {Path: /sos, Method: post}}
      Environment:
        Variables:
          TABLE_NAME: incident_ingest
  IngestIou:
    Type: AWS::Serverless::Function
    ...
  NotifyOfficials:
    Type: AWS::Serverless::Function
    Properties:
      Events:
        SNS: {Type: SNS, Properties: {Topic: !Ref DistrictOpsTopic}}
```

`sam local start-api --docker-network localstack` runs it.

#### F7.4 Dashboard

Plain HTML page polling `http://localhost:4566/…/incident_ingest` (LocalStack DynamoDB endpoint). Table columns: time, geohash, injury_type, severity, victim_count, priority_score, dispatch_draft. Auto-refresh every 3 seconds. This is a hackathon demo, not a product.

#### F7.5 Acceptance criteria

- One SOS published from a phone via Nostr appears on the dashboard in ≤ 10 s with a non-empty dispatch draft.
- Killing OpenSearch and restarting: agent gracefully retries queries.
- Agent trace lines are visible in the terminal during the video shoot.

---

## 7. Non-functional requirements

### 7.1 Performance

| Metric | Target |
|---|---|
| Cold app start | ≤ 2 s on Pixel 6a |
| BLE peer discovery | ≤ 10 s from cold |
| One-hop mesh message | ≤ 3 s end-to-end |
| Multi-hop (2 hops) | ≤ 8 s end-to-end |
| LLM first token | ≤ 1 s on Pixel 7, ≤ 1.5 s on Pixel 6a |
| LLM full 100-token answer | ≤ 8 s on Pixel 7 |
| IOU sign + emit | ≤ 500 ms |
| UPI 123Pay dial | matches Flowpay's baseline (no regression) |
| Cedar authorization check | ≤ 5 ms per message |

### 7.2 Battery

Adaptive scanning per Bitchat's whitepaper: 200 ms scan / 2 s interval when idle, active state boosts to 400 ms / 500 ms. Expected battery drain in idle standby: ~3–5% per hour, matching Bitchat's measured baseline. Not measured to production standard; noted in writeup.

### 7.3 Storage

- App binary (before models): ≤ 40 MB
- On-device knowledge index (SQLite + MiniLM ONNX): ≤ 25 MB
- Gemma 3 1B int4 `.task` file: ~529 MB, side-loaded not bundled
- Chat/IOU/transaction DB: bounded to 100 MB, oldest evicted after 30 days
- Panic wipe clears everything above

### 7.4 Permissions

Minimum required, requested in this order:

1. `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (Android 12+)
2. `ACCESS_FINE_LOCATION` (required for BLE scan on all Android versions; explained honestly in the rationale dialog)
3. `POST_NOTIFICATIONS` (Android 13+)
4. `CAMERA` (QR code scanning for setup + officer key exchange)
5. `RECEIVE_SMS` (Flowpay: never `READ_SMS`)
6. `CALL_PHONE`, `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS` (Flowpay: dial USSD/IVR and end call via overlay)
7. `READ_CONTACTS` (optional; for name lookup on the payment screen)
8. `SYSTEM_ALERT_WINDOW` (Flowpay's call overlay)

Explicitly not requested: `INTERNET` (see next), `READ_SMS`, location for anything other than BLE scan, storage.

### 7.5 Internet permission decision

Sankat Setu is a *hybrid* app: BLE + on-device LLM + UPI dialing all work with zero internet, but the Nostr bridge needs an `INTERNET` permission when connectivity is available.

**Decision:** we declare `INTERNET`. Reasoning: Nostr is a genuine "connectivity returns" feature that gives real value, and declaring the permission is honest. Flowpay's argument for zero-INTERNET does not apply here since we do talk to Nostr relays. The writeup makes the tradeoff explicit.

### 7.6 Accessibility

- All primary actions reachable via TalkBack.
- Minimum touch target 48dp.
- Font scale honored up to 200%.
- High-contrast theme in both light and dark modes.
- SOS button labeled clearly, no icons-only.

### 7.7 Localization

English + Hindi at launch. All user-visible strings via `strings.xml`; no inline `Text("…")` (Flowpay CI gate reused). Other languages are post-hackathon.

### 7.8 Privacy

- No accounts, no phone numbers stored.
- Identity keys generated at first launch, held in Android Keystore.
- Ed25519 signature keys never leave the device.
- Raw bank SMS bodies never persisted (Flowpay rule).
- No analytics, no telemetry, no third-party SDKs that talk to the internet other than Nostr relays.
- Chat history stored SQLCipher-encrypted; passphrase wrapped by Keystore.
- Panic wipe (triple-tap the logo) clears everything within 500 ms.

---

## 8. System architecture

```
┌────────────────────────── App Process ──────────────────────────┐
│                                                                 │
│  Compose UI Layer                                               │
│  ├─ ChatScreen  ├─ AssistantScreen  ├─ PayScreen                │
│  ├─ IouScreen   ├─ SosComposer      ├─ SettingsScreen           │
│                                                                 │
│  ViewModels (Hilt-injected)                                     │
│  ├─ ChatViewModel   ├─ AssistantViewModel                       │
│  ├─ PayViewModel    ├─ IouViewModel                             │
│                                                                 │
│  Domain Services                                                │
│  ├─ MessageRouter   ├─ AgentLoop     ├─ IouLedger               │
│  ├─ PaymentService  ├─ CedarAuthz                               │
│                                                                 │
│  Transport Layer                                                │
│  ├─ MeshTransport (BLE, Noise XX/X)                             │
│  ├─ NostrTransport (WebSocket)                                  │
│  └─ UpiTransport (dialer + SMS listener)                        │
│                                                                 │
│  Persistence                                                    │
│  └─ Room + SQLCipher                                            │
│      Entities: Peer, Message, CourierEnvelope, PublicHistory,   │
│      Transaction, IouVoucher, KnowledgeChunk                    │
│                                                                 │
│  Native Bridges                                                 │
│  ├─ Cedar FFI (JNI to Rust)                                     │
│  ├─ MediaPipe LLM Inference (JNI to C++)                        │
│  └─ ONNX Runtime Mobile (JNI to C++)                            │
└─────────────────────────────────────────────────────────────────┘
                     ↕
            Android Foreground Service
            (persistent notification, prevents OEM kill)
                     ↕
       BLE hardware  |  Cell dialer  |  Wi-Fi/data (when up)
```

Composition root: `SankatSetuApplication.kt` owns a single `AppContainer` (Flowpay pattern — hand-rolled DI, no framework, one graph to read by eye). All singletons instantiated here.

Foreground service `MeshForegroundService` keeps the BLE stack alive with a persistent notification: "Sankat Setu is on — 3 peers nearby." Required to survive OEM background-execution kills.

---

## 9. Data model (Room + SQLCipher)

### 9.1 Entities

```kotlin
@Entity(tableName = "peers", indices = [Index("last_seen"), Index("pubkey", unique = true)])
data class Peer(
    @PrimaryKey val pubkey: ByteArray,           // Ed25519 identity, 32 bytes
    val noise_static: ByteArray,                 // Curve25519, 32 bytes
    val nickname: String,
    val first_seen: Long,
    val last_seen: Long,
    val is_favorite: Boolean = false,
    val officer_signature: ByteArray? = null,    // if signed by district officer
    val nostr_pubkey: ByteArray? = null          // for online DM fallback
)

@Entity(tableName = "messages",
        indices = [Index("channel"), Index("sent_at"), Index("thread_pubkey"), Index("message_id", unique = true)])
data class Message(
    @PrimaryKey val message_id: ByteArray,       // 16 bytes
    val thread_pubkey: ByteArray?,               // null for public/channel messages
    val channel: String?,                         // #chat / #sos / #official / null
    val sender_pubkey: ByteArray,
    val body: String,
    val body_kind: String = "text",              // text | sos | iou | system
    val sent_at: Long,
    val received_at: Long,
    val ttl_at_receipt: Int,
    val hop_count: Int,                          // 7 - ttl
    val status: String                            // sending | sent | delivered | read | failed
)

@Entity(tableName = "courier_envelopes",
        indices = [Index("recipient_tag"), Index("expires_at")])
data class CourierEnvelope(
    @PrimaryKey val envelope_id: ByteArray,
    val recipient_tag: ByteArray,                // 16-byte HMAC-day tag
    val sealed_content: ByteArray,
    val deposit_by: ByteArray,                    // sender pubkey
    val deposited_at: Long,
    val expires_at: Long,
    val copy_budget: Int,
    val tier: String                              // favorite | verified
)

@Entity(tableName = "public_history",
        indices = [Index("channel"), Index("received_at")])
data class PublicHistory(
    @PrimaryKey val packet_id: ByteArray,
    val channel: String,
    val raw_packet: ByteArray,
    val received_at: Long,
    val expires_at: Long                          // received_at + 6h
)

// Flowpay Transaction entity reused as-is, adding one column:
@Entity(tableName = "transactions", ...)
data class Transaction(
    ...(Flowpay fields as-is)...,
    val settles_iou_id: ByteArray? = null        // NEW: links to IouVoucher.iou_id
)

@Entity(tableName = "iou_vouchers",
        indices = [Index("counterparty_pubkey"), Index("status"), Index("expires_at")])
data class IouVoucher(
    @PrimaryKey val iou_id: ByteArray,           // 16 bytes
    val direction: String,                        // OWED_TO_ME | I_OWE
    val counterparty_pubkey: ByteArray,
    val from_upi_hash: ByteArray,
    val from_upi_hint: String,
    val amount_paise: Long,
    val currency: String = "INR",
    val memo: String,
    val created_at: Long,
    val expires_at: Long,
    val nonce: ByteArray,
    val signature: ByteArray,
    val status: String,                           // PENDING | SETTLING | SETTLED | SETTLEMENT_FAILED | REJECTED | EXPIRED
    val settlement_upi_ref: String? = null,
    val settled_at: Long? = null
)

@Entity(tableName = "knowledge_chunks",
        indices = [Index("source"), Index("section")])
data class KnowledgeChunk(
    @PrimaryKey val chunk_id: String,
    val source: String,
    val section: String,
    val text: String,
    val embedding: ByteArray                     // 384-float MiniLM vector, quantized to int8
)
```

### 9.2 DAO essentials

Standard Room DAOs. One notable pattern from Flowpay: **all writes to `Message.status` go through `MessageRepository` which is the sole writer**, mirroring Flowpay's PaymentSessionManager pattern (single writer prevents race conditions between mesh delivery ack and user-side cancel).

---

## 10. Wire protocol contracts

### 10.1 Mesh packet binary layout (bytes)

```
Offset  Size  Field
 0      1     version (=2)
 1      1     type (see F1.3)
 2      1     ttl
 3      8     timestamp_ms (u64 big-endian)
11      1     flags (bit0=signed, bit1=fragmented, bit2=padded)
12      8     sender_id
20      8     recipient_id (0 for broadcast)
28      2     payload_length (u16)
30      N     payload
30+N    64    signature (if bit0 set)
```

Total minimum: 30 bytes. Max unpadded: MTU_effective (~185 bytes on BLE 4.2 default, higher with negotiated MTU).

### 10.2 Fragment header (per fragment)

```
Offset  Size  Field
 0      8     fragment_id
 8      2     index (u16)
10      2     total (u16)
12      2     fragment_payload_length (u16)
14      N     fragment payload
```

### 10.3 Nostr event we publish

```json
{
  "id": "...",
  "pubkey": "...",
  "created_at": 1758150000,
  "kind": 1,
  "tags": [
    ["t", "sos"],
    ["t", "geohash-dr5rs"],
    ["t", "geohash-dr5rs7j"],
    ["client", "sankat-setu"],
    ["v", "1"]
  ],
  "content": "{\"injury_type\":\"bleeding\",\"severity\":\"serious\",\"victim_count\":2,\"note\":\"trapped in house...\"}",
  "sig": "..."
}
```

### 10.4 SAM Local `POST /sos` request

Used when a phone reaches Wi-Fi and prefers a direct LAN upload over Nostr:

```
POST /sos HTTP/1.1
Content-Type: application/json

{
  "sos_envelope": "<base64 of the mesh sosBroadcast payload>",
  "geohash": "dr5rs7j",
  "received_at": 1758150000000
}
```

Response: `{"incident_id": "...", "priority_score": 87}`.

---

## 11. UI screens

### 11.1 Screen inventory

| Screen | Purpose | Reused from Flowpay? |
|---|---|---|
| Splash | Logo + tagline for 800 ms | No |
| Setup 1/4 – Bank + SIM | Standard Flowpay setup | Yes, unchanged |
| Setup 2/4 – Identity | Generate keys, pick nickname | New |
| Setup 3/4 – Permissions | Group all permission requests | New |
| Setup 4/4 – Officer QR (skippable) | Optional officer key exchange | New |
| TestConfiguration | Flowpay's connectivity smoke test | Yes, unchanged |
| Home / Tab bar (bottom) | 4 tabs + persistent SOS FAB | New |
| ChatTab | Peer list + `#public` room | New |
| ChatDetail | Thread view with peer | New |
| AssistantTab | Chat-style UI for LLM Q&A + tool cards | New |
| PayTab | Scan QR / Pay Contact / Send IOU (three cards) | Extends Flowpay's MainActivity |
| PayContactSheet | Phone + amount entry | Yes, adapted |
| PayResultScreen | Success/Failed detail | Yes (Flowpay `PaymentResultActivity`) |
| IouTab | Three sections: Owed to you / You owe / Settled | New |
| IouDetail | Full envelope, actions | New |
| SosComposer | Injury/severity/count picker, geohash preview | New |
| SosBroadcastConfirm | Confirm the SOS with a "3 second undo" | New |
| Settings | Officer key management, panic wipe, Nostr relays | Extends Flowpay's SettingsActivity |
| TransactionHistory | Reused Flowpay screen, filter tabs added | Yes, extended |

### 11.2 Key screen wireframes (text)

**Home / Chat tab**
```
┌────────────────────────────────┐
│ Sankat Setu               ⚙   │
├────────────────────────────────┤
│ #public       12 unread     >  │
├────────────────────────────────┤
│ Nearby peers (3)               │
│ ┌────────────────────────────┐ │
│ │ Meera • 1 hop • ●          │ │
│ │ "coming, be there in 10"   │ │
│ ├────────────────────────────┤ │
│ │ Rakesh • 2 hops • ●        │ │
│ │ "u ok?"                    │ │
│ ├────────────────────────────┤ │
│ │ Priya • 1 hop • official ★ │ │
│ │ "evacuation to Yelagiri"   │ │
│ └────────────────────────────┘ │
│                                │
│ Recent broadcasts              │
│ [#sos] flood alert, 12 min ago │
├────────────────────────────────┤
│  💬 Chat  🤖  💰  📢 SOS       │
└────────────────────────────────┘
                         ⚠ SOS
```

**Assistant tab**
```
┌────────────────────────────────┐
│ ← Assistant                    │
├────────────────────────────────┤
│  You                           │
│  ┌────────────────────────┐    │
│  │ river is rising fast,  │    │
│  │ safe to walk to next   │    │
│  │ village 2 km?          │    │
│  └────────────────────────┘    │
│                                │
│  Assistant                     │
│  ┌────────────────────────┐    │
│  │ Do not walk through    │    │
│  │ moving water above     │    │
│  │ knee height. If depth  │    │
│  │ is unknown, wait…      │    │
│  │                        │    │
│  │ Call 112 immediately.  │    │
│  └────────────────────────┘    │
│  📖 IFRC First Aid §7.3        │
│  [ Broadcast SOS ] [ Shelters ]│
├────────────────────────────────┤
│ Ask something…             ▶   │
└────────────────────────────────┘
```

**Pay tab**
```
┌────────────────────────────────┐
│ ← Pay                     🕐  │
├────────────────────────────────┤
│ ┌────────────────────────────┐ │
│ │ 📷 Scan QR                 │ │
│ │ Dial *99*1*3# to pay a QR  │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │
│ │ 📞 Pay Contact             │ │
│ │ UPI 123Pay IVR — any SIM   │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │
│ │ 🔗 Send Mesh IOU           │ │
│ │ When there's no signal at  │ │
│ │ all                        │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

Rest of screens follow Flowpay's Material 3 aesthetic. All colors from `ui/theme/Color.kt` tokens (single source of truth per Flowpay's rule).

---

## 12. Error and edge cases

### 12.1 Mesh transport

| Case | Behavior |
|---|---|
| Peer disappears mid-transfer | Outbox retries. Fragment reassembly holds partial 30 s then discards. |
| Fragment reassembly overflow | Drop oldest, log warning. |
| LRU seen-set collision (unlikely) | Not distinguishable from replay; drop. Acceptable. |
| Malformed packet from a peer | Drop, log peer as `suspicious`; three strikes → block for 24 h. |
| Bluetooth off | Show non-blocking banner on Chat tab: "Bluetooth is off". No crash. |
| GATT server can't bind (rare) | Foreground service posts a "restart Bluetooth" prompt. |

### 12.2 LLM

| Case | Behavior |
|---|---|
| Model file missing | Assistant tab shows a "Download or side-load model" onboarding card. No crash. |
| First-token > 3 s | Show "still thinking…" hint at 2 s. |
| RAG returns empty | Model uses fallback template: "I don't have specific guidance — call 112." |
| Out-of-memory during load | Fall back to lookup-only mode (no generation), surface just the retrieved passages. |

### 12.3 Payments

Exactly Flowpay's error surface. Timeout (10 min) deletes PENDING row silently. Cancelled call → row marked Cancelled. All 14 supported bank SMS templates matched paise-exact.

### 12.4 IOU

| Case | Behavior |
|---|---|
| Fake IOU (bad signature) | Drop at receive, log, do not surface. |
| Same iou_id received twice | Dedup by primary key. |
| Sender never comes online before expiry | Auto-EXPIRE at 72 h. Receiver keeps envelope as evidence. |
| Settlement fires but bank SMS never confirms | Follow Flowpay's timeout path. IOU status = SETTLEMENT_FAILED. |
| Receiver rejects (blocked contact) | `iouRejected` mesh reply. Sender sees status = REJECTED. |
| Amount > sender's actual UPI balance | Settlement attempt fails at bank; IOU = SETTLEMENT_FAILED with reason. |

### 12.5 Cedar

| Case | Behavior |
|---|---|
| `libcedar_java_ffi.so` missing for device ABI | App logs warning, falls back to "allow all writes" — degraded but not broken. Not for production. |
| Policy set fails to parse | App logs, uses embedded default policy (no `#official`, no rate limit). |
| Officer signature verification fails | User's officer_signature flag is false; they cannot write to `#official`. |

### 12.6 Nostr

| Case | Behavior |
|---|---|
| All 5 relays unreachable | Publish queue grows, retried on next connectivity change. |
| Relay returns malformed event | Ignore. |
| Rate limit from a relay | Rotate to a different relay. |

---

## 13. Metrics and success criteria for the demo

The video is what the judges see (Rule 04 is strict about this). The metrics that matter are the ones the video demonstrates.

### 13.1 Must be demonstrated on-camera

1. Two phones with **airplane mode + Bluetooth on** exchanging encrypted chat messages, hop count visible.
2. A three-phone mesh with a phone in the middle acting as pure relay.
3. On-device LLM answering a genuine emergency question, source card visible.
4. UPI 123Pay call composing on-screen, bank SMS arriving, transaction row flipping to SUCCESS.
5. Mesh IOU: two offline phones, send + receive within seconds, later auto-settlement when one phone gets connectivity.
6. Gateway laptop terminal: Strands agent trace, dispatch draft appearing, dashboard row.

### 13.2 Repo-side deliverables

- Public GitHub repo, license Apache 2.0.
- README.md: what it is, why, how to build.
- ARCHITECTURE.md: the diagram from §8 and the detail from §5–7.
- NOTICE.md: every third-party source credited.
- CHANGELOG.md: honest, brief.
- LICENSE and per-library license references.

### 13.3 Writeup on AWS Builder Center

- Problem section with real Indian disaster examples.
- Architecture section reusing our diagram.
- "Where AWS fits" section, one paragraph per AWS piece we used.
- "What we learned" section, three honest sub-bullets (mesh protocol, on-device LLM, Cedar).
- AI-coding-tools disclosure.
- Repo link, demo video link.

Target: top-5 blogs prize eligibility.

---

## 14. Milestone breakdown by day

### 14.1 Day 0 (Wed, pre-clock)

Learning + environment setup only. **No repo, no project code** — Rule 03 is strict.

- Everyone: dev machine up with Android Studio Ladybug+, SDK 35, NDK 27, Docker Desktop.
- Everyone: WeMakeDevs account, First Commit registration, AWS Builder Center profile, SheerID verification.
- Team lead: claim $100/team AWS credit.
- Person A: cross-compile `libcedar_java_ffi.so` for `arm64-v8a` and `armeabi-v7a` with `cargo-ndk`. Confirm loads on a real device.
- Person B: MediaPipe LLM Inference Android sample running with `gemma-3-1b-it-int4.task` side-loaded on a Pixel 7.
- Person C: docker-compose up OpenSearch + LocalStack, one seed `sam local invoke` succeeds.
- Person D: read Bitchat whitepaper, note the Kotlin port priorities.
- Everyone: read Flowpay's `docs/ARCHITECTURE.md` and the two payment files.

### 14.2 Day 1 (Thu, clock starts)

**Goal: two-phone one-hop encrypted chat.**

- Morning: `git init`, first commit, `AppContainer` scaffold, Compose skeleton, Home + Chat tab shells.
- Person A: port `MeshTransport.kt` from Bitchat's Swift, get BLE advertising + scanning + GATT server/client running. Two devices see each other in the peer list.
- Person B: port `NoiseSession.kt` around `noise-java`, XX handshake working between two peers.
- Person C: begin `MessageRouter.kt` with one message type (text broadcast) and Flowpay-style Room setup.
- Person D: draft the demo-video script and start on the Assistant tab scaffold.
- End-of-day gate: two Pixels in airplane mode exchange one encrypted "hello".

### 14.3 Day 2 (Fri)

**Goal: mesh depth + LLM.**

- Multi-hop relay with TTL, LRU dedup keyed by (sender, timestamp, type, payload_digest).
- Signed announces at 4 s / 15–30 s.
- Sender outbox implementation (100/peer, 24 h TTL).
- Courier envelope layer with spray-and-wait budget 4, HMAC-day recipient tag.
- MediaPipe wired, first Q&A works.
- Ship pre-built RAG SQLite as APK asset; retrieval + prompt assembly done.
- End-of-day gate: three-phone mesh chat + on-device LLM answers a first-aid question offline.

### 14.4 Day 3 (Sat)

**Goal: payments + Cedar + gateway. Bangalore day for one teammate if desired.**

- Import Flowpay payment submodule, wire USSD and 123Pay into `PayTab`.
- Build IOU voucher: envelope + state machine + UI + auto-settle.
- Cedar `.so` loaded, policy set authored, every inbound message goes through `isAuthorized`.
- Gateway: SAM template with 3 Lambdas; Strands agent reading Nostr + DynamoDB Local; dashboard HTML.
- Nostr publish on connectivity-return implemented.
- End-of-day gate: full end-to-end path on two phones + one laptop.

### 14.5 Day 4 (Sun)

**Goal: demo video, writeup, submit.**

- 09:00–13:00: record the six video beats on real devices.
- 13:00–15:00: edit, subtitle, add repo URL card, export.
- 15:00–17:00: repo hygiene, README, ARCHITECTURE, NOTICE, LICENSE.
- 17:00–19:00: writeup + Builder Center blog.
- 19:00–20:00: submit before deadline. Buffer for reruns.

---

## 15. Team assignments (for a 4-person team)

| Role | Person | Owns |
|---|---|---|
| Mesh + Transport | A | BLE, Noise, MessageRouter, Cedar bridge |
| LLM + Assistant | B | MediaPipe, RAG, Assistant tab, agent loop |
| Payments + IOU + Persistence | C | Flowpay integration, IOU voucher, Room schema |
| Gateway + Nostr + Video | D | SAM Local, Strands agent, dashboard, Nostr client, demo video |

Cross-cutting: everyone reviews everyone's code. Everyone can commit anywhere. Blocks resolved in a shared voice channel, no PR queues.

If team is smaller than 4: collapse D into A + C. If team is a single person: cut F5 (Cedar), F6 (Nostr), and F7 (gateway); demo focuses on F1 (mesh), F2 (LLM), F3 (payments), F4 (IOU).

---

## 16. Dependencies

### 16.1 Software

| Dependency | Version | Source |
|---|---|---|
| Android Studio | Ladybug or later | jetbrains |
| Android SDK | 35 | |
| Android NDK | 27.x | |
| Kotlin | 2.1+ | |
| Jetpack Compose BOM | 2025.x | |
| Room | 2.6+ | |
| SQLCipher-android | 4.5+ | Zetetic |
| `noise-java` | latest | github.com/rfksystems/noise-java (fork for Android) |
| `com.google.mediapipe:tasks-genai` | 0.10.16+ | Google |
| ONNX Runtime Mobile | 1.19+ | Microsoft |
| `com.cedarpolicy:cedar-java` | 4.2.3-uber | Maven Central |
| `secp256k1-kmp` | latest | ACINQ |
| Nostr Kotlin binding | latest | rust-nostr/nostr-sdk-kmp |
| Flowpay payment module | main | github.com/Flowpayup/Payments-Without-Internet |
| Bitchat source | main | github.com/permissionlesstech/bitchat |
| Briar `bramble-android` | main | github.com/briar/briar (reference) |
| OpenSearch | 2.15+ | opensearch-project |
| LocalStack CE | 3.x | localstack |
| SAM CLI | 1.130+ | AWS |
| Strands Agents SDK Python | latest | pip `strands-agents` |
| Ollama | latest | local |

### 16.2 Model files

- `gemma-3-1b-it-int4.task` — ~529 MB, side-loaded
- `all-MiniLM-L6-v2.int8.onnx` — ~22 MB, in APK
- `gemma2:9b` or `llama3.2:3b` for Ollama on gateway

### 16.3 Hardware

- 3 Android phones (Pixel 6 or later ideal), 4th bonus for the multi-hop shot.
- 1 laptop, ideally with an NVIDIA GPU for Ollama, but Apple Silicon works.
- USB-C cables, screen-share cable / OBS setup for video capture.

### 16.4 Corpus documents

Downloaded and processed at build time:
- NDMA guidelines (ndma.gov.in) — assorted PDFs
- IFRC first-aid handbook — CC-BY, from ifrc.org
- WHO psychological first aid — from who.int
- Sphere handbook (2018 edition) — spherestandards.org
- IMD warning taxonomy — mausam.imd.gov.in

---

## 17. Risks and mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| BLE background execution kills on OEM ROMs | High | High | Demo on stock Pixels only. Foreground service with persistent notification. Documented as future work. |
| Cedar FFI cross-compile fails | Medium | Medium | Fallback: run Cedar on the gateway; phone calls it via LAN. Weaker but still counts. |
| MediaPipe model too big for APK | Certain | Low | Side-load `.task` file via ADB. Documented. Not a distribution model. |
| Noise-java rough edges on Android | Low | Medium | Fork it, replace `java.security.SecureRandom` where needed. Test on Android 10 minimum. |
| Nostr relay flakiness during demo | Medium | Low | 5 relays preselected, redundancy is the point. Fall back to LAN Lambda upload for demo. |
| Sunday deadline miss | Low | Critical | Video recorded Saturday night. Sunday is buffer + writeup only. |
| Bangalore day pulls a teammate out of coding | Low | Low | The day is workshops + mentor feedback + Amazon team — net-positive. Person brings back polish for the video. |
| Judges don't recognise Flowpay's payment code as our own | N/A | N/A | It isn't ours, we say so. Judged work is what we built around it. |
| BLE MTU negotiation fails on some devices | Low | Low | Fall back to default MTU (23) — slower but works. |
| Battery drain during multi-hour demo | Low | Low | External power banks for demo phones. |
| Panic wipe accidentally triggered on judges' demo phone | Low | High | Setting to disable panic wipe for demo builds; ship "demo mode" that shows a toast instead. |

---

## 18. Open questions

| # | Question | Decision needed by | Owner |
|---|---|---|---|
| Q1 | Final app name — Sankat Setu vs alternatives? | Thu morning | Team lead |
| Q2 | Include Wi-Fi Direct as a second transport tier for bulk transfer (Bridgefy pattern)? | Fri morning | Person A |
| Q3 | Use Nostr for the gateway agent's *only* input, or also LAN Lambda? | Sat morning | Person D |
| Q4 | Gemma 3 1B baseline or attempt Gemma 2 2B on higher-end demo phones? | Thu morning | Person B |
| Q5 | Preload officer signature for demo purposes, or perform the QR exchange live in the video? | Sat evening | Team lead |
| Q6 | Publish signed APK artifact on the repo Releases page? | Sun morning | Team lead |
| Q7 | Video voiceover language: English only or English + Hindi subtitles? | Sun morning | Person D |
| Q8 | Do we shoot a second, 30-sec "trailer" for the Builder Center blog cover? | Sun afternoon | Person D |

---

## 19. Post-hackathon future work (for the writeup)

Not required to ship, listed to show judges we understand what a productionisation would look like:

- iOS build with a shared Rust core (Bitchat's approach in reverse: Swift + Kotlin around a common Rust protocol library).
- Epoch-rotating peer IDs for unlinkable presence (Bitchat's own §9 future-work item).
- Prekey-based forward secrecy for sealed courier mail.
- Wi-Fi Direct as a second transport for bulk media (Bridgefy pattern).
- Multi-language UI (Tamil, Kannada, Malayalam, Marathi, Bengali).
- Federated identity via a trusted district authority — verifiable presence rather than pseudonymous.
- Integration with actual NDRF / state disaster management systems (real dispatch, not a demo dashboard).
- Emergency-SOS-via-satellite integration if a public API ever ships.

---

## 20. Appendix

### 20.1 Glossary

- **BLE**: Bluetooth Low Energy. Radio protocol used for our mesh.
- **Noise Protocol**: cryptographic handshake framework, used in Signal's underlying protocol, WhatsApp, Wireguard. XX pattern = mutual auth + forward secrecy. X pattern = one-way sealed to a static key.
- **GATT**: Generic Attribute Profile. BLE's data-transfer layer.
- **RSSI**: Received Signal Strength Indicator. Approximate distance measure between BLE peers.
- **Geohash**: encodes lat/lon as a short alphanumeric string; prefix length controls precision (5 chars ~5 km, 7 chars ~150 m).
- **Nostr**: Notes and Other Stuff Transmitted by Relays. Decentralised signed-event protocol.
- **Strands Agents SDK**: AWS-open-sourced agent framework for building agent loops with tool-calling.
- **Cedar**: AWS-open-sourced policy language for authorization.
- **PARA**: Proximity-Aware Routing Algorithm, from the BlueMesh paper (Chouhan, ISJEM 2026).
- **DTMF**: Dual-Tone Multi-Frequency. The touch-tones sent during a phone call to interact with an IVR.
- **NDMA**: National Disaster Management Authority (India).
- **IFRC**: International Federation of Red Cross and Red Crescent Societies.
- **Sphere**: humanitarian standards handbook.

### 20.2 References

- Bitchat whitepaper v2.0: `github.com/permissionlesstech/bitchat/blob/main/WHITEPAPER.md`
- Bitchat source: `github.com/permissionlesstech/bitchat`
- Flowpay: `github.com/Flowpayup/Payments-Without-Internet`
- Briar: `github.com/briar/briar`
- Bridgefy: `github.com/bridgefy`
- Nostr protocol: `github.com/nostr-protocol/nostr`
- MediaPipe LLM Inference: `github.com/google-ai-edge/mediapipe-samples`
- Strands Agents SDK: `github.com/strands-agents/harness-sdk`
- Cedar: `github.com/cedar-policy/cedar` and `cedar-java`
- BlueMesh paper: Chouhan M., ISJEM Vol 05 Issue 04 Special Edition, April 2026, DOI 10.55041/ISJEM06769
- First Commit hackathon rules: `wemakedevs.org/aws/first-commit/rules`

---

*End of PRD v1.*
