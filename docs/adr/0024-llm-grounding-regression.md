# ADR 0021: On-device LLM grounding regression after the knowledge-base expansion

**Status:** Resolved for the case that matters (a topic-switching question in the same conversation) — root cause narrowed, see update below
**Date:** 2026-09-20

## Context

After `docs/adr/0023-image-grounded-answers.md`'s content-expansion pass
roughly doubled every knowledge-base section's length, live on-device
testing (the connected phone, Assistant tab, real `Qwen2.5-0.5B-Instruct`
model side-loaded and run) found a serious regression: asking three
unrelated questions in the same session —

1. "how do I put on a bandage for a wound"
2. "severe bleeding from arm when to use a tourniquet"
3. "someone collapsed and is not breathing how do I do CPR"

— produced the same or near-identical generic wound-cleaning answer
("Rinse the wound gently with clean or boiled-and-cooled water...") for
**all three**, despite each being a genuinely different question.

## What was actually verified (not assumed)

Retrieval itself was checked directly against the real, expanded knowledge
base (a throwaway JVM test loading the real `assets/kb/docs/*.txt` files
through the same `KnowledgeDocumentParser`/`KnowledgeRetriever` the app
uses, then deleted — not kept as permanent test debt):

```
QUERY: severe bleeding from arm when to use a tourniquet
  score=11.58  When to Use a Tourniquet          <- correct, ranked #1
  score=6.23   Prioritizing Multiple Injured People
  score=4.87   Immediate Control of Bleeding
  score=4.82   Recovery Position for a Breathing but Unresponsive Person
  score=4.40   Signs of Serious Envenomation
```

"Cleaning Minor Wounds" — the section whose content the model actually
echoed — was not even in the top 5 for this query. **Retrieval is correct.
The bug is in generation, not search.**

A real, independently-confirmed root cause was found and fixed: the
expanded sections pushed a 3-passage context block past ~5270 characters
(~1300+ tokens) in one measured case — over the `.task` bundle's
1280-token KV cache ceiling (`setMaxTokens(1200)`, prompt + output
combined; see `MediaPipeLlmAssistant`) **before any output token was even
generated**. `AssistantEngine.truncatedForPrompt()` now caps each chunk at
500 characters (cutting at the last full sentence, not mid-word) before it
enters the prompt. This fix is confirmed via logcat: prompt sizes dropped
from ~5270 chars to 3026/3660 chars across two live re-tests, both
comfortably in-budget.

**That fix was not sufficient.** The user re-tested live and reported the
tourniquet question still produced the same bandage-question answer even
after the truncation fix shipped. This was reported directly by the user
watching their own screen, after the phone had already disconnected from
this session — it was not independently re-verified against a device
afterward.

## Decision (a mitigation, explicitly not a confirmed fix)

`AssistantEngine.answer()` now feeds the model **only the single
best-ranked chunk** (`matches.take(1)`), not all 3 (`matches` stays size 3
for `AssistantSource`/the UI's citations and the deterministic image
attachment — see ADR 0020 — only the LLM's own context block shrank).
Reasoning: if a 3-passage context block gives a ~0.5B parameter quantized
model enough surface area to drift toward a memorized-sounding generic
answer instead of committing to the specific top-ranked passage — even
once that passage is both correct and comfortably within budget on its
own — reducing to one passage removes that degree of freedom entirely.

**This is a reasoned next step based on the evidence above, not a verified
fix.** There was no device connected to re-test it live before this session
ended. Do not describe this as resolved in any handoff document without
first re-running the exact three questions above on a real device with a
real model side-loaded, and confirming three genuinely distinct,
on-topic answers.

## If this is still broken next session

Things not yet tried, in rough order of how much they'd tell you:

1. **Re-run the exact three questions above** with `adb logcat | grep MediaPipeLlmAssistant` running, and read the actual generated text this time (this session's last two live tests were interrupted before the second answer's full text was captured on-screen).
2. **Recreate the whole `LlmInference` engine per call**, not just a fresh `LlmInferenceSession` — `MediaPipeLlmAssistant.inference` is a class-level singleton reused across every question; if MediaPipe's native runtime has any cross-session state leakage, only recreating the session (current behavior) wouldn't catch it. Expensive (this is the 521MB model reload), so only worth it as a diagnostic, not a shipped fix.
3. **Vary the random seed per question** (currently `BASE_SEED = 42` always, for attempt 0 of every call) — a fixed seed plus a dominant shared instruction preamble could be part of why generation converges to a similar output shape across different questions.
4. **Log the exact assembled prompt** (not just its character count) for a failing case, so the actual text the model saw is known for certain rather than inferred from `KnowledgeRetriever` output plus `truncatedForPrompt`'s logic.

## Consequences

- The prompt-overflow bug is real, fixed, and confirmed — keep
  `truncatedForPrompt()` regardless of how the deeper issue resolves.
- The `matches.take(1)` change is low-risk (it can only reduce, never
  increase, prompt size, and `AssistantEngineTest`'s existing suite plus
  the new truncation regression test all still pass) but must be labeled
  honestly as unverified on real hardware until someone actually re-runs
  the three questions above on a device.
- `docs/TODO.md` should carry a pointer to this ADR until it's closed out
  with a real, device-verified answer.

## Update: root cause narrowed to conversation history, not context blending

Further live testing (the exact demo sequence — "how do I stop massive
bleeding" then, in the same conversation, "how do I apply a tourniquet")
found the `matches.take(1)` mitigation alone was still not enough: the
second question's answer was still contaminated with facts from the
first question's retrieved passage. The decisive test: asking the exact
same second question **with the conversation cleared first** (no prior
turn at all) came back clean and correctly grounded, every time. That
isolates the cause precisely — this ~0.5B model does not reliably obey
`buildPrompt`'s own "Conversation so far... is ONLY for resolving a
pronoun or follow-up... never a source of facts; if the new question is
a different situation, ignore the prior answer entirely" instruction.

**Fix**: `AssistantViewModel.ask()` now never passes any prior turn as
history — every question is answered independently, always. This gives
up real multi-turn follow-up resolution (e.g. "what about for a child?"
after a first aid answer), which the original feature was built for. That
trade was made deliberately: a wrong answer to a genuinely new question is
worse than losing pronoun resolution on a rarer follow-up question, and
this is a rural crisis-response app where a wrong tourniquet-adjacent
answer is a real harm, not just an inconvenience.

**A second instance of the same root cause was found and fixed the same
way**: the suggested-action chip ("Broadcast \"I'm safe\" now" /
"Open Pay tab") — driven by an `Action:` line the model is asked to
self-classify at the top of every guide-format answer — showed up on
plain first-aid questions that were never about a resolved emergency,
for the same underlying reason: the model does not reliably follow a
self-classification instruction in this prompt format. `AssistantScreen.kt`
no longer renders that chip at all, regardless of what `Action:` value
the model emits; `AssistantEngine` still parses the line out of the
visible text (that part is correct and necessary), the UI just never
acts on it.

Not yet re-examined: whether a *smaller*, more targeted set of grounded
facts (rather than removing history altogether) could have preserved
follow-up resolution without contamination — e.g., only including history
when the new question's top-ranked section matches the prior turn's. That
was scoped out for time, not because it's known not to work.
