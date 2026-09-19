package com.sankatsetu.app.mesh.router

import com.sankatsetu.app.mesh.protocol.MessageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * End-to-end test of two [MessageRouter]s wired directly to each other via
 * in-memory [MeshLink]s — no BLE, no Android, no emulator. This is the
 * closest thing to a "two-phone mesh chat" test that can run in a plain JVM
 * unit test: it exercises the exact same encode → fragment → relay →
 * reassemble → dedup → deliver pipeline real devices would, just over a
 * direct method call instead of a Bluetooth radio.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageRouterFragmentationTest {

    private fun peerId(byte: Int) = ByteArray(8) { byte.toByte() }

    @Test
    fun `small directed message delivers between two routers without fragmentation`() = runTest {

        lateinit var routerB: MessageRouter
        val routerA = MessageRouter(localPeerId = peerId(0xAA), scope = this)
        routerB = MessageRouter(localPeerId = peerId(0xBB), scope = this)

        val linkAtoB = object : MeshLink {
            override val linkId = "a-to-b"
            override suspend fun send(bytes: ByteArray): Boolean {
                this@runTest.launch { routerB.handleInboundBytes("b-to-a", bytes) }
                return true
            }
        }
        val linkBtoA = object : MeshLink {
            override val linkId = "b-to-a"
            override suspend fun send(bytes: ByteArray): Boolean {
                this@runTest.launch { routerA.handleInboundBytes("a-to-b", bytes) }
                return true
            }
        }
        routerA.onLinkConnected(linkAtoB)
        routerB.onLinkConnected(linkBtoA)

        // Attach the collector BEFORE triggering the send: MutableSharedFlow's
        // default replay=0 means a collector that attaches after the
        // emission has already happened misses it and waits forever for the
        // next one — this is what a real ChatViewModel avoids by collecting
        // from init{} before any mesh traffic can occur.
        val receivedDeferred = async { routerB.inboundApplicationPackets.first() }
        routerA.sendDirected(MessageType.MESSAGE, recipientId = peerId(0xBB), payload = "hi from A".toByteArray())
        advanceUntilIdle()

        val received = receivedDeferred.await()
        assertEquals(MessageType.MESSAGE, received.type)
        assertArrayEquals("hi from A".toByteArray(), received.payload)
        assertArrayEquals(peerId(0xAA), received.senderId)
    }

    @Test
    fun `payload larger than one BLE frame fragments, relays, and reassembles correctly`() = runTest {

        lateinit var routerB: MessageRouter
        val routerA = MessageRouter(localPeerId = peerId(0x01), scope = this)
        routerB = MessageRouter(localPeerId = peerId(0x02), scope = this)

        routerA.onLinkConnected(object : MeshLink {
            override val linkId = "a-to-b"
            override suspend fun send(bytes: ByteArray): Boolean {
                this@runTest.launch { routerB.handleInboundBytes("b-to-a", bytes) }
                return true
            }
        })
        routerB.onLinkConnected(object : MeshLink {
            override val linkId = "b-to-a"
            override suspend fun send(bytes: ByteArray): Boolean {
                this@runTest.launch { routerA.handleInboundBytes("a-to-b", bytes) }
                return true
            }
        })

        // Comfortably larger than FragmentPacket.DEFAULT_CHUNK_SIZE (469), so
        // this must go through the fragment/reassemble path, not a single frame.
        val bigPayload = ByteArray(2000) { (it % 200).toByte() }

        val receivedDeferred = async { routerB.inboundApplicationPackets.first() }
        routerA.sendDirected(MessageType.MESSAGE, recipientId = peerId(0x02), payload = bigPayload)
        advanceUntilIdle()

        val received = receivedDeferred.await()
        assertEquals(MessageType.MESSAGE, received.type)
        assertArrayEquals(bigPayload, received.payload)
    }

    @Test
    fun `three-router chain relays a message the origin cannot reach directly`() = runTest {

        // A -- B -- C: A has no direct link to C, only to B.
        lateinit var routerA: MessageRouter
        lateinit var routerB: MessageRouter
        lateinit var routerC: MessageRouter
        routerA = MessageRouter(localPeerId = peerId(0x0A), scope = this)
        routerB = MessageRouter(localPeerId = peerId(0x0B), scope = this)
        routerC = MessageRouter(localPeerId = peerId(0x0C), scope = this)

        // Two different keys are involved on each side of a link: the id THIS
        // router registers it under (so its own ingress-exclusion filter,
        // `links.keys.filter { it != ingressLinkId }`, can recognize "this is
        // the link I got it from"), and the id the PEER registered its own
        // reverse link under (which is what must be passed as fromLinkId when
        // notifying the peer). The two are unrelated strings in general.
        fun link(to: () -> MessageRouter, selfKey: String, peerKey: String) = object : MeshLink {
            override val linkId = selfKey
            override suspend fun send(bytes: ByteArray): Boolean {
                this@runTest.launch { to().handleInboundBytes(peerKey, bytes) }
                return true
            }
        }

        routerA.onLinkConnected(link({ routerB }, selfKey = "A-B", peerKey = "B-A"))
        routerB.onLinkConnected(link({ routerA }, selfKey = "B-A", peerKey = "A-B"))
        routerB.onLinkConnected(link({ routerC }, selfKey = "B-C", peerKey = "C-B"))
        routerC.onLinkConnected(link({ routerB }, selfKey = "C-B", peerKey = "B-C"))

        val receivedDeferred = async { routerC.inboundApplicationPackets.first() }
        routerA.sendDirected(MessageType.MESSAGE, recipientId = peerId(0x0C), payload = "relay me".toByteArray())
        advanceUntilIdle()

        val received = receivedDeferred.await()
        assertArrayEquals("relay me".toByteArray(), received.payload)
        assertArrayEquals(peerId(0x0A), received.senderId)
    }

    /**
     * Proves the exact mechanism `ChatViewModel.handleAnnounce` now relies on
     * for multi-hop encrypted chat (see its own doc — auto-handshake used to
     * be gated to one-hop peers only, widened once this was confirmed): a
     * [MessageType.NOISE_HANDSHAKE] round trip is ordinary directed traffic
     * to [MessageRouter], with no special-casing for hop count anywhere in
     * `relayDirected`/`handleInboundBytes`. This test doesn't touch real
     * `NoiseSession` crypto (opaque payload bytes stand in for handshake
     * messages) — it isolates exactly the transport-layer claim: a directed
     * packet reaches a peer 2 hops away, and that peer's reply makes it all
     * the way back, over the same A–B–C chain the plain-message test above
     * already proves for a one-way send.
     */
    @Test
    fun `NOISE_HANDSHAKE round-trips across a three-router chain, both directions`() = runTest {

        lateinit var routerA: MessageRouter
        lateinit var routerB: MessageRouter
        lateinit var routerC: MessageRouter
        routerA = MessageRouter(localPeerId = peerId(0x1A), scope = this)
        routerB = MessageRouter(localPeerId = peerId(0x1B), scope = this)
        routerC = MessageRouter(localPeerId = peerId(0x1C), scope = this)

        fun link(to: () -> MessageRouter, selfKey: String, peerKey: String) = object : MeshLink {
            override val linkId = selfKey
            override suspend fun send(bytes: ByteArray): Boolean {
                this@runTest.launch { to().handleInboundBytes(peerKey, bytes) }
                return true
            }
        }

        routerA.onLinkConnected(link({ routerB }, selfKey = "A-B", peerKey = "B-A"))
        routerB.onLinkConnected(link({ routerA }, selfKey = "B-A", peerKey = "A-B"))
        routerB.onLinkConnected(link({ routerC }, selfKey = "B-C", peerKey = "C-B"))
        routerC.onLinkConnected(link({ routerB }, selfKey = "C-B", peerKey = "B-C"))

        // A initiates, exactly like ChatViewModel.startHandshake's isInitiator branch.
        val helloReceived = async { routerC.inboundApplicationPackets.first() }
        routerA.sendDirected(MessageType.NOISE_HANDSHAKE, recipientId = peerId(0x1C), payload = "handshake-msg-1".toByteArray())
        advanceUntilIdle()
        val hello = helloReceived.await()
        assertEquals(MessageType.NOISE_HANDSHAKE, hello.type)
        assertArrayEquals("handshake-msg-1".toByteArray(), hello.payload)
        assertArrayEquals(peerId(0x1A), hello.senderId)

        // C replies, exactly like ChatViewModel.handleHandshake's reply send — must
        // make it all the way back across the same two-hop chain to reach A.
        val replyReceived = async { routerA.inboundApplicationPackets.first() }
        routerC.sendDirected(MessageType.NOISE_HANDSHAKE, recipientId = peerId(0x1A), payload = "handshake-msg-2".toByteArray())
        advanceUntilIdle()
        val reply = replyReceived.await()
        assertEquals(MessageType.NOISE_HANDSHAKE, reply.type)
        assertArrayEquals("handshake-msg-2".toByteArray(), reply.payload)
        assertArrayEquals(peerId(0x1C), reply.senderId)
    }
}
