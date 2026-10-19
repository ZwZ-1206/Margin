package com.liubai.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** 熬夜提醒：到就寝时间推送一次温和提示 */
class BedtimeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mgr = applicationContext.getSystemService(NotificationManager::class.java) ?: return Result.success()
        mgr.createNotificationChannel(
            NotificationChannel("bedtime", "就寝提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
        mgr.notify(
            9003,
            NotificationCompat.Builder(applicationContext, "bedtime")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("该睡了")
                .setContentText("屏幕明天还在。今晚，就到这里吧。")
                .setAutoCancel(true)
                .build(),
        )
        return Result.success()
    }
}
