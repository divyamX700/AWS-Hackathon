package com.sankatsetu.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeDocumentParserTest {

    @Test
    fun `parses title, source and sections into separate chunks`() {
        val doc = """
            |# Snake Bite Response
            |Source: WHO snakebite envenoming guidance (authored summary)
            |
            |## Immediate Steps
            |Keep the person calm and still. Remove tight clothing or jewellery near the bite.
            |
            |## What Not To Do
            |Do not cut the wound, apply a tourniquet, or attempt to suck out venom.
        """.trimMargin()

        val chunks = KnowledgeDocumentParser.parse("snake_bite", doc)

        assertEquals(2, chunks.size)
        assertEquals("Snake Bite Response", chunks[0].source)
        assertEquals("Immediate Steps", chunks[0].section)
        assertTrue(chunks[0].text.contains("calm and still"))
        assertEquals("What Not To Do", chunks[1].section)
        assertTrue(chunks[1].text.contains("tourniquet"))
    }

    @Test
    fun `missing title falls back to the file name as source`() {
        val doc = """
            |## Only Section
            |Some body text here.
        """.trimMargin()

        val chunks = KnowledgeDocumentParser.parse("untitled_doc", doc)

        assertEquals(1, chunks.size)
        assertEquals("untitled_doc", chunks[0].source)
    }

    @Test
    fun `empty sections produce no chunk`() {
        val doc = """
            |# Title
            |
            |## Empty Section
            |
            |## Real Section
            |Actual content.
        """.trimMargin()

        val chunks = KnowledgeDocumentParser.parse("doc", doc)

        assertEquals(1, chunks.size)
        assertEquals("Real Section", chunks[0].section)
    }

    @Test
    fun `chunk ids are unique and stable within a document`() {
        val doc = """
            |# Title
            |
            |## First
            |a
            |
            |## Second
            |b
        """.trimMargin()

        val chunks = KnowledgeDocumentParser.parse("doc", doc)

        assertEquals(listOf("doc#0", "doc#1"), chunks.map { it.id })
    }
}
