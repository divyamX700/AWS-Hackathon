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

**Day 1 slice** (per `docs/adr/0001-hackathon-scope-and-day1-slice.md`):
BLE mesh transport, binary wire protocol, TTL/dedup/jitter/fanout routing,
Noise `XX`-encrypted 1:1 chat, minimal Room+SQLCipher persistence, minimal
Compose UI (peer list + one thread). Two phones in airplane mode + Bluetooth
on should be able to discover each other and exchange encrypted messages.

Not yet built: on-device LLM assistant, payment import, mesh IOU, Cedar
authorization, Nostr bridge, gateway coordinator. These land Day 2-3 per
`docs/PLAN.md` — this README will be updated as each lands.

**This has not been built or run on a device yet** — it was authored on a
machine without an Android SDK or a physical device attached. Before
anything else, open the project in Android Studio, let Gradle sync, and fix
whatever the first real compile turns up. `docs/adr/0002` flags the two
known risk points to check first (the `noise-java` Maven coordinate, and
Ed25519 Keystore support on API 29-32).

## Running it

```bash
git clone <this repo>
cd SankatSetu
# Open in Android Studio (Ladybug 2024.2.1 or later), let Gradle sync.
# Or from the command line, once local.properties points at your SDK:
./gradlew assembleDebug
```

Requires two Android 10+ (API 29+) devices with Bluetooth LE for the mesh
demo. No AWS account, no server, no internet connection needed for Day 1's
chat feature.

Day 2's Assistant tab will additionally require side-loading a Gemma model
file — see `docs/adr/0005-model-assets-not-committed.md` once that lands.

## Repo layout

```
app/                    the Android app
  src/main/java/com/sankatsetu/app/
    mesh/
      protocol/         binary wire format, TLV packets — pure Kotlin, unit-tested
      crypto/           identity keys, Noise XX/X sessions
      router/           TTL/dedup/jitter/fanout dispatch — pure Kotlin, unit-tested
      transport/        BLE advertising/scanning/GATT, foreground service
    data/               Room entities/DAOs, SQLCipher wiring
    di/                 hand-rolled composition root (AppContainer)
    ui/                 Compose screens + ViewModels
  src/test/             JVM unit tests (protocol + router — no device needed)
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
