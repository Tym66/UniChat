package com.unichat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ModuleDao {

    @Query("SELECT * FROM modules ORDER BY downloads DESC, stars DESC")
    fun observeAll(): Flow<List<ModuleInfo>>

    @Query("SELECT * FROM modules WHERE category = :category ORDER BY downloads DESC, stars DESC")
    fun observeByCategory(category: String): Flow<List<ModuleInfo>>

    @Query("SELECT * FROM modules WHERE name LIKE '%' || :kw || '%' OR packageName LIKE '%' || :kw || '%' OR description LIKE '%' || :kw || '%' ORDER BY downloads DESC, stars DESC")
    fun search(kw: String): Flow<List<ModuleInfo>>

    @Query("SELECT * FROM modules WHERE sourceUrl = :url LIMIT 1")
    suspend fun findByUrl(url: String): ModuleInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(modules: List<ModuleInfo>)

    @Query("DELETE FROM modules")
    suspend fun clear()
}
