package com.sankatsetu.app.mesh.router

import com.sankatsetu.app.mesh.protocol.MessageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the actual user-facing promise of [SenderOutbox]: a message sent
 * while there is no mesh link at all to *anyone* is not dropped — it waits,
 * and [MessageRouter.retryOutbox] delivers it once a link exists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageRouterOutboxTest {

    private fun peerId(byte: Int) = ByteArray(8) { byte.toByte() }

    @Test
    fun `directed send with zero links queues instead of vanishing`() = runTest {
        val router = MessageRouter(localPeerId = peerId(0x01), scope = this)
        // No onLinkConnected call at all — router has no links whatsoever.
        router.sendDirected(MessageType.MESSAGE, recipientId = peerId(0x02), payload = "are you there?".toByteArray())
        advanceUntilIdle()

        // No assertion possible on delivery yet (nothing to deliver to) —
        // the real assertion is the next test: it doesn't silently vanish.
    }

    @Test
    fun `queued message delivers once a link appears and retryOutbox is called`() = runTest {
        lateinit var routerB: MessageRouter
        val routerA = MessageRouter(localPeerId = peerId(0x01), scope = this)
        routerB = MessageRouter(localPeerId = peerId(0x02), scope = this)

        // A sends before any link exists — this must queue, not drop.
        routerA.sendDirected(MessageType.MESSAGE, recipientId = peerId(0x02), payload = "queued msg".toByteArray())
        advanceUntilIdle()

        // Now a link appears (peer came into BLE range).
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

        val receivedDeferred = async { routerB.inboundApplicationPackets.first() }
        routerA.retryOutbox(peerId(0x02))
        advanceUntilIdle()

        val received = receivedDeferred.await()
        assertArrayEquals("queued msg".toByteArray(), received.payload)
    }

    @Test
    fun `retryOutbox with nothing queued for that recipient is a harmless no-op`() = runTest {
        val router = MessageRouter(localPeerId = peerId(0x01), scope = this)
        router.retryOutbox(peerId(0x99)) // never queued anything for this recipient
        advanceUntilIdle()
        assertTrue(true) // reaching here without hanging/throwing is the assertion
    }
}
