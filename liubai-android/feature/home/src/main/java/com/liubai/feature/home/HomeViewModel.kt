package com.liubai.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liubai.core.common.DateKeys
import com.liubai.core.common.DurationFormat
import com.liubai.data.di.ServiceLocator
import com.liubai.data.usage.UsageStatsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class TopApp(val packageName: String, val totalMs: Long)

data class HomeUiState(
    val screenTodayMs: Long = 0L,
    val screenYesterdayMs: Long = 0L,
    val focusTodayMs: Long = 0L,
    val focusSessions: Int = 0,
    val openCount: Int = 0,
    val appCount: Int = 0,
    val dailyGoalMin: Int = 120,
    val topApps: List<TopApp> = emptyList(),
    val hasUsagePermission: Boolean = true,
    val estimated: Boolean = false,
) {
    val screenTodayText: String get() = DurationFormat.chinese(screenTodayMs)
    val focusTodayText: String get() = DurationFormat.chinese(focusTodayMs)
    val goalProgress: Float get() = if (dailyGoalMin <= 0) 0f else screenTodayMs / (dailyGoalMin * 60_000f)
    val overGoal: Boolean get() = goalProgress > 1f
    val deltaText: String
        get() {
            val diff = screenTodayMs - screenYesterdayMs
            if (screenYesterdayMs == 0L) return "昨日无数据"
            val arrow = if (diff >= 0) "↑" else "↓"
            return "$arrow ${DurationFormat.short(kotlin.math.abs(diff))} 较昨日"
        }
}

class HomeViewModel : ViewModel() {

    private val usageRepo = ServiceLocator.usageRepository
    private val focusRepo = ServiceLocator.focusRepository
    private val settingsRepo = ServiceLocator.settingsRepository

    private val reader = UsageStatsReader(ServiceLocator.appContext)
    private val selfPkg = ServiceLocator.appContext.packageName

    /** 显示时钟：每秒一拍，让实时增量平滑增长 */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    /** 当前前台应用快照（每 5 秒读一次；读的时刻一起带回来做插值） */
    private data class Foreground(val pkg: String, val elapsedMs: Long, val readAt: Long)

    private val foregroundPoll: Flow<Foreground?> = flow {
        while (true) {
            val readAt = System.currentTimeMillis()
            val fg = runCatching { reader.currentForeground() }.getOrNull()
            emit(fg?.takeIf { it.first != selfPkg }?.let { Foreground(it.first, it.second, readAt) })
            delay(5_000L)
        }
    }.flowOn(Dispatchers.IO) // UsageStats 查询是同步 I/O，不能压在主线程上

    /**
     * 实时增量（毫秒）。
     *
     * UsageStats 只在应用「切到后台」时才落一条事件，所以正在前台用的那个 App
     * 的时长短期内不会进库——这是「数字不动」的根本原因。这里把当前前台应用的
     * 已持续毫秒按墙上时钟插值补上：它被切后台、事件落库后，前台快照就会变成
     * 别的应用或空，增量自动归零，不会重复计。
     */
    private val liveDelta: Flow<Long> = combine(ticker, foregroundPoll) { now, fg ->
        if (fg == null) 0L else fg.elapsedMs + (now - fg.readAt).coerceAtLeast(0L)
    }

    /**
     * 采集循环：首页可见期间每 15 秒重算一次今日用量。
     * 跟随 uiState 订阅启停（collectAsStateWithLifecycle 首页不可见即停），
     * 不会在后台空转。系统事件本身有几秒延迟，不重采的话从别的应用回到留白，
     * 看到的还是几分钟前的数字。
     */
    private val collectLoop: Flow<Int> = flow {
        var n = 0
        while (true) {
            runCatching { usageRepo.collectNow() }
            emit(n++)
            delay(15_000L)
        }
    }.flowOn(Dispatchers.IO) // 采集涉及 UsageStats 查询与数据库写入，离开主线程
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 四个数据源的合成（不含实时增量，避免超过 combine 的五流上限） */
    private val baseState: Flow<HomeUiState> = combine(
        usageRepo.observeDailyUsage(DateKeys.today()),
        focusRepo.observeDaily(1),
        settingsRepo.settings,
        usageRepo.observeUsageRange(
            LocalDate.now().minusDays(1).toString(),
            LocalDate.now().minusDays(1).toString(),
        ),
    ) { todayUsage, focusDaily, settings, yesterdayUsage ->
        val today = DateKeys.today()
        val screenToday = todayUsage.sumOf { it.totalMs }
        val focusToday = focusDaily.firstOrNull { it.date == today }
        val yesterdayTotal = yesterdayUsage.sumOf { it.totalMs }
        HomeUiState(
            screenTodayMs = screenToday,
            screenYesterdayMs = yesterdayTotal,
            focusTodayMs = focusToday?.focusMs ?: 0L,
            focusSessions = (focusToday?.completedCount ?: 0) + (focusToday?.interruptCount ?: 0),
            openCount = todayUsage.sumOf { it.openCount },
            appCount = todayUsage.size,
            dailyGoalMin = settings.dailyGoalMin,
            topApps = todayUsage.sortedByDescending { it.totalMs }.take(3)
                .map { TopApp(it.packageName, it.totalMs) },
            hasUsagePermission = reader.hasPermission(),
            estimated = todayUsage.any { it.estimatedMs > 0 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val uiState: StateFlow<HomeUiState> = combine(
        baseState,
        collectLoop,
        liveDelta,
    ) { base, _, delta ->
        base.copy(screenTodayMs = base.screenTodayMs + delta)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
