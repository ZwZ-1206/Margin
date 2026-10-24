package com.liubai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FocusSessionEntity::class,
        TagEntity::class,
        AppUsageDailyEntity::class,
        TimerCheckpointEntity::class,
        CollectCursorEntity::class,
        AppLimitEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class LiubaiDatabase : RoomDatabase() {
    abstract fun focusDao(): FocusDao
    abstract fun usageDao(): UsageDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun cursorDao(): CursorDao
    abstract fun limitDao(): LimitDao

    companion object {
        @Volatile
        private var instance: LiubaiDatabase? = null

        /** v1 → v2：app_limits 新增 hardLock 列（硬锁开关，默认关闭） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN hardLock INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun get(context: Context): LiubaiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, LiubaiDatabase::class.java, "liubai.db")
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
