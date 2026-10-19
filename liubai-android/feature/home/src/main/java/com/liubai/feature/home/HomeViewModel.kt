package com.liubai.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liubai.core.common.DateKeys
import com.liubai.core.common.DurationFormat
import com.liubai.data.di.ServiceLocator
import com.liubai.data.usage.UsageStatsReader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    val uiState: StateFlow<HomeUiState> = combine(
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

    init {
        // 进入首页触发一次增量采集，保证数据新鲜
        viewModelScope.launch { usageRepo.collectNow() }
    }
}
