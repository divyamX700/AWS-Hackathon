package com.sankatsetu.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sankatsetu.app.SankatSetuApplication
import com.sankatsetu.app.mesh.transport.MeshForegroundService
import com.sankatsetu.app.ui.chat.ChatListScreen
import com.sankatsetu.app.ui.chat.ChatThreadScreen
import com.sankatsetu.app.ui.chat.ChatViewModel
import com.sankatsetu.app.ui.chat.PeerUiModel
import com.sankatsetu.app.ui.theme.SankatSetuTheme

/**
 * Day 1 scope: a single Activity with a two-screen "navigation" (peer list
 * ↔ thread) done with plain Compose state rather than Navigation-Compose —
 * intentionally minimal per docs/PLAN.md's Day 1 gate ("two-phone one-hop
 * encrypted chat working"). Tabs for Assistant/Pay/SOS (PRD §11.1) and real
 * navigation land Day 2-3 as those features exist.
 */
class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // We don't branch on individual grants for Day 1 — MeshTransport's
        // start() calls no-op safely if a permission is actually missing,
        // and the "Bluetooth is off" banner (ChatScreen.kt) covers the
        // user-facing signal either way. Per-permission rationale screens
        // (PRD §5.1 Setup 3/4) are Day 2 UI polish.
        startMeshService()
    }

    private lateinit var chatViewModel: ChatViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SankatSetuApplication
        chatViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(
                        identity = app.container.identity,
                        router = app.container.messageRouter,
                        peerDao = app.container.database.peerDao(),
                        messageDao = app.container.database.messageDao()
                    ) as T
                }
            }
        )[ChatViewModel::class.java]

        requestPermissions.launch(requiredPermissions())

        setContent {
            SankatSetuTheme {
                Surface(modifier = Modifier) {
                    var openThread by remember { mutableStateOf<PeerUiModel?>(null) }
                    val thread = openThread
                    if (thread == null) {
                        ChatListScreen(viewModel = chatViewModel, onOpenThread = { openThread = it })
                    } else {
                        ChatThreadScreen(viewModel = chatViewModel, peer = thread, onBack = { openThread = null })
                    }
                }
            }
        }
    }

    private fun startMeshService() {
        val intent = Intent(this, MeshForegroundService::class.java)
        startForegroundService(intent)
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_ADVERTISE
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return permissions.toTypedArray()
    }
}
