package com.sankatsetu.app.mesh.router

import com.sankatsetu.app.mesh.authz.MessageKind
import com.sankatsetu.app.mesh.authz.MeshAuthorizer
import com.sankatsetu.app.mesh.protocol.MessageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Verifies [MessageRouter] actually calls its [MeshAuthorizer] at the
 * `handleInboundBytes` choke point and drops a denied packet — using a
 * plain fake, not a real [com.sankatsetu.app.mesh.authz.CedarAuthorizer],
 * since a JVM unit test has no native Cedar library to load (see that
 * class's fail-open doc). This test is what actually proves the wiring in
 * `docs/adr/0017-cedar-cross-compile.md` works, independent of whether the
 * real Cedar `.so` has been cross-compiled yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageRouterAuthorizationTest {

    private fun peerId(byte: Int) = ByteArray(8) { byte.toByte() }

    /** Denies every kind in [deniedKinds] for every sender; allows everything else. */
    private class FakeAuthorizer(private val deniedKinds: Set<MessageKind>) : MeshAuthorizer {
        val checks = ConcurrentHashMap<String, MutableList<MessageKind>>()
        override fun isAllowed(senderIdHex: String, kind: MessageKind): Boolean {
            checks.getOrPut(senderIdHex) { mutableListOf() }.add(kind)
            return kind !in deniedKinds
        }
    }

    @Test
    fun `denied public broadcast never reaches the recipient`() = runTest {
        val authorizer = FakeAuthorizer(deniedKinds = setOf(MessageKind.PUBLIC))
        val routerA = MessageRouter(localPeerId = peerId(0xAA), scope = this)
        val routerB = MessageRouter(localPeerId = peerId(0xBB), scope = this, cedarAuthorizer = authorizer)

        routerA.onLinkConnected(object : MeshLink {
            override val linkId = "a-to-b"
            override suspend fun send(bytes: ByteArray): Boolean {
                this@runTest.launch { routerB.handleInboundBytes("b-to-a", bytes) }
                return true
            }
        })

        var delivered = false
        val collector = launch { routerB.inboundApplicationPackets.first(); delivered = true }
        routerA.broadcast(MessageType.MESSAGE, payload = "flood".toByteArray())
        advanceUntilIdle()
        collector.cancel()

        assertTrue("denied packet must not be delivered", !delivered)
        assertEquals(listOf(MessageKind.PUBLIC), authorizer.checks.values.single())
    }

    @Test
    fun `allowed message still delivers normally`() = runTest {
        val authorizer = FakeAuthorizer(deniedKinds = emptySet())
        val routerA = MessageRouter(localPeerId = peerId(0x01), scope = this)
        val routerB = MessageRouter(localPeerId = peerId(0x02), scope = this, cedarAuthorizer = authorizer)

        routerA.onLinkConnected(object : MeshLink {
            override val linkId = "a-to-b"
            override suspend fun send(bytes: ByteArray): Boolean {
                this@runTest.launch { routerB.handleInboundBytes("b-to-a", bytes) }
                return true
            }
        })

        val receivedDeferred = async { routerB.inboundApplicationPackets.first() }
        routerA.sendDirected(MessageType.MESSAGE, recipientId = peerId(0x02), payload = "hi".toByteArray())
        advanceUntilIdle()

        val received = receivedDeferred.await()
        assertArrayEquals("hi".toByteArray(), received.payload)
    }

    @Test
    fun `no authorizer configured means everything is allowed, unchanged from before Cedar existed`() = runTest {
        val routerA = MessageRouter(localPeerId = peerId(0x0A), scope = this)
        val routerB = MessageRouter(localPeerId = peerId(0x0B), scope = this) // cedarAuthorizer left null

        routerA.onLinkConnected(object : MeshLink {
            override val linkId = "a-to-b"
            override suspend fun send(bytes: ByteArray): Boolean {
                this@runTest.launch { routerB.handleInboundBytes("b-to-a", bytes) }
                return true
            }
        })

        val receivedDeferred = async { routerB.inboundApplicationPackets.first() }
        routerA.broadcast(MessageType.SOS_BROADCAST, payload = "help".toByteArray())
        advanceUntilIdle()

        val received = receivedDeferred.await()
        assertArrayEquals("help".toByteArray(), received.payload)
    }
}
