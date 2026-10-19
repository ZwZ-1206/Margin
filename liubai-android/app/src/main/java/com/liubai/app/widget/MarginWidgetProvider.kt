package com.liubai.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.liubai.core.common.DurationFormat
import com.liubai.data.widget.WidgetData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * 桌面小部件：RemoteViews 实现（无额外依赖）。
 * 小 2x2 与中 4x1 两种尺寸，展示今日屏幕 / 专注 / 拿起次数。
 *
 * 更新时机：系统定时（30 分钟）、App 打开、每次采集完成、开机。点击打开 App。
 */
open class MarginWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    override fun onEnabled(context: Context) = updateAll(context)

    companion object {

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val data = runBlocking(Dispatchers.IO) { WidgetData.snapshot(context) }
            for (entry in providers(context)) {
                val (provider, layoutId) = entry
                val ids = mgr.getAppWidgetIds(provider)
                if (ids.isEmpty()) continue
                for (id in ids) {
                    mgr.updateAppWidget(id, build(context, layoutId, data))
                }
            }
        }

        private fun providers(context: Context) = listOf(
            ComponentName(context, MarginWidgetProvider::class.java) to com.liubai.app.R.layout.widget_small,
            ComponentName(context, MarginWidgetMediumProvider::class.java) to com.liubai.app.R.layout.widget_medium,
        )

        private fun build(context: Context, layoutId: Int, data: WidgetData.Snapshot): RemoteViews {
            val views = RemoteViews(context.packageName, layoutId)
            views.setTextViewText(com.liubai.app.R.id.widget_screen, DurationFormat.chinese(data.screenMs))
            views.setTextViewText(com.liubai.app.R.id.widget_focus, "专注 ${DurationFormat.chinese(data.focusMs)}")
            if (layoutId == com.liubai.app.R.layout.widget_medium) {
                views.setTextViewText(com.liubai.app.R.id.widget_pickup, "${data.pickups}")
            }
            val intent = Intent(context, Class.forName("com.liubai.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                android.R.id.background,
                PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return views
        }
    }
}

/** 中尺寸（4x1）小部件，与小尺寸共用更新逻辑 */
class MarginWidgetMediumProvider : MarginWidgetProvider()
