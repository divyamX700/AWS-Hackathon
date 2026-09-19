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
            text = "Apply firm direct pressure to a bleeding wound."
        )
    )

    private class FakeLlm(private val available: Boolean, private val response: String?) : LlmAssistant {
        override val isAvailable: Boolean = available
        override suspend fun generate(prompt: String): String? = response
    }

    /** Counts calls — for asserting [AssistantEngine.answer] never retries the on-device generation itself. */
    private class CountingFakeLlm(private val response: String?) : LlmAssistant {
        override val isAvailable: Boolean = true
        var callCount = 0
            private set
        override suspend fun generate(prompt: String): String? {
            callCount++
            return response
        }
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
    fun `query with no matches and no model returns the honest no-guidance fallback and no sources`() = runTest {
        val engine = AssistantEngine(chunks, llm = UnavailableLlmAssistant)
        val answer = engine.answer("what's the weather like on mars")

        assertFalse(answer.wasGenerated)
        assertTrue(answer.text.contains("112"))
        assertTrue(answer.sources.isEmpty())
    }

    @Test
    fun `query with no KB match but a model available still gets a real generated answer`() = runTest {
        // A real bug found by actually asking the app "hello": this used to
        // return FALLBACK_NO_MATCH unconditionally, so the model never ran
        // for anything outside the crisis knowledge base — plain
        // conversation was impossible even with a model side-loaded. See
        // AssistantEngine.answer's doc.
        val engine = AssistantEngine(chunks, llm = FakeLlm(available = true, response = "Hello! How can I help?"))
        val answer = engine.answer("hello")

        assertTrue(answer.wasGenerated)
        assertEquals("Hello! How can I help?", answer.text)
        assertTrue(answer.sources.isEmpty()) // never implies this came from the knowledge base
    }

    @Test
    fun `an Action line is parsed out of the answer text and into suggestedAction`() = runTest {
        val engine = AssistantEngine(
            chunks,
            llm = FakeLlm(available = true, response = "Press hard on the wound. Call 112.\nAction: BROADCAST_SAFE")
        )
        val answer = engine.answer("the bleeding stopped, what now")

        assertEquals(SuggestedAction.BROADCAST_SAFE, answer.suggestedAction)
        assertEquals("Press hard on the wound. Call 112.", answer.text) // Action line stripped, not shown to the user
    }

    @Test
    fun `a missing or malformed Action line defaults to NONE`() = runTest {
        val engine = AssistantEngine(chunks, llm = FakeLlm(available = true, response = "Press hard on the wound."))
        val answer = engine.answer("how do I stop bleeding")

        assertEquals(SuggestedAction.NONE, answer.suggestedAction)
    }

    @Test
    fun `a missing Action line does not trigger a retry generation`() = runTest {
        // MediaPipeLlmAssistant.generate() is already its own length-gated
        // retry loop (see its doc); AssistantEngine used to add a second,
        // outer retry here for a missing Action line, and a real-device
        // test found the two compounding into up to 4 total on-device
        // generations for one question (86s measured). This asserts that
        // regression stays fixed: exactly one call, ever, from here.
        val fakeLlm = CountingFakeLlm("1. Apply pressure.\n2. Keep applying pressure.") // no Action line
        val engine = AssistantEngine(chunks, llm = fakeLlm)

        val answer = engine.answer("the bleeding stopped, what now")

        assertEquals(1, fakeLlm.callCount)
        assertEquals(SuggestedAction.NONE, answer.suggestedAction)
    }

    @Test
    fun `a literal backslash-n in the model output is normalized to a real newline`() = runTest {
        val engine = AssistantEngine(
            chunks,
            llm = FakeLlm(available = true, response = "Action: NONE\nSituation: bleeding.\\n1. Apply pressure.")
        )
        val answer = engine.answer("how do I stop bleeding")

        assertFalse(answer.text.contains("\\n")) // no literal backslash-n left in the displayed text
        assertTrue(answer.text.contains("Situation: bleeding.\n1. Apply pressure.")) // became a real line break instead
    }

    @Test
    fun `a stray Action rule line echoed by the model is stripped from the displayed text`() = runTest {
        // Real-device generation: the model echoed the prompt's own
        // "Action rule:" heading as a visible answer line instead of the
        // required "Action:" format line, because both start with the
        // same word. The prompt now tells it not to, but this is a
        // defensive strip in case a small model slips anyway.
        val engine = AssistantEngine(
            chunks,
            llm = FakeLlm(available = true, response = "Action: NONE\n1. Apply pressure.\nAction rule: BROADCAST_SAFE")
        )
        val answer = engine.answer("the bleeding stopped, what now")

        assertFalse(answer.text.contains("Action rule"))
        assertEquals(SuggestedAction.NONE, answer.suggestedAction) // the real Action: line, not the stray one
    }
}
