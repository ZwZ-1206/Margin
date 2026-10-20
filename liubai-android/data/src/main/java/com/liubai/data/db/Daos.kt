package com.liubai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :from AND startedAt <= :to ORDER BY startedAt DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :from AND startedAt <= :to ORDER BY startedAt DESC")
    suspend fun listBetween(from: Long, to: Long): List<FocusSessionEntity>

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Query("UPDATE focus_sessions SET durationMs = :durationMs WHERE id = :id")
    suspend fun updateDuration(id: Long, durationMs: Long)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM tags WHERE archived = 0 ORDER BY createdAt ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    suspend fun listTags(): List<TagEntity>

    @Insert
    suspend fun insertTag(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: Long)
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM app_usage_daily WHERE date = :date ORDER BY totalMs DESC")
    fun observeDay(date: String): Flow<List<AppUsageDailyEntity>>

    @Query("SELECT * FROM app_usage_daily WHERE date BETWEEN :from AND :to ORDER BY date, totalMs DESC")
    fun observeRange(from: String, to: String): Flow<List<AppUsageDailyEntity>>

    @Query("SELECT * FROM app_usage_daily WHERE date BETWEEN :from AND :to")
    suspend fun listRange(from: String, to: String): List<AppUsageDailyEntity>

    @Upsert
    suspend fun upsertAll(rows: List<AppUsageDailyEntity>)

    @Query("SELECT COALESCE(SUM(totalMs), 0) FROM app_usage_daily WHERE date = :date")
    fun observeDayTotal(date: String): Flow<Long>

    @Query("DELETE FROM app_usage_daily WHERE date < :before")
    suspend fun purgeBefore(before: String)
}

@Dao
interface LimitDao {
    @Query("SELECT * FROM app_limits")
    fun observeAll(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits")
    suspend fun listAll(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits WHERE packageName = :pkg")
    suspend fun get(pkg: String): AppLimitEntity?

    @Upsert
    suspend fun upsert(limit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Dao
interface CheckpointDao {
    @Query("SELECT * FROM timer_checkpoint WHERE id = 0")
    fun observe(): Flow<TimerCheckpointEntity?>

    @Query("SELECT * FROM timer_checkpoint WHERE id = 0")
    suspend fun load(): TimerCheckpointEntity?

    @Upsert
    suspend fun save(cp: TimerCheckpointEntity)

    @Query("DELETE FROM timer_checkpoint")
    suspend fun clear()
}

@Dao
interface CursorDao {
    @Query("SELECT * FROM collect_cursor WHERE id = 0")
    suspend fun get(): CollectCursorEntity?

    @Upsert
    suspend fun put(cursor: CollectCursorEntity)
}
