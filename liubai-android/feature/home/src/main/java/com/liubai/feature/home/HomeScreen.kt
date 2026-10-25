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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.liubai.core.designsystem.component.PermissionRationaleDialog
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
    val context = androidx.compose.ui.platform.LocalContext.current
    // 复用 resolver：原来在列表循环里 new，每次重组都重新查 PackageManager，导致回首页卡顿
    val resolver = remember(context) { AppLabelResolver(context) }
    val iconCache = remember { mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap?>() }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // 只在「首次安装后的第一次启动」自动弹一次；之后仅当用户点击权限入口时才弹
    LaunchedEffect(state.hasUsagePermission) {
        if (!state.hasUsagePermission) {
            val prefs = context.getSharedPreferences("onboarding", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("usage_asked", false)) {
                prefs.edit().putBoolean("usage_asked", true).apply()
                showPermissionDialog = true
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "Margin",
                    style = MaterialTheme.typography.displayMedium,
                    color = extras.textSecondary,
                )
                Spacer(Modifier.width(6.dp))
                Text("「留白」", style = MaterialTheme.typography.displayMedium, color = extras.textPrimary)
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
                    Button(onClick = { showPermissionDialog = true }) { Text("开启屏幕洞察") }
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
                            RingTimeValue(state.screenTodayMs)
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
                                val bmp = iconCache.getOrPut(app.packageName) {
                                    resolver.icon(app.packageName)?.toBitmapOrNull()
                                }
                                if (bmp != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bmp,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                    )
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

    if (showPermissionDialog) {
        PermissionRationaleDialog(
            title = "开启屏幕洞察",
            message = "留白需要「使用情况访问」权限，才能统计各应用的使用时长、" +
                "生成每日报告与限额提醒。\n\n" +
                "这是安卓唯一的官方途径：数据只存在你的手机里，不会上传到任何服务器。" +
                "拒绝也不影响专注、标签与白噪音功能。",
            onConfirm = {
                showPermissionDialog = false
                onGrantPermission()
            },
            onDismiss = { showPermissionDialog = false },
        )
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

/**
 * 时间环中央的今日时长。
 *
 * 原来直接把「21 小时 45 分钟」塞进一个 displayMedium 文本，十来个字符远超
 * 216dp 圆环的内径，会横向溢出到环外。改成数字大、单位小，超过一小时时
 * 小时与分钟分两行——数字最多三位，宽度天然受控。
 */
@Composable
private fun RingTimeValue(totalMs: Long) {
    val extras = LocalExtraColors.current
    val totalMin = (totalMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMin / 60
    val minutes = totalMin % 60

    @Composable
    fun NumberWithUnit(value: Long, unit: String, big: Boolean) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$value",
                style = if (big) MaterialTheme.typography.displayMedium
                else MaterialTheme.typography.headlineMedium,
                color = extras.textPrimary,
            )
            Text(
                unit,
                style = if (big) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.labelLarge,
                color = extras.textPrimary,
                modifier = Modifier.padding(
                    start = 2.dp,
                    bottom = if (big) 10.dp else 5.dp,
                ),
            )
        }
    }

    if (hours > 0) {
        NumberWithUnit(hours, "小时", big = true)
        NumberWithUnit(minutes, "分钟", big = false)
    } else {
        NumberWithUnit(minutes, "分钟", big = true)
    }
}
