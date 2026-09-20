package com.sankatsetu.app.mesh.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SosPacketTest {

    @Test
    fun `encode then decode round-trips all fields`() {
        val original = SosPacket(category = SosCategory.MEDICAL, createdAt = 1234567890L)
        val decoded = SosPacket.decode(original.encode()!!)

        assertNotNull(decoded)
        assertEquals(original.sosId, decoded!!.sosId)
        assertEquals(original.category, decoded.category)
        assertEquals(original.createdAt, decoded.createdAt)
        assertNull(decoded.latitude)
        assertNull(decoded.longitude)
    }

    @Test
    fun `encode then decode round-trips a real location fix`() {
        val original = SosPacket(category = SosCategory.FLOOD, createdAt = 1234567890L, latitude = 26.1445, longitude = 91.7362)
        val decoded = SosPacket.decode(original.encode()!!)

        assertNotNull(decoded)
        assertEquals(original.latitude, decoded!!.latitude)
        assertEquals(original.longitude, decoded.longitude)
    }

    @Test
    fun `every category round-trips through its wire value`() {
        for (category in SosCategory.entries) {
            val decoded = SosPacket.decode(SosPacket(category = category).encode()!!)
            assertEquals(category, decoded?.category)
        }
    }

    @Test
    fun `garbage bytes fail to decode instead of crashing`() {
        assertNull(SosPacket.decode(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `unknown category wire value fails to decode rather than defaulting silently`() {
        // Hand-craft a packet with an out-of-range category byte (0x99) —
        // a genuinely unknown category should never be silently coerced
        // into an existing one, since a wrong emergency category is worse
        // than no category at all.
        val idBytes = "id".toByteArray(Charsets.UTF_8)
        val bytes = byteArrayOf(0x00, idBytes.size.toByte()) + idBytes +
            byteArrayOf(0x01, 1, 0x99.toByte()) +
            byteArrayOf(0x02, 8, 0, 0, 0, 0, 0, 0, 0, 1)
        assertNull(SosPacket.decode(bytes))
    }
}
