package com.sankatsetu.app.assistant

/** One source passage the answer is grounded in, for the UI's source card (PRD §11.2's Assistant screen). */
data class AssistantSource(val source: String, val section: String, val text: String)

data class AssistantAnswer(
    val text: String,
    val sources: List<AssistantSource>,
    /** True if [text] came from the LLM; false if it's the extractive fallback (top retrieved passage verbatim). */
    val wasGenerated: Boolean
)

private const val FALLBACK_NO_MATCH =
    "I don't have specific guidance for this — call 112 immediately."

private const val EMERGENCY_NUMBER_REMINDER = "\n\nCall 112 immediately if this is a medical or life-threatening emergency."

/**
 * Retrieval-augmented answering: [KnowledgeRetriever] finds relevant
 * passages, [llm] optionally turns them into a natural-language answer.
 * Degrades gracefully with no model present — see docs/adr/0009 — by
 * returning the top matched passage's own text verbatim (`wasGenerated =
 * false`) instead of failing or fabricating an answer with no grounding.
 */
class AssistantEngine(
    private val knowledgeBase: List<KnowledgeChunk>,
    private val llm: LlmAssistant = UnavailableLlmAssistant
) {
    suspend fun answer(query: String): AssistantAnswer {
        val matches = KnowledgeRetriever.search(query, knowledgeBase, topK = 3)
        if (matches.isEmpty()) {
            return AssistantAnswer(FALLBACK_NO_MATCH, emptyList(), wasGenerated = false)
        }

        val sources = matches.map { AssistantSource(it.chunk.source, it.chunk.section, it.chunk.text) }

        if (llm.isAvailable) {
            val prompt = buildPrompt(query, matches.map { it.chunk })
            val generated = llm.generate(prompt)
            if (generated != null) {
                return AssistantAnswer(generated.trim(), sources, wasGenerated = true)
            }
            // llm.generate() failing (isAvailable was true but generation
            // errored) still falls through to the extractive answer below
            // rather than surfacing an error to the user.
        }

        // Extractive fallback: the single best-matched passage, verbatim.
        // Not as fluent as a generated answer, but never fabricates anything
        // beyond what's actually in the knowledge base.
        val best = matches.first().chunk
        return AssistantAnswer(best.text + EMERGENCY_NUMBER_REMINDER, sources, wasGenerated = false)
    }

    private fun buildPrompt(query: String, chunks: List<KnowledgeChunk>): String {
        val context = chunks.withIndex().joinToString("\n\n") { (i, chunk) ->
            "[${i + 1}] ${chunk.text}\n    Source: ${chunk.source}, section ${chunk.section}"
        }
        return """
            |You are a first-response assistant for rural India, working completely offline.
            |Answer ONLY using the CONTEXT below. If the context does not answer the question,
            |say "I don't have specific guidance for this — call 112 immediately."
            |Keep your answer to 3 short paragraphs at most. Always end by mentioning the
            |emergency number 112.
            |
            |CONTEXT:
            |$context
            |
            |USER QUESTION: $query
            |
            |ASSISTANT:
        """.trimMargin()
    }
}
