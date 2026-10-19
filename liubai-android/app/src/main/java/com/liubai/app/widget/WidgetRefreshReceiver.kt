package com.liubai.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 接收来自 data 层的显式广播并刷新小部件。
 * 这样小部件不必受 updatePeriodMillis 30 分钟下限的约束：
 * 每次采集完成、精确监控轮询、专注状态变化都能立即刷新。
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.liubai.action.WIDGET_REFRESH") return
        MarginWidgetProvider.updateAll(context)
    }
}
