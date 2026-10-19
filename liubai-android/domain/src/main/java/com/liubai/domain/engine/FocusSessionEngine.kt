package com.liubai.domain.engine

import com.liubai.domain.model.FocusMode
import com.liubai.domain.model.PomodoroConfig
import com.liubai.domain.model.SessionStatus
import com.liubai.domain.model.TimerPhase
import com.liubai.domain.model.TimerState

/** 引擎内部事件，由 ViewModel 消费（持久化 / 通知） */
sealed interface EngineEvent {
    data class FocusCycleCompleted(val cycleIndex: Int) : EngineEvent
    data class BreakCompleted(val nextCycle: Int) : EngineEvent
    data class SessionFinished(val totalFocusMs: Long, val cycles: Int) : EngineEvent
}

data class TickResult(val state: TimerState, val events: List<EngineEvent>)

data class StopResult(val state: TimerState, val record: SessionArchive?)

data class SessionArchive(
    val mode: FocusMode,
    val tagId: Long?,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val status: SessionStatus,
    val cycles: Int,
)

/**
 * 专注计时引擎：纯函数状态机，零 Android 依赖。
 *
 * 状态流转：
 *   IDLE --start--> FOCUS --(达到目标)--> BREAK(番茄) / FINISHED(倒计时)
 *   FOCUS/BREAK --pause--> 暂停 --resume--> 继续
 *   任意运行态 --stop--> IDLE + 归档会话
 *   任意态 --进程死亡--> 由 Checkpoint 重建后继续（见 restore）
 */
object FocusSessionEngine {

    fun start(
        mode: FocusMode,
        tagId: Long?,
        config: PomodoroConfig,
        countdownMin: Int,
        nowElapsed: Long,
        nowWall: Long,
    ): TimerState {
        val targetMs = when (mode) {
            FocusMode.POMODORO -> config.focusMin * 60_000L
            FocusMode.COUNTDOWN -> countdownMin * 60_000L
            FocusMode.STOPWATCH -> 0L
        }
        return TimerState(
            mode = mode,
            phase = TimerPhase.FOCUS,
            running = true,
            tagId = tagId,
            focusTargetMs = targetMs,
            segmentStartMs = nowElapsed,
            sessionStartedAt = nowWall,
        )
    }

    fun pause(state: TimerState, now: Long): TimerState = when (state.phase) {
        TimerPhase.FOCUS -> state.copy(
            running = false,
            focusElapsedMs = state.focusElapsedAt(now),
            segmentStartMs = 0L,
        )
        TimerPhase.BREAK -> state.copy(
            running = false,
            breakElapsedMs = state.breakElapsedAt(now),
            breakSegmentStartMs = 0L,
        )
        else -> state
    }

    fun resume(state: TimerState, now: Long): TimerState = when (state.phase) {
        TimerPhase.FOCUS -> state.copy(running = true, segmentStartMs = now)
        TimerPhase.BREAK -> state.copy(running = true, breakSegmentStartMs = now)
        else -> state
    }

    /** 跳过当前休息，直接进入下一轮专注（不自动开始） */
    fun skipBreak(state: TimerState, now: Long): TimerState {
        if (state.phase != TimerPhase.BREAK) return state
        return state.copy(
            phase = TimerPhase.FOCUS,
            running = false,
            focusElapsedMs = 0L,
            segmentStartMs = 0L,
            breakElapsedMs = 0L,
        )
    }

    /**
     * 周期 tick。调用方每秒调用一次；状态跃迁在此完成。
     * 番茄钟：专注达标 → 短休/长休（每 N 轮）→ 下一轮
     * 倒计时：专注达标 → FINISHED
     * 正计时：永不自动完成
     */
    fun tick(state: TimerState, now: Long, config: PomodoroConfig): TickResult {
        if (!state.running) return TickResult(state, emptyList())
        val events = mutableListOf<EngineEvent>()

        var s = state
        when (state.phase) {
            TimerPhase.FOCUS -> {
                if (state.focusTargetMs > 0 && state.focusElapsedAt(now) >= state.focusTargetMs) {
                    val completedCycles = state.cycleIndex + 1
                    val accumulated = state.sessionFocusMs + state.focusTargetMs
                    events += EngineEvent.FocusCycleCompleted(completedCycles)
                    s = when (state.mode) {
                        FocusMode.POMODORO -> {
                            val longBreak = config.cyclesBeforeLongBreak > 0 &&
                                completedCycles % config.cyclesBeforeLongBreak == 0
                            val breakTarget = (if (longBreak) config.longBreakMin else config.shortBreakMin) * 60_000L
                            state.copy(
                                phase = TimerPhase.BREAK,
                                running = config.autoStartBreak,
                                cycleIndex = completedCycles,
                                sessionFocusMs = accumulated,
                                focusElapsedMs = 0L,
                                segmentStartMs = 0L,
                                breakTargetMs = breakTarget,
                                breakElapsedMs = 0L,
                                breakSegmentStartMs = if (config.autoStartBreak) now else 0L,
                            )
                        }
                        FocusMode.COUNTDOWN -> {
                            events.clear()
                            events += EngineEvent.SessionFinished(accumulated, completedCycles)
                            state.copy(
                                phase = TimerPhase.FINISHED,
                                running = false,
                                cycleIndex = completedCycles,
                                sessionFocusMs = accumulated,
                                focusElapsedMs = state.focusTargetMs,
                                segmentStartMs = 0L,
                            )
                        }
                        FocusMode.STOPWATCH -> state
                    }
                }
            }
            TimerPhase.BREAK -> {
                if (state.breakTargetMs > 0 && state.breakElapsedAt(now) >= state.breakTargetMs) {
                    events += EngineEvent.BreakCompleted(state.cycleIndex)
                    s = state.copy(
                        phase = TimerPhase.FOCUS,
                        running = config.autoStartNextFocus,
                        focusElapsedMs = 0L,
                        segmentStartMs = if (config.autoStartNextFocus) now else 0L,
                        breakElapsedMs = 0L,
                        breakTargetMs = state.focusTargetMs,
                    )
                }
            }
            else -> Unit
        }
        return TickResult(s, events)
    }

    /** 用户主动结束（长按确认）。返回归档信息；当前片段计入总时长。 */
    fun stop(state: TimerState, now: Long, nowWall: Long): StopResult {
        if (state.phase == TimerPhase.IDLE || state.phase == TimerPhase.FINISHED) {
            return StopResult(TimerState(), null)
        }
        val totalFocus = state.totalFocusMsAt(now)
        val status = if (state.phase == TimerPhase.BREAK || state.mode == FocusMode.STOPWATCH) {
            SessionStatus.COMPLETED
        } else {
            SessionStatus.INTERRUPTED
        }
        val record = SessionArchive(
            mode = state.mode,
            tagId = state.tagId,
            startedAt = state.sessionStartedAt,
            endedAt = nowWall,
            durationMs = totalFocus,
            status = status,
            cycles = state.cycleIndex,
        )
        return StopResult(TimerState(), record)
    }

    /**
     * 进程重建后的恢复：把引擎推进到 now，返回修正后的状态。
     * 调用方据此判断“离开期间已完成/已超时”。
     */
    fun restore(state: TimerState, now: Long, config: PomodoroConfig): TickResult =
        tick(state, now, config)
}
