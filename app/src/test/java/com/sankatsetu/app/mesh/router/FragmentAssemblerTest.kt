package com.sankatsetu.app.mesh.router

import com.sankatsetu.app.mesh.protocol.FragmentPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FragmentAssemblerTest {

    @Test
    fun `reassembles once every fragment has arrived, in any arrival order`() {
        val assembler = FragmentAssembler()
        val original = ByteArray(1000) { it.toByte() }
        val fragments = FragmentPacket.split(original, chunkSize = 400).shuffled(kotlin.random.Random(42))

        var result: ByteArray? = null
        for (f in fragments) {
            val r = assembler.addFragment(f)
            if (r != null) result = r
        }
        assertArrayEquals(original, result)
    }

    @Test
    fun `returns null until the last fragment arrives`() {
        val assembler = FragmentAssembler()
        val fragments = FragmentPacket.split(ByteArray(1000), chunkSize = 400)
        assertNull(assembler.addFragment(fragments[0]))
        assertNull(assembler.addFragment(fragments[1]))
        // third (last) fragment completes it — not asserted here, covered above
    }

    @Test
    fun `duplicate fragment does not complete or corrupt the assembly`() {
        val assembler = FragmentAssembler()
        val fragments = FragmentPacket.split(ByteArray(900), chunkSize = 400) // 3 fragments
        assembler.addFragment(fragments[0])
        assertNull(assembler.addFragment(fragments[0])) // replay of the same fragment: not new progress
        assertNull(assembler.addFragment(fragments[1]))
        val result = assembler.addFragment(fragments[2])
        assertArrayEquals(ByteArray(900), result)
    }

    @Test
    fun `expired assembly restarts fresh rather than mixing with stale chunks`() {
        var clock = 0L
        val assembler = FragmentAssembler(timeoutMillis = 1000L, now = { clock })
        val fragments = FragmentPacket.split(ByteArray(900), chunkSize = 400)

        assembler.addFragment(fragments[0])
        clock = 5000L // well past the timeout
        // A fresh fragment 0 for the same ID should restart the assembly, not
        // silently combine with the abandoned attempt from a "different" logical send.
        assertNull(assembler.addFragment(fragments[0]))
        assertNull(assembler.addFragment(fragments[1]))
        val result = assembler.addFragment(fragments[2])
        assertArrayEquals(ByteArray(900), result)
    }

    @Test
    fun `bounded concurrent assemblies evict the oldest`() {
        val assembler = FragmentAssembler(maxConcurrent = 2)
        // Start 3 independent multi-fragment assemblies, only ever sending fragment 0 of each.
        val a = FragmentPacket.split(ByteArray(900), chunkSize = 400)
        val b = FragmentPacket.split(ByteArray(900), chunkSize = 400)
        val c = FragmentPacket.split(ByteArray(900), chunkSize = 400)

        assembler.addFragment(a[0])
        assembler.addFragment(b[0])
        assembler.addFragment(c[0]) // should evict 'a's in-progress assembly (oldest)

        // Completing 'a' now should require starting over — its earlier fragment 0 was evicted.
        assertNull(assembler.addFragment(a[1]))
        assertNull(assembler.addFragment(a[2])) // still incomplete: fragment 0 was lost to eviction
    }
}
