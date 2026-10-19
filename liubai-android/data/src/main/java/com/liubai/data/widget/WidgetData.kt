package com.liubai.data.widget

import android.content.Context
import com.liubai.core.common.DateKeys
import com.liubai.data.di.ServiceLocator
import com.liubai.data.usage.UsageStatsReader

/** 小部件数据源。放在 data 层，避免 app 模块直接依赖 Room 类型。 */
object WidgetData {

    data class Snapshot(val screenMs: Long, val focusMs: Long, val pickups: Int)

    suspend fun snapshot(context: Context): Snapshot {
        ServiceLocator.init(context)
        val today = DateKeys.today()
        val db = ServiceLocator.database
        val screenMs = db.usageDao().listRange(today, today).sumOf { it.totalMs }
        val focusMs = db.focusDao()
            .listBetween(DateKeys.startOfDay(today), DateKeys.endOfDay(today))
            .sumOf { it.durationMs }
        val pickups = UsageStatsReader(context).pickupCountToday()
        return Snapshot(screenMs, focusMs, pickups)
    }
}
