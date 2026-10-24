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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

class UsageRepositoryImpl(
    private val db: LiubaiDatabase,
    private val reader: UsageStatsReader,
) : UsageRepository {

    private val usageDao = db.usageDao()
    private val cursorDao = db.cursorDao()

    /**
     * 采集互斥锁。
     *
     * 之前没有锁：首页进入、MonitorService 每 60 秒、后台 Worker 都可能同时调用 collectNow，
     * 两个协程读到同一个水位线、算出同一批事件、各自执行「库里旧值 + 本次值」，
     * 今日时长就被算成两倍（20:47 显示 21 小时 45 分钟就是这么来的）。
     */
    private val collectMutex = Mutex()

    override fun observeDailyUsage(date: String): Flow<List<AppUsageDaily>> =
        usageDao.observeDay(date).map { list -> list.map { it.toDomain() } }

    override fun observeUsageRange(fromDate: String, toDate: String): Flow<List<AppUsageDaily>> =
        usageDao.observeRange(fromDate, toDate).map { list -> list.map { it.toDomain() } }

    /**
     * 采集入口：今日全量重算 + 历史增量补齐。
     *
     * 今日采用「从 0 点重算并覆盖」而不是累加——重算是幂等的，跑多少次结果都一样，
     * 既能避免重复计数，也能把之前已经算错的数据自动修正回真实值。
     */
    override suspend fun collectNow(): CollectionResult = collectMutex.withLock {
        val today = refreshToday()
        val history = collectIncremental()
        CollectionResult(
            eventsProcessed = today.events + history.events,
            gapsDetected = today.gaps + history.gaps,
        )
    }

    /**
     * 今日全量重算：读当天 0 点至今的全部事件 → 归约成区间 → 覆盖写库。
     * openCount 沿用库里旧值（重算拿不到可靠的启动次数）。
     */
    private suspend fun refreshToday(): Tally {
        val zone = ZoneId.systemDefault()
        val today = DateKeys.today()
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

        val events = reader.readEvents(dayStart)
        val existing = usageDao.listRange(today, today)

        // 读不到事件但库里已有数据（权限被撤、或刚过零点）时，不要用空值把数据抹掉
        if (events.isEmpty() && existing.isNotEmpty()) return Tally(0, 0)

        val daily = UsageAggregator
            .toDaily(UsageAggregator.toIntervals(events), zone)[today]
            .orEmpty()
        val openCounts = existing.associate { it.packageName to it.openCount }

        usageDao.deleteDay(today)
        if (daily.isNotEmpty()) {
            usageDao.upsertAll(
                daily.map { (pkg, ms) ->
                    com.liubai.data.db.AppUsageDailyEntity(
                        date = today,
                        packageName = pkg,
                        totalMs = ms,
                        openCount = openCounts[pkg] ?: 0,
                    )
                }
            )
        }
        return Tally(events.size, estimateGap(today))
    }

    /**
     * 历史日期增量：从水位线拉取新事件 → 归约 → 累加到对应日期。
     * 今日已被 [refreshToday] 覆盖，这里跳过，避免两套逻辑互相污染。
     */
    private suspend fun collectIncremental(): Tally {
        val today = DateKeys.today()
        val cursor = cursorDao.get()
        val lastEventTs = cursor?.lastEventTs ?: 0L
        // +1：上次已处理到 lastEventTs，从它的下一毫秒开始，避免边界事件被重复归约
        val from = if (lastEventTs > 0) lastEventTs + 1 else 0L
        val events = reader.readEvents(from)

        if (events.isEmpty()) {
            // 无新事件也要推进水位线，避免下次重复拉取同一窗口
            val now = System.currentTimeMillis()
            if (lastEventTs <= 0) {
                cursorDao.put(
                    com.liubai.data.db.CollectCursorEntity(lastEventTs = now, updatedAt = now)
                )
            }
            return Tally(0, 0)
        }

        val intervals = UsageAggregator.toIntervals(events)
        val fresh = UsageAggregator.toDaily(intervals)

        var gaps = 0
        for ((date, apps) in fresh) {
            if (date == today) continue
            val existing = usageDao.listRange(date, date).associateBy { it.packageName }
            val merged = apps.map { (pkg, ms) ->
                val prev = existing[pkg]?.totalMs ?: 0L
                AppUsageDailyEntity_local(date, pkg, prev + ms, existing[pkg]?.openCount ?: 0)
            }
            usageDao.upsertAll(merged.map { it.toEntity() })
            gaps += estimateGap(date)
        }

        val maxTs = events.maxOf { it.timestamp }
        cursorDao.put(
            com.liubai.data.db.CollectCursorEntity(
                lastEventTs = maxTs,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return Tally(events.size, gaps)
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

/** 单次采集的统计结果 */
private data class Tally(val events: Int, val gaps: Int)

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
