package com.sankatsetu.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankatsetu.app.assistant.AssistantEngine
import com.sankatsetu.app.assistant.AssistantExchange
import com.sankatsetu.app.assistant.AssistantSource
import com.sankatsetu.app.assistant.SuggestedAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class AssistantTurn(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val answer: String? = null, // null while the answer is still being computed
    val sources: List<AssistantSource> = emptyList(),
    val wasGenerated: Boolean = false,
    val suggestedAction: SuggestedAction = SuggestedAction.NONE
)

data class AssistantUiState(
    val turns: List<AssistantTurn> = emptyList(),
    val isThinking: Boolean = false
)

/**
 * Entirely offline Q&A — [AssistantEngine] never touches the network. See
 * docs/adr/0009-on-device-assistant-scope.md for what's a genuine retrieval
 * algorithm today vs. what Day 3 upgrades (neural embeddings, a larger
 * corpus, on-device generation once a model is actually side-loaded).
 */
class AssistantViewModel(private val engine: AssistantEngine) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState

    fun ask(question: String) {
        if (question.isBlank()) return
        val turn = AssistantTurn(question = question)

        // Snapshot prior turns as history *before* appending the new one —
        // this is what makes a follow-up like "what about for a child?"
        // resolve against the actual preceding exchange instead of the
        // model seeing each question cold, per the multi-turn requirement.
        // Only a completed, KB-grounded answer counts as history (sources
        // non-empty) — a still-pending or extractive-fallback answer isn't
        // something the model itself said, and a plain-chat answer (no KB
        // match, see AssistantEngine.answer's general-conversation branch)
        // isn't part of *this* crisis conversation at all. A real latency
        // bug found by actually asking "hello" then a real question right
        // after: before this filter, "hello" — a real generation, so it
        // passed the old wasGenerated-only check — got threaded into the
        // next prompt's "Conversation so far" block as irrelevant history,
        // bloating the prompt against the model's shared 1280-token prompt+
        // output budget and pushing it toward the rambling/retry failure
        // mode already documented in MediaPipeLlmAssistant's own comments.
        val history = _uiState.value.turns.mapNotNull { prior ->
            val answer = prior.answer
            if (answer != null && prior.wasGenerated && prior.sources.isNotEmpty()) {
                AssistantExchange(prior.question, answer)
            } else {
                null
            }
        }

        _uiState.value = _uiState.value.copy(
            turns = _uiState.value.turns + turn,
            isThinking = true
        )

        viewModelScope.launch {
            val result = engine.answer(question, history)
            val updatedTurns = _uiState.value.turns.map {
                if (it.id == turn.id) {
                    it.copy(
                        answer = result.text,
                        sources = result.sources,
                        wasGenerated = result.wasGenerated,
                        suggestedAction = result.suggestedAction
                    )
                } else it
            }
            _uiState.value = _uiState.value.copy(turns = updatedTurns, isThinking = false)
        }
    }

    /**
     * Clears the visible conversation and, just as importantly, the model's
     * own memory of it: [ask] builds [AssistantEngine.answer]'s `history`
     * argument from `_uiState.value.turns` on every call, so an emptied
     * turns list is the entire mechanism — there's no separate context
     * object to reset elsewhere. Turns were never persisted to a database
     * in the first place (a restart already loses them); this just lets the
     * person do it deliberately, mid-session, without restarting the app.
     */
    fun clearConversation() {
        _uiState.value = AssistantUiState()
    }
}
