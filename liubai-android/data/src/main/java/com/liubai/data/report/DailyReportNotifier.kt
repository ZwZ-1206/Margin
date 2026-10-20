package com.liubai.data.report

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.liubai.core.common.DateKeys
import com.liubai.core.common.DurationFormat
import com.liubai.data.di.ServiceLocator
import com.liubai.data.usage.AppLabelResolver
import java.time.LocalDate

/**
 * 次日打开 App 时推送「昨日报告」。
 *
 * 每次启动检查一次，同一自然日只推一次；昨天没有任何数据则不打扰。
 * 与 22:00 的定时日报互补：一个是「今天怎么样了」，一个是「昨天你过得如何」。
 */
object DailyReportNotifier {

    private const val CHANNEL = "daily_report"
    private const val PREFS = "daily_report_state"
    private const val NOTIFICATION_ID = 1010

    suspend fun notifyYesterdayIfNeeded(context: Context) {
        ServiceLocator.init(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = DateKeys.today()
        if (prefs.getString("shown_on", null) == today) return

        val yesterday = LocalDate.parse(today).minusDays(1).toString()
        val db = ServiceLocator.database
        val rows = db.usageDao().listRange(yesterday, yesterday)
        val screenMs = rows.sumOf { it.totalMs }
        val sessions = db.focusDao().listBetween(
            DateKeys.startOfDay(yesterday),
            DateKeys.endOfDay(yesterday),
        )
        val focusMs = sessions.sumOf { it.durationMs }
        if (screenMs <= 0 && focusMs <= 0) return

        val resolver = AppLabelResolver(context)
        val top = rows.maxByOrNull { it.totalMs }?.let { resolver.label(it.packageName) }
        val title = "昨天你用了 ${DurationFormat.chinese(screenMs)}"
        val body = buildString {
            append("专注 ${DurationFormat.chinese(focusMs)}")
            if (sessions.isNotEmpty()) append("，共 ${sessions.size} 次")
            if (top != null) append("。占用最多的是 $top。")
            append("今天想怎么过？")
        }

        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "每日报告", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val contentIntent = launch?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
        contentIntent?.let { builder.setContentIntent(it) }
        mgr.notify(NOTIFICATION_ID, builder.build())

        prefs.edit().putString("shown_on", today).apply()
    }
}
