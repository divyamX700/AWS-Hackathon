package com.sankatsetu.app.mesh.protocol

import java.io.ByteArrayOutputStream

/**
 * Signed presence beacon. TLV-encoded payload carried inside a
 * [MessageType.ANNOUNCE] packet. Format ported from Bitchat's
 * `AnnouncementPacket` (public domain).
 *
 * Sent every 4s while isolated, backing off to 15-30s jittered once
 * connected (see [com.sankatsetu.app.mesh.router.MessageRouter]).
 *
 * Publishing [noisePublicKey] and [signingPublicKey] in cleartext here is a
 * known metadata leak inherited from Bitchat's design — see
 * docs/concepts/ble-mesh-protocol.md#metadata-leakage. It's what lets a peer
 * recognize you across reconnects without a server, at the cost of a passive
 * listener being able to do the same.
 */
data class AnnouncementPacket(
    val nickname: String,
    val noisePublicKey: ByteArray,
    val signingPublicKey: ByteArray,
    /** Up to 10 peer IDs this node currently has a live link to (topology hint, 60s freshness). */
    val directNeighbors: List<ByteArray> = emptyList()
) {
    private enum class Tlv(val id: Byte) {
        NICKNAME(0x01), NOISE_KEY(0x02), SIGNING_KEY(0x03), NEIGHBORS(0x04)
    }

    fun encode(): ByteArray? {
        val nicknameBytes = nickname.toByteArray(Charsets.UTF_8)
        if (nicknameBytes.size > 255 || noisePublicKey.size > 255 || signingPublicKey.size > 255) return null

        val out = ByteArrayOutputStream()
        writeTlv(out, Tlv.NICKNAME.id, nicknameBytes)
        writeTlv(out, Tlv.NOISE_KEY.id, noisePublicKey)
        writeTlv(out, Tlv.SIGNING_KEY.id, signingPublicKey)

        if (directNeighbors.isNotEmpty()) {
            val capped = directNeighbors.take(10)
            val neighborBytes = ByteArrayOutputStream()
            for (n in capped) neighborBytes.write(if (n.size == 8) n else n.copyOf(8))
            val bytes = neighborBytes.toByteArray()
            if (bytes.size <= 255) writeTlv(out, Tlv.NEIGHBORS.id, bytes)
        }
        return out.toByteArray()
    }

    companion object {
        private fun writeTlv(out: ByteArrayOutputStream, type: Byte, value: ByteArray) {
            out.write(type.toInt())
            out.write(value.size)
            out.write(value)
        }

        fun decode(data: ByteArray): AnnouncementPacket? {
            var offset = 0
            var nickname: String? = null
            var noiseKey: ByteArray? = null
            var signingKey: ByteArray? = null
            var neighbors: List<ByteArray> = emptyList()

            while (offset + 2 <= data.size) {
                val type = data[offset]; offset += 1
                val length = data[offset].toInt() and 0xFF; offset += 1
                if (offset + length > data.size) return null
                val value = data.copyOfRange(offset, offset + length)
                offset += length

                when (type) {
                    Tlv.NICKNAME.id -> nickname = String(value, Charsets.UTF_8)
                    Tlv.NOISE_KEY.id -> noiseKey = value
                    Tlv.SIGNING_KEY.id -> signingKey = value
                    Tlv.NEIGHBORS.id -> {
                        if (length > 0 && length % 8 == 0) {
                            neighbors = (0 until length / 8).map { i -> value.copyOfRange(i * 8, i * 8 + 8) }
                        }
                    }
                    else -> Unit // unknown TLV — skip, forward-compatible
                }
            }
            if (nickname == null || noiseKey == null || signingKey == null) return null
            return AnnouncementPacket(nickname, noiseKey, signingKey, neighbors)
        }
    }
}
