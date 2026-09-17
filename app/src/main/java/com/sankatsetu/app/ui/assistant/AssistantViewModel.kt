package com.sankatsetu.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankatsetu.app.assistant.AssistantEngine
import com.sankatsetu.app.assistant.AssistantSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class AssistantTurn(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val answer: String? = null, // null while the answer is still being computed
    val sources: List<AssistantSource> = emptyList(),
    val wasGenerated: Boolean = false
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

        _uiState.value = _uiState.value.copy(
            turns = _uiState.value.turns + turn,
            isThinking = true
        )

        viewModelScope.launch {
            val result = engine.answer(question)
            val updatedTurns = _uiState.value.turns.map {
                if (it.id == turn.id) {
                    it.copy(answer = result.text, sources = result.sources, wasGenerated = result.wasGenerated)
                } else it
            }
            _uiState.value = _uiState.value.copy(turns = updatedTurns, isThinking = false)
        }
    }
}
