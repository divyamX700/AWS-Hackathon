package com.sankatsetu.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A known mesh peer. Primary key is the 8-byte peer ID (see
 * [com.sankatsetu.app.mesh.crypto.Identity.peerId]), stored as a base64
 * string since Room needs a comparable/indexable column type.
 *
 * Full schema (officer_signature, nostr_pubkey, is_favorite) lands with
 * F5/F6 in Day 3 — see docs/PRD.md §9.1. Day 1 only needs enough to show a
 * peer list with hop counts.
 */
@Entity(tableName = "peers", indices = [Index("lastSeen")])
data class PeerEntity(
    @PrimaryKey val peerIdBase64: String,
    val noiseStaticKeyBase64: String,
    val nickname: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val lastKnownHopCount: Int
)
