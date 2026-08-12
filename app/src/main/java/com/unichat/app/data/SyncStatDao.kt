package com.unichat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStatDao {

    @Query("SELECT * FROM sync_stats")
    fun observeAll(): Flow<List<SyncStat>>

    @Query("SELECT * FROM sync_stats WHERE platform = :platform LIMIT 1")
    suspend fun get(platform: String): SyncStat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: SyncStat)
}
