package com.sankatsetu.app.assistant

/** One source passage the answer is grounded in, for the UI's source card (PRD §11.2's Assistant screen). */
data class AssistantSource(val source: String, val section: String, val text: String)

/** One prior question+answer, for multi-turn context — see [AssistantEngine.answer]. */
data class AssistantExchange(val question: String, val answer: String)

data class AssistantAnswer(
    val text: String,
    val sources: List<AssistantSource>,
    /** True if [text] came from the LLM; false if it's the extractive fallback (top retrieved passage verbatim). */
    val wasGenerated: Boolean
)

private const val FALLBACK_NO_MATCH =
    "I don't have specific guidance for this — call 112 immediately."

private const val EMERGENCY_NUMBER_REMINDER = "\n\nCall 112 immediately if this is a medical or life-threatening emergency."

/** How many prior exchanges to carry into the prompt — kept small on purpose, see [AssistantEngine.buildPrompt]. */
private const val MAX_HISTORY_TURNS = 2

/**
 * Retrieval-augmented answering: [KnowledgeRetriever] finds relevant
 * passages, [llm] turns them into a structured, synthesized answer — not a
 * dump of the raw retrieved text. [MediaPipeLlmAssistant] already retries a
 * bad/empty sample once before giving up (see its doc), so the extractive
 * fallback here is for the case that's actually undegradable: no model
 * side-loaded at all, or the device's second attempt still failing. That
 * fallback is never silent — [AssistantAnswer.wasGenerated] tells the UI
 * exactly which happened, and `AssistantScreen.kt` labels it visibly.
 */
class AssistantEngine(
    private val knowledgeBase: List<KnowledgeChunk>,
    private val llm: LlmAssistant = UnavailableLlmAssistant
) {
    /** [history] is prior turns in *this* conversation, oldest first — see [buildPrompt] for how much of it is actually used. */
    suspend fun answer(query: String, history: List<AssistantExchange> = emptyList()): AssistantAnswer {
        val matches = KnowledgeRetriever.search(query, knowledgeBase, topK = 3)
        if (matches.isEmpty()) {
            return AssistantAnswer(FALLBACK_NO_MATCH, emptyList(), wasGenerated = false)
        }

        val sources = matches.map { AssistantSource(it.chunk.source, it.chunk.section, it.chunk.text) }

        if (llm.isAvailable) {
            val prompt = buildPrompt(query, matches.map { it.chunk }, history)
            val generated = llm.generate(prompt)?.trim()
            if (!generated.isNullOrEmpty()) {
                return AssistantAnswer(generated, sources, wasGenerated = true)
            }
        }

        // Extractive fallback: the single best-matched passage, verbatim.
        // Not as useful as a synthesized guide, but never fabricates
        // anything beyond what's actually in the knowledge base — and the
        // UI marks this state visibly rather than presenting it as if it
        // were a real generated answer.
        val best = matches.first().chunk
        return AssistantAnswer(best.text + EMERGENCY_NUMBER_REMINDER, sources, wasGenerated = false)
    }

    /**
     * Asks for a short structured guide — situation + numbered steps + what
     * to avoid + the 112 reminder — synthesized from the context in the
     * model's own words, not the raw retrieved passages pasted back. Early
     * testing (see docs/adr/0011's update) showed this small model reliably
     * *completes* this format rather than trailing off, likely because a
     * numbered list gives it a concrete continuation pattern to follow —
     * whereas an open-ended "write a paragraph" instruction is exactly what
     * produced short, cut-off answers before.
     *
     * History is capped at [MAX_HISTORY_TURNS] prior exchanges, kept short:
     * this model's `.task` bundle has a 1280-token KV cache shared across
     * prompt *and* generated output (see `MediaPipeLlmAssistant`), so
     * unbounded history would eventually crowd out the retrieved context or
     * the answer itself. Two turns is enough for "and what about children"
     * follow-up-style questions without risking that.
     */
    private fun buildPrompt(query: String, chunks: List<KnowledgeChunk>, history: List<AssistantExchange>): String {
        val context = chunks.withIndex().joinToString("\n\n") { (i, chunk) ->
            "[${i + 1}] ${chunk.text}\n    Source: ${chunk.source}, section ${chunk.section}"
        }
        val historyBlock = if (history.isEmpty()) {
            ""
        } else {
            val recent = history.takeLast(MAX_HISTORY_TURNS)
            "Conversation so far (for resolving pronouns/follow-ups ONLY, not a source of facts):\n" +
                recent.joinToString("\n") { "User: ${it.question}\nGuide: ${it.answer}" } +
                "\n\n"
        }

        return """
            |Instruction: You are a calm, direct crisis-response guide for rural India, working
            |completely offline. Answer the new question as a short practical guide, in your own
            |words — do not copy text verbatim. Format your answer EXACTLY like this, and nothing
            |more:
            |Situation: one short sentence on what this is.
            |1. First action step.
            |2. Next action step.
            |3. Last action step.
            |Avoid: one short line on what not to do, only if the context mentions one.
            |Always call 112 immediately for a life-threatening emergency.
            |
            |Hard rules: base every fact ONLY on the "Context passages" below — they are the sole
            |source of truth for the new question. The "Conversation so far" section, if present,
            |is ONLY for understanding what a pronoun or follow-up phrase like "for a child" or
            |"what about her" refers to — never pull facts, symptoms, or treatments from it. If
            |the new question is about a different situation than the conversation so far (for
            |example, the topic changed from a snake bite to a burn), ignore the previous answer's
            |specific details entirely and answer fresh from the context passages only. Write AT
            |MOST 3 numbered steps, never more, even if the context has more detail available —
            |pick only the 3 most important actions. Stop writing immediately after the 112 line;
            |do not add a 4th step, do not add extra advice, do not repeat yourself, do not invent
            |anything not grounded in the context passages (for example, never suggest a specific
            |medicine or dose unless the context explicitly names it). If the context passages do
            |not answer the new question, say so plainly in one line instead of guessing.
            |
            |$historyBlock|Context passages (the only source of facts for the new question):
            |$context
            |
            |New question: $query
            |
            |Answer:
        """.trimMargin()
    }
}
