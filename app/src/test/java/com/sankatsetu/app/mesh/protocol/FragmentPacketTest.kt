package com.sankatsetu.app.mesh.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FragmentPacketTest {

    @Test
    fun `encode-decode round-trips a single fragment`() {
        val fragment = FragmentPacket(fragmentId = 123456789L, index = 2, total = 5, chunk = "hello".toByteArray())
        val decoded = FragmentPacket.decode(fragment.encode())
        assertEquals(fragment.fragmentId, decoded!!.fragmentId)
        assertEquals(fragment.index, decoded.index)
        assertEquals(fragment.total, decoded.total)
        assertArrayEquals(fragment.chunk, decoded.chunk)
    }

    @Test
    fun `split reconstructs the exact original bytes when concatenated in order`() {
        val original = ByteArray(1200) { (it % 251).toByte() } // bigger than one chunk
        val fragments = FragmentPacket.split(original, chunkSize = 469)

        assertEquals(3, fragments.size) // ceil(1200/469) = 3
        assertEquals(fragments[0].fragmentId, fragments[1].fragmentId) // same ID across the set
        assertEquals(fragments[0].fragmentId, fragments[2].fragmentId)
        fragments.forEachIndexed { i, f -> assertEquals(i, f.index) }
        fragments.forEach { assertEquals(3, it.total) }

        val reconstructed = fragments.flatMap { it.chunk.toList() }.toByteArray()
        assertArrayEquals(original, reconstructed)
    }

    @Test
    fun `decode rejects a header too short to contain the fixed fields`() {
        assertNull(FragmentPacket.decode(ByteArray(5)))
    }

    @Test
    fun `decode rejects an index out of range for its own total`() {
        val bogus = FragmentPacket(fragmentId = 1L, index = 0, total = 1, chunk = ByteArray(0)).encode()
        // Header layout: fragmentId[0..7], index[8..9], total[10..11].
        // Corrupt total's low byte (already 0 at [10], 1 at [11]) to 0.
        bogus[11] = 0
        assertNull(FragmentPacket.decode(bogus))
    }
}
