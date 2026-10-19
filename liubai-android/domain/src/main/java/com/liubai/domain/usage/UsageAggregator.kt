package com.liubai.domain.usage

import com.liubai.domain.model.AppUsageDaily
import com.liubai.domain.model.EventType
import com.liubai.domain.model.UsageEvent
import com.liubai.domain.model.UsageInterval
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 使用统计归约算法（纯函数，可 JVM 单测）。
 *
 * 规则：
 *  - RESUMED→PAUSED 配对成区间；同包重叠区间取并集（覆盖分屏/快速切换）
 *  - SCREEN_OFF 关闭所有未闭合区间（锁屏即截断）
 *  - 跨午夜区间按本地时区切分到两天
 *  - 乱序事件先排序；幂等由 data 层按 (package, startTs) 去重保证
 */
object UsageAggregator {

    fun toIntervals(events: List<UsageEvent>): List<UsageInterval> {
        if (events.isEmpty()) return emptyList()
        val sorted = events.sortedBy { it.timestamp }
        val open = mutableMapOf<String, Long>() // package -> open ts
        val raw = mutableListOf<UsageInterval>()
        for (e in sorted) {
            when (e.type) {
                EventType.APP_RESUMED -> open.putIfAbsent(e.packageName, e.timestamp)
                EventType.APP_PAUSED -> {
                    val start = open.remove(e.packageName)
                    if (start != null && e.timestamp > start) {
                        raw += UsageInterval(e.packageName, start, e.timestamp)
                    }
                }
                EventType.SCREEN_OFF -> {
                    open.forEach { (pkg, start) ->
                        if (e.timestamp > start) raw += UsageInterval(pkg, start, e.timestamp)
                    }
                    open.clear()
                }
                EventType.SCREEN_ON -> Unit // 亮屏本身不产生使用区间，由 RESUMED 记录
            }
        }
        return mergeOverlaps(raw)
    }

    /** 同包重叠/相接区间取并集 */
    fun mergeOverlaps(intervals: List<UsageInterval>): List<UsageInterval> =
        intervals.groupBy { it.packageName }.values.flatMap { perPkg ->
            val sorted = perPkg.sortedBy { it.startTs }
            val merged = mutableListOf<UsageInterval>()
            for (iv in sorted) {
                val last = merged.lastOrNull()
                if (last != null && iv.startTs <= last.endTs) {
                    merged[merged.lastIndex] = last.copy(endTs = maxOf(last.endTs, iv.endTs))
                } else {
                    merged += iv
                }
            }
            merged
        }.sortedWith(compareBy({ it.startTs }, { it.packageName }))

    /** 按本地日期切分并聚合；openCount 以区间起始日计 */
    fun toDaily(
        intervals: List<UsageInterval>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<String, Map<String, Long>> {
        val result = mutableMapOf<String, MutableMap<String, Long>>()
        val counts = mutableMapOf<String, MutableMap<String, Int>>()
        for (iv in intervals) {
            var cursor = iv.startTs
            val zdt = Instant.ofEpochMilli(cursor).atZone(zone)
            var day = zdt.toLocalDate()
            while (cursor < iv.endTs) {
                val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val sliceEnd = minOf(iv.endTs, nextMidnight)
                val key = DateTimeFormatter.ISO_LOCAL_DATE.format(day)
                val perApp = result.getOrPut(key) { mutableMapOf() }
                perApp[iv.packageName] = (perApp[iv.packageName] ?: 0L) + (sliceEnd - cursor)
                if (cursor == iv.startTs) {
                    val c = counts.getOrPut(key) { mutableMapOf() }
                    c[iv.packageName] = (c[iv.packageName] ?: 0) + 1
                }
                cursor = sliceEnd
                day = day.plusDays(1)
            }
        }
        return result
    }

    /** 屏幕点亮总时长 − 已覆盖区间时长 > 阈值即判定缺口（由 data 层喂入点亮时长） */
    fun detectGapMs(screenOnMs: Long, coveredMs: Long): Long =
        (screenOnMs - coveredMs).coerceAtLeast(0L)
}

/** 便于 data 层落库的行 */
fun Map<String, Map<String, Long>>.toRows(
    estimatedPerDay: Map<String, Long> = emptyMap(),
): List<AppUsageDaily> =
    flatMap { (date, apps) ->
        apps.map { (pkg, ms) ->
            AppUsageDaily(
                date = date,
                packageName = pkg,
                totalMs = ms,
                openCount = 0,
                estimatedMs = estimatedPerDay[date] ?: 0L,
            )
        }
    }
