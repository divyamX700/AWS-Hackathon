package com.sankatsetu.app.assistant

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEngineTest {

    private val chunks = listOf(
        KnowledgeChunk(
            id = "bleeding",
            source = "Test Source",
            section = "Bleeding",
            keywords = listOf("bleeding", "wound"),
            text = "Apply firm direct pressure to a bleeding wound."
        )
    )

    private class FakeLlm(private val available: Boolean, private val response: String?) : LlmAssistant {
        override val isAvailable: Boolean = available
        override suspend fun generate(prompt: String): String? = response
    }

    @Test
    fun `no model available falls back to the extractive top match`() = runTest {
        val engine = AssistantEngine(chunks, llm = UnavailableLlmAssistant)
        val answer = engine.answer("how do I stop bleeding")

        assertFalse(answer.wasGenerated)
        assertTrue(answer.text.contains("firm direct pressure"))
        assertTrue(answer.text.contains("112")) // emergency number reminder appended
        assertEquals(1, answer.sources.size)
    }

    @Test
    fun `model available and generation succeeds returns the generated text`() = runTest {
        val engine = AssistantEngine(chunks, llm = FakeLlm(available = true, response = "Press hard on the wound. Call 112."))
        val answer = engine.answer("how do I stop bleeding")

        assertTrue(answer.wasGenerated)
        assertEquals("Press hard on the wound. Call 112.", answer.text)
        assertEquals(1, answer.sources.size) // sources still attached even to a generated answer
    }

    @Test
    fun `model available but generation fails still falls back to extractive`() = runTest {
        val engine = AssistantEngine(chunks, llm = FakeLlm(available = true, response = null))
        val answer = engine.answer("how do I stop bleeding")

        assertFalse(answer.wasGenerated)
        assertTrue(answer.text.contains("firm direct pressure"))
    }

    @Test
    fun `query with no matches returns the honest no-guidance fallback and no sources`() = runTest {
        val engine = AssistantEngine(chunks, llm = UnavailableLlmAssistant)
        val answer = engine.answer("what's the weather like on mars")

        assertFalse(answer.wasGenerated)
        assertTrue(answer.text.contains("112"))
        assertTrue(answer.sources.isEmpty())
    }
}
