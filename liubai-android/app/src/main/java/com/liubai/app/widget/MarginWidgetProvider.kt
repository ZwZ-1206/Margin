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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 桌面小部件：RemoteViews 实现（无额外依赖）。
 * 小 2x2 与中 4x1 两种尺寸，展示今日屏幕 / 专注 / 拿起次数。
 *
 * 更新时机：系统定时（30 分钟）、App 打开、每次采集完成、开机、精确监控轮询。
 * 点击打开 App（用包管理器启动 Intent，避免 R8 混淆后类名找不到）。
 */
open class MarginWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    override fun onEnabled(context: Context) = updateAll(context)

    companion object {

        fun updateAll(context: Context) {
            val app = context.applicationContext
            // onUpdate 跑在主线程，读库 + 查 UsageStats 会卡 UI，统一放后台
            CoroutineScope(Dispatchers.IO).launch {
                val data = runCatching { WidgetData.snapshot(app) }.getOrNull() ?: return@launch
                val mgr = AppWidgetManager.getInstance(app) ?: return@launch
                providers(app).forEach { (provider, layoutId) ->
                    val ids = mgr.getAppWidgetIds(provider)
                    if (ids.isEmpty()) return@forEach
                    val views = build(app, layoutId, data)
                    ids.forEach { id -> mgr.updateAppWidget(id, views) }
                }
            }
        }

        private fun providers(context: Context): List<Pair<ComponentName, Int>> = listOf(
            ComponentName(context, MarginWidgetProvider::class.java) to
                com.liubai.app.R.layout.widget_small,
            ComponentName(context, MarginWidgetMediumProvider::class.java) to
                com.liubai.app.R.layout.widget_medium,
        )

        private fun build(context: Context, layoutId: Int, data: WidgetData.Snapshot): RemoteViews {
            val views = RemoteViews(context.packageName, layoutId)
            views.setTextViewText(
                com.liubai.app.R.id.widget_screen,
                DurationFormat.chinese(data.screenMs),
            )
            views.setTextViewText(
                com.liubai.app.R.id.widget_focus,
                "专注 ${DurationFormat.chinese(data.focusMs)}",
            )
            if (layoutId == com.liubai.app.R.layout.widget_medium) {
                views.setTextViewText(com.liubai.app.R.id.widget_pickup, "${data.pickups}")
            }
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            if (launch != null) {
                views.setOnClickPendingIntent(
                    android.R.id.background,
                    PendingIntent.getActivity(
                        context, 0, launch,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            return views
        }
    }
}

/** 中尺寸（4x1）小部件，与小尺寸共用更新逻辑 */
class MarginWidgetMediumProvider : MarginWidgetProvider()
