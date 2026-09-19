package com.sankatsetu.app.ui.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankatsetu.app.data.SosEntity
import com.sankatsetu.app.mesh.emergency.SosManager
import com.sankatsetu.app.mesh.protocol.SosCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Backs the SOS surface on the Chat tab — see docs/TODO.md's ideation and
 * [SosManager]'s doc for the reasoning. [uiState] doubles as both the
 * reviewable log and the source for the interrupting alert
 * ([SosUiState.latestUnacknowledgedIncoming]): more than one SOS can be
 * in flight at once, so "the interrupt" and "the log" are the same list,
 * not two separate pieces of state that could drift apart.
 */
data class SosUiState(val alerts: List<SosEntity> = emptyList()) {
    /** Drives the full-screen interrupt — the most recent incoming alert nobody has dismissed yet. Never an outgoing (own) report. */
    val latestUnacknowledgedIncoming: SosEntity?
        get() = alerts.filter { !it.isOutgoing && !it.acknowledged }.maxByOrNull { it.receivedAt }
}

class SosViewModel(private val sosManager: SosManager) : ViewModel() {
    private val _uiState = MutableStateFlow(SosUiState())
    val uiState: StateFlow<SosUiState> = _uiState

    init {
        viewModelScope.launch {
            sosManager.observeAll().collect { alerts -> _uiState.value = SosUiState(alerts) }
        }
    }

    fun sendSos(category: SosCategory) {
        viewModelScope.launch { sosManager.broadcastSos(category) }
    }

    fun acknowledge(sosId: String) {
        viewModelScope.launch { sosManager.acknowledge(sosId) }
    }
}
