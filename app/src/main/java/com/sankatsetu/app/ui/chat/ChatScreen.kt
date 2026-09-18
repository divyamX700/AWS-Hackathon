package com.sankatsetu.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankatsetu.app.ui.components.SignalBars
import com.sankatsetu.app.ui.theme.ConsoleReadoutStyle
import com.sankatsetu.app.ui.theme.SankatSetuColors

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
                        Text("Chat")
                        Text(
                            "You: $selfNickname",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Change your name")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.bluetoothOn) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp)
                ) {
                    Text("Bluetooth is off — turn it on to reach nearby phones")
                }
            }

            // The single most load-bearing pattern in the crisis-UX research
            // behind this screen (docs/PRODUCT.md, Evidence on Hand): one tap,
            // no typing, tells everyone in range you're okay. It stays visible
            // whether or not anyone is connected yet — the reassurance of
            // having tried matters even before a peer is in range.
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.broadcastImSafe(); imSafeSent = true },
                    enabled = readyPeerCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (readyPeerCount > 0) "I'm safe — notify $readyPeerCount ${if (readyPeerCount == 1) "peer" else "peers"}" else "I'm safe — no peers in range yet")
                }
            }
            if (imSafeSent) {
                Text(
                    "Sent to everyone currently reachable.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            if (state.peers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SignalBars(filledBars = 0, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Scanning for nearby phones…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
 * matching what hop count already means physically), the status line runs
 * in the console monospace register. See
 * docs/adr/0014-field-radio-design-language.md.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PeerRow(peer: PeerUiModel, onClick: () -> Unit, onLongPress: () -> Unit) {
    val (bars, tint, statusWord) = when {
        !peer.connected -> Triple(0, SankatSetuColors.OfflineGray, "OFFLINE")
        peer.handshakeEstablished -> Triple((5 - peer.hopCount.coerceIn(1, 4)), MaterialTheme.colorScheme.primary, if (peer.hopCount <= 1) "1 HOP · READY" else "${peer.hopCount} HOPS · RELAYED")
        else -> Triple(1, SankatSetuColors.ImdYellow, "CONNECTING")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignalBars(filledBars = bars, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.nickname, style = MaterialTheme.typography.bodyLarge)
                Text(statusWord, style = ConsoleReadoutStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(peer.nickname)
                        Text(
                            if (peer.connected) "online" else "disconnected",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (peer.connected) SankatSetuColors.ImdGreen else SankatSetuColors.OfflineGray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (peer.handshakeEstablished) "Message…" else "Waiting for secure link…") },
                    enabled = peer.handshakeEstablished
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            viewModel.sendMessage(peer.peerIdBase64, draft.trim())
                            draft = ""
                        }
                    },
                    enabled = peer.handshakeEstablished && draft.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
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
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(text)
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
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                ) {
                    Text(
                        formatMessageTime(sentAt),
                        style = ConsoleReadoutStyle.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val (glyph, color) = when (status) {
        "queued" -> "waiting…" to MaterialTheme.colorScheme.onSurfaceVariant
        "sending" -> "sending…" to MaterialTheme.colorScheme.onSurfaceVariant
        "sent" -> "✓" to MaterialTheme.colorScheme.onSurfaceVariant
        "delivered" -> "✓✓" to MaterialTheme.colorScheme.onSurfaceVariant
        "read" -> "✓✓" to SankatSetuColors.ReadBlue
        else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    if (glyph.isNotEmpty()) {
        Text(glyph, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
