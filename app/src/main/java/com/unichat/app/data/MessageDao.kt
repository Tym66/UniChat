package com.unichat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp ASC")
    fun observeByContact(contactId: Long): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(msg: Message): Long

    /** 批量插入(hook 进程经 provider 写入) */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(msgs: List<Message>): List<Long>

    @Query("SELECT * FROM messages WHERE platform = :platform AND platformMsgId = :platformMsgId LIMIT 1")
    suspend fun findByPlatformId(platform: String, platformMsgId: String): Message?

    /** 已读同步:把某个联系人某个平台的消息全部标记已读 */
    @Query("UPDATE messages SET isRead = 1 WHERE contactId = :contactId AND platform = :platform")
    suspend fun markPlatformRead(contactId: Long, platform: String)

    @Query("UPDATE messages SET isRead = 1 WHERE contactId = :contactId")
    suspend fun markAllRead(contactId: Long)

    /** 平台最近一条消息(用于跨平台已读同步判断) */
    @Query("SELECT * FROM messages WHERE contactId = :contactId AND platform = :platform ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestOfPlatform(contactId: Long, platform: String): Message?
}
