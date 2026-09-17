package com.sankatsetu.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sankatsetu.app.SankatSetuApplication
import com.sankatsetu.app.mesh.transport.MeshForegroundService
import com.sankatsetu.app.ui.assistant.AssistantScreen
import com.sankatsetu.app.ui.assistant.AssistantViewModel
import com.sankatsetu.app.ui.chat.ChatListScreen
import com.sankatsetu.app.ui.chat.ChatThreadScreen
import com.sankatsetu.app.ui.chat.ChatViewModel
import com.sankatsetu.app.ui.chat.PeerUiModel
import com.sankatsetu.app.ui.theme.SankatSetuTheme

private enum class Tab(val label: String, val emoji: String) {
    CHAT("Chat", "💬"),
    ASSISTANT("Assistant", "🤖")
}

/**
 * Day 2 scope: a bottom nav bar switches between Chat and the offline
 * Assistant, both still using plain Compose state rather than
 * Navigation-Compose — Pay/SOS tabs (PRD §11.1) land Day 3 as those
 * features exist. The Chat tab's own list/thread state predates this and is
 * unchanged from Day 1.
 */
class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Day 1's assumption here was wrong, caught by actually running this
        // on a device: Android 14 validates a connectedDevice foreground
        // service's permissions at Service.onCreate()/startForeground() time
        // and throws — a hard crash, not a graceful no-op — if none of the
        // "relevant" permissions (BLUETOOTH_ADVERTISE/CONNECT/SCAN) are
        // granted yet. Starting the service unconditionally after merely
        // *requesting* permissions (not checking whether they were granted)
        // crashed the app on first launch. A per-permission rationale UI
        // that lets the user retry from Settings (PRD §5.1 Setup 3/4) is
        // still Day 3 polish; this is the minimum fix to not crash.
        val hasBluetoothPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            grants[Manifest.permission.BLUETOOTH_ADVERTISE] == true ||
                grants[Manifest.permission.BLUETOOTH_CONNECT] == true ||
                grants[Manifest.permission.BLUETOOTH_SCAN] == true
        } else {
            true // pre-API-31 BLE doesn't need a runtime-requested "relevant" FGS permission the same way
        }
        if (hasBluetoothPermission) {
            startMeshService()
        }
    }

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var assistantViewModel: AssistantViewModel

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

        assistantViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AssistantViewModel(engine = app.container.assistantEngine) as T
                }
            }
        )[AssistantViewModel::class.java]

        requestPermissions.launch(requiredPermissions())

        setContent {
            SankatSetuTheme {
                Surface(modifier = Modifier) {
                    var currentTab by remember { mutableStateOf(Tab.CHAT) }
                    var openThread by remember { mutableStateOf<PeerUiModel?>(null) }

                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentTab == Tab.CHAT,
                                    onClick = { currentTab = Tab.CHAT },
                                    icon = { Text(Tab.CHAT.emoji) },
                                    label = { Text(Tab.CHAT.label) }
                                )
                                NavigationBarItem(
                                    selected = currentTab == Tab.ASSISTANT,
                                    onClick = { currentTab = Tab.ASSISTANT },
                                    icon = { Text(Tab.ASSISTANT.emoji) },
                                    label = { Text(Tab.ASSISTANT.label) }
                                )
                            }
                        }
                    ) { padding ->
                        Surface(Modifier.padding(padding)) {
                            when (currentTab) {
                                Tab.CHAT -> {
                                    val thread = openThread
                                    if (thread == null) {
                                        ChatListScreen(viewModel = chatViewModel, onOpenThread = { openThread = it })
                                    } else {
                                        ChatThreadScreen(viewModel = chatViewModel, peer = thread, onBack = { openThread = null })
                                    }
                                }
                                Tab.ASSISTANT -> AssistantScreen(viewModel = assistantViewModel)
                            }
                        }
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
