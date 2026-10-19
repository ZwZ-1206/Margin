package com.liubai.domain.model

/** 采集到的一条原始使用事件（已由 data 层从系统事件映射） */
data class UsageEvent(
    val type: EventType,
    val packageName: String,
    val timestamp: Long,
)

enum class EventType { APP_RESUMED, APP_PAUSED, SCREEN_OFF, SCREEN_ON }

/** 前台区间 */
data class UsageInterval(
    val packageName: String,
    val startTs: Long,
    val endTs: Long,
)

/** 单应用日聚合（date 为 yyyy-MM-dd） */
data class AppUsageDaily(
    val date: String,
    val packageName: String,
    val totalMs: Long,
    val openCount: Int,
    val estimatedMs: Long = 0L,
)
