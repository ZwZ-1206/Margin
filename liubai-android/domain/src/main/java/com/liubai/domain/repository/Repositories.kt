package com.liubai.domain.repository

import com.liubai.domain.model.AppLimit
import com.liubai.domain.model.AppSettings
import com.liubai.domain.model.AppUsageDaily
import com.liubai.domain.model.FocusDaily
import com.liubai.domain.model.FocusSessionRecord
import com.liubai.domain.model.Tag
import com.liubai.domain.model.TimerState
import kotlinx.coroutines.flow.Flow

/** 采集结果：处理事件数与检测到的数据缺口数 */
data class CollectionResult(val eventsProcessed: Int, val gapsDetected: Int)

interface FocusRepository {
    fun observeRecentSessions(limit: Int = 50): Flow<List<FocusSessionRecord>>
    fun observeSessionsBetween(fromDate: String, toDate: String): Flow<List<FocusSessionRecord>>
    fun observeTags(): Flow<List<Tag>>
    fun observeDaily(rangeDays: Int): Flow<List<FocusDaily>>
    suspend fun saveSession(record: FocusSessionRecord): Long
    suspend fun saveTag(tag: Tag): Long
    suspend fun deleteTag(id: Long)
    suspend fun loadCheckpoint(): TimerState?
    fun observeCheckpoint(): Flow<TimerState?>
    suspend fun saveCheckpoint(state: TimerState)
    suspend fun clearCheckpoint()
}

interface LimitsRepository {
    fun observeLimits(): Flow<List<AppLimit>>
    suspend fun setLimit(limit: AppLimit)
    suspend fun removeLimit(packageName: String)
    suspend fun listLimits(): List<AppLimit>
    suspend fun limitOf(packageName: String): AppLimit?
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface UsageRepository {
    fun observeDailyUsage(date: String): Flow<List<AppUsageDaily>>
    fun observeUsageRange(fromDate: String, toDate: String): Flow<List<AppUsageDaily>>
    /** 执行一次增量采集（事件驱动入口） */
    suspend fun collectNow(): CollectionResult
    suspend fun lastCollectAt(): Long
    suspend fun setLastCollectAt(ts: Long)
}

/**
 * 依赖容器接口。实现位于 :app（组合根），
 * 各 feature 通过工厂函数获取，避免 feature 之间互相依赖。
 */
interface AppContainer {
    val focusRepository: FocusRepository
    val settingsRepository: SettingsRepository
    val usageRepository: UsageRepository
    val limitsRepository: LimitsRepository
}
