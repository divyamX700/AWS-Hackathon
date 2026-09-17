package com.sankatsetu.app.ui.chat

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankatsetu.app.data.MessageDao
import com.sankatsetu.app.data.MessageEntity
import com.sankatsetu.app.data.PeerDao
import com.sankatsetu.app.data.PeerEntity
import com.sankatsetu.app.mesh.crypto.Identity
import com.sankatsetu.app.mesh.crypto.NoiseSession
import com.sankatsetu.app.mesh.protocol.AnnouncementPacket
import com.sankatsetu.app.mesh.protocol.MeshPacket
import com.sankatsetu.app.mesh.protocol.MessageType
import com.sankatsetu.app.mesh.protocol.PrivateMessagePacket
import com.sankatsetu.app.mesh.router.MessageRouter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class PeerUiModel(
    val peerIdBase64: String,
    val nickname: String,
    val hopCount: Int,
    val handshakeEstablished: Boolean
)

data class ChatUiState(
    val peers: List<PeerUiModel> = emptyList(),
    val bluetoothOn: Boolean = true
)

/**
 * Day 1 scope: peer discovery via announces, one-hop-aware peer list, Noise
 * XX handshake lifecycle per peer, and encrypted 1:1 text messaging. Public
 * `#public` broadcast, courier store-and-forward, and gossip sync are Day 2.
 *
 * This is the piece that turns [MessageRouter]'s raw [MeshPacket] stream
 * into something a screen can render — see docs/concepts/ble-mesh-protocol.md
 * for how announce/handshake/encrypted flows fit together end to end.
 */
class ChatViewModel(
    private val identity: Identity,
    private val router: MessageRouter,
    private val peerDao: PeerDao,
    private val messageDao: MessageDao
) : ViewModel() {

    private val nickname: String = "builder-${identity.peerId.take(2).joinToString("") { "%02x".format(it) }}"

    // One Noise session per peer we've ever started a handshake with.
    private val sessions = ConcurrentHashMap<String, NoiseSession>()
    private val knownNicknames = ConcurrentHashMap<String, String>()
    private val knownHopCounts = ConcurrentHashMap<String, Int>()

    private val _bluetoothOn = MutableStateFlow(true)
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    fun threadMessages(peerIdBase64: String) = messageDao.observeThread(peerIdBase64)

    init {
        viewModelScope.launch {
            combine(peerDao.observeAll(), _bluetoothOn) { peers, btOn ->
                ChatUiState(
                    peers = peers.map { p ->
                        PeerUiModel(
                            peerIdBase64 = p.peerIdBase64,
                            nickname = knownNicknames[p.peerIdBase64] ?: p.nickname,
                            hopCount = knownHopCounts[p.peerIdBase64] ?: p.lastKnownHopCount,
                            handshakeEstablished = sessions[p.peerIdBase64]?.isEstablished == true
                        )
                    },
                    bluetoothOn = btOn
                )
            }.collect { _uiState.value = it }
        }
        viewModelScope.launch { observeInbound() }
        viewModelScope.launch { announceLoop() }
    }

    /**
     * Adaptive announce cadence per whitepaper §4.5: 4s while we have zero
     * connected peers ("isolated"), backing off to a jittered 15-30s once
     * we have at least one — no need to shout every 4s once discovery has
     * already worked.
     */
    private suspend fun announceLoop() {
        while (true) {
            sendAnnounce()
            val isolated = router.connectedLinkCount() == 0
            val delayMs = if (isolated) {
                4_000L
            } else {
                (15_000..30_000).random().toLong()
            }
            delay(delayMs)
        }
    }

    private suspend fun sendAnnounce() {
        val packet = AnnouncementPacket(
            nickname = nickname,
            noisePublicKey = identity.noisePublicKey,
            signingPublicKey = identity.signingPublicKeyBytes()
        )
        val encoded = packet.encode() ?: return
        router.broadcast(MessageType.ANNOUNCE, encoded, sign = true)
    }

    private suspend fun observeInbound() {
        router.inboundApplicationPackets.collect { packet ->
            when (packet.type) {
                MessageType.ANNOUNCE -> handleAnnounce(packet)
                MessageType.NOISE_HANDSHAKE -> handleHandshake(packet)
                MessageType.NOISE_ENCRYPTED -> handleEncrypted(packet)
                else -> Unit // SOS/IOU/courier/etc. handled by their own ViewModels, Day 2-3
            }
        }
    }

    private suspend fun handleAnnounce(packet: MeshPacket) {
        val announce = AnnouncementPacket.decode(packet.payload) ?: return
        val peerIdB64 = Base64.encodeToString(packet.senderId, Base64.NO_WRAP)
        val hopCount = MeshPacket.DEFAULT_TTL - packet.ttl

        knownNicknames[peerIdB64] = announce.nickname
        knownHopCounts[peerIdB64] = hopCount.toInt()

        val now = System.currentTimeMillis()
        val existing = peerDao.getByPeerId(peerIdB64)
        if (existing == null) {
            peerDao.upsert(
                PeerEntity(
                    peerIdBase64 = peerIdB64,
                    noiseStaticKeyBase64 = Base64.encodeToString(announce.noisePublicKey, Base64.NO_WRAP),
                    nickname = announce.nickname,
                    firstSeen = now,
                    lastSeen = now,
                    lastKnownHopCount = hopCount.toInt()
                )
            )
        } else {
            peerDao.touch(peerIdB64, now, hopCount.toInt())
        }

        // Auto-initiate a handshake with newly-discovered one-hop peers so
        // encrypted messaging is ready by the time the user opens the thread.
        if (hopCount <= 1 && sessions[peerIdB64] == null) {
            startHandshake(peerIdB64, packet.senderId, isInitiator = isLexicographicInitiator(packet.senderId))
        }
    }

    /** Both sides seeing each other's announce would otherwise race to both initiate; break the tie deterministically. */
    private fun isLexicographicInitiator(remotePeerId: ByteArray): Boolean {
        for (i in identity.peerId.indices) {
            val a = identity.peerId[i].toInt() and 0xFF
            val b = remotePeerId[i].toInt() and 0xFF
            if (a != b) return a < b
        }
        return false
    }

    private suspend fun startHandshake(peerIdB64: String, remotePeerId: ByteArray, isInitiator: Boolean) {
        val session = NoiseSession(identity.noisePrivateKey, identity.noisePublicKey, isInitiator)
        sessions[peerIdB64] = session
        if (isInitiator) {
            session.nextHandshakeMessage()?.let { msg ->
                router.sendDirected(MessageType.NOISE_HANDSHAKE, remotePeerId, msg)
            }
        }
    }

    private suspend fun handleHandshake(packet: MeshPacket) {
        val peerIdB64 = Base64.encodeToString(packet.senderId, Base64.NO_WRAP)
        val session = sessions[peerIdB64]
            ?: NoiseSession(identity.noisePrivateKey, identity.noisePublicKey, isInitiator = false)
                .also { sessions[peerIdB64] = it }

        session.consumeHandshakeMessage(packet.payload)
        session.nextHandshakeMessage()?.let { reply ->
            router.sendDirected(MessageType.NOISE_HANDSHAKE, packet.senderId, reply)
        }
    }

    private suspend fun handleEncrypted(packet: MeshPacket) {
        val peerIdB64 = Base64.encodeToString(packet.senderId, Base64.NO_WRAP)
        val session = sessions[peerIdB64] ?: return // no session: can't decrypt, drop (see NoiseSession.decrypt doc)
        val plaintext = session.decrypt(packet.payload) ?: return
        val privateMessage = PrivateMessagePacket.decode(plaintext) ?: return

        messageDao.insert(
            MessageEntity(
                messageId = privateMessage.messageId,
                threadPeerIdBase64 = peerIdB64,
                senderPeerIdBase64 = peerIdB64,
                body = privateMessage.content,
                sentAt = packet.timestamp,
                receivedAt = System.currentTimeMillis(),
                hopCount = MeshPacket.DEFAULT_TTL - packet.ttl,
                status = "delivered",
                isOutgoing = false
            )
        )
    }

    fun sendMessage(peerIdBase64: String, text: String) {
        viewModelScope.launch {
            val session = sessions[peerIdBase64]
            if (session == null || !session.isEstablished) return@launch // UI should disable send until handshake completes

            val privateMessage = PrivateMessagePacket(content = text)
            val encoded = privateMessage.encode() ?: return@launch
            val ciphertext = session.encrypt(encoded) ?: return@launch
            val remotePeerId = Base64.decode(peerIdBase64, Base64.NO_WRAP)

            val now = System.currentTimeMillis()
            messageDao.insert(
                MessageEntity(
                    messageId = privateMessage.messageId,
                    threadPeerIdBase64 = peerIdBase64,
                    senderPeerIdBase64 = Base64.encodeToString(identity.peerId, Base64.NO_WRAP),
                    body = text,
                    sentAt = now,
                    receivedAt = now,
                    hopCount = 0,
                    status = "sending",
                    isOutgoing = true
                )
            )

            router.sendDirected(MessageType.NOISE_ENCRYPTED, remotePeerId, ciphertext)
            messageDao.updateStatus(privateMessage.messageId, "sent")
        }
    }
}
