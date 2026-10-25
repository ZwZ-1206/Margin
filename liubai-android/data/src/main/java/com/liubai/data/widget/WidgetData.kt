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

    suspend fun snapshot(context: Context): Snapshot {
        ServiceLocator.init(context)
        val today = DateKeys.today()
        val db = ServiceLocator.database
        val rows = db.usageDao().listRange(today, today)
        val screenMs = rows.sumOf { it.totalMs }
        val focusMs = db.focusDao()
            .listBetween(DateKeys.startOfDay(today), DateKeys.endOfDay(today))
            .sumOf { it.durationMs }
        val pickups = UsageStatsReader(context).pickupCountToday()
        return Snapshot(screenMs, focusMs, pickups, topApps(context, rows))
    }

    /** TOP 3：排除留白自身与空记录，图标就地转换（data 层持有 android.graphics，无跨层泄漏） */
    private fun topApps(
        context: Context,
        rows: List<com.liubai.data.db.AppUsageDailyEntity>,
    ): List<TopApp> {
        val self = context.packageName
        val resolver = AppLabelResolver(context)
        return rows
            .asSequence()
            .filter { it.packageName != self && it.totalMs > 0 }
            .sortedByDescending { it.totalMs }
            .take(3)
            .map { row ->
                TopApp(
                    packageName = row.packageName,
                    label = resolver.label(row.packageName),
                    totalMs = row.totalMs,
                    icon = iconBitmap(context, row.packageName),
                )
            }
            .toList()
    }

    private fun iconBitmap(context: Context, packageName: String): Bitmap? = runCatching {
        val drawable = AppLabelResolver(context).icon(packageName) ?: return null
        val px = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(canvas)
        bitmap
    }.getOrNull()
}
