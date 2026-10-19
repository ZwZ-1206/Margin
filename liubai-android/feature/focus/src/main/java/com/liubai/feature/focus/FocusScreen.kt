package com.liubai.feature.focus

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liubai.core.common.DurationFormat
import com.liubai.core.designsystem.component.RingProgress
import com.liubai.core.designsystem.component.SectionCard
import com.liubai.core.designsystem.component.SegmentedToggle
import com.liubai.core.designsystem.theme.LocalExtraColors
import com.liubai.data.focus.WhiteNoiseSynth
import com.liubai.domain.model.FocusMode
import com.liubai.domain.model.TimerPhase
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(
    modifier: Modifier = Modifier,
    vm: FocusViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val extras = LocalExtraColors.current
    val context = LocalContext.current
    var showStopConfirm by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var shareToast by remember { mutableStateOf<String?>(null) }

    // 每秒刷新一次本地时间轴（环与文字由引擎状态 + elapsedRealtime 推导）
    var nowTick by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.hasActiveSession, state.timer.running) {
        while (state.hasActiveSession) {
            nowTick = android.os.SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        when {
            state.timer.phase == TimerPhase.FINISHED -> {
                FinishedView(
                    focusMs = state.timer.totalFocusMsAt(nowTick),
                    cycles = state.timer.cycleIndex,
                    onDismiss = { vm.dismissFinished(context) },
                    onAgain = {
                        vm.dismissFinished(context)
                        vm.start(context)
                    },
                    onShare = {
                        val tag = state.timer.tagId?.let { id -> state.tags.firstOrNull { it.id == id } }
                        val bitmap = ShareCard.render(
                            durationMs = state.timer.totalFocusMsAt(nowTick),
                            tagLabel = tag?.let { it.emoji + " " + it.name },
                            cycles = state.timer.cycleIndex,
                            completed = true,
                        )
                        val uri = ShareCard.save(
                            context,
                            bitmap,
                            "margin-focus-" + System.currentTimeMillis(),
                        )
                        shareToast = if (uri != null) "已保存到相册" else "保存失败，请检查存储权限"
                    },
                )
            }

            state.hasActiveSession -> {
                // ===== 进行态 =====
                val target = when (state.timer.phase) {
                    TimerPhase.FOCUS -> state.timer.focusTargetMs
                    TimerPhase.BREAK -> state.timer.breakTargetMs
                    else -> 0L
                }
                val elapsed = when (state.timer.phase) {
                    TimerPhase.FOCUS -> state.timer.focusElapsedAt(nowTick)
                    TimerPhase.BREAK -> state.timer.breakElapsedAt(nowTick)
                    else -> 0L
                }
                val remaining = (target - elapsed).coerceAtLeast(0L)
                val progress = if (target > 0) elapsed.toFloat() / target else 0f

                Text(
                    text = when (state.timer.phase) {
                        TimerPhase.FOCUS -> "专注中"
                        TimerPhase.BREAK -> "休息中"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = if (state.timer.phase == TimerPhase.BREAK) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.primary,
                )
                if (state.timer.mode == FocusMode.POMODORO) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val cycles = state.settings.pomodoro.cyclesBeforeLongBreak.coerceAtLeast(1)
                        repeat(cycles) { i ->
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i < state.timer.cycleIndex % cycles || (state.timer.cycleIndex > 0 && state.timer.cycleIndex % cycles == 0 && i == cycles - 1 && state.timer.phase == TimerPhase.BREAK))
                                            MaterialTheme.colorScheme.primary
                                        else extras.ringTrack
                                    )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                Box(contentAlignment = Alignment.Center) {
                    RingProgress(
                        progress = progress,
                        diameter = 260.dp,
                        strokeWidth = 18.dp,
                        trackColor = extras.ringTrack,
                        progressColor = if (state.timer.phase == TimerPhase.BREAK) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            DurationFormat.timer(remaining),
                            style = MaterialTheme.typography.displayLarge,
                            color = extras.textPrimary,
                        )
                        Text(
                            if (state.timer.running) "点击通知栏可快捷操作" else "已暂停",
                            style = MaterialTheme.typography.labelMedium,
                            color = extras.textTertiary,
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { if (state.timer.running) vm.pause(context) else vm.resume(context) },
                        modifier = Modifier.height(52.dp),
                    ) {
                        Text(if (state.timer.running) "暂停" else "继续", style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(
                        onClick = { showStopConfirm = true },
                        modifier = Modifier.height(52.dp),
                    ) {
                        Text("结束", style = MaterialTheme.typography.titleLarge, color = extras.textSecondary)
                    }
                }
                state.timer.tagId?.let { id ->
                    val tag = state.tags.firstOrNull { it.id == id }
                    if (tag != null) {
                        Spacer(Modifier.height(12.dp))
                        Text("${tag.emoji} ${tag.name}", color = extras.textSecondary)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            else -> {
                // ===== 设置态 =====
                Text("开始一段专注", style = MaterialTheme.typography.headlineLarge, color = extras.textPrimary)
                Spacer(Modifier.height(20.dp))

                SegmentedToggle(
                    options = listOf(FocusMode.POMODORO, FocusMode.COUNTDOWN, FocusMode.STOPWATCH),
                    selected = state.timer.mode,
                    onSelect = vm::selectMode,
                    label = {
                        when (it) {
                            FocusMode.POMODORO -> "番茄钟"
                            FocusMode.COUNTDOWN -> "倒计时"
                            FocusMode.STOPWATCH -> "正计时"
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (state.timer.mode) {
                        FocusMode.POMODORO -> "${state.settings.pomodoro.focusMin} 分钟专注 + ${state.settings.pomodoro.shortBreakMin} 分钟休息，每 ${state.settings.pomodoro.cyclesBeforeLongBreak} 轮长休"
                        FocusMode.COUNTDOWN -> "设定一个时长，结束即完成"
                        FocusMode.STOPWATCH -> "不限时长，随时结束"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.textTertiary,
                    textAlign = TextAlign.Center,
                )

                if (state.timer.mode == FocusMode.COUNTDOWN) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 25, 30, 45, 60).forEach { min ->
                            FilterChip(
                                selected = state.countdownMin == min,
                                onClick = { /* 改设置 */ },
                                label = { Text("$min 分钟") },
                            )
                        }
                    }
                    Text(
                        "时长可在设置页修改（当前 ${state.countdownMin} 分钟）",
                        style = MaterialTheme.typography.labelSmall,
                        color = extras.textTertiary,
                    )
                }

                Spacer(Modifier.height(20.dp))

                // 标签
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("标签", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { showTagDialog = true }) { Text("＋ 新建") }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (state.tags.isEmpty()) {
                        Text(
                            "给专注加个标签，比如「学习」「工作」",
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.textTertiary,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.tags.forEach { tag ->
                                FilterChip(
                                    selected = state.selectedTagId == tag.id,
                                    onClick = { vm.selectTag(if (state.selectedTagId == tag.id) null else tag.id) },
                                    label = { Text("${tag.emoji} ${tag.name}") },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 白噪音
                SectionCard {
                    Text("白噪音", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "程序化实时合成，不占安装包体积",
                        style = MaterialTheme.typography.labelSmall,
                        color = extras.textTertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WhiteNoiseSynth.Sound.entries.forEach { sound ->
                            FilterChip(
                                selected = state.settings.whiteNoise == sound.name,
                                onClick = {
                                    vm.setNoise(context, if (state.settings.whiteNoise == sound.name) null else sound)
                                },
                                label = {
                                    Text(
                                        when (sound) {
                                            WhiteNoiseSynth.Sound.RAIN -> "雨声"
                                            WhiteNoiseSynth.Sound.STREAM -> "溪流"
                                            WhiteNoiseSynth.Sound.FOREST -> "森林"
                                            WhiteNoiseSynth.Sound.OCEAN -> "海浪"
                                            WhiteNoiseSynth.Sound.CAFE -> "咖啡馆"
                                            WhiteNoiseSynth.Sound.BROWN -> "棕噪音"
                                        }
                                    )
                                },
                            )
                        }
                    }
                    if (state.settings.whiteNoise != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("音量 ${state.settings.whiteNoiseVolume}%", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = state.settings.whiteNoiseVolume / 100f,
                            onValueChange = { vm.setVolume((it * 100).toInt()) },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { vm.start(context) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text("开始专注", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    shareToast?.let { msg ->
        AlertDialog(
            onDismissRequest = { shareToast = null },
            title = { Text(msg) },
            confirmButton = { TextButton(onClick = { shareToast = null }) { Text("好") } },
        )
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("结束本次专注？") },
            text = { Text("已专注的时长会记录为「中断」，仍计入统计。") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    vm.stop(context)
                }) { Text("结束") }
            },
            dismissButton = { TextButton(onClick = { showStopConfirm = false }) { Text("继续专注") } },
        )
    }

    if (showTagDialog) {
        var name by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf("🎯") }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("新建标签") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("🎯", "📚", "💼", "🏃", "🎨", "🧘").forEach { e ->
                            FilterChip(
                                selected = emoji == e,
                                onClick = { emoji = e },
                                label = { Text(e) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) vm.addTag(name.trim(), emoji)
                        showTagDialog = false
                    }
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showTagDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun FinishedView(
    focusMs: Long,
    cycles: Int,
    onDismiss: () -> Unit,
    onAgain: () -> Unit,
    onShare: () -> Unit,
) {
    val extras = LocalExtraColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        Text("完成了", style = MaterialTheme.typography.displayMedium, color = extras.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "本次专注 ${DurationFormat.chinese(focusMs)} · $cycles 轮",
            style = MaterialTheme.typography.bodyLarge,
            color = extras.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "放下手机，去享受这段「留白」",
            style = MaterialTheme.typography.bodyMedium,
            color = extras.textTertiary,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("再来一次", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f).height(48.dp)) {
                Text("保存分享卡片")
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) {
                Text("回到首页")
            }
        }
    }
}
