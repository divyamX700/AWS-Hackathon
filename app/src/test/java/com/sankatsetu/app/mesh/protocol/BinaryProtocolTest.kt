package com.sankatsetu.app.mesh.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryProtocolTest {

    private fun senderId(byte: Int) = ByteArray(8) { byte.toByte() }

    @Test
    fun `broadcast packet round-trips without padding`() {
        val packet = MeshPacket(
            type = MessageType.MESSAGE,
            ttl = MeshPacket.DEFAULT_TTL,
            timestamp = 1_700_000_000_000L,
            senderId = senderId(0xAB),
            payload = "hello mesh".toByteArray(Charsets.UTF_8)
        )

        val encoded = BinaryProtocol.encode(packet, padding = false)
        val decoded = BinaryProtocol.decode(encoded)

        assertEquals(packet, decoded)
    }

    @Test
    fun `directed packet with recipient and signature round-trips`() {
        val packet = MeshPacket(
            type = MessageType.NOISE_ENCRYPTED,
            ttl = 6,
            timestamp = 1_700_000_001_234L,
            senderId = senderId(0x01),
            recipientId = senderId(0x02),
            payload = ByteArray(64) { it.toByte() },
            signature = ByteArray(64) { (it * 3).toByte() }
        )

        val encoded = BinaryProtocol.encode(packet, padding = false)
        val decoded = BinaryProtocol.decode(encoded)

        assertEquals(packet, decoded)
        assertArrayEquals(packet.recipientId, decoded!!.recipientId)
        assertArrayEquals(packet.signature, decoded.signature)
    }

    @Test
    fun `variable-length ECDSA-sized signature round-trips`() {
        // ECDSA-P256 DER signatures vary ~68-72 bytes (not Ed25519's fixed
        // 64) — see docs/adr/0007-ecdsa-not-ed25519.md. The signature field
        // is length-prefixed specifically so 70 (or 68, or 72) all work.
        for (sigLength in intArrayOf(68, 70, 71, 72)) {
            val packet = MeshPacket(
                type = MessageType.ANNOUNCE,
                ttl = 7,
                timestamp = 999L,
                senderId = senderId(0x07),
                payload = "announce payload".toByteArray(),
                signature = ByteArray(sigLength) { (it + sigLength).toByte() }
            )
            val encoded = BinaryProtocol.encode(packet, padding = false)
            val decoded = BinaryProtocol.decode(encoded)
            assertEquals("failed for signature length $sigLength", packet, decoded)
        }
    }

    @Test
    fun `padded frame decodes back to the same logical packet`() {
        val packet = MeshPacket(
            type = MessageType.NOISE_HANDSHAKE,
            ttl = 7,
            timestamp = 42L,
            senderId = senderId(0x11),
            recipientId = senderId(0x22),
            payload = "short handshake payload".toByteArray()
        )

        val padded = BinaryProtocol.encode(packet, padding = true)
        // A Noise frame should be padded up to one of the fixed buckets, not left at its natural length.
        assertTrue("expected padded size to be one of the fixed buckets", padded.size == 256 || padded.size == 512)

        val decoded = BinaryProtocol.decode(padded)
        assertEquals(packet, decoded)
    }

    @Test
    fun `decrementing ttl to zero stops further relay eligibility`() {
        val packet = MeshPacket(
            type = MessageType.MESSAGE,
            ttl = 1,
            timestamp = 0L,
            senderId = senderId(0x99),
            payload = ByteArray(0)
        )
        val decremented = packet.decremented()
        assertEquals(0, decremented.ttl.toInt())

        val flooredAtZero = decremented.decremented()
        assertEquals(0, flooredAtZero.ttl.toInt()) // never goes negative
    }

    @Test
    fun `malformed short buffer fails to decode instead of throwing`() {
        val garbage = ByteArray(5) { it.toByte() }
        assertNull(BinaryProtocol.decode(garbage))
    }

    @Test
    fun `signing bytes are stable across different ttl values`() {
        val base = MeshPacket(
            type = MessageType.ANNOUNCE,
            ttl = 7,
            timestamp = 123L,
            senderId = senderId(0x05),
            payload = "announce".toByteArray()
        )
        val afterOneHop = base.decremented()

        // Signature must survive relaying — ttl is excluded from the signed bytes.
        assertArrayEquals(base.signingBytes(), afterOneHop.signingBytes())
    }
}
