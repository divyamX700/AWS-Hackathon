package com.sankatsetu.app.ui.pay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sankatsetu.app.data.IouEntity
import com.sankatsetu.app.payments.UssdDialer
import com.sankatsetu.app.ui.components.StampMark
import com.sankatsetu.app.ui.components.StatusPill
import com.sankatsetu.app.ui.components.counterfoilEdge
import com.sankatsetu.app.ui.theme.SankatSetuColors
import com.sankatsetu.app.ui.theme.pressScale

/**
 * The Pay tab (docs/PRD.md §F3/F4): two cards that open the system dialer
 * pre-filled with a USSD/IVR code for a real UPI payment (the person must
 * tap call themselves — see `UssdDialer.kt`), a composer for a mesh IOU
 * voucher, and the IOU list grouped by direction/status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayScreen(viewModel: PayViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showComposer by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pay", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
    LazyColumn(
        Modifier.fillMaxWidth().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "This works with no internet. USSD and IVR use your SIM's voice channel, and mesh IOUs travel over Bluetooth.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // The core, primary ledger entries: real UPI money movement. These
        // lead the page. The mesh IOU below is real but secondary, one
        // entry type in the register, not a competing headline action.
        item { LedgerSectionHeader("Pay now") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactActionButton(
                    title = "USSD *99#",
                    modifier = Modifier.weight(1f),
                    onClick = { UssdDialer.openDialer(context, "*99#") }
                )
                CompactActionButton(
                    title = "UPI 123Pay",
                    modifier = Modifier.weight(1f),
                    onClick = { UssdDialer.openDialer(context, "*99#") }
                )
            }
        }

        item { LedgerSectionHeader("Mesh IOU") }
        item {
            PayActionCard(
                title = "Send Mesh IOU",
                subtitle = "A signed promise to pay, not an actual transfer. It goes to a nearby phone over Bluetooth, with no bank and no internet needed, and gets settled later.",
                onClick = { showComposer = !showComposer }
            )
        }

        if (showComposer) {
            item {
                IouComposer(
                    peers = state.peers,
                    onSend = { peerId, nickname, amountPaise, memo ->
                        viewModel.sendIou(peerId, nickname, amountPaise, memo)
                        showComposer = false
                    }
                )
            }
        }

        item { IouSectionHeader("Owed to you") }
        if (state.owedToMe.isEmpty()) {
            item { EmptySectionText("Nothing owed to you yet.") }
        } else {
            items(state.owedToMe, key = { it.iouId }) { iou -> IouCard(iou) }
        }

        item { IouSectionHeader("You owe") }
        if (state.iOwe.isEmpty()) {
            item { EmptySectionText("You don't owe anything right now.") }
        } else {
            items(state.iOwe, key = { it.iouId }) { iou ->
                IouCard(iou, onMarkSettled = { viewModel.markSettled(iou.iouId) })
            }
        }

        item { IouSectionHeader("Settled") }
        if (state.settled.isEmpty()) {
            item { EmptySectionText("No settled IOUs yet.") }
        } else {
            items(state.settled, key = { it.iouId }) { iou -> IouCard(iou) }
        }
    }
    }
}

@Composable
private fun LedgerSectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = com.sankatsetu.app.ui.theme.ConsoleReadoutStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun CompactActionButton(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp).pressScale(interaction),
        interactionSource = interaction,
        shape = MaterialTheme.shapes.small
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * A torn-counterfoil dashed edge, not a solid border — this card is the
 * IOU composer trigger, and the IOU is a pending promise by definition, so
 * even the entry point into it carries the "not yet settled" mark. See
 * docs/adr/0019-ledger-register-redesign.md.
 */
@Composable
private fun PayActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .counterfoilEdge(SankatSetuColors.StatusCaution.copy(alpha = 0.6f), cornerRadius = 6.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = null
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IouComposer(
    peers: List<PeerPickerEntry>,
    onSend: (peerIdBase64: String, nickname: String, amountPaise: Long, memo: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<PeerPickerEntry?>(null) }
    var amountRupees by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("New mesh IOU", style = MaterialTheme.typography.titleLarge)

            if (peers.isEmpty()) {
                Text(
                    "No nearby phones yet. Open the Chat tab first so a peer shows up here.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selected?.nickname ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pay to") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        peers.forEach { peer ->
                            DropdownMenuItem(
                                text = { Text(peer.nickname) },
                                onClick = { selected = peer; expanded = false }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = amountRupees,
                onValueChange = { amountRupees = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount (₹)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("What for?") },
                modifier = Modifier.fillMaxWidth()
            )

            val amountPaise = amountRupees.toDoubleOrNull()?.let { (it * 100).toLong() }
            val sendInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    val peer = selected ?: return@Button
                    val paise = amountPaise ?: return@Button
                    onSend(peer.peerIdBase64, peer.nickname, paise, memo.trim())
                },
                enabled = selected != null && amountPaise != null && amountPaise > 0,
                interactionSource = sendInteraction,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(48.dp).pressScale(sendInteraction)
            ) {
                Text("Send IOU", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun IouSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun EmptySectionText(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * One vocabulary shared with the Chat tab's peer rows — settled shares the
 * same stamp-ink green as a ready peer (both mean "confirmed, no action
 * needed"), rejected shares the one reserved red. A settled or rejected
 * entry gets the plain hairline border every ledger row has; only a still-
 * pending IOU keeps the dashed counterfoil edge, so the mark disappears
 * the moment it's actually resolved — the visual proof that it stopped
 * being a promise and became a closed entry. See
 * docs/adr/0019-ledger-register-redesign.md.
 */
@Composable
private fun IouCard(iou: IouEntity, onMarkSettled: (() -> Unit)? = null) {
    val (tint, statusWord) = when (iou.status) {
        "settled" -> SankatSetuColors.StatusSafe to "SETTLED"
        "rejected" -> SankatSetuColors.StatusCritical to "REJECTED"
        else -> SankatSetuColors.StatusCaution to "PENDING"
    }
    val pending = iou.status == "pending"
    Card(
        Modifier
            .fillMaxWidth()
            .let { if (pending) it.counterfoilEdge(tint.copy(alpha = 0.6f)) else it },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (pending) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${iou.counterpartyNickname} · ₹${"%.2f".format(iou.amountPaise / 100.0)}", style = MaterialTheme.typography.titleMedium)
                if (iou.memo.isNotBlank()) {
                    Text(iou.memo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                if (pending) StatusPill(statusWord, tint) else StampMark(statusWord, tint, seed = iou.iouId.hashCode())
            }
            if (onMarkSettled != null && pending) {
                OutlinedButton(onClick = onMarkSettled, shape = MaterialTheme.shapes.small) { Text("Mark paid") }
            }
        }
    }
}
