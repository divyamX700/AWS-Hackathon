# ADR 0011: On-device LLM model choice for the crisis Assistant

## Status
Accepted (Day 3)

## Context

The user asked us to verify a specific claim — "Qwen 3 0.6B is good enough
for our purpose" — before picking a model, rather than taking it on faith.
This ADR records that verification and the resulting decision.

The app already depends on `com.google.mediapipe:tasks-genai:0.10.16`
(added Day 2 — see `MediaPipeLlmAssistant.kt`), which exposes Google's
**MediaPipe LLM Inference API**. That API loads a single-file model bundle
in the `.task` format (a zip-like container holding the converted
weights + tokenizer + a small runtime config). This is the format the whole
Day 2 assistant pipeline is already built and tested against.

## What we found researching the claim

Google's on-device model conversions are published under the
`litert-community` org on Hugging Face. As of this research pass:

| Model | Params | `.task` bundle available? | Format actually shipped |
|---|---|---|---|
| `litert-community/Qwen3-0.6B` | 0.6B | **No** | `.litertlm` only (4 variants: int8, int4, MediaTek-NPU-specific, dynamic int4) |
| `litert-community/Qwen2.5-0.5B-Instruct` | 0.5B | **Yes** | Both `.task` (f32/q8) and `.tflite`/`.litertlm` |
| `litert-community/Gemma3-1B-IT` | 1B | Yes, many variants | `.task` (f32/q4/q8) — but the repo is **gated** behind a Gemma license click-through on Hugging Face, blocking unattended download |

The critical finding: **`.litertlm` is not the same format as `.task`.**
It's the container format for Google's newer **LiteRT-LM** runtime — a
separate, C++-native inference engine
([google-ai-edge/LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM))
that superseded the MediaPipe GenAI task-bundle pipeline industry-wide, but
ships no ready-made Kotlin/Java Android AAR comparable to `tasks-genai`'s
`LlmInference` class. Integrating it would mean writing our own JNI bridge
to a C++ inference library from scratch — a multi-day project, not
something to attempt hours before a hackathon deadline.

**So: Qwen3-0.6B is a good *model* — the underlying Qwen3 architecture and
its instruction-tuned 0.6B checkpoint are genuinely strong for its size, and
the user's instinct about model quality was reasonable — but it is not
*deployable through the runtime this app already uses*.** The claim doesn't
hold up for our specific integration constraint, which is exactly why it
was worth checking before committing to it.

## Decision

Ship **`litert-community/Qwen2.5-0.5B-Instruct`**, specifically the
`Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task` artifact
(int8-quantized weights, 1280-token KV cache, ~521 MB):

- It is a real `.task` bundle — drops directly into the `MediaPipeLlmAssistant`
  code already written and tested, with zero runtime changes.
- It is **not gated** (Apache-2.0, no Hugging Face license click-through),
  so it can be fetched unattended.
- 0.5B parameters at int8 is realistic for a mid-range Android phone: it
  fits comfortably in RAM alongside the rest of the app and the mesh/BLE
  stack, and generates at an interactive rate on CPU (MediaPipe's GPU
  delegate is a bonus, not a requirement).
- Qwen2.5 and Qwen3 share the same base architecture family and training
  lineage; the 0.5B Qwen2.5-Instruct checkpoint is a close capability match
  for what a 0.6B Qwen3 would have offered for this specific job (short,
  grounded, instruction-following answers over retrieved passages) — we are
  not meaningfully downgrading quality to solve the format problem.

If Google ships a Kotlin AAR for LiteRT-LM (or `tasks-genai` gains
`.litertlm` support) after the hackathon, re-evaluating Qwen3-0.6B directly
is a clean follow-up — nothing in this design is Qwen2.5-specific beyond the
model file path.

## Update: the `.task` file itself needed a second pass

Getting a `.task` bundle wasn't the end of it. Two more real, on-device
findings, in the order they were hit:

1. **`tasks-genai:0.10.16`(Day 2) couldn't load ANY current litert-community
   `.task` export**, including the "legacy-named" `_seq128_` ones on other
   models (tested against both `Qwen2.5-0.5B-Instruct` and
   `Qwen2.5-1.5B-Instruct`) — same native error both times:
   `TfLitePrefillDecodeRunnerCalculator` failing a `RET_CHECK` on a missing
   signature key. Every current litert-community export, regardless of
   filename convention, appears to use a newer TFLite signature layout than
   0.10.16 understands. Bumping to `0.10.25`/`0.10.35` (the newest available)
   fails differently: D8 refuses those `.aar`s outright
   (`Unsupported class file major version 65` — compiled for Java 21,
   newer than this project's AGP 7.4.2 / D8 combination can dex, which is
   itself downstream of the Day 1 JDK11 toolchain pin in ADR 0006). Bisecting
   the versions between found **`tasks-genai:0.10.25`+ won't dex, but
   `0.10.20` both dexes fine AND loads the model successfully** — that's
   what's actually shipped.
2. **Prompt format matters more than expected for a 0.5B model.** Wrapping
   the prompt in Qwen's ChatML turn markers (`<|im_start|>...<|im_end|>`) —
   the textbook-correct format for a Qwen-family chat model — made this
   specific quantized conversion emit an immediate stop token with *zero*
   generated content. That points to the bundled tokenizer not mapping those
   literal strings to the true control tokens the base model was trained on
   for this conversion, so the model read them as confusing plain text
   instead of turn boundaries. A plain, unstructured instruction prompt (see
   `AssistantEngine.buildPrompt`) reliably produces real generated text
   instead. `AssistantEngine.answer()` also now treats an empty (but
   non-null) generation the same as a failed one and falls through to the
   extractive answer, rather than showing the user a blank reply — this
   really happened during testing and would otherwise look like a silent
   bug to a user, not a model limitation.

**Known, accepted limitation:** as a genuinely 0.5B model, Qwen2.5-0.5B-Instruct's
generated answers are sometimes short or trail off mid-sentence rather than
completing a full, polished paragraph. This is a real capability ceiling of
a model this small, not a pipeline bug — it's exactly why the extractive
fallback and the always-shown source citations exist: the user still gets
correct, grounded information even when the phrasing is rough.

## Why RAG matters more than model choice here

Regardless of which small model is picked, a 0.5-1B parameter model
**will** hallucinate specifics (dosages, phone numbers, exact procedures) if
asked to answer from its own weights alone. That's why the Assistant is
architected so the LLM's only job is to *phrase* an answer — it is
instructed to answer strictly from retrieved passages and to say so
explicitly when nothing relevant was retrieved (see `AssistantEngine.buildPrompt`).
The retrieval quality (now BM25 + TF-IDF, see `KnowledgeRetriever.kt`) and
the accuracy of the underlying text corpus (`docs/knowledge-base/`, mirrored
into `assets/kb/docs/`) matter more to real-world safety than which small
model does the phrasing.
