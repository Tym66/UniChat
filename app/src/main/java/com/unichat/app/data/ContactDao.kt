package com.unichat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY lastTime DESC")
    fun observeAll(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE name LIKE '%' || :kw || '%' OR phone LIKE '%' || :kw || '%' OR remark LIKE '%' || :kw || '%' ORDER BY lastTime DESC")
    fun search(kw: String): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): Contact?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(contact: Contact): Long

    @Update
    suspend fun update(contact: Contact)

    /** 按平台标识查找联系人 */
    @Query("SELECT * FROM contacts WHERE wechatId = :platformId OR douyinId = :platformId OR phone = :platformId LIMIT 1")
    suspend fun findByPlatformId(platformId: String): Contact?

    @Query("UPDATE contacts SET unreadCount = unreadCount + 1, lastMessage = :lastMsg, lastTime = :ts, updatedAt = :now WHERE id = :contactId")
    suspend fun bump(contactId: Long, lastMsg: String, ts: Long, now: Long)

    @Query("UPDATE contacts SET unreadCount = 0, updatedAt = :now WHERE id = :contactId")
    suspend fun markRead(contactId: Long, now: Long)
}
