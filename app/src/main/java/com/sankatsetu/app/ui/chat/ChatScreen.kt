package com.sankatsetu.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankatsetu.app.ui.components.SignalBars
import com.sankatsetu.app.ui.components.StatusPill
import com.sankatsetu.app.ui.theme.ConsoleReadoutStyle
import com.sankatsetu.app.ui.theme.PillShape
import com.sankatsetu.app.ui.theme.SankatSetuColors
import com.sankatsetu.app.ui.theme.SankatSetuMotion
import com.sankatsetu.app.ui.theme.pressScale

/** Peer list — the mesh's front door. Tap a peer to open [ChatThreadScreen], long-press to forget a stale one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(viewModel: ChatViewModel, onOpenThread: (PeerUiModel) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var peerToForget by remember { mutableStateOf<PeerUiModel?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    // Re-read on recomposition after a rename, not cached in a StateFlow —
    // renames are rare and this avoids threading self-identity through the
    // combine() pipeline that drives peer list updates.
    var selfNickname by remember { mutableStateOf(viewModel.selfNickname()) }

    var imSafeSent by remember { mutableStateOf(false) }
    val readyPeerCount = state.peers.count { it.handshakeEstablished }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chat", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "You: $selfNickname",
                            style = ConsoleReadoutStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Change your name")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AnimatedVisibility(visible = !state.bluetoothOn, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 8.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp)
                ) {
                    Text(
                        "Bluetooth is off — turn it on to reach nearby phones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // The single most load-bearing pattern in the crisis-UX research
            // behind this screen (docs/PRODUCT.md, Evidence on Hand): one tap,
            // no typing, tells everyone in range you're okay. Full-width pill,
            // the app's one accent color, spring press + haptic feedback so
            // the single most important action in the app feels the most
            // deliberate one to press.
            val imSafeInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = { viewModel.broadcastImSafe(); imSafeSent = true },
                enabled = readyPeerCount > 0,
                interactionSource = imSafeInteraction,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = SankatSetuColors.StatusSafe, contentColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(52.dp)
                    .pressScale(imSafeInteraction)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (readyPeerCount > 0) "I'm safe — notify $readyPeerCount ${if (readyPeerCount == 1) "peer" else "peers"}" else "I'm safe — no peers in range yet",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            AnimatedVisibility(visible = imSafeSent, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "Sent to everyone currently reachable.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }

            if (state.peers.isEmpty()) {
                MeshSearchingCard(modifier = Modifier.padding(16.dp, 8.dp))
                Box(Modifier.weight(1f))
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { MeshActiveCard(peerCount = state.peers.size, readyCount = readyPeerCount) }
                    items(state.peers, key = { it.peerIdBase64 }) { peer ->
                        PeerRow(peer, onClick = { onOpenThread(peer) }, onLongPress = { peerToForget = peer })
                    }
                }
            }
        }
    }

    peerToForget?.let { peer ->
        AlertDialog(
            onDismissRequest = { peerToForget = null },
            title = { Text("Forget ${peer.nickname}?") },
            text = { Text("Removes this peer and its chat history from this phone only. If their phone is still nearby, it will reappear as a new entry.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.forgetPeer(peer.peerIdBase64)
                    peerToForget = null
                }) { Text("Forget") }
            },
            dismissButton = {
                TextButton(onClick = { peerToForget = null }) { Text("Cancel") }
            }
        )
    }

    if (showRenameDialog) {
        RenameDialog(
            currentNickname = selfNickname,
            deviceBluetoothName = viewModel.deviceBluetoothName(),
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.renameSelf(newName)
                selfNickname = newName
                showRenameDialog = false
            }
        )
    }
}

/**
 * The mesh's "actively working" moment when there's nothing to show yet —
 * a radar sweep (this app's own real metaphor: a radio listening for other
 * radios) inside a proper bordered panel, replacing a static icon floating
 * alone in empty space. This is the single biggest fix for the "feels like
 * a wireframe" problem a real-device review of the empty state surfaced:
 * a screen with nothing on it doesn't read as designed no matter how
 * refined its typography is. See docs/adr/0018-ui-revamp.md.
 */
@Composable
private fun MeshSearchingCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            com.sankatsetu.app.ui.components.MeshRadar(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
            Text("Searching for nearby phones", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Keep Bluetooth on and stay within about 30 metres of another phone running Sankat Setu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

/**
 * The mesh status dashboard once at least one peer exists — a real number,
 * not just a list, so the screen reads as "here's the state of your mesh"
 * before it reads as "here's a list of contacts." Sits above the peer rows
 * as the list's own header, per the bento-dashboard pattern this pass's
 * research recommended.
 */
@Composable
private fun MeshActiveCard(peerCount: Int, readyCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$peerCount",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (peerCount == 1) "phone in range" else "phones in range",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusPill("$readyCount READY", if (readyCount > 0) SankatSetuColors.StatusSafe else SankatSetuColors.OfflineGray)
                Spacer(Modifier.height(6.dp))
                com.sankatsetu.app.ui.components.MeshRadar(
                    color = MaterialTheme.colorScheme.primary,
                    diameter = 44.dp,
                    ringCount = 2
                )
            }
        }
    }
}

/**
 * The OS Bluetooth device name is offered as a one-tap suggestion, never
 * applied by default — a phone's Bluetooth name is very often someone's
 * real name, and this app shouldn't broadcast that to nearby strangers in
 * a disaster zone unless the person chooses to. See
 * docs/adr/0015-editable-nickname.md.
 */
@Composable
private fun RenameDialog(
    currentNickname: String,
    deviceBluetoothName: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var draft by remember { mutableStateOf(currentNickname) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name on the mesh") },
        text = {
            Column {
                Text(
                    "This is what nearby phones see instead of a random ID.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (deviceBluetoothName != null && deviceBluetoothName != draft) {
                    TextButton(onClick = { draft = deviceBluetoothName }) {
                        Text("Use this phone's Bluetooth name: \"$deviceBluetoothName\"")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (draft.isNotBlank()) onConfirm(draft.trim()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * A row reads like a channel roster entry, not a chat-app contact card:
 * signal bars carry connection quality at a glance (radio-relay language,
 * matching what hop count already means physically), the status pill runs
 * in the console monospace register. Hairline border + surfaceContainer
 * fill (not a Material default elevated Card) reads as the "precision
 * instrument" density this pass's research recommends over heavier
 * drop-shadow elevation. See docs/adr/0018-ui-revamp.md.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PeerRow(peer: PeerUiModel, onClick: () -> Unit, onLongPress: () -> Unit) {
    val (bars, tint, statusWord) = when {
        !peer.connected -> Triple(0, SankatSetuColors.OfflineGray, "OFFLINE")
        peer.handshakeEstablished -> Triple((5 - peer.hopCount.coerceIn(1, 4)), SankatSetuColors.StatusSafe, if (peer.hopCount <= 1) "1 HOP · READY" else "${peer.hopCount} HOPS · RELAYED")
        else -> Triple(1, SankatSetuColors.StatusCaution, "CONNECTING")
    }
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick, onLongClick = onLongPress),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignalBars(filledBars = bars, tint = tint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.nickname, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                StatusPill(statusWord, tint)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(viewModel: ChatViewModel, peer: PeerUiModel, onBack: () -> Unit) {
    val messages by viewModel.threadMessages(peer.peerIdBase64).collectAsState(initial = emptyList())
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(peer.peerIdBase64) { viewModel.onThreadOpened(peer.peerIdBase64) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(peer.nickname, style = MaterialTheme.typography.titleLarge)
                        StatusPill(
                            if (peer.connected) "ONLINE" else "DISCONNECTED",
                            if (peer.connected) SankatSetuColors.StatusSafe else SankatSetuColors.OfflineGray
                        )
                    }
                },
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(
                        text = message.body,
                        isOutgoing = message.isOutgoing,
                        hopCount = message.hopCount,
                        status = message.status,
                        sentAt = message.sentAt
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                    placeholder = { Text(if (peer.handshakeEstablished) "Message…" else "Waiting for secure link…") },
                    enabled = peer.handshakeEstablished
                )
                Spacer(Modifier.width(8.dp))
                val sendInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            viewModel.sendMessage(peer.peerIdBase64, draft.trim())
                            draft = ""
                        }
                    },
                    interactionSource = sendInteraction,
                    enabled = peer.handshakeEstablished && draft.isNotBlank(),
                    modifier = Modifier
                        .pressScale(sendInteraction)
                        .clip(PillShape)
                        .background(if (draft.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(text: String, isOutgoing: Boolean, hopCount: Int, status: String, sentAt: Long) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = if (isOutgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isOutgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                if (!isOutgoing && hopCount > 1) {
                    Text(
                        "via $hopCount hops",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(
                        formatMessageTime(sentAt),
                        style = ConsoleReadoutStyle.copy(fontSize = 10.sp),
                        color = if (isOutgoing) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOutgoing) {
                        Spacer(Modifier.width(4.dp))
                        MessageStatusGlyph(status)
                    }
                }
            }
        }
    }
}

private val messageTimeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
private fun formatMessageTime(epochMs: Long): String = messageTimeFormat.format(java.util.Date(epochMs))

/**
 * WhatsApp-style delivery ticks: single grey = left this phone but not yet
 * confirmed, double grey = the peer's app decrypted it, double blue = the
 * peer opened the thread and saw it. "queued" gets a clock instead of a
 * tick since it never actually reached a radio yet — see ChatViewModel's
 * EnvelopeKind/onThreadOpened for how each transition fires.
 */
@Composable
private fun MessageStatusGlyph(status: String) {
    val onPrimaryMuted = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    val (glyph, color) = when (status) {
        "queued" -> "waiting…" to onPrimaryMuted
        "sending" -> "sending…" to onPrimaryMuted
        "sent" -> "✓" to onPrimaryMuted
        "delivered" -> "✓✓" to onPrimaryMuted
        "read" -> "✓✓" to SankatSetuColors.ReadBlue
        else -> "" to onPrimaryMuted
    }
    if (glyph.isNotEmpty()) {
        Text(glyph, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
