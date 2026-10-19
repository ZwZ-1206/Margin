package com.liubai.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** 后台任务调度：周期采集 + 每日报告 */
object WorkScheduler {

    fun scheduleUsageCollect(context: Context) {
        // 15 分钟是 WorkManager 周期任务的系统下限
        val request = PeriodicWorkRequestBuilder<UsageCollectWorker>(
            15, TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UsageCollectWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** 就寝提醒：按配置时间每天一次。关闭时取消。 */
    fun scheduleBedtime(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork("bedtime")
            return
        }
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delayMin = ((target.timeInMillis - now.timeInMillis) / 60000).coerceAtLeast(0)
        val request = PeriodicWorkRequestBuilder<BedtimeWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMin, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork("bedtime", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleDailyReport(context: Context) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delayMin = (target.timeInMillis - now.timeInMillis) / 60000
        val request = PeriodicWorkRequestBuilder<DailyReportWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMin, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily-report",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
