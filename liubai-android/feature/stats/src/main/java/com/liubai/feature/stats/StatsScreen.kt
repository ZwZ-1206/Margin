package com.liubai.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liubai.core.common.DurationFormat
import com.liubai.core.designsystem.component.ScreenHeader
import com.liubai.core.designsystem.component.SectionCard
import com.liubai.core.designsystem.component.SegmentedToggle
import com.liubai.core.designsystem.component.StatTile
import com.liubai.core.designsystem.theme.LocalExtraColors
import com.liubai.data.usage.AppLabelResolver
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    vm: StatsViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val extras = LocalExtraColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            ScreenHeader(title = "统计", onBack = onBack)
        }

        item {
            SegmentedToggle(
                options = StatsPeriod.entries.toList(),
                selected = state.period,
                onSelect = vm::selectPeriod,
                label = { it.label },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(state.totalScreenText, "屏幕时间", Modifier.weight(1f), extras.catScreen)
                StatTile(state.totalFocusText, "专注时长", Modifier.weight(1f), extras.catFocus)
                StatTile(state.avgScreenText, "日均屏幕", Modifier.weight(1f))
            }
        }

        item {
            SectionCard {
                Text("每日屏幕时间", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                if (state.bars.all { it.screenMs == 0L }) {
                    Text(
                        "这段时间还没有屏幕数据。开启「使用情况访问」权限后，数据会随使用自动累积。",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.textTertiary,
                    )
                } else {
                    DualBarChart(
                        bars = state.bars,
                        screenColor = extras.catScreen,
                        focusColor = extras.catFocus,
                        trackColor = extras.ringTrack,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(extras.catScreen, "屏幕")
                        Spacer(Modifier.width(16.dp))
                        LegendDot(extras.catFocus, "专注")
                    }
                }
            }
        }

        if (state.appRanks.isNotEmpty()) {
            item {
                SectionCard {
                    Text("应用排行", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    val max = state.appRanks.maxOf { it.totalMs }.coerceAtLeast(1)
                    val resolver = AppLabelResolver(LocalContext.current)
                    state.appRanks.take(10).forEachIndexed { index, app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = extras.textTertiary,
                                modifier = Modifier.width(20.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    resolver.label(app.packageName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = extras.textPrimary,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                                        .background(extras.ringTrack)
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth(app.totalMs.toFloat() / max)
                                            .height(6.dp).clip(CircleShape)
                                            .background(extras.catScreen)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                DurationFormat.short(app.totalMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = extras.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        if (state.focusRows.isNotEmpty()) {
            item {
                SectionCard {
                    Text("专注记录", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    state.focusRows.take(10).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    timeText(row.startedAt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = extras.textPrimary,
                                )
                                Text(
                                    row.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (row.status == "完成") extras.success else extras.textTertiary,
                                )
                            }
                            Text(
                                DurationFormat.chinese(row.durationMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = extras.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val extras = LocalExtraColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = extras.textTertiary)
    }
}

/** 双柱图：屏幕（主色）+ 专注（辅色）并列 */
@Composable
private fun DualBarChart(
    bars: List<DayBar>,
    screenColor: Color,
    focusColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val extras = LocalExtraColors.current
    Canvas(modifier = modifier) {
        if (bars.isEmpty()) return@Canvas
        val maxMs = bars.maxOf { maxOf(it.screenMs, it.focusMs) }.coerceAtLeast(1)
        val slot = size.width / bars.size
        val barWidth = (slot * 0.28f).coerceAtMost(14.dp.toPx())
        val gap = 3.dp.toPx()
        val chartHeight = size.height - 18.dp.toPx()

        bars.forEachIndexed { i, bar ->
            val cx = slot * i + slot / 2
            val screenH = (bar.screenMs.toFloat() / maxMs) * chartHeight
            val focusH = (bar.focusMs.toFloat() / maxMs) * chartHeight
            // 底轨
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(cx - barWidth - gap / 2, 0f),
                size = Size(barWidth, chartHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
            )
            if (screenH > 0) {
                drawRoundRect(
                    color = screenColor,
                    topLeft = Offset(cx - barWidth - gap / 2, chartHeight - screenH),
                    size = Size(barWidth, screenH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
                )
            }
            if (focusH > 0) {
                drawRoundRect(
                    color = focusColor,
                    topLeft = Offset(cx + gap / 2, chartHeight - focusH),
                    size = Size(barWidth, focusH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
                )
            }
        }
    }
    // 标签行
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        bars.forEach { bar ->
            Text(
                bar.label,
                style = MaterialTheme.typography.labelSmall,
                color = extras.textTertiary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private fun timeText(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
