package com.liubai.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.liubai.core.common.DateKeys
import com.liubai.core.common.DurationFormat
import com.liubai.data.di.ServiceLocator
import com.liubai.data.lock.AppLockManager
import com.liubai.data.usage.AppLabelResolver
import com.liubai.data.usage.UsageStatsReader
import com.liubai.domain.model.ChallengeConfig

/**
 * 干预检查：单应用限额 + 挑战（连续使用 / 拿起次数）。
 *
 * 触发路径有两条：
 *  1. UsageCollectWorker 采集后（周期兜底，15 分钟级）
 *  2. 系统应用计时器回调 notifyLimitReached（分钟级，零轮询）
 */
object InterventionChecker {

    private const val CHANNEL = "intervention"
    private const val WARN_LEAD_MS = 5 * 60_000L // 限额前 5 分钟预警

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "限额与挑战提醒", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    suspend fun check(context: Context) {
        ensureChannel(context)
        val settings = ServiceLocator.settingsRepository.current()
        if (!settings.monitorEnabled) return
        val today = DateKeys.today()
        // 已触发硬锁的应用：通知文案要说明「已锁定」，而不是只说超限
        val locked = runCatching { AppLockManager.lockedPackages(context) }
            .getOrDefault(emptySet())
        checkLimits(context, today, locked)
        checkChallenges(context, today, settings.challenge)
    }

    private suspend fun checkLimits(context: Context, date: String, locked: Set<String>) {
        val enabled = ServiceLocator.limitsRepository.listLimits().filter { it.enabled }
        if (enabled.isEmpty()) return
        val rows = ServiceLocator.database.usageDao().listRange(date, date)
            .associateBy { it.packageName }
        val notified = NotifiedStore(context)
        val resolver = AppLabelResolver(context)

        for (limit in enabled) {
            val used = rows[limit.packageName]?.totalMs ?: 0L
            val name = resolver.label(limit.packageName)
            when {
                used >= limit.limitMs -> {
                    if (notified.mark(limit.packageName + ":over", date)) {
                        val isLocked = limit.packageName in locked
                        notify(
                            context,
                            id = limit.packageName.hashCode(),
                            title = if (isLocked) "$name 已锁定" else "$name 今日已达限额",
                            body = if (isLocked) {
                                "已使用 ${DurationFormat.chinese(used)}，限额 ${DurationFormat.chinese(limit.limitMs)}。" +
                                    "再打开它需要输入解锁密码；忘记密码可在留白 设置 > 干预 修改，不需要旧密码。"
                            } else {
                                "已使用 ${DurationFormat.chinese(used)}，限额 ${DurationFormat.chinese(limit.limitMs)}。剩下的时间，留给别的吧。"
                            },
                        )
                    }
                }
                used >= limit.limitMs - WARN_LEAD_MS -> {
                    if (notified.mark(limit.packageName + ":warn", date)) {
                        notify(
                            context,
                            id = limit.packageName.hashCode(),
                            title = "$name 还剩 5 分钟",
                            body = "已使用 ${DurationFormat.chinese(used)}，接近你设的 ${DurationFormat.chinese(limit.limitMs)}。",
                        )
                    }
                }
            }
        }
    }

    private suspend fun checkChallenges(context: Context, date: String, cfg: ChallengeConfig) {
        val reader = UsageStatsReader(context)
        val notified = NotifiedStore(context)

        if (cfg.continuousEnabled) {
            val (pkg, ms) = reader.currentForeground() ?: (null to 0L)
            if (pkg != null && ms >= cfg.continuousMin * 60_000L) {
                if (notified.mark("continuous", date)) {
                    val name = AppLabelResolver(context).label(pkg)
                    notify(
                        context,
                        id = 9001,
                        title = "已连续使用 ${cfg.continuousMin} 分钟",
                        body = "当前在 $name。起来动一动，喝口水——屏幕可以等。",
                    )
                }
            }
        }

        if (cfg.pickupEnabled) {
            val count = reader.pickupCountToday()
            if (count >= cfg.pickupCount) {
                if (notified.mark("pickup", date)) {
                    notify(
                        context,
                        id = 9002,
                        title = "今天已拿起手机 $count 次",
                        body = "超过你设的 ${cfg.pickupCount} 次。不用戒掉，只是看见了。",
                    )
                }
            }
        }
    }

    private fun notify(context: Context, id: Int, title: String, body: String) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build(),
        )
    }

    /** 是否已提醒过（同一天同一项只提醒一次），存在 SharedPreferences */
    private class NotifiedStore(private val context: Context) {
        private val prefs by lazy { context.getSharedPreferences("intervention_notified", Context.MODE_PRIVATE) }

        fun mark(key: String, day: String): Boolean {
            val k = "$key:$day"
            if (prefs.getBoolean(k, false)) return false
            prefs.edit().putBoolean(k, true).apply()
            return true
        }
    }
}
