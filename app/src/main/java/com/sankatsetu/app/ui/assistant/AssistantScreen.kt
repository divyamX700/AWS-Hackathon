package com.sankatsetu.app.ui.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.sankatsetu.app.assistant.KnowledgeChunk
import com.sankatsetu.app.assistant.SuggestedAction
import com.sankatsetu.app.ui.theme.pressScale
import com.sankatsetu.app.ui.theme.rememberShimmerProgress

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
                title = { Text("Assistant", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { docsView = DocsView.List }) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Browse offline guides")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Ask about first aid, evacuation, or what to do in an emergency — works completely offline.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
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
            val sendInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        viewModel.ask(draft.trim())
                        draft = ""
                    }
                    keyboardController?.hide()
                },
                interactionSource = sendInteraction,
                enabled = draft.isNotBlank(),
                modifier = Modifier
                    .pressScale(sendInteraction)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(if (draft.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Ask",
                    tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    turn.question,
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        turn.answer?.let { answer ->
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(answer, style = MaterialTheme.typography.bodyLarge)
                    // Retrieved source passages (turn.sources) are what the
                    // answer is grounded in, but not shown here anymore —
                    // the per-source chip list read as clutter at the end of
                    // every answer. The data is still there on AssistantTurn
                    // if a future UI (e.g. a "why this answer" expandable)
                    // wants it.
                    Spacer(Modifier.height(8.dp))
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
                        Spacer(Modifier.height(10.dp))
                        when (turn.suggestedAction) {
                            SuggestedAction.BROADCAST_SAFE -> AssistChip(
                                onClick = onBroadcastSafe,
                                label = { Text("Broadcast \"I'm safe\" now") },
                                leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                shape = MaterialTheme.shapes.small,
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                            SuggestedAction.OPEN_PAY -> AssistChip(
                                onClick = onOpenPay,
                                label = { Text("Open Pay tab") },
                                leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                shape = MaterialTheme.shapes.small,
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                            SuggestedAction.NONE -> Unit
                        }
                    }

                    if (turn.wasGenerated) {
                        Spacer(Modifier.height(6.dp))
                        when {
                            turn.draft != null -> DraftedMessageCard(draft = turn.draft, onCopy = { clipboard.setText(AnnotatedString(turn.draft)) })
                            turn.isDrafting -> Row(verticalAlignment = Alignment.CenterVertically) {
                                ShimmerLine(modifier = Modifier.width(140.dp).height(14.dp))
                            }
                            else -> TextButton(onClick = onDraftRequest, contentPadding = PaddingValues(0.dp)) {
                                Text("Draft a message to share", style = MaterialTheme.typography.labelLarge)
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
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(draft, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy message", tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

/** A moving shimmer bar — replaces the plain spinner+"Thinking…" row with something that reads as active work happening, not a stalled app. */
@Composable
private fun ThinkingIndicator() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShimmerLine(modifier = Modifier.fillMaxWidth().height(16.dp))
        ShimmerLine(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
    }
}

@Composable
private fun ShimmerLine(modifier: Modifier = Modifier) {
    val progress = rememberShimmerProgress()
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(progress * 600f - 300f, 0f),
                    end = Offset(progress * 600f, 0f)
                )
            )
    ) {}
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
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                            Text(chunk.section, style = MaterialTheme.typography.headlineSmall)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(titles) { source ->
                        val sectionCount = bySource[source]?.size ?: 0
                        val interaction = remember { MutableInteractionSource() }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressScale(interaction)
                                .clickable(interactionSource = interaction, indication = null, onClick = { onOpen(source) }),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(source, style = MaterialTheme.typography.titleMedium)
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
