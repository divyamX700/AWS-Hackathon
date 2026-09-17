# ADR 0005: Model files are side-loaded, not committed

**Status:** Accepted
**Date:** 2026-09-18

## Context

Day 2 adds the on-device LLM assistant (PRD F2): a ~529 MB Gemma 3 1B int4
`.task` file for MediaPipe LLM Inference, plus a ~22 MB MiniLM ONNX embedding
model. GitHub has a 100 MB hard file-size limit (and Git LFS bandwidth quotas
that don't fit a hackathon's zero-budget constraint).

## Decision

- `app/src/main/assets/models/*.task` and `*.onnx` are gitignored (see
  `.gitignore`).
- The MiniLM embedding model and the pre-built RAG SQLite index (~20 MB,
  built from the OpenSearch corpus at dev time) are small enough to commit
  and should be checked in once Day 2 produces them — they are *not*
  excluded by the current gitignore pattern, only the raw `.onnx`/`.task`
  files are.

  Correction while writing this ADR: the gitignore pattern
  `app/src/main/assets/models/*.onnx` would also catch the MiniLM model,
  which we *do* want committed. Fix before Day 2 lands: gitignore only the
  Gemma `.task` file by exact name, not the whole `models/*.onnx` glob.
- The README's setup instructions must include the exact `adb push` command
  and Kaggle/HuggingFace download link for the Gemma model file, so a fresh
  clone plus one documented command reproduces a working device.

## Consequences

Anyone cloning this repo cold cannot run the Assistant tab until they've
side-loaded the model file. This is called out explicitly in `README.md`'s
"Running it" section, not left as a silent failure.
