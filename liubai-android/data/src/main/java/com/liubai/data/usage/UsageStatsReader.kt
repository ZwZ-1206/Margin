package com.liubai.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.liubai.domain.model.EventType
import com.liubai.domain.model.UsageEvent

/**
 * UsageStats 事件读取：把系统事件流映射为领域事件。
 * 依赖 ACTIVITY_RESUMED / ACTIVITY_PAUSED 配对；SCREEN_NON_INTERACTIVE 作为灭屏截断。
 */
class UsageStatsReader(private val context: Context) {

    private val usm: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /** 真正的授权检查（AppOps），而不是「服务是否存在」 */
    fun hasPermission(): Boolean = UsageAccess.isGranted(context)

    /** 拉取 [fromTs, now] 区间内的事件；[fromTs] 为 0 时回退查最近 24h */
    fun readEvents(fromTs: Long): List<UsageEvent> {
        val mgr = usm ?: return emptyList()
        val now = System.currentTimeMillis()
        val begin = if (fromTs > 0) fromTs else now - 24 * 3600_000L
        val events = mgr.queryEvents(begin, now) ?: return emptyList()
        val out = mutableListOf<UsageEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> EventType.APP_RESUMED

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> EventType.APP_PAUSED

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> EventType.SCREEN_OFF
                UsageEvents.Event.SCREEN_INTERACTIVE -> EventType.SCREEN_ON
                else -> null
            } ?: continue
            if (e.packageName == context.packageName) continue // 不计入自身
            out += UsageEvent(type = type, packageName = e.packageName, timestamp = e.timeStamp)
        }
        return out
    }

    /** 当前前台应用与已持续毫秒（无前台时返回 null） */
    fun currentForeground(): Pair<String, Long>? {
        val mgr = usm ?: return null
        val events = mgr.queryEvents(
            System.currentTimeMillis() - 12 * 3600_000L,
            System.currentTimeMillis(),
        ) ?: return null
        val e = UsageEvents.Event()
        var lastPkg: String? = null
        var lastTs = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    lastPkg = e.packageName; lastTs = e.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (e.packageName == lastPkg) { lastPkg = null }
                }
            }
        }
        val pkg = lastPkg ?: return null
        return pkg to (System.currentTimeMillis() - lastTs).coerceAtLeast(0)
    }

    /** 今日亮屏（拿起）次数 */
    fun pickupCountToday(): Int {
        val mgr = usm ?: return 0
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val events = mgr.queryEvents(cal.timeInMillis, System.currentTimeMillis()) ?: return 0
        val e = UsageEvents.Event()
        var count = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) count++
        }
        return count
    }

    /** 今日系统侧原始总时长（用于缺口检测的保守上界参考） */
    fun systemTotalToday(): Long {
        val mgr = usm ?: return 0L
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val stats = mgr.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis,
            System.currentTimeMillis(),
        ) ?: return 0L
        return stats.sumOf { it.totalTimeInForeground }
    }
}
