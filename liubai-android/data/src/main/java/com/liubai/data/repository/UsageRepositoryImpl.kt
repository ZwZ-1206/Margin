package com.liubai.data.repository

import com.liubai.core.common.DateKeys
import com.liubai.data.db.LiubaiDatabase
import com.liubai.data.usage.UsageStatsReader
import com.liubai.domain.model.AppUsageDaily
import com.liubai.domain.repository.CollectionResult
import com.liubai.domain.repository.UsageRepository
import com.liubai.domain.usage.UsageAggregator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UsageRepositoryImpl(
    private val db: LiubaiDatabase,
    private val reader: UsageStatsReader,
) : UsageRepository {

    private val usageDao = db.usageDao()
    private val cursorDao = db.cursorDao()

    override fun observeDailyUsage(date: String): Flow<List<AppUsageDaily>> =
        usageDao.observeDay(date).map { list -> list.map { it.toDomain() } }

    override fun observeUsageRange(fromDate: String, toDate: String): Flow<List<AppUsageDaily>> =
        usageDao.observeRange(fromDate, toDate).map { list -> list.map { it.toDomain() } }

    /**
     * 增量采集：从水位线拉取新事件 → 归约 → 与库中当日行合并 upsert。
     * 合并策略：以「系统事件重算值」为准覆盖当日行（区间并集后不重复计数）。
     */
    override suspend fun collectNow(): CollectionResult {
        val cursor = cursorDao.get()
        val from = cursor?.lastEventTs ?: 0L
        val events = reader.readEvents(from)
        if (events.isEmpty()) {
            // 无新事件也要推进水位线，避免下次重复拉取窗口
            val now = System.currentTimeMillis()
            cursorDao.put(
                com.liubai.data.db.CollectCursorEntity(
                    lastEventTs = now,
                    updatedAt = now,
                )
            )
            return CollectionResult(0, 0)
        }

        val intervals = UsageAggregator.toIntervals(events)
        val fresh = UsageAggregator.toDaily(intervals)

        // 对每个受影响日期：库中旧行 + 本次新行按包名取并集最大值，避免跨次双算
        var gaps = 0
        for ((date, apps) in fresh) {
            val existing = usageDao.listRange(date, date).associateBy { it.packageName }
            val merged = apps.map { (pkg, ms) ->
                val prev = existing[pkg]?.totalMs ?: 0L
                // 水位线保证各次事件窗口不相交，直接累加；同窗内区间已由归约器并集
                val total = prev + ms
                AppUsageDailyEntity_local(date, pkg, total, existing[pkg]?.openCount ?: 0)
            }
            usageDao.upsertAll(merged.map { it.toEntity() })
            gaps += estimateGap(date)
        }

        val maxTs = events.maxOf { it.timestamp }
        cursorDao.put(com.liubai.data.db.CollectCursorEntity(lastEventTs = maxTs, updatedAt = System.currentTimeMillis()))
        return CollectionResult(events.size, gaps)
    }

    /** 缺口估计：系统原始总时长 − 已入库时长 > 10 分钟记一次缺口 */
    private suspend fun estimateGap(date: String): Int {
        if (date != DateKeys.today()) return 0
        val covered = usageDao.listRange(date, date).sumOf { it.totalMs }
        val system = reader.systemTotalToday()
        return if (system - covered > 10 * 60_000L) 1 else 0
    }

    override suspend fun lastCollectAt(): Long = cursorDao.get()?.updatedAt ?: 0L

    override suspend fun setLastCollectAt(ts: Long) {
        cursorDao.put(com.liubai.data.db.CollectCursorEntity(lastEventTs = ts, updatedAt = ts))
    }
}

/** 局部数据类，避免在合并处直接暴露 Entity 构造细节 */
private data class AppUsageDailyEntity_local(
    val date: String,
    val packageName: String,
    val totalMs: Long,
    val openCount: Int,
)

private fun AppUsageDailyEntity_local.toEntity() =
    com.liubai.data.db.AppUsageDailyEntity(
        date = date,
        packageName = packageName,
        totalMs = totalMs,
        openCount = openCount,
    )

/** AppUsageDaily 构造辅助（合并路径） */
private fun com.liubai.data.db.AppUsageDailyEntity.toEntity(): com.liubai.data.db.AppUsageDailyEntity = this
