package com.liubai.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.liubai.core.common.DateKeys
import com.liubai.core.common.DurationFormat
import com.liubai.data.di.ServiceLocator

/** 每日报告：22:00 推送今日屏幕时间与专注汇总 */
class DailyReportWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val today = DateKeys.today()
        // 直接读库聚合（worker 无需 Flow）
        val rows = com.liubai.data.db.LiubaiDatabase.get(applicationContext)
            .usageDao().listRange(today, today)
        val screenMs = rows.sumOf { it.totalMs }
        val sessions = com.liubai.data.db.LiubaiDatabase.get(applicationContext)
            .focusDao().listBetween(DateKeys.startOfDay(today), DateKeys.endOfDay(today))
        val focusMs = sessions.sumOf { it.durationMs }

        val mgr = applicationContext.getSystemService(NotificationManager::class.java)
        mgr?.createNotificationChannel(
            NotificationChannel("daily_report", "每日报告", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val text = "今日屏幕时间 ${DurationFormat.chinese(screenMs)} · 专注 ${DurationFormat.chinese(focusMs)}"
        mgr?.notify(
            1002,
            NotificationCompat.Builder(applicationContext, "daily_report")
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle("今日「留白」报告")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build(),
        )
        return Result.success()
    }
}
