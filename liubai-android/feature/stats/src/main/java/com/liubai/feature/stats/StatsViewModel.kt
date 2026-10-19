package com.liubai.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liubai.core.common.DateKeys
import com.liubai.core.common.DurationFormat
import com.liubai.data.di.ServiceLocator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class StatsPeriod(val days: Int, val label: String) {
    DAY(1, "今天"), WEEK(7, "本周"), MONTH(30, "本月"),
}

data class DayBar(val date: String, val label: String, val screenMs: Long, val focusMs: Long)

data class AppRank(val packageName: String, val totalMs: Long)

data class FocusRow(val startedAt: Long, val durationMs: Long, val status: String, val tagName: String?)

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val bars: List<DayBar> = emptyList(),
    val totalScreenMs: Long = 0L,
    val totalFocusMs: Long = 0L,
    val avgScreenMs: Long = 0L,
    val appRanks: List<AppRank> = emptyList(),
    val focusRows: List<FocusRow> = emptyList(),
) {
    val totalScreenText: String get() = DurationFormat.chinese(totalScreenMs)
    val totalFocusText: String get() = DurationFormat.chinese(totalFocusMs)
    val avgScreenText: String get() = DurationFormat.short(avgScreenMs)
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel : ViewModel() {

    private val periodFlow = MutableStateFlow(StatsPeriod.WEEK)
    private val usageRepo = ServiceLocator.usageRepository
    private val focusRepo = ServiceLocator.focusRepository

    val uiState: StateFlow<StatsUiState> = periodFlow
        .flatMapLatest { period ->
            val to = LocalDate.now()
            val from = to.minusDays(period.days.toLong() - 1)
            combine(
                usageRepo.observeUsageRange(from.toString(), to.toString()),
                focusRepo.observeSessionsBetween(from.toString(), to.toString()),
            ) { usage, sessions ->
                val bars = (0 until period.days).map { i ->
                    val d = to.minusDays(period.days.toLong() - 1 - i)
                    val key = d.toString()
                    DayBar(
                        date = key,
                        label = when (period) {
                            StatsPeriod.DAY -> "今天"
                            StatsPeriod.WEEK -> "周" + "一二三四五六日"[d.dayOfWeek.value - 1]
                            StatsPeriod.MONTH -> "${d.dayOfMonth}日"
                        },
                        screenMs = usage.filter { it.date == key }.sumOf { it.totalMs },
                        focusMs = sessions.filter { DateKeys.of(it.startedAt) == key }.sumOf { it.durationMs },
                    )
                }
                val ranks = usage.groupBy { it.packageName }
                    .mapValues { (_, list) -> list.sumOf { it.totalMs } }
                    .entries.sortedByDescending { it.value }
                    .take(20)
                    .map { AppRank(it.key, it.value) }
                StatsUiState(
                    period = period,
                    bars = bars,
                    totalScreenMs = usage.sumOf { it.totalMs },
                    totalFocusMs = sessions.sumOf { it.durationMs },
                    avgScreenMs = if (period.days > 0) usage.sumOf { it.totalMs } / period.days else 0,
                    appRanks = ranks,
                    focusRows = sessions.sortedByDescending { it.startedAt }.take(30).map {
                        FocusRow(
                            startedAt = it.startedAt,
                            durationMs = it.durationMs,
                            status = if (it.status.name == "COMPLETED") "完成" else "中断",
                            tagName = null,
                        )
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun selectPeriod(period: StatsPeriod) {
        periodFlow.value = period
    }
}
