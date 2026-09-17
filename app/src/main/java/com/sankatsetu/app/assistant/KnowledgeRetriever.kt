package com.sankatsetu.app.assistant

/**
 * Retrieval over the on-device knowledge base. This is a **keyword/term-
 * overlap scorer, not neural embedding similarity** — the PRD (§6.2, §F2.2)
 * describes MiniLM ONNX embeddings + an ANN index; that requires a model
 * file we don't have bundled and is real future work, not this. What's here
 * is a genuine, working, tested retrieval algorithm in the meantime — see
 * docs/adr/0009-on-device-assistant-scope.md for the honest accounting of
 * what's real vs. deferred in the Day 2 Assistant feature.
 *
 * Scoring: each query token contributes [KEYWORD_WEIGHT] if it matches one
 * of a chunk's curated keywords exactly, or [TEXT_WORD_WEIGHT] per
 * occurrence as a whole word in the chunk's body text. Keyword matches are
 * weighted higher because they're curated signal; body-text matches are
 * noisier (common words like "the" are filtered as stop words first).
 */
object KnowledgeRetriever {
    private const val KEYWORD_WEIGHT = 10
    private const val TEXT_WORD_WEIGHT = 1

    private val STOP_WORDS = setOf(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "am",
        "i", "you", "he", "she", "it", "we", "they", "my", "your", "his", "her", "its", "our", "their",
        "and", "or", "but", "if", "then", "so", "to", "of", "in", "on", "at", "for", "with", "by",
        "do", "does", "did", "can", "could", "will", "would", "should", "what", "how", "when", "where", "why",
        "this", "that", "these", "those", "have", "has", "had", "not", "no"
    )

    data class ScoredChunk(val chunk: KnowledgeChunk, val score: Int)

    /** Tokenizes [text] into lowercase alphanumeric words, dropping stop words. */
    fun tokenize(text: String): List<String> =
        Regex("[a-zA-Z0-9]+")
            .findAll(text.lowercase())
            .map { it.value }
            .filter { it !in STOP_WORDS && it.length > 1 }
            .toList()

    /** Returns the top [topK] chunks with score > 0, highest first. Empty if nothing matches. */
    fun search(query: String, chunks: List<KnowledgeChunk>, topK: Int = 3): List<ScoredChunk> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        return chunks
            .map { chunk -> ScoredChunk(chunk, scoreChunk(queryTokens, chunk)) }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(topK)
    }

    private fun scoreChunk(queryTokens: List<String>, chunk: KnowledgeChunk): Int {
        val keywordSet = chunk.keywords.map { it.lowercase() }.toSet()
        val bodyTokens = tokenize(chunk.text)
        val bodyTokenCounts = bodyTokens.groupingBy { it }.eachCount()

        var score = 0
        for (token in queryTokens.distinct()) {
            if (token in keywordSet) score += KEYWORD_WEIGHT
            score += (bodyTokenCounts[token] ?: 0) * TEXT_WORD_WEIGHT
        }
        return score
    }
}
