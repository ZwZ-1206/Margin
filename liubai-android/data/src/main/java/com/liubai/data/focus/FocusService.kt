package com.liubai.data.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.liubai.core.common.DurationFormat
import com.liubai.data.di.ServiceLocator
import com.liubai.domain.engine.EngineEvent
import com.liubai.domain.engine.FocusSessionEngine
import com.liubai.domain.model.FocusMode
import com.liubai.domain.model.FocusSessionRecord
import com.liubai.domain.model.PomodoroConfig
import com.liubai.domain.model.TimerPhase
import com.liubai.domain.model.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 专注前台服务：驱动 FocusSessionEngine 每秒 tick，
 * 维护通知、断点、白噪音，并在进程重建后恢复。
 *
 * 双保险：结束时通过 AlarmManager 精确闹钟登记 FocusAlarmReceiver，
 * 即使服务被系统杀死，也能归档会话并推送完成通知。
 */
class FocusService : Service() {

    companion object {
        const val ACTION_START = "com.liubai.action.FOCUS_START"
        const val ACTION_PAUSE = "com.liubai.action.FOCUS_PAUSE"
        const val ACTION_RESUME = "com.liubai.action.FOCUS_RESUME"
        const val ACTION_STOP = "com.liubai.action.FOCUS_STOP"
        const val ACTION_SET_NOISE = "com.liubai.action.FOCUS_NOISE"
        const val ACTION_RESTORE = "com.liubai.action.FOCUS_RESTORE"
        const val ACTION_DISMISS = "com.liubai.action.FOCUS_DISMISS"

        const val EXTRA_MODE = "mode"
        const val EXTRA_TAG_ID = "tagId"
        const val EXTRA_COUNTDOWN_MIN = "countdownMin"
        const val EXTRA_NOISE = "noise"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_RAIN_INTENSITY = "rain_intensity"

        private const val ALARM_REQUEST = 2001

        fun startIntent(context: Context, mode: FocusMode, tagId: Long?, countdownMin: Int): Intent =
            Intent(context, FocusService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_TAG_ID, tagId)
                .putExtra(EXTRA_COUNTDOWN_MIN, countdownMin)

        fun actionIntent(context: Context, action: String): Intent =
            Intent(context, FocusService::class.java).setAction(action)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null
    private var state: TimerState = TimerState()
    // 声音统一交给 SoundController（内置合成音色 + 用户导入音频）

    // 不再监听 SCREEN_OFF 自动暂停：专注的本意就是放下手机，
    // 锁屏恰恰是最该继续计时的动作。计时基于 elapsedRealtime，睡眠不影响准确性。

    override fun onCreate() {
        super.onCreate()
        FocusNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> handleStart(intent!!)
            ACTION_PAUSE, ACTION_RESUME, ACTION_STOP -> handleAction(action)
            ACTION_SET_NOISE -> handleNoise(intent!!)
            ACTION_RESTORE -> restore()
            ACTION_DISMISS -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> restore()
        }
        return START_STICKY
    }

    private fun handleStart(intent: Intent) = runCatching { doStart(intent) }
        .onFailure { android.util.Log.e("FocusService", "start failed", it) }

    private fun doStart(intent: Intent) {
        val mode = runCatching { FocusMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "") }
            .getOrDefault(FocusMode.POMODORO)
        val tagId = if (intent.hasExtra(EXTRA_TAG_ID)) intent.getLongExtra(EXTRA_TAG_ID, 0L) else null
        val countdownMin = intent.getIntExtra(EXTRA_COUNTDOWN_MIN, 30)
        scope.launch {
            val settings = ServiceLocator.settingsRepository.current()
            state = FocusSessionEngine.start(
                mode = mode,
                tagId = tagId,
                config = settings.pomodoro,
                countdownMin = countdownMin,
                nowElapsed = SystemClock.elapsedRealtime(),
                nowWall = System.currentTimeMillis(),
            )
            startForeground(FocusNotifications.NOTIFICATION_ID, notificationFor())
            scheduleEndAlarm()
            saveCheckpoint()
            startTicking()
            startNoiseIfEnabled()
        }
    }

    private fun handleAction(action: String) {
        val now = SystemClock.elapsedRealtime()
        when (action) {
            ACTION_PAUSE -> {
                state = FocusSessionEngine.pause(state, now)
                scope.launch { saveCheckpoint() }
            }
            ACTION_RESUME -> {
                state = FocusSessionEngine.resume(state, now)
                scope.launch { saveCheckpoint() }
            }
            ACTION_STOP -> {
                scope.launch { archiveAndStop() }
                return
            }
        }
        startForeground(FocusNotifications.NOTIFICATION_ID, notificationFor())
    }

    private fun handleNoise(intent: Intent) {
        val key = intent.getStringExtra(EXTRA_NOISE)
        val volume = intent.getIntExtra(EXTRA_VOLUME, 60) / 100f
        val intensity = intent.getIntExtra(EXTRA_RAIN_INTENSITY, 50) / 100f
        SoundController.play(this, key, volume, intensity)
    }

    /** 进程重建：加载断点、推进引擎、恢复通知 */
    private fun restore() {
        scope.launch {
            val saved = ServiceLocator.focusRepository.loadCheckpoint() ?: run {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            val settings = ServiceLocator.settingsRepository.current()
            val (restored, events) = FocusSessionEngine.restore(saved, SystemClock.elapsedRealtime(), settings.pomodoro)
            state = restored
            if (state.phase == TimerPhase.FINISHED) {
                events.filterIsInstance<EngineEvent.SessionFinished>().firstOrNull()?.let {
                    archive(restored, it.totalFocusMs, it.cycles, complete = true)
                }
                ServiceLocator.focusRepository.clearCheckpoint()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            startForeground(FocusNotifications.NOTIFICATION_ID, notificationFor())
            if (state.running) {
                startTicking()
                startNoiseIfEnabled()
            }
        }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            var lastCheckpoint = SystemClock.elapsedRealtime()
            while (isActive && state.isActive) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                val result = FocusSessionEngine.tick(state, now, pomodoroConfigCache)
                state = result.state
                result.events.forEach { ev ->
                    when (ev) {
                        is EngineEvent.SessionFinished -> {
                            archive(state, ev.totalFocusMs, ev.cycles, complete = true)
                            finishAndClear()
                            return@launch
                        }
                        is EngineEvent.FocusCycleCompleted -> saveCheckpoint()
                        is EngineEvent.BreakCompleted -> saveCheckpoint()
                    }
                }
                startForeground(FocusNotifications.NOTIFICATION_ID, notificationFor())
                if (now - lastCheckpoint >= 5000) {
                    saveCheckpoint()
                    lastCheckpoint = now
                }
            }
        }
    }

    private var pomodoroConfigCache = PomodoroConfig()

    private suspend fun refreshConfigCache() {
        pomodoroConfigCache = ServiceLocator.settingsRepository.current().pomodoro
    }

    private fun startNoiseIfEnabled() {
        scope.launch {
            val settings = ServiceLocator.settingsRepository.current()
            pomodoroConfigCache = settings.pomodoro
            SoundController.play(
                this@FocusService,
                settings.whiteNoise,
                (settings.whiteNoiseVolume / 100f).coerceIn(0f, 1f),
                (settings.rainIntensity / 100f).coerceIn(0f, 1f),
            )
        }
    }

    private suspend fun archiveAndStop() {
        val now = SystemClock.elapsedRealtime()
        val result = FocusSessionEngine.stop(state, now, System.currentTimeMillis())
        state = result.state
        result.record?.let { archive(state, it.durationMs, it.cycles, complete = it.status.name == "COMPLETED") }
        finishAndClear()
    }

    private suspend fun archive(finalState: TimerState, durationMs: Long, cycles: Int, complete: Boolean) {
        if (durationMs < 1000) return
        ServiceLocator.focusRepository.saveSession(
            FocusSessionRecord(
                mode = finalState.mode,
                tagId = finalState.tagId,
                startedAt = finalState.sessionStartedAt,
                endedAt = System.currentTimeMillis(),
                durationMs = durationMs,
                status = if (complete) com.liubai.domain.model.SessionStatus.COMPLETED
                else com.liubai.domain.model.SessionStatus.INTERRUPTED,
                cycles = cycles,
            )
        )
    }

    private fun finishAndClear() {
        scope.launch {
            ServiceLocator.focusRepository.clearCheckpoint()
            cancelEndAlarm()
            SoundController.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun saveCheckpoint() {
        if (state.isActive) ServiceLocator.focusRepository.saveCheckpoint(state)
    }

    private fun notificationFor() = FocusNotifications.build(
        context = this,
        phase = state.phase,
        running = state.running,
        remainingMs = when (state.phase) {
            TimerPhase.FOCUS -> state.focusRemainingAt(SystemClock.elapsedRealtime())
            TimerPhase.BREAK -> state.breakRemainingAt(SystemClock.elapsedRealtime())
            else -> 0L
        },
        cycleIndex = state.cycleIndex,
    )

    /** 精确闹钟双保险：按预计结束时间登记 */
    private fun scheduleEndAlarm() {
        val mgr = getSystemService(AlarmManager::class.java) ?: return
        val fireAt = when (state.phase) {
            TimerPhase.FOCUS -> if (state.focusTargetMs > 0)
                SystemClock.elapsedRealtime() + state.focusRemainingAt(SystemClock.elapsedRealtime()) else return
            TimerPhase.BREAK -> SystemClock.elapsedRealtime() + state.breakRemainingAt(SystemClock.elapsedRealtime())
            else -> return
        }
        val pi = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST,
            Intent(this, FocusAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !mgr.canScheduleExactAlarms()) {
            mgr.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, fireAt, pi)
        } else {
            mgr.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, fireAt, pi)
        }
    }

    private fun cancelEndAlarm() {
        val mgr = getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST,
            Intent(this, FocusAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mgr.cancel(pi)
    }

    override fun onDestroy() {
        tickJob?.cancel()
        SoundController.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/** 闹钟兜底：服务被杀死后由这里归档并通知 */
class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val restore = Intent(ctx, FocusService::class.java).setAction(FocusService.ACTION_RESTORE)
        ctx.startForegroundService(restore)
    }
}

/** 通知栏「完成」按钮：归档当前会话 */
class FocusStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        ctx.startForegroundService(
            Intent(ctx, FocusService::class.java).setAction(FocusService.ACTION_STOP)
        )
    }
}
