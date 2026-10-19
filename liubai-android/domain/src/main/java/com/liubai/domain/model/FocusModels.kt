package com.liubai.domain.model

/** 专注模式 */
enum class FocusMode { POMODORO, COUNTDOWN, STOPWATCH }

/** 计时阶段 */
enum class TimerPhase { IDLE, FOCUS, BREAK, FINISHED }

/** 会话归档状态 */
enum class SessionStatus { COMPLETED, INTERRUPTED }

/** 主题模式 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** 番茄钟参数 */
data class PomodoroConfig(
    val focusMin: Int = 25,
    val shortBreakMin: Int = 5,
    val longBreakMin: Int = 15,
    val cyclesBeforeLongBreak: Int = 4,
    val autoStartBreak: Boolean = true,
    val autoStartNextFocus: Boolean = false,
)

/**
 * 计时引擎唯一状态。
 * 时间语义：[segmentStartMs] 是当前运行片段的 elapsedRealtime 起点；
 * [focusElapsedMs]/[breakElapsedMs] 是已提交（暂停或跨周期结转）的毫秒数。
 */
data class TimerState(
    val mode: FocusMode = FocusMode.POMODORO,
    val phase: TimerPhase = TimerPhase.IDLE,
    val running: Boolean = false,
    val tagId: Long? = null,
    val focusTargetMs: Long = 0L,
    val focusElapsedMs: Long = 0L,
    val segmentStartMs: Long = 0L,
    val breakTargetMs: Long = 0L,
    val breakElapsedMs: Long = 0L,
    val breakSegmentStartMs: Long = 0L,
    val cycleIndex: Int = 0,
    val sessionFocusMs: Long = 0L,
    val sessionStartedAt: Long = 0L,
) {
    fun focusElapsedAt(now: Long): Long =
        focusElapsedMs + if (running && phase == TimerPhase.FOCUS) (now - segmentStartMs).coerceAtLeast(0) else 0L

    fun breakElapsedAt(now: Long): Long =
        breakElapsedMs + if (running && phase == TimerPhase.BREAK) (now - breakSegmentStartMs).coerceAtLeast(0) else 0L

    fun focusRemainingAt(now: Long): Long =
        if (focusTargetMs <= 0L) 0L else (focusTargetMs - focusElapsedAt(now)).coerceAtLeast(0)

    fun breakRemainingAt(now: Long): Long =
        if (breakTargetMs <= 0L) 0L else (breakTargetMs - breakElapsedAt(now)).coerceAtLeast(0)

    /** 本次会话累计专注毫秒（含当前运行片段） */
    fun totalFocusMsAt(now: Long): Long =
        sessionFocusMs + if (phase == TimerPhase.FOCUS) focusElapsedAt(now) else 0L

    val isActive: Boolean get() = phase != TimerPhase.IDLE && phase != TimerPhase.FINISHED
}

/** 归档后的专注会话记录 */
data class FocusSessionRecord(
    val id: Long = 0L,
    val mode: FocusMode,
    val tagId: Long?,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val status: SessionStatus,
    val cycles: Int,
)

/** 专注标签 */
data class Tag(
    val id: Long = 0L,
    val name: String,
    val emoji: String,
    val createdAt: Long = 0L,
    val archived: Boolean = false,
)

/** 专注日聚合（date 为 yyyy-MM-dd） */
data class FocusDaily(
    val date: String,
    val focusMs: Long,
    val completedCount: Int,
    val interruptCount: Int,
)

/** 应用设置 */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dailyGoalMin: Int = 120,
    val pomodoro: PomodoroConfig = PomodoroConfig(),
    val countdownMin: Int = 30,
    val monitorEnabled: Boolean = true,
    /** 精确监控：常驻通知 + 每分钟检查（更及时，更费电） */
    val preciseMonitor: Boolean = false,
    val whiteNoise: String? = null,
    val whiteNoiseVolume: Int = 60,
    /** 赞助收款码图片路径（filesDir 内相对路径，null = 未上传） */
    val sponsorQrPath: String? = null,
    val challenge: ChallengeConfig = ChallengeConfig(),
)
