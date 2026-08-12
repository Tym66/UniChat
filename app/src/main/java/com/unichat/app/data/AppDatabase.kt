package com.unichat.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Contact::class, Message::class, ModuleInfo::class, SyncStat::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun moduleDao(): ModuleDao
    abstract fun syncStatDao(): SyncStatDao

    companion object {
        /** v1 -> v2:新增 sync_stats 表(平台同步状态) */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_stats` (" +
                        "`platform` TEXT NOT NULL, " +
                        "`hookInstalled` INTEGER NOT NULL, " +
                        "`lastSyncAt` INTEGER NOT NULL, " +
                        "`msgCount` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`platform`))"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "unichat.db"
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
