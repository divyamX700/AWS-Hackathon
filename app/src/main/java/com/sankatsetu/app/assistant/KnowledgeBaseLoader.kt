package com.sankatsetu.app.assistant

import android.content.Context

/**
 * Loads every `.txt` file in the `assets/kb/docs` directory into
 * [KnowledgeChunk]s. This is the only Android-specific piece of the
 * retrieval feature — kept
 * deliberately thin so [KnowledgeRetriever]'s actual scoring logic stays
 * unit-testable without an asset manager or a device (see
 * [KnowledgeDocumentParser] for the pure-Kotlin parsing logic itself, which
 * *is* unit tested).
 *
 * Day 2 shipped one hand-authored JSON file with curated keywords per
 * chunk. Day 3 replaces that with plain-text documents (see
 * docs/knowledge-base/ for the source-of-truth copies, mirrored here) — see
 * docs/adr/0011 for why: BM25 doesn't need curated keywords, and plain text
 * is far easier to author, review, and extend than hand-picked keyword
 * lists per passage.
 *
 * Document format (see any file under docs/knowledge-base/ for real
 * examples):
 * ```
 * # Document Title
 * Source: <where this guidance is drawn from>
 *
 * ## Section Heading
 * Body text for this section, one or more paragraphs.
 *
 * ## Another Section
 * ...
 * ```
 * Each `##` section becomes one [KnowledgeChunk], sized for grounding a
 * single retrieved answer (a paragraph or two) rather than a whole document.
 */
object KnowledgeBaseLoader {
    private const val ASSET_DIR = "kb/docs"

    fun load(context: Context): List<KnowledgeChunk> {
        val fileNames = context.assets.list(ASSET_DIR)?.filter { it.endsWith(".txt") } ?: emptyList()
        return fileNames.flatMap { fileName ->
            val text = context.assets.open("$ASSET_DIR/$fileName").bufferedReader().use { it.readText() }
            KnowledgeDocumentParser.parse(fileName.removeSuffix(".txt"), text)
        }
    }
}
