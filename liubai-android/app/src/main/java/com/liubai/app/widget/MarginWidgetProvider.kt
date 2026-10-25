package com.liubai.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.liubai.core.common.DurationFormat
import com.liubai.data.widget.WidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 桌面小部件：RemoteViews 实现（无额外依赖）。
 * 小 2x2（屏幕/专注）、中 4x1（屏幕/专注/拿起）、大 4x3（概览 + 使用时长 TOP 5）。
 *
 * 更新时机：系统定时（30 分钟）、App 打开、每次采集完成、开机、精确监控轮询。
 * 点击打开 App（用包管理器启动 Intent，避免 R8 混淆后类名找不到）；
 * 大部件点击某个应用行可直接打开该应用。
 *
 * 兼容性原则（OEM 桌面报「载入窗口小部件时出现问题」的根因都在桌面进程侧）：
 * 1. 布局只用 RemoteViews 官方支持的 View 与 API 1 属性，不用 previewLayout；
 * 2. 本条是兜底：更新链路上任何异常都必须吞掉——App 进程一旦在绑定/更新窗口期
 *    崩溃，桌面就会弹「载入窗口小部件时出现问题」，且用户侧看不出是 App 崩了。
 */
open class MarginWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // 绑定窗口期：厂商桌面 bindAppWidgetId 后会等 provider 回传可渲染的 RemoteViews，
        // 等不到（或等太久）就报「载入窗口小部件时出现问题」。取数要走 IO，冷启动可能数秒，
        // 因此先用同步动作推一份零 IO 的占位内容，保证桌面瞬间拿到东西，再异步补真实数据。
        pushPlaceholder(context, appWidgetManager, appWidgetIds)
        updateAll(context)
    }

    override fun onEnabled(context: Context) = updateAll(context)

    companion object {

        /** 共用一个受监督的 scope，避免每次广播都新建且异常逃逸到线程默认处理器 */
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** 重数据（TOP 5 图标）取数超时上限：超时就保持轻量版，绝不拖住桌面 */
        private const val TOP_TIMEOUT_MS = 4_000L

        /** 本 provider 对应的布局（占位推送用，零 IO） */
        private fun layoutId(provider: Any): Int = when (provider) {
            is MarginWidgetMediumProvider -> com.liubai.app.R.layout.widget_medium
            is MarginWidgetLargeProvider -> com.liubai.app.R.layout.widget_large
            else -> com.liubai.app.R.layout.widget_small
        }

        private fun pushPlaceholder(
            context: Context,
            mgr: AppWidgetManager,
            ids: IntArray,
        ) {
            val pkg = context.packageName
            val layout = layoutId(context)
            ids.forEach { id ->
                runCatching { mgr.updateAppWidget(id, RemoteViews(pkg, layout)) }
            }
        }

        fun updateAll(context: Context) {
            val app = context.applicationContext
            widgetScope.launch {
                runCatching {
                    val mgr = runCatching { AppWidgetManager.getInstance(app) }.getOrNull()
                        ?: return@runCatching
                    // 轻量数据（三个尺寸共用）：只读 Room + 使用情况，不含图标
                    val data = runCatching { WidgetData.snapshot(app) }.getOrNull()
                    providers(app).forEach { (provider, layoutId) ->
                        val ids = runCatching { mgr.getAppWidgetIds(provider) }.getOrNull()
                        if (ids == null || ids.isEmpty()) return@forEach
                        val views = if (data != null) {
                            runCatching { build(app, layoutId, data) }.getOrNull()
                        } else {
                            null
                        } ?: RemoteViews(app.packageName, layoutId)
                        // 单个 widgetId 推送失败不影响其他实例；updateAppWidget 抛异常
                        // 会直接拖垮 App 进程，必须逐个兜住
                        ids.forEach { id ->
                            runCatching { mgr.updateAppWidget(id, views) }
                        }
                    }
                    // 重数据（仅 4x3 大部件）：TOP 5 图标要走 PackageManager，慢，
                    // 单独一条路径 + 超时保护，失败就保持轻量版，不影响其他尺寸
                    val largeProvider = providers(app).last().first
                    val largeIds = runCatching { mgr.getAppWidgetIds(largeProvider) }.getOrNull()
                    if (largeIds != null && largeIds.isNotEmpty()) {
                        val withTop = runCatching {
                            withTimeout(TOP_TIMEOUT_MS) { WidgetData.snapshotWithTop(app) }
                        }.getOrNull()
                        if (withTop != null) {
                            val largeViews = runCatching {
                                build(app, com.liubai.app.R.layout.widget_large, withTop)
                            }.getOrNull()
                            if (largeViews != null) {
                                largeIds.forEach { id ->
                                    runCatching { mgr.updateAppWidget(id, largeViews) }
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun providers(context: Context): List<Pair<ComponentName, Int>> = listOf(
            ComponentName(context, MarginWidgetProvider::class.java) to
                com.liubai.app.R.layout.widget_small,
            ComponentName(context, MarginWidgetMediumProvider::class.java) to
                com.liubai.app.R.layout.widget_medium,
            ComponentName(context, MarginWidgetLargeProvider::class.java) to
                com.liubai.app.R.layout.widget_large,
        )

        /** 大部件单行视图的 id 组合 */
        private data class TopRow(
            val row: Int, val icon: Int, val name: Int, val time: Int, val bar: Int,
        )

        private val TOP_ROWS = listOf(
            TopRow(
                com.liubai.app.R.id.top_row_1,
                com.liubai.app.R.id.top_icon_1,
                com.liubai.app.R.id.top_name_1,
                com.liubai.app.R.id.top_time_1,
                com.liubai.app.R.id.top_bar_1,
            ),
            TopRow(
                com.liubai.app.R.id.top_row_2,
                com.liubai.app.R.id.top_icon_2,
                com.liubai.app.R.id.top_name_2,
                com.liubai.app.R.id.top_time_2,
                com.liubai.app.R.id.top_bar_2,
            ),
            TopRow(
                com.liubai.app.R.id.top_row_3,
                com.liubai.app.R.id.top_icon_3,
                com.liubai.app.R.id.top_name_3,
                com.liubai.app.R.id.top_time_3,
                com.liubai.app.R.id.top_bar_3,
            ),
            TopRow(
                com.liubai.app.R.id.top_row_4,
                com.liubai.app.R.id.top_icon_4,
                com.liubai.app.R.id.top_name_4,
                com.liubai.app.R.id.top_time_4,
                com.liubai.app.R.id.top_bar_4,
            ),
            TopRow(
                com.liubai.app.R.id.top_row_5,
                com.liubai.app.R.id.top_icon_5,
                com.liubai.app.R.id.top_name_5,
                com.liubai.app.R.id.top_time_5,
                com.liubai.app.R.id.top_bar_5,
            ),
        )

        private fun build(context: Context, layoutId: Int, data: WidgetData.Snapshot): RemoteViews {
            val views = RemoteViews(context.packageName, layoutId)
            views.setTextViewText(
                com.liubai.app.R.id.widget_screen,
                DurationFormat.chinese(data.screenMs),
            )
            // 中部件上方已有「今日专注」标签、大部件是行内标签，都不要再重复「专注」二字
            views.setTextViewText(
                com.liubai.app.R.id.widget_focus,
                if (layoutId == com.liubai.app.R.layout.widget_medium) {
                    DurationFormat.chinese(data.focusMs)
                } else {
                    "专注 ${DurationFormat.chinese(data.focusMs)}"
                },
            )
            when (layoutId) {
                com.liubai.app.R.layout.widget_small ->
                    views.setTextViewText(com.liubai.app.R.id.widget_pickup, "拿起 ${data.pickups}")
                com.liubai.app.R.layout.widget_medium ->
                    views.setTextViewText(com.liubai.app.R.id.widget_pickup, "${data.pickups}")
                com.liubai.app.R.layout.widget_large ->
                    views.setTextViewText(com.liubai.app.R.id.widget_pickup, "今日拿起 ${data.pickups} 次")
            }
            if (layoutId == com.liubai.app.R.layout.widget_large) {
                fillTopApps(context, views, data)
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

        /** 大部件：填充 TOP 5，缺数据时显示空状态；点击应用行直达该应用 */
        private fun fillTopApps(context: Context, views: RemoteViews, data: WidgetData.Snapshot) {
            val top = data.topApps
            val leader = top.firstOrNull()?.totalMs ?: 0L
            TOP_ROWS.forEachIndexed { index, row ->
                val app = top.getOrNull(index)
                if (app == null) {
                    views.setViewVisibility(row.row, View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(row.row, View.VISIBLE)
                views.setTextViewText(row.name, app.label)
                views.setTextViewText(row.time, DurationFormat.chinese(app.totalMs))
                views.setImageViewBitmap(row.icon, app.icon)
                // 占比条用 ImageView + ClipDrawable：RemoteViews 的 setInt 反射动作
                // 调用 ImageView.setImageLevel(0~10000) 驱动裁剪宽度。
                // 比 ProgressBar 更保守——ImageView 是 RemoteViews 支持最完整的控件，
                // 且不依赖任何平台样式在桌面进程里的解析
                views.setInt(
                    row.bar,
                    "setImageLevel",
                    if (leader > 0) (app.totalMs * 10000 / leader).toInt() else 0,
                )
                // 点击这一行 → 打开对应应用（取不到启动 Intent 时静默跳过）
                val target = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                if (target != null) {
                    views.setOnClickPendingIntent(
                        row.row,
                        PendingIntent.getActivity(
                            context,
                            100 + index, // requestCode 互不相同，否则三行会互相覆盖
                            target,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }
            }
            views.setViewVisibility(
                com.liubai.app.R.id.top_empty,
                if (top.isEmpty()) View.VISIBLE else View.GONE,
            )
        }
    }
}

/** 中尺寸（4x1）小部件，与小尺寸共用更新逻辑 */
class MarginWidgetMediumProvider : MarginWidgetProvider()

/** 大尺寸（4x3）小部件：概览 + 使用时长 TOP 5 */
class MarginWidgetLargeProvider : MarginWidgetProvider()
