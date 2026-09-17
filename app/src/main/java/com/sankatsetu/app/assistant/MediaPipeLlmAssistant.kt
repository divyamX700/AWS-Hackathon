package com.sankatsetu.app.assistant

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wraps MediaPipe's LLM Inference API around a side-loaded Gemma model file.
 * The model itself is never bundled in the APK — see
 * docs/adr/0005-model-assets-not-committed.md — so [isAvailable] genuinely
 * reflects whether this specific device has one at [modelPath] right now,
 * and every failure mode (missing file, OOM, unsupported device, corrupt
 * model) degrades to "unavailable" rather than crashing the Assistant tab.
 *
 * Model file expected at (see README.md's "Running it" for the adb push
 * command): `/sdcard/Android/data/com.sankatsetu.app/files/models/gemma-3-1b-it-int4.task`
 */
class MediaPipeLlmAssistant(
    private val context: Context,
    private val modelPath: String
) : LlmAssistant {

    @Volatile
    private var inference: LlmInference? = null
    @Volatile
    private var initFailed = false

    override val isAvailable: Boolean
        get() = !initFailed && File(modelPath).exists()

    override suspend fun generate(prompt: String): String? {
        if (!isAvailable) return null
        return withContext(Dispatchers.Default) {
            try {
                val engine = inference ?: createEngine() ?: return@withContext null
                engine.generateResponse(prompt)
            } catch (e: Exception) {
                // Model files can fail to load or run for reasons entirely out
                // of our control (device RAM, a corrupted side-loaded file,
                // an unsupported chip) — never let that crash the Assistant
                // tab. It should just fall back to KnowledgeRetriever's
                // extractive mode, same as if the file weren't there at all.
                Log.w(TAG, "LLM generation failed, falling back to extractive mode", e)
                initFailed = true
                null
            }
        }
    }

    @Synchronized
    private fun createEngine(): LlmInference? {
        inference?.let { return it }
        if (initFailed) return null
        return try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setTopK(40)
                .setTemperature(0.7f)
                .build()
            LlmInference.createFromOptions(context, options).also { inference = it }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize MediaPipe LlmInference", e)
            initFailed = true
            null
        }
    }

    fun close() {
        inference?.close()
        inference = null
    }

    companion object {
        private const val TAG = "MediaPipeLlmAssistant"

        fun defaultModelPath(context: Context): String =
            File(context.getExternalFilesDir("models"), "gemma-3-1b-it-int4.task").absolutePath
    }
}
