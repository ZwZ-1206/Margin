package com.liubai.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liubai.core.common.DurationFormat
import com.liubai.core.designsystem.component.RingProgress
import com.liubai.core.designsystem.component.SectionCard
import com.liubai.core.designsystem.component.StatTile
import com.liubai.core.designsystem.theme.LocalExtraColors
import com.liubai.data.usage.AppLabelResolver

@Composable
fun HomeScreen(
    onStartFocus: () -> Unit,
    onOpenStats: () -> Unit,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val extras = LocalExtraColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "MARGIN",
                style = MaterialTheme.typography.labelSmall,
                color = extras.textTertiary,
                letterSpacing = androidx.compose.ui.unit.TextUnit(2.4f, androidx.compose.ui.unit.TextUnitType.Sp),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("「留白」", style = MaterialTheme.typography.displayMedium, color = extras.textPrimary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Margin",
                    style = MaterialTheme.typography.titleLarge,
                    color = extras.textSecondary,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Text(
                "A little less scrolling. A little more living.",
                style = MaterialTheme.typography.bodySmall,
                color = extras.textTertiary,
            )
        }

        if (!state.hasUsagePermission) {
            item {
                SectionCard {
                    Text("开启屏幕洞察", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "需要「使用情况访问」权限才能统计各应用的使用时长。拒绝也不影响专注功能。",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onGrantPermission) { Text("去授权") }
                }
            }
        }

        item {
            SectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RingProgress(
                            progress = state.goalProgress,
                            diameter = 216.dp,
                            trackColor = extras.ringTrack,
                            progressColor = if (state.overGoal) extras.warning else MaterialTheme.colorScheme.primary,
                            overageColor = extras.warning,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "今日屏幕",
                                style = MaterialTheme.typography.labelMedium,
                                color = extras.textTertiary,
                            )
                            Text(
                                state.screenTodayText,
                                style = MaterialTheme.typography.displayMedium,
                                color = extras.textPrimary,
                            )
                            Text(
                                "目标 ${DurationFormat.chinese(state.dailyGoalMin * 60_000L)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = extras.textSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.deltaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.screenTodayMs <= state.screenYesterdayMs) extras.success else extras.warning,
                    )
                    if (state.estimated) {
                        Text(
                            "部分数据为估算值（系统事件存在缺口）",
                            style = MaterialTheme.typography.labelSmall,
                            color = extras.textTertiary,
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(state.focusTodayText, "今日专注", Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                StatTile("${state.focusSessions}", "专注次数", Modifier.weight(1f))
                StatTile("${state.appCount}", "使用应用", Modifier.weight(1f))
            }
        }

        item {
            Button(
                onClick = onStartFocus,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text("开始专注", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (state.topApps.isNotEmpty()) {
            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("今日占用最多", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = onOpenStats) { Text("全部统计") }
                    }
                    Spacer(Modifier.height(8.dp))
                    val max = state.topApps.maxOf { it.totalMs }.coerceAtLeast(1)
                    state.topApps.forEach { app ->
                        val resolver = AppLabelResolver(LocalContext.current)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(extras.window),
                                contentAlignment = Alignment.Center,
                            ) {
                                resolver.icon(app.packageName)?.let { d ->
                                    d.toBitmapOrNull()?.let { bmp ->
                                        androidx.compose.foundation.Image(
                                            bitmap = bmp,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    resolver.label(app.packageName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = extras.textPrimary,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(extras.ringTrack)
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(app.totalMs.toFloat() / max)
                                            .height(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
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

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Drawable → Bitmap?（Image 位图加载；失败时返回 null 由占位承接） */
private fun android.graphics.drawable.Drawable.toBitmapOrNull(): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        val bmp = android.graphics.Bitmap.createBitmap(
            intrinsicWidth.coerceAtLeast(1),
            intrinsicHeight.coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        val canvas = android.graphics.Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp.asImageBitmap()
    }.getOrNull()
