package com.liubai.data.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.liubai.core.common.DateKeys
import com.liubai.data.di.ServiceLocator
import com.liubai.data.usage.AppLabelResolver
import com.liubai.data.usage.UsageStatsReader

/** 小部件数据源。放在 data 层，避免 app 模块直接依赖 Room 类型。 */
object WidgetData {

    /** 大尺寸小部件的「使用时长 TOP N」条目 */
    data class TopApp(
        val packageName: String,
        val label: String,
        val totalMs: Long,
        /** 应用图标（已缩放到 24dp），取不到时为 null */
        val icon: Bitmap?,
    )

    data class Snapshot(
        val screenMs: Long,
        val focusMs: Long,
        val pickups: Int,
        /** 今日使用时长 TOP 3（已排除留白自身，按耗时降序） */
        val topApps: List<TopApp> = emptyList(),
    )

    /**
     * 轻量快照：只读 Room + 使用情况，不碰 PackageManager 图标。
     * 2x2 / 4x1 用这份——桌面绑定窗口期要的是「快」。
     */
    suspend fun snapshot(context: Context): Snapshot = base(context).toSnapshot(emptyList())

    /**
     * 带 TOP N 的完整快照：仅 4x3 大部件使用。
     * 图标加载走 PackageManager，冷启动可能要数百毫秒到数秒，
     * 因此与轻量路径分离，调用方应自行加超时保护。
     */
    suspend fun snapshotWithTop(context: Context, n: Int = 3): Snapshot {
        val b = base(context)
        return b.toSnapshot(topApps(context, b.rows, n))
    }

    private data class Base(
        val screenMs: Long,
        val focusMs: Long,
        val pickups: Int,
        val rows: List<com.liubai.data.db.AppUsageDailyEntity>,
    ) {
        fun toSnapshot(top: List<TopApp>) = Snapshot(screenMs, focusMs, pickups, top)
    }

    private suspend fun base(context: Context): Base {
        ServiceLocator.init(context)
        val today = DateKeys.today()
        val db = ServiceLocator.database
        val rows = db.usageDao().listRange(today, today)
        val screenMs = rows.sumOf { it.totalMs }
        val focusMs = db.focusDao()
            .listBetween(DateKeys.startOfDay(today), DateKeys.endOfDay(today))
            .sumOf { it.durationMs }
        val pickups = UsageStatsReader(context).pickupCountToday()
        return Base(screenMs, focusMs, pickups, rows)
    }

    /** TOP N：排除留白自身与空记录，图标就地转换（data 层持有 android.graphics，无跨层泄漏） */
    private fun topApps(
        context: Context,
        rows: List<com.liubai.data.db.AppUsageDailyEntity>,
        n: Int,
    ): List<TopApp> {
        val self = context.packageName
        val picked = rows
            .asSequence()
            .filter { it.packageName != self && it.totalMs > 0 }
            .sortedByDescending { it.totalMs }
            .take(n)
            .toList()
        if (picked.isEmpty()) return emptyList()
        // 只构造一次 resolver：图标查询是本次最重的步骤，避免每个应用各建一份
        val resolver = AppLabelResolver(context)
        return picked.map { row ->
            TopApp(
                packageName = row.packageName,
                label = runCatching { resolver.label(row.packageName) }
                    .getOrDefault(row.packageName),
                totalMs = row.totalMs,
                icon = iconBitmap(context, resolver, row.packageName),
            )
        }
    }

    private fun iconBitmap(
        context: Context,
        resolver: AppLabelResolver,
        packageName: String,
    ): Bitmap? = runCatching {
        val drawable = resolver.icon(packageName) ?: return null
        val px = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(canvas)
        bitmap
    }.getOrNull()
}
