package com.sankatsetu.app.assistant

/**
 * One retrievable passage of the offline knowledge base. Deliberately plain
 * data — no Android/JSON dependency — so [KnowledgeRetriever] can be unit
 * tested without touching an asset file or a device.
 *
 * There is no curated `keywords` field (Day 2 had one): [KnowledgeRetriever]
 * is now a real BM25 + TF-IDF ranker over [text] itself, so passages don't
 * need hand-picked keyword lists to be findable — which also means new
 * knowledge-base documents (see docs/knowledge-base/) can be authored as
 * plain text with no extra bookkeeping. See docs/adr/0011.
 */
data class KnowledgeChunk(
    val id: String,
    val source: String,
    val section: String,
    val text: String
)
