package com.sankatsetu.app.assistant

/**
 * One retrievable passage of the offline knowledge base. Deliberately plain
 * data — no Android/JSON dependency — so [KnowledgeRetriever] can be unit
 * tested without touching an asset file or a device. See
 * docs/adr/0009-on-device-assistant-scope.md for what corpus actually ships
 * (a small hand-written starter set, not the full NDMA/IFRC/WHO corpus the
 * PRD describes) and why.
 */
data class KnowledgeChunk(
    val id: String,
    val source: String,
    val section: String,
    val keywords: List<String>,
    val text: String
)
