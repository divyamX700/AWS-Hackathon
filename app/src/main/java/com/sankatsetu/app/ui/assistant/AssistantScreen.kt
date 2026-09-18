package com.sankatsetu.app.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(viewModel: AssistantViewModel) {
    val state by viewModel.uiState.collectAsState()
    var draft by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Assistant") }) }) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
        if (state.turns.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Ask about first aid, evacuation, or what to do in an emergency — works completely offline.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.turns, key = { it.id }) { turn -> TurnCard(turn) }
                if (state.isThinking) {
                    item { ThinkingIndicator() }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask something…") }
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        viewModel.ask(draft.trim())
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
            }
        }
    }
    }
}

@Composable
private fun TurnCard(turn: AssistantTurn) {
    Column(Modifier.fillMaxWidth()) {
        // The question, right-aligned like an outgoing chat bubble.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(turn.question, Modifier.padding(10.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        turn.answer?.let { answer ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(answer, style = MaterialTheme.typography.bodyLarge)
                    // Retrieved source passages (turn.sources) are what the
                    // answer is grounded in, but not shown here anymore —
                    // the per-source "📖 doc — section" chip list read as
                    // clutter at the end of every answer. The data is still
                    // there on AssistantTurn if a future UI (e.g. a "why
                    // this answer" expandable) wants it.
                    //
                    // Generated vs. excerpt is marked with a small leading
                    // icon rather than a colored border-left — craft-floor
                    // guidance bans that pattern as a decorative habit, and
                    // an icon plus label reads clearly without it.
                    Spacer(Modifier.padding(top = 2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (turn.wasGenerated) Icons.Filled.AutoAwesome else Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp).size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (turn.wasGenerated) "Generated on-device" else "Direct excerpt — on-device AI unavailable right now",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.padding(end = 8.dp))
        Text("Thinking…", style = MaterialTheme.typography.labelSmall)
    }
}
