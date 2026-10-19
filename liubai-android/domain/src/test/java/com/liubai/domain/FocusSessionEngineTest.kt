package com.liubai.domain

import com.liubai.domain.engine.EngineEvent
import com.liubai.domain.engine.FocusSessionEngine
import com.liubai.domain.model.SessionStatus
import com.liubai.domain.model.FocusMode
import com.liubai.domain.model.PomodoroConfig
import com.liubai.domain.model.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionEngineTest {

    private val config = PomodoroConfig(focusMin = 25, shortBreakMin = 5, longBreakMin = 15, cyclesBeforeLongBreak = 4)

    @Test
    fun `pomodoro focus completes then break starts`() {
        var s = FocusSessionEngine.start(FocusMode.POMODORO, null, config, 30, nowElapsed = 1_000, nowWall = 100)
        val result = FocusSessionEngine.tick(s, now = 1_000 + 25 * 60_000, config)
        s = result.state
        assertEquals(TimerPhase.BREAK, s.phase)
        assertEquals(1, s.cycleIndex)
        assertEquals(5 * 60_000L, s.breakTargetMs)
        assertTrue(result.events.any { it is EngineEvent.FocusCycleCompleted })
        assertEquals(25 * 60_000L, s.sessionFocusMs)
    }

    @Test
    fun `fourth cycle gets long break`() {
        var s = FocusSessionEngine.start(FocusMode.POMODORO, null, config, 30, 0, 0)
        repeat(3) {
            s = FocusSessionEngine.tick(s, s.segmentStartMs + 25 * 60_000, config).state
            s = FocusSessionEngine.tick(s, s.breakSegmentStartMs + s.breakTargetMs, config).state
            // autoStartNextFocus=false，引擎停在下轮专注待开始态，需手动 resume
            s = FocusSessionEngine.resume(s, s.breakSegmentStartMs + s.breakTargetMs)
        }
        s = FocusSessionEngine.tick(s, s.segmentStartMs + 25 * 60_000, config).state
        assertEquals(TimerPhase.BREAK, s.phase)
        assertEquals(15 * 60_000L, s.breakTargetMs)
    }

    @Test
    fun `countdown finishes with event`() {
        val s0 = FocusSessionEngine.start(FocusMode.COUNTDOWN, null, config, 30, 0, 0)
        val result = FocusSessionEngine.tick(s0, 30 * 60_000, config)
        assertEquals(TimerPhase.FINISHED, result.state.phase)
        assertTrue(result.events.any { it is EngineEvent.SessionFinished })
    }

    @Test
    fun `pause and resume accumulates correctly`() {
        var s = FocusSessionEngine.start(FocusMode.STOPWATCH, null, config, 30, 0, 0)
        s = FocusSessionEngine.pause(s, 10_000)
        assertEquals(10_000L, s.focusElapsedAt(10_000))
        s = FocusSessionEngine.resume(s, 60_000)
        // 暂停 50 秒不计入
        assertEquals(20_000L, s.focusElapsedAt(70_000))
    }

    @Test
    fun `stop mid focus archives interrupted with partial time`() {
        val s0 = FocusSessionEngine.start(FocusMode.POMODORO, 7L, config, 30, 0, 1_000)
        val stop = FocusSessionEngine.stop(s0, now = 10 * 60_000, nowWall = 2_000)
        val record = stop.record!!
        assertEquals(SessionStatus.INTERRUPTED, record.status)
        assertEquals(10 * 60_000L, record.durationMs)
        assertEquals(7L, record.tagId)
        assertEquals(TimerPhase.IDLE, stop.state.phase)
    }

    @Test
    fun `stop during break archives completed`() {
        var s = FocusSessionEngine.start(FocusMode.POMODORO, null, config, 30, 0, 0)
        s = FocusSessionEngine.tick(s, 25 * 60_000, config).state
        val stop = FocusSessionEngine.stop(s, 25 * 60_000 + 60_000, 1)
        assertEquals(SessionStatus.COMPLETED, stop.record!!.status)
        assertEquals(25 * 60_000L, stop.record!!.durationMs)
    }

    @Test
    fun `restore after process death completes missed session`() {
        val s0 = FocusSessionEngine.start(FocusMode.COUNTDOWN, null, config, 30, 0, 0)
        // 进程被杀 2 小时后恢复：应判定完成
        val restored = FocusSessionEngine.restore(s0, 2 * 3600_000, config)
        assertEquals(TimerPhase.FINISHED, restored.state.phase)
    }

    @Test
    fun `stopwatch never auto completes`() {
        val s0 = FocusSessionEngine.start(FocusMode.STOPWATCH, null, config, 30, 0, 0)
        val result = FocusSessionEngine.tick(s0, 10 * 3600_000, config)
        assertEquals(TimerPhase.FOCUS, result.state.phase)
        assertTrue(result.events.isEmpty())
    }
}
