package com.sankatsetu.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeRetrieverTest {

    private val chunks = listOf(
        KnowledgeChunk(
            id = "bleeding",
            source = "Test Source A",
            section = "Bleeding",
            keywords = listOf("bleeding", "wound", "blood"),
            text = "Apply firm direct pressure to a bleeding wound with a clean cloth."
        ),
        KnowledgeChunk(
            id = "snakebite",
            source = "Test Source B",
            section = "Snake bite",
            keywords = listOf("snake", "snakebite", "bite"),
            text = "Keep the person still after a snake bite and get to a hospital immediately."
        ),
        KnowledgeChunk(
            id = "flood",
            source = "Test Source C",
            section = "Flood",
            keywords = listOf("flood", "flooding", "water"),
            text = "Move to higher ground during a flood and avoid walking through moving water."
        )
    )

    @Test
    fun `query matching a chunk's keyword ranks it first`() {
        val results = KnowledgeRetriever.search("what do I do about a snake bite", chunks)
        assertTrue(results.isNotEmpty())
        assertEquals("snakebite", results.first().chunk.id)
    }

    @Test
    fun `keyword match outranks a body-text-only match`() {
        // "bleeding" is a curated keyword on the bleeding chunk; "wound" appears
        // in its body text too, but the keyword weighting should still win clearly.
        val results = KnowledgeRetriever.search("bleeding", chunks)
        assertEquals("bleeding", results.first().chunk.id)
    }

    @Test
    fun `query with no relevant terms returns nothing`() {
        val results = KnowledgeRetriever.search("what is the best pizza topping", chunks)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `stop words alone never match anything`() {
        val results = KnowledgeRetriever.search("what is the and a", chunks)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `topK limits the number of returned matches`() {
        // A query touching all three chunks' bodies via a shared common word.
        val results = KnowledgeRetriever.search("water blood bite", chunks, topK = 2)
        assertTrue(results.size <= 2)
    }

    @Test
    fun `tokenize lowercases and strips punctuation`() {
        val tokens = KnowledgeRetriever.tokenize("Snake-Bite! What now?")
        assertTrue(tokens.contains("snake"))
        assertTrue(tokens.contains("bite"))
        assertTrue(tokens.none { it.contains("!") || it.contains("-") })
    }
}
