package com.sankatsetu.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: PeerEntity)

    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun observeAll(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE peerIdBase64 = :peerIdBase64 LIMIT 1")
    suspend fun getByPeerId(peerIdBase64: String): PeerEntity?

    @Query("UPDATE peers SET lastSeen = :timestamp, lastKnownHopCount = :hopCount WHERE peerIdBase64 = :peerIdBase64")
    suspend fun touch(peerIdBase64: String, timestamp: Long, hopCount: Int)
}
