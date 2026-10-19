package com.liubai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FocusSessionEntity::class,
        TagEntity::class,
        AppUsageDailyEntity::class,
        TimerCheckpointEntity::class,
        CollectCursorEntity::class,
        AppLimitEntity::class,
    ],
    version = 1,
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

        fun get(context: Context): LiubaiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, LiubaiDatabase::class.java, "liubai.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
