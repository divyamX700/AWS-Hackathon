package com.sankatsetu.app.mesh.router

import com.sankatsetu.app.mesh.protocol.MeshPacket
import com.sankatsetu.app.mesh.protocol.MessageType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenMessageCacheTest {

    private fun packet(sender: Int, ts: Long, body: String) = MeshPacket(
        type = MessageType.MESSAGE,
        ttl = 7,
        timestamp = ts,
        senderId = ByteArray(8) { sender.toByte() },
        payload = body.toByteArray()
    )

    @Test
    fun `first sighting of a packet is marked new`() {
        val cache = SeenMessageCache()
        assertTrue(cache.markIfNew(packet(1, 100L, "hello")))
    }

    @Test
    fun `identical packet arriving twice is deduped`() {
        val cache = SeenMessageCache()
        val p = packet(1, 100L, "hello")
        assertTrue(cache.markIfNew(p))
        assertFalse(cache.markIfNew(p)) // same (sender, timestamp, type, payload-digest) -> duplicate
    }

    @Test
    fun `different payload from same sender and timestamp is not deduped`() {
        val cache = SeenMessageCache()
        assertTrue(cache.markIfNew(packet(1, 100L, "hello")))
        assertTrue(cache.markIfNew(packet(1, 100L, "different body")))
    }

    @Test
    fun `entry expires after ttl and is treated as new again`() {
        var now = 0L
        val cache = SeenMessageCache(ttlMillis = 1000L, now = { now })
        val p = packet(1, 100L, "hello")

        assertTrue(cache.markIfNew(p))
        now = 500L
        assertFalse(cache.markIfNew(p)) // still within 5-minute-equivalent window

        now = 1500L
        assertTrue(cache.markIfNew(p)) // past ttl -> treated as new
    }

    @Test
    fun `eviction bounds memory at capacity`() {
        val cache = SeenMessageCache(maxEntries = 4)
        repeat(10) { i -> cache.markIfNew(packet(i, i.toLong(), "msg$i")) }
        // The oldest entries should have been evicted, so re-marking one of the
        // earliest packets is accepted again as "new" rather than deduped.
        assertTrue(cache.markIfNew(packet(0, 0L, "msg0")))
    }
}
