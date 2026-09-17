package com.sankatsetu.app.mesh.router

import com.sankatsetu.app.mesh.crypto.Identity
import com.sankatsetu.app.mesh.protocol.BinaryProtocol
import com.sankatsetu.app.mesh.protocol.MeshPacket
import com.sankatsetu.app.mesh.protocol.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Transport-agnostic packet dispatcher: the piece that turns "a bunch of BLE
 * links to nearby phones" into "a mesh". Owns TTL clamping, dedup, relay
 * jitter, and fanout subset selection. Ported behaviourally from Bitchat's
 * `MessageRouter.swift` + `BLEFanoutSelector.swift` (public domain); see
 * docs/concepts/ble-mesh-protocol.md for the full parameter table this file
 * implements against.
 *
 * [MeshTransport] feeds this class raw bytes off the wire and asks it to
 * relay/broadcast; this class never touches `BluetoothGatt` directly.
 */
class MessageRouter(
    private val identity: Identity,
    private val scope: CoroutineScope,
    private val seenCache: SeenMessageCache = SeenMessageCache()
) {
    private val links = ConcurrentHashMap<String, MeshLink>()
    private val linksLock = Mutex()

    private val _inboundApplicationPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    /** Emits packets addressed to us (recipientId == our peer ID) or public broadcasts, post-dedup. */
    val inboundApplicationPackets: SharedFlow<MeshPacket> = _inboundApplicationPackets

    // --- Link lifecycle, called by MeshTransport ---

    fun onLinkConnected(link: MeshLink) {
        links[link.linkId] = link
    }

    fun onLinkDisconnected(linkId: String) {
        links.remove(linkId)
    }

    fun connectedLinkCount(): Int = links.size

    // --- Outbound ---

    /** Origin a new broadcast (public chat, announce, SOS, etc.) at the default TTL. */
    suspend fun broadcast(type: MessageType, payload: ByteArray, sign: Boolean = false, padded: Boolean = false) {
        val unsigned = MeshPacket(
            type = type,
            ttl = MeshPacket.DEFAULT_TTL,
            timestamp = System.currentTimeMillis(),
            senderId = identity.peerId,
            recipientId = null,
            payload = payload
        )
        val packet = if (sign) unsigned.copy(signature = identity.sign(unsigned.signingBytes())) else unsigned
        seenCache.markIfNew(packet) // never relay our own origin back to ourselves
        relayToFanout(packet, ingressLinkId = null, padded = padded)
    }

    /** Origin directed traffic (handshake, private message, IOU) toward a known peer ID. */
    suspend fun sendDirected(type: MessageType, recipientId: ByteArray, payload: ByteArray, sign: Boolean = false, padded: Boolean = true) {
        val unsigned = MeshPacket(
            type = type,
            ttl = MeshPacket.DEFAULT_TTL,
            timestamp = System.currentTimeMillis(),
            senderId = identity.peerId,
            recipientId = recipientId,
            payload = payload
        )
        val packet = if (sign) unsigned.copy(signature = identity.sign(unsigned.signingBytes())) else unsigned
        seenCache.markIfNew(packet)
        relayDirected(packet, ingressLinkId = null, padded = padded)
    }

    // --- Inbound ---

    /** Called by [MeshTransport] whenever bytes arrive on any link. */
    suspend fun handleInboundBytes(fromLinkId: String, raw: ByteArray) {
        val packet = BinaryProtocol.decode(raw) ?: return // malformed — drop silently, don't crash the mesh
        if (packet.senderId.contentEquals(identity.peerId)) return // our own packet came back around; ignore

        if (!seenCache.markIfNew(packet)) return // duplicate: dedup absorbs it, no re-relay

        val isForUs = packet.recipientId == null || packet.recipientId.contentEquals(identity.peerId)
        if (isForUs) _inboundApplicationPackets.tryEmit(packet)

        if (packet.ttl <= 0) return // hop budget exhausted, don't relay further

        val decremented = packet.decremented()
        if (packet.recipientId != null && !isForUs) {
            // Directed traffic not addressed to us: relay on toward the recipient.
            relayDirected(decremented, ingressLinkId = fromLinkId, padded = isNoiseType(packet.type))
        } else if (packet.recipientId == null) {
            // Broadcast: continue flooding to our fanout subset (minus the link it
            // came in on). Dense neighbourhoods clamp TTL down to bound total
            // flood volume — a message doesn't need 7 hops of budget when every
            // hop already reaches 6+ peers (whitepaper §4.2).
            val clamped = if (links.size >= DENSE_LINK_THRESHOLD && decremented.ttl > DENSE_BROADCAST_TTL_CAP) {
                decremented.copy(ttl = DENSE_BROADCAST_TTL_CAP)
            } else {
                decremented
            }
            relayToFanout(clamped, ingressLinkId = fromLinkId, padded = false)
        }
        // Directed traffic addressed to us: consumed above, nothing further to relay.
    }

    // --- Relay mechanics ---

    private suspend fun relayToFanout(packet: MeshPacket, ingressLinkId: String?, padded: Boolean) {
        val candidateIds = linksLock.withLock { links.keys.filter { it != ingressLinkId } }
        if (candidateIds.isEmpty()) return

        val targetIds = if (FanoutSelector.shouldSubset(packet.type)) {
            val k = FanoutSelector.subsetSize(candidateIds.size)
            FanoutSelector.deterministicSubset(candidateIds, k, seed = fanoutSeed(packet))
        } else {
            candidateIds.toSet()
        }

        scheduleRelay(packet, targetIds, padded, directed = false)
    }

    private suspend fun relayDirected(packet: MeshPacket, ingressLinkId: String?, padded: Boolean) {
        // Day 1: no source-routing table yet (that's Day-2 gossip/topology work), so
        // directed traffic still floods — but with tight jitter and full fanout,
        // matching Bitchat's own fallback-to-flooding behaviour when no confirmed
        // route exists yet (whitepaper §4.3).
        val candidateIds = linksLock.withLock { links.keys.filter { it != ingressLinkId } }
        if (candidateIds.isEmpty()) return
        scheduleRelay(packet, candidateIds.toSet(), padded, directed = true)
    }

    private fun scheduleRelay(packet: MeshPacket, targetLinkIds: Set<String>, padded: Boolean, directed: Boolean) {
        val bytes = BinaryProtocol.encode(packet, padding = padded)
        val jitterRangeMs = if (directed) DIRECTED_JITTER_MS else broadcastJitterRange(targetLinkIds.size)

        for (linkId in targetLinkIds) {
            if (!links.containsKey(linkId)) continue // skip already-disconnected targets before even scheduling the jitter delay
            scope.launch {
                val jitter = Random.nextLong(jitterRangeMs.first, jitterRangeMs.last + 1)
                delay(jitter)
                // Re-check the link is still live post-jitter; a duplicate relay
                // arriving from elsewhere during the delay is already absorbed by
                // SeenMessageCache on the receiving end, so we don't need to
                // re-check seenCache here — we're the origin of this specific send.
                links[linkId]?.send(bytes)
            }
        }
    }

    private fun broadcastJitterRange(linkCount: Int): LongRange =
        if (linkCount >= DENSE_LINK_THRESHOLD) DENSE_JITTER_MS else SPARSE_JITTER_MS

    private fun fanoutSeed(packet: MeshPacket): String =
        packet.senderId.joinToString("") { "%02x".format(it) } + ":" + packet.timestamp

    private fun isNoiseType(type: MessageType): Boolean =
        type == MessageType.NOISE_HANDSHAKE || type == MessageType.NOISE_ENCRYPTED

    companion object {
        private const val DENSE_LINK_THRESHOLD = 6
        private const val DENSE_BROADCAST_TTL_CAP: Byte = 5
        private val SPARSE_JITTER_MS = 10L..220L
        private val DENSE_JITTER_MS = 40L..220L // widened, not just capped — see docs/concepts/ble-mesh-protocol.md
        private val DIRECTED_JITTER_MS = 5L..40L
    }
}
