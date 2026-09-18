package com.sankatsetu.app.mesh.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IouPacketTest {

    @Test
    fun `encode then decode round-trips all fields`() {
        val original = IouPacket(
            amountPaise = 15000,
            memo = "bus fare",
            signature = byteArrayOf(1, 2, 3, 4, 5)
        )
        val decoded = IouPacket.decode(original.encode()!!)

        assertNotNull(decoded)
        assertEquals(original.iouId, decoded!!.iouId)
        assertEquals(original.amountPaise, decoded.amountPaise)
        assertEquals(original.memo, decoded.memo)
        assertEquals(original.createdAt, decoded.createdAt)
        assertEquals(original.signature.toList(), decoded.signature.toList())
    }

    @Test
    fun `signingBytes excludes the signature so it stays stable across signing`() {
        val unsigned = IouPacket(amountPaise = 500, memo = "chai", signature = ByteArray(0))
        val signed = unsigned.copy(signature = byteArrayOf(9, 9, 9))

        assertEquals(unsigned.signingBytes().toList(), signed.signingBytes().toList())
    }

    @Test
    fun `garbage bytes fail to decode instead of crashing`() {
        assertNull(IouPacket.decode(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `settlement ack round-trips`() {
        val ack = IouSettlementAck(iouId = "abc-123", settledAt = 1234567890L)
        val decoded = IouSettlementAck.decode(ack.encode())

        assertEquals(ack, decoded)
    }
}
