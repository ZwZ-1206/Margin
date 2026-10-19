package com.liubai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mode: String,
    val tagId: Long?,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val status: String,
    val cycles: Int,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val emoji: String,
    val createdAt: Long,
    val archived: Boolean = false,
)

/** 应用日使用聚合（date = yyyy-MM-dd） */
@Entity(tableName = "app_usage_daily", primaryKeys = ["date", "packageName"])
data class AppUsageDailyEntity(
    val date: String,
    val packageName: String,
    val totalMs: Long,
    val openCount: Int,
    val estimatedMs: Long = 0L,
)

/** 专注引擎断点（进程死亡恢复） */
@Entity(tableName = "timer_checkpoint")
data class TimerCheckpointEntity(
    @PrimaryKey val id: Int = 0,
    val mode: String,
    val phase: String,
    val running: Boolean,
    val tagId: Long?,
    val focusTargetMs: Long,
    val focusElapsedMs: Long,
    val segmentStartMs: Long,
    val breakTargetMs: Long,
    val breakElapsedMs: Long,
    val breakSegmentStartMs: Long,
    val cycleIndex: Int,
    val sessionFocusMs: Long,
    val sessionStartedAt: Long,
    val savedAt: Long,
)

/** 采集水位线：增量事件拉取的起点 */
@Entity(tableName = "collect_cursor")
data class CollectCursorEntity(
    @PrimaryKey val id: Int = 0,
    val lastEventTs: Long,
    val updatedAt: Long,
)
