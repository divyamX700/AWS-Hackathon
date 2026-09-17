# ADR 0009: On-device assistant — what's real vs. what Day 3 replaces

**Status:** Accepted
**Date:** 2026-09-18

## Context

`docs/PRD.md` §F2 describes the Assistant feature as: MediaPipe LLM
Inference running Gemma 3 1B, a MiniLM ONNX embedding model for retrieval
over a full NDMA/IFRC/WHO/Sphere corpus, with a pre-built SQLite ANN index.
None of that model/corpus infrastructure can be built in this session — the
model files are multi-hundred-megabyte binaries (side-loaded per
`docs/adr/0005`, not something to fetch here), and building the "full
corpus, embedded and indexed" pipeline described in PRD §6.2 is itself a
data-processing project, not a few hours of coding.

## Decision

Built the feature in a way that is **honest about what's real** rather than
stubbing it out or overclaiming:

### What's genuinely real, working, and tested

- **`KnowledgeRetriever`**: a real, working retrieval algorithm — keyword
  and body-text term-overlap scoring (curated keywords weighted 10x over
  plain body-text word matches, stop words filtered) — fully unit tested
  (`KnowledgeRetrieverTest`, 6 tests). It is **not** the neural-embedding
  similarity search the PRD describes; it's a classical IR technique that
  needs no model file at all and works today.
- **A real starter knowledge base** (`assets/kb/starter_corpus.json`): 8
  hand-written entries covering bleeding, snake bite, CPR, flood safety,
  earthquake safety, shock, burns, and evacuation — standard, accurate
  first-aid/disaster guidance (the kind found in any Red Cross / WHO basic
  first-aid reference), each with a curated keyword list. Not the full
  NDMA/IFRC/WHO/Sphere corpus the PRD describes — that corpus-building
  pipeline (fetch real documents, chunk them, verify licensing per doc) is
  Day 3+ work.
- **`AssistantEngine`**: retrieval-augmented answering with a real,
  honest fallback chain — if no model is available (the common case, since
  nothing is bundled), it returns the single best-matched passage's actual
  text, verbatim, rather than fabricating a summary. If a model *is*
  available but generation fails for any reason, it falls back the same
  way rather than surfacing an error. Fully unit tested
  (`AssistantEngineTest`, 4 tests) using a fake `LlmAssistant` so the
  fallback logic is verified without needing MediaPipe or a model file at
  all.
- **`MediaPipeLlmAssistant`**: a real wrapper around MediaPipe's actual
  `LlmInference` API (`com.google.mediapipe.tasks.genai.llminference`,
  version 0.10.16 — added to `app/build.gradle.kts` and confirmed to
  compile and dex cleanly under the AGP 7.4.2 toolchain from
  `docs/adr/0006`, unlike CameraX 1.4.1/Lifecycle 2.8.7's bytecode issues).
  `isAvailable` checks for the model file's actual presence on disk before
  ever touching the MediaPipe API, and every failure path (missing file,
  OOM, unsupported chip, corrupt model) degrades to "unavailable" rather
  than crashing — verified by installing the built APK on a real emulator
  and confirming the Assistant tab doesn't crash with no model present.

### What isn't real yet, and shouldn't be described as working

- **Neural embedding retrieval** (MiniLM ONNX + ANN index) — replaced by
  the keyword scorer above for now.
- **The full NDMA/IFRC/WHO/Sphere corpus** — replaced by 8 starter entries.
- **Actually running Gemma 3 1B on a device** — the wrapper code is real
  and will work once a model file is side-loaded (see README.md's `adb
  push` instructions once Day 3 adds them), but this was never tested with
  an actual model file in this session, since that file doesn't exist here.

## A real bug this caught

Installing the Day 2 build (with the new bottom nav bar) on a real API 34
emulator surfaced an unrelated but serious bug: `MainActivity`'s permission
callback called `startMeshService()` unconditionally after merely
*requesting* permissions, without checking whether they were actually
*granted*. On Android 14, starting a `connectedDevice`-type foreground
service without at least one granted Bluetooth permission throws a
`SecurityException` that crashes the whole app on first launch — a
guaranteed crash for anyone who denies the Bluetooth permission prompt, or
whose grant hasn't resolved yet. Fixed in `MainActivity.kt`: the callback
now checks the actual `grants` map before starting the service. Verified
both ways on-device: permissions denied → no crash, service simply doesn't
start; permissions granted → service starts successfully
(`isForeground=true` confirmed via `dumpsys activity services`).

## Consequences

The Assistant tab is genuinely functional today — ask it a first-aid
question with zero setup and it gives a real, grounded answer from the
starter corpus. Anyone demoing before Day 3 should describe it as "offline
keyword-matched guidance from a starter corpus, with the wiring already in
place for on-device LLM generation once a model is loaded" — not "a full
RAG pipeline over the complete disaster-response corpus," which is what
Day 3 needs to actually build.
