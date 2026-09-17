# Notice

Sankat Setu is built for the First Commit hackathon (Bharat Builds Tour,
Sept 17-20, 2026) and knowingly builds on the following open-source projects.
Per the project's own sourcing policy (see `docs/adr/0002-vendoring-and-porting-strategy.md`),
attribution is handled here rather than through license-compliance
engineering — the priority was a working demo, not a legal audit.

## Bitchat

`mesh/protocol/*.kt` (packet header format, TLV announce/private-message
encoding, PKCS#7-style padding) and the flood-control parameters in
`mesh/router/*.kt` (TTL, dedup window, jitter ranges, fanout subset
selection) are a Kotlin port of algorithms and wire formats from
[permissionlesstech/bitchat](https://github.com/permissionlesstech/bitchat),
released into the public domain (Unlicense). We wrote our own Kotlin
implementation from the whitepaper and source rather than translating a
build artifact; no Swift source was copied verbatim.

Source: https://github.com/permissionlesstech/bitchat
Whitepaper: https://github.com/permissionlesstech/bitchat/blob/main/WHITEPAPER.md
License: Unlicense (public domain)

## Flowpay (Payments-Without-Internet)

The payment module (`*99#` USSD dialing, UPI 123Pay IVR DTMF construction,
bank SMS confirmation parsing) is imported from
[Flowpayup/Payments-Without-Internet](https://github.com/Flowpayup/Payments-Without-Internet),
licensed Apache 2.0. Landing Day 3 per `docs/PLAN.md`.

Source: https://github.com/Flowpayup/Payments-Without-Internet
License: Apache License 2.0

## Briar

`mesh/transport/MeshForegroundService.kt`'s approach to surviving Android's
background-execution limits was informed by reading
[briar/briar](https://github.com/briar/briar)'s `bramble-android` module as
a design reference. No Briar source was copied — Briar is GPLv3 and this
project has not adopted that license.

Source: https://github.com/briar/briar
License: GNU GPL v3 (referenced for design only, not incorporated)

## BlueMesh (Chouhan, ISJEM 2026)

The optional RSSI-weighted "Proximity-Aware Routing Algorithm" extension
(planned, not yet implemented as of Day 1) is credited to:

> Chouhan, M. "BlueMesh: A Decentralized Bluetooth-Based Messaging System
> Without Internet Using MERN Stack." *International Scientific Journal of
> Engineering and Management*, Vol. 05, Issue 04, Special Edition, April 2026.
> DOI: 10.55041/ISJEM06769

No code was published alongside this paper; only the algorithmic idea is
credited.

## MediaPipe

`mesh/assistant/*` (Day 2) is built starting from Google's MediaPipe LLM
Inference Android sample code.

Source: https://github.com/google-ai-edge/mediapipe-samples
License: Apache License 2.0

## noise-java

`mesh/crypto/NoiseSession.kt` depends on rweather/noise-java, the reference
Java implementation of the Noise Protocol Framework.

Source: https://github.com/rweather/noise-java
License: See upstream repository

## cedar-java / Cedar

Day 3's on-device authorization policies run on AWS's Cedar policy engine.

Source: https://github.com/cedar-policy/cedar-java
License: Apache License 2.0

## Strands Agents SDK

The gateway coordinator's agent loop (Day 3, `gateway/agent/`) uses AWS's
Strands Agents SDK.

Source: https://github.com/strands-agents/harness-sdk
License: Apache License 2.0
