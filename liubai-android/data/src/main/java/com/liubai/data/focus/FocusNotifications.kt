package com.liubai.data.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.liubai.core.common.DurationFormat
import com.liubai.domain.model.TimerPhase

/** 专注前台通知：阶段文案 + 剩余时间 + 快捷操作 */
object FocusNotifications {

    const val CHANNEL_ID = "focus_progress"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "专注进行中",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "显示当前专注阶段与剩余时间"
                    setShowBadge(false)
                    enableVibration(false)
                }
            )
        }
    }

    fun build(
        context: Context,
        phase: TimerPhase,
        running: Boolean,
        remainingMs: Long,
        cycleIndex: Int,
    ): Notification {
        val title = when (phase) {
            TimerPhase.FOCUS -> "专注中 · 第 ${cycleIndex + 1} 轮"
            TimerPhase.BREAK -> "休息一下 · 第 $cycleIndex 轮完成"
            TimerPhase.FINISHED -> "专注完成"
            TimerPhase.IDLE -> "「留白」"
        }
        val text = if (phase == TimerPhase.FINISHED) {
            "本轮专注已完成，去记录这一刻吧"
        } else {
            "剩余 ${DurationFormat.timer(remainingMs)}" + if (!running) "（已暂停）" else ""
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, Class.forName("com.liubai.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(running)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (phase == TimerPhase.FOCUS || phase == TimerPhase.BREAK) {
            builder.addAction(
                0,
                if (running) "暂停" else "继续",
                servicePendingIntent(context, if (running) FocusService.ACTION_PAUSE else FocusService.ACTION_RESUME),
            )
            builder.addAction(0, "结束", servicePendingIntent(context, FocusService.ACTION_STOP))
        }
        return builder.build()
    }

    private fun servicePendingIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, FocusService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
