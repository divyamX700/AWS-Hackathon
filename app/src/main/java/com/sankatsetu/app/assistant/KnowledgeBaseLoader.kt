package com.sankatsetu.app.assistant

import android.content.Context
import org.json.JSONArray

/**
 * Loads `assets/kb/starter_corpus.json` into [KnowledgeChunk]s. The only
 * Android-specific piece of the retrieval feature — kept deliberately thin
 * so [KnowledgeRetriever]'s actual scoring logic stays unit-testable without
 * an asset manager or a device.
 */
object KnowledgeBaseLoader {
    private const val ASSET_PATH = "kb/starter_corpus.json"

    fun load(context: Context): List<KnowledgeChunk> {
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val root = org.json.JSONObject(json)
        val chunksArray: JSONArray = root.getJSONArray("chunks")

        return (0 until chunksArray.length()).map { i ->
            val obj = chunksArray.getJSONObject(i)
            val keywordsArray = obj.getJSONArray("keywords")
            val keywords = (0 until keywordsArray.length()).map { keywordsArray.getString(it) }
            KnowledgeChunk(
                id = obj.getString("id"),
                source = obj.getString("source"),
                section = obj.getString("section"),
                keywords = keywords,
                text = obj.getString("text")
            )
        }
    }
}
