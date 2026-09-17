package com.sankatsetu.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) // dedup: SeenMessageCache already filtered relays, but belt-and-suspenders on the DB
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE threadPeerIdBase64 = :peerIdBase64 ORDER BY sentAt ASC")
    fun observeThread(peerIdBase64: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadPeerIdBase64 IS NULL ORDER BY sentAt ASC")
    fun observePublicChannel(): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateStatus(messageId: String, status: String)
}
