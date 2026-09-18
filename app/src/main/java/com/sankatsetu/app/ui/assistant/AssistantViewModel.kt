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
    val suggestedAction: SuggestedAction = SuggestedAction.NONE,
    /** null = not drafted yet, "" while drafting, non-empty = the drafted message. See [AssistantViewModel.requestDraft]. */
    val draft: String? = null,
    val isDrafting: Boolean = false
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
        // Only completed, actually-generated answers count as history: a
        // still-pending or extractive-fallback answer isn't something the
        // model itself said, so it shouldn't be replayed back to it as if
        // it were.
        val history = _uiState.value.turns.mapNotNull { prior ->
            val answer = prior.answer
            if (answer != null && prior.wasGenerated) AssistantExchange(prior.question, answer) else null
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
     * The agent's second stage, run only on request (see AssistantEngine's
     * doc for why it's not folded into [ask]'s own call): drafts a short
     * message the person could send over the mesh, from a completed turn.
     */
    fun requestDraft(turnId: String) {
        val turn = _uiState.value.turns.find { it.id == turnId } ?: return
        val answer = turn.answer ?: return
        if (turn.isDrafting || turn.draft != null) return

        _uiState.value = _uiState.value.copy(
            turns = _uiState.value.turns.map { if (it.id == turnId) it.copy(isDrafting = true) else it }
        )

        viewModelScope.launch {
            val drafted = engine.draftShareableMessage(turn.question, answer) ?: turn.question
            _uiState.value = _uiState.value.copy(
                turns = _uiState.value.turns.map {
                    if (it.id == turnId) it.copy(isDrafting = false, draft = drafted) else it
                }
            )
        }
    }
}
