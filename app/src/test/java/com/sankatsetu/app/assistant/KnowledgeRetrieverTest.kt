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
            text = "Apply firm direct pressure to a bleeding wound with a clean cloth."
        ),
        KnowledgeChunk(
            id = "snakebite",
            source = "Test Source B",
            section = "Snake bite",
            text = "Keep the person still after a snake bite and get to a hospital immediately. " +
                "Do not cut the snake bite wound or try to suck out venom."
        ),
        KnowledgeChunk(
            id = "flood",
            source = "Test Source C",
            section = "Flood",
            text = "Move to higher ground during a flood and avoid walking through moving flood water."
        )
    )

    @Test
    fun `query strongly matching one chunk's distinctive terms ranks it first`() {
        val results = KnowledgeRetriever.search("what do I do about a snake bite", chunks)
        assertTrue(results.isNotEmpty())
        assertEquals("snakebite", results.first().chunk.id)
    }

    @Test
    fun `a term repeated in only one chunk outranks a single shared-word match`() {
        // "bleeding" appears in the bleeding chunk's body; BM25's term-frequency
        // and IDF weighting alone (no curated keywords needed) should surface it.
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
        // A query touching all three chunks' bodies via shared common words.
        val results = KnowledgeRetriever.search("water wound bite", chunks, topK = 2)
        assertTrue(results.size <= 2)
    }

    @Test
    fun `results are sorted highest score first`() {
        val results = KnowledgeRetriever.search("flood water bite wound", chunks, topK = 3)
        val scores = results.map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }

    @Test
    fun `tokenize lowercases and strips punctuation`() {
        val tokens = KnowledgeRetriever.tokenize("Snake-Bite! What now?")
        assertTrue(tokens.contains("snake"))
        assertTrue(tokens.contains("bite"))
        assertTrue(tokens.none { it.contains("!") || it.contains("-") })
    }
}
