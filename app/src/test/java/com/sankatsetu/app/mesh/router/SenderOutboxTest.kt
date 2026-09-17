package com.sankatsetu.app.mesh.router

import com.sankatsetu.app.mesh.protocol.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderOutboxTest {

    private fun recipient(byte: Int) = ByteArray(8) { byte.toByte() }

    // Real wall-clock "now" by default, matching SenderOutbox's own default
    // `now`, so a message queued "now" is never mistaken for expired by
    // tests that aren't specifically exercising expiry (which override
    // `now` on the SenderOutbox itself instead, see below).
    private fun message(id: String, recipientId: ByteArray, queuedAt: Long = System.currentTimeMillis()) = SenderOutbox.QueuedMessage(
        messageId = id,
        recipientId = recipientId,
        type = MessageType.MESSAGE,
        payload = "hi".toByteArray(),
        sign = false,
        queuedAt = queuedAt
    )

    @Test
    fun `enqueued message is returned by pending for the same recipient`() {
        val outbox = SenderOutbox()
        val r = recipient(1)
        outbox.enqueue(message("m1", r))
        assertEquals(1, outbox.pending(r).size)
        assertEquals("m1", outbox.pending(r)[0].messageId)
    }

    @Test
    fun `pending for an unrelated recipient is empty`() {
        val outbox = SenderOutbox()
        outbox.enqueue(message("m1", recipient(1)))
        assertTrue(outbox.pending(recipient(2)).isEmpty())
    }

    @Test
    fun `expired messages are pruned and no longer returned`() {
        var clock = 0L
        val outbox = SenderOutbox(ttlMillis = 1000L, now = { clock })
        val r = recipient(1)
        outbox.enqueue(message("m1", r, queuedAt = 0L))
        clock = 500L
        assertEquals(1, outbox.pending(r).size) // still within TTL

        clock = 5000L
        assertTrue(outbox.pending(r).isEmpty()) // past TTL
    }

    @Test
    fun `message dropped once it reaches the attempt cap`() {
        val outbox = SenderOutbox(maxAttempts = 3)
        val r = recipient(1)
        outbox.enqueue(message("m1", r))

        outbox.recordAttempt(r, "m1")
        assertEquals(1, outbox.pending(r).size) // 1 attempt, still under cap of 3

        outbox.recordAttempt(r, "m1")
        outbox.recordAttempt(r, "m1")
        assertTrue(outbox.pending(r).isEmpty()) // 3rd attempt hit the cap — dropped
    }

    @Test
    fun `remove clears a specific message without touching the rest of the queue`() {
        val outbox = SenderOutbox()
        val r = recipient(1)
        outbox.enqueue(message("m1", r))
        outbox.enqueue(message("m2", r))

        outbox.remove(r, "m1")

        val remaining = outbox.pending(r)
        assertEquals(1, remaining.size)
        assertEquals("m2", remaining[0].messageId)
    }

    @Test
    fun `oldest message evicted once per-peer cap is reached`() {
        val outbox = SenderOutbox(maxPerPeer = 2)
        val r = recipient(1)
        outbox.enqueue(message("m1", r))
        outbox.enqueue(message("m2", r))
        outbox.enqueue(message("m3", r)) // should evict m1

        val ids = outbox.pending(r).map { it.messageId }
        assertEquals(listOf("m2", "m3"), ids)
    }

    @Test
    fun `queues for different recipients are independent`() {
        val outbox = SenderOutbox()
        outbox.enqueue(message("m1", recipient(1)))
        outbox.enqueue(message("m2", recipient(2)))

        assertEquals(1, outbox.pending(recipient(1)).size)
        assertEquals(1, outbox.pending(recipient(2)).size)
    }
}
