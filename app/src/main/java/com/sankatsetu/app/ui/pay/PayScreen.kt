package com.sankatsetu.app.ui.pay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.sankatsetu.app.ui.theme.ConsoleReadoutStyle
import com.sankatsetu.app.ui.theme.SankatSetuColors

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

    Scaffold(topBar = { TopAppBar(title = { Text("Pay") }) }) { padding ->
    LazyColumn(
        Modifier.fillMaxWidth().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Works with no internet — USSD and IVR use your SIM's voice/USSD channel, and mesh IOUs travel over Bluetooth.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // The two USSD/IVR shortcuts are equally weighted, minor actions —
        // a side-by-side compact pair reads as one dialing toolset instead
        // of repeating the same full-width card shape three times in a row
        // (the "same-size cards" scaffold a design review flagged). The
        // mesh IOU is the app's own distinct capability, so it stays the
        // one full-width, clearly-primary card below.
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
        item {
            PayActionCard(
                title = "Send Mesh IOU",
                subtitle = "Sign a promise-to-pay and deliver it over Bluetooth to a nearby phone — no bank, no internet, settle later.",
                onClick = { showComposer = !showComposer },
                highlighted = true
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
private fun CompactActionButton(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(title)
    }
}

@Composable
private fun PayActionCard(title: String, subtitle: String, onClick: () -> Unit, highlighted: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("New mesh IOU", style = MaterialTheme.typography.bodyLarge)

            if (peers.isEmpty()) {
                Text(
                    "No nearby phones yet — open the Chat tab so a peer shows up here first.",
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
            Button(
                onClick = {
                    val peer = selected ?: return@Button
                    val paise = amountPaise ?: return@Button
                    onSend(peer.peerIdBase64, peer.nickname, paise, memo.trim())
                },
                enabled = selected != null && amountPaise != null && amountPaise > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send IOU")
            }
        }
    }
}

@Composable
private fun IouSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun EmptySectionText(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Status reads as one monospace readout, colored by the same IMD scale as
 * the Chat tab's signal bars — a settled IOU and a ready peer share the
 * same green because both mean "no action needed," a pending IOU shares
 * caution-orange with anything else in the app that means "handle this
 * soon." One vocabulary, not a per-screen palette. See
 * docs/adr/0014-field-radio-design-language.md.
 */
@Composable
private fun IouCard(iou: IouEntity, onMarkSettled: (() -> Unit)? = null) {
    val (tint, statusWord) = when (iou.status) {
        "settled" -> SankatSetuColors.ImdGreen to "SETTLED"
        "rejected" -> SankatSetuColors.ImdRed to "REJECTED"
        else -> SankatSetuColors.ImdOrange to "PENDING"
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("₹${"%.2f".format(iou.amountPaise / 100.0)} — ${iou.counterpartyNickname}", style = MaterialTheme.typography.bodyLarge)
                if (iou.memo.isNotBlank()) {
                    Text(iou.memo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(statusWord, style = ConsoleReadoutStyle, color = tint, modifier = Modifier.padding(top = 2.dp))
            }
            if (onMarkSettled != null && iou.status == "pending") {
                OutlinedButton(onClick = onMarkSettled) { Text("Mark paid") }
            }
        }
    }
}
