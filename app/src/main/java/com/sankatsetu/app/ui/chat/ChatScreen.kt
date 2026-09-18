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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import com.sankatsetu.app.ui.theme.SankatSetuColors

/** Peer list — the mesh's front door. Tap a peer to open [ChatThreadScreen], long-press to forget a stale one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(viewModel: ChatViewModel, onOpenThread: (PeerUiModel) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var peerToForget by remember { mutableStateOf<PeerUiModel?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Chat") }) }) { padding ->
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

            if (state.peers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Looking for nearby phones…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PeerRow(peer: PeerUiModel, onClick: () -> Unit, onLongPress: () -> Unit) {
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
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        color = when {
                            !peer.connected -> SankatSetuColors.OfflineGray
                            peer.handshakeEstablished -> SankatSetuColors.SafeGreen
                            else -> SankatSetuColors.CautionAmber
                        },
                        shape = CircleShape
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.nickname, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        !peer.connected -> "Disconnected"
                        peer.handshakeEstablished -> "Ready to chat"
                        else -> "Connecting…"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HopBadge(peer.hopCount)
        }
    }
}

@Composable
private fun HopBadge(hopCount: Int) {
    Box(
        Modifier
            .background(SankatSetuColors.HopBadgeBackground, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            if (hopCount <= 1) "1 hop" else "$hopCount hops",
            style = MaterialTheme.typography.labelSmall
        )
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
                            color = if (peer.connected) SankatSetuColors.SafeGreen else SankatSetuColors.OfflineGray
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
                        status = message.status
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
private fun MessageBubble(text: String, isOutgoing: Boolean, hopCount: Int, status: String) {
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
                if (isOutgoing) {
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        MessageStatusGlyph(status)
                    }
                }
            }
        }
    }
}

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
