package com.sankatsetu.app.ui.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Payments
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.sankatsetu.app.assistant.KnowledgeChunk
import com.sankatsetu.app.assistant.SuggestedAction

/** Which layer of the Docs browser is showing, if any — see [DocsBrowser]. Back (system or app bar) steps down one level instead of leaving the tab. */
private sealed class DocsView {
    data object Closed : DocsView()
    data object List : DocsView()
    data class Reading(val source: String) : DocsView()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    knowledgeBase: List<KnowledgeChunk>,
    onBroadcastSafe: () -> Unit,
    onOpenPay: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var draft by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    var docsView by remember { mutableStateOf<DocsView>(DocsView.Closed) }

    BackHandler(enabled = docsView is DocsView.Reading) { docsView = DocsView.List }
    BackHandler(enabled = docsView is DocsView.List) { docsView = DocsView.Closed }

    if (docsView != DocsView.Closed) {
        DocsBrowser(
            knowledgeBase = knowledgeBase,
            view = docsView,
            onOpen = { source -> docsView = DocsView.Reading(source) },
            onBack = { docsView = if (docsView is DocsView.Reading) DocsView.List else DocsView.Closed }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assistant") },
                actions = {
                    IconButton(onClick = { docsView = DocsView.List }) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Browse offline guides")
                    }
                }
            )
        }
    ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
        if (state.turns.isEmpty()) {
            // weight(1f), not fillMaxSize(): a fillMaxSize() sibling claims
            // the whole Column's height, leaving nothing for the input Row
            // below it — real-device screenshot found this pushing the
            // question field and send button off the bottom of the screen
            // entirely, hidden under the app's own nav bar.
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
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
                items(state.turns, key = { it.id }) { turn ->
                    TurnCard(
                        turn = turn,
                        onBroadcastSafe = onBroadcastSafe,
                        onOpenPay = onOpenPay,
                        onDraftRequest = { viewModel.requestDraft(turn.id) }
                    )
                }
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
                placeholder = { Text("Ask something…") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = {
                        if (draft.isNotBlank()) {
                            viewModel.ask(draft.trim())
                            draft = ""
                        }
                        keyboardController?.hide()
                    }
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        viewModel.ask(draft.trim())
                        draft = ""
                    }
                    keyboardController?.hide()
                },
                enabled = draft.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
            }
        }
    }
    }
}

/**
 * [turn.suggestedAction] and the draft-message flow are the agent's two
 * further stages beyond a plain answer — see
 * docs/adr/0016-on-device-agent-architecture.md. Neither ever fires on its
 * own: an action chip only calls [onBroadcastSafe]/[onOpenPay] on an
 * explicit tap, and a draft is only generated when the person asks for one.
 */
@Composable
private fun TurnCard(
    turn: AssistantTurn,
    onBroadcastSafe: () -> Unit,
    onOpenPay: () -> Unit,
    onDraftRequest: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth()) {
        // The question, right-aligned like an outgoing chat bubble.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(turn.question, Modifier.padding(10.dp))
            }
        }
        // height, not width — this is a vertical gap in a Column; the
        // previous width-only Spacer had zero height and did nothing,
        // leaving the question and answer bubbles visually touching.
        Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(6.dp))
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

                    if (turn.wasGenerated && turn.suggestedAction != SuggestedAction.NONE) {
                        Spacer(Modifier.height(8.dp))
                        when (turn.suggestedAction) {
                            SuggestedAction.BROADCAST_SAFE -> AssistChip(
                                onClick = onBroadcastSafe,
                                label = { Text("Broadcast \"I'm safe\" now") },
                                leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            SuggestedAction.OPEN_PAY -> AssistChip(
                                onClick = onOpenPay,
                                label = { Text("Open Pay tab") },
                                leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            SuggestedAction.NONE -> Unit
                        }
                    }

                    if (turn.wasGenerated) {
                        Spacer(Modifier.height(4.dp))
                        when {
                            turn.draft != null -> DraftedMessageCard(draft = turn.draft, onCopy = { clipboard.setText(AnnotatedString(turn.draft)) })
                            turn.isDrafting -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(14.dp).padding(end = 6.dp))
                                Text("Drafting a message to share…", style = MaterialTheme.typography.labelSmall)
                            }
                            else -> TextButton(onClick = onDraftRequest, contentPadding = PaddingValues(0.dp)) {
                                Text("Draft a message to share")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The agent's drafted message, with a one-tap copy — no auto-send; the person decides where it goes (paste into any Chat thread). */
@Composable
private fun DraftedMessageCard(draft: String, onCopy: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(draft, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy message")
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

/**
 * Lets the person read the offline knowledge base directly, not just
 * through a generated answer — the same guides the Assistant already
 * grounds its answers in, browsable on their own. Two levels: a list of
 * documents ([DocsView.List]), then one document's sections stacked and
 * scrollable ([DocsView.Reading]). Back (system, gesture, or the app bar's
 * arrow) steps down one level via the [BackHandler]s in [AssistantScreen],
 * never straight out of the tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocsBrowser(
    knowledgeBase: List<KnowledgeChunk>,
    view: DocsView,
    onOpen: (String) -> Unit,
    onBack: () -> Unit
) {
    // Preserves each document's on-disk order (chunk id embeds a
    // sequential index — see KnowledgeDocumentParser) and the order
    // documents were first seen, rather than re-sorting alphabetically.
    val bySource = remember(knowledgeBase) { knowledgeBase.groupBy { it.source } }
    val titles = remember(bySource) { bySource.keys.toList() }
    val title = when (view) {
        is DocsView.Reading -> view.source
        else -> "Offline Guides"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (view) {
            is DocsView.Reading -> {
                val sections = bySource[view.source].orEmpty()
                LazyColumn(
                    Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(sections, key = { it.id }) { chunk ->
                        Column {
                            Text(chunk.section, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(6.dp))
                            Text(chunk.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(titles) { source ->
                        val sectionCount = bySource[source]?.size ?: 0
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { onOpen(source) })
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(source, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "$sectionCount ${if (sectionCount == 1) "section" else "sections"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
