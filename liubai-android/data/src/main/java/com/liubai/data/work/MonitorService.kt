package com.liubai.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.liubai.data.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 精确监控模式（默认关闭，用户在设置页开启）。
 *
 * 为什么需要它：WorkManager 周期任务最短 15 分钟，且受 Doze 影响；
 * 亮屏 / 解锁这类广播在 Android 8+ 不能静态注册，只能由存活进程动态接收。
 *
 * 锁屏时的行为（明确写在这里，别再猜）：
 *  - 进程不会死，但 CPU 休眠后 delay() 基于 uptimeMillis 不再推进，
 *    所以"每分钟一次"在锁屏期间会停摆。这是有意的：锁屏时前台是 SystemUI，
 *    任何真实应用都不产生使用时长，继续轮询只会白耗电。
 *  - 解锁/亮屏的瞬间会立即补一次采集，锁屏前最后不到一分钟的用量也在这一次里入账。
 *  - 额外挂 SCREEN_OFF：在熄屏那一刻先结算一次，把"锁屏前这段时间"落库，
 *    避免用户解锁后看到的数据比实际少一截。
 *
 * 代价：常驻一条通知 + 亮屏期间每 60 秒一次轻量查询（不持 WakeLock）。
 */
class MonitorService : Service() {

    companion object {
        const val ACTION_START = "com.liubai.action.MONITOR_START"
        const val ACTION_STOP = "com.liubai.action.MONITOR_STOP"
        const val CHANNEL = "monitor"
        const val NOTIFICATION_ID = 1004
        private const val POLL_MS = 60_000L

        fun intent(context: Context, start: Boolean) =
            Intent(context, MonitorService::class.java)
                .setAction(if (start) ACTION_START else ACTION_STOP)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: kotlinx.coroutines.Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_SCREEN_OFF,
                -> tick()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ensureChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("精确监控已开启")
                .setContentText("每分钟检查一次限额与挑战，可在设置页关闭")
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build(),
        )
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON).apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var last = 0L
            while (isActive) {
                if (SystemClock.elapsedRealtime() - last >= POLL_MS) {
                    tick()
                    last = SystemClock.elapsedRealtime()
                }
                delay(5_000)
            }
        }
        tick()
    }

    private fun tick() {
        scope.launch {
            runCatching {
                ServiceLocator.usageRepository.collectNow()
                InterventionChecker.check(this@MonitorService)
                WidgetBroadcaster.refresh(this@MonitorService)
            }
        }
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "精确监控", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/** 通知宿主（app 模块）刷新桌面小部件；显式广播，绕过 30 分钟系统下限 */
object WidgetBroadcaster {
    const val ACTION_REFRESH = "com.liubai.action.WIDGET_REFRESH"

    fun refresh(context: Context) {
        context.sendBroadcast(Intent(ACTION_REFRESH).setPackage(context.packageName))
    }
}
