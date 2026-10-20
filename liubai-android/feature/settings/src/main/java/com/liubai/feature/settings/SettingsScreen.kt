package com.liubai.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.liubai.core.designsystem.component.PermissionRationaleDialog
import com.liubai.core.designsystem.component.SectionCard
import com.liubai.data.usage.UsageAccess
import com.liubai.core.designsystem.component.SegmentedToggle
import com.liubai.core.designsystem.theme.LocalExtraColors
import com.liubai.domain.model.ThemeMode
import java.io.File

@Composable
fun SettingsScreen(
    onOpenLimits: () -> Unit,
    onOpenHelp: () -> Unit,
    onCheckUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val extras = LocalExtraColors.current
    val context = LocalContext.current
    var showPermDialog by remember { mutableStateOf(false) }
    // 权限状态不是响应式的：从系统设置返回时重新检查一次
    var usageGranted by remember { mutableStateOf(UsageAccess.isGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        usageGranted = UsageAccess.isGranted(context)
    }
    var toast by remember { mutableStateOf<String?>(null) }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { target ->
            vm.exportBackup { json ->
                context.contentResolver.openOutputStream(target)?.use { it.write(json.toByteArray()) }
                toast = "备份已导出"
            }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { source ->
            val text = context.contentResolver.openInputStream(source)?.bufferedReader()?.readText()
            if (text != null) {
                vm.importBackup(text) { count ->
                    toast = if (count != null) "已导入 $count 条专注记录" else "备份文件解析失败"
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("设置", style = MaterialTheme.typography.displaySmall, color = extras.textPrimary)
        }

        // ===== 外观 =====
        item {
            SectionCard {
                Text("外观", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                SegmentedToggle(
                    options = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM),
                    selected = state.settings.theme,
                    onSelect = vm::setTheme,
                    label = {
                        when (it) {
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                            ThemeMode.SYSTEM -> "跟随系统"
                        }
                    },
                )
            }
        }

        // ===== 目标与番茄钟 =====
        item {
            SectionCard {
                Text("每日屏幕目标", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "当前 ${state.settings.dailyGoalMin / 60} 小时 ${state.settings.dailyGoalMin % 60} 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.textSecondary,
                )
                Slider(
                    value = state.settings.dailyGoalMin.toFloat(),
                    onValueChange = { vm.setDailyGoal(it.toInt()) },
                    valueRange = 30f..720f,
                    steps = 22,
                )

                Spacer(Modifier.height(12.dp))
                Text("番茄钟", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                NumberRow("专注时长（分钟）", state.settings.pomodoro.focusMin) { vm.setPomodoro(focusMin = it) }
                NumberRow("短休息（分钟）", state.settings.pomodoro.shortBreakMin) { vm.setPomodoro(shortBreakMin = it) }
                NumberRow("长休息（分钟）", state.settings.pomodoro.longBreakMin) { vm.setPomodoro(longBreakMin = it) }
                NumberRow("几轮后长休息", state.settings.pomodoro.cyclesBeforeLongBreak) { vm.setPomodoro(cycles = it) }
                NumberRow("倒计时默认（分钟）", state.settings.countdownMin) { vm.setCountdownMin(it) }

                Spacer(Modifier.height(8.dp))
                SwitchRow("自动开始休息", state.settings.pomodoro.autoStartBreak, vm::setAutoStartBreak)
                SwitchRow("休息后自动开始下一轮", state.settings.pomodoro.autoStartNextFocus, vm::setAutoStartNextFocus)
            }
        }

        // ===== 监控 =====
        item {
            SectionCard {
                Text("屏幕监控", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "关闭后不再采集使用数据，统计页将停止更新。专注功能不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.textTertiary,
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow("启用屏幕使用监控", state.settings.monitorEnabled, vm::setMonitor)
                SwitchRow(
                    "精确监控（更及时）",
                    state.settings.preciseMonitor,
                ) { on -> vm.setPreciseMonitor(context, on) }
                if (state.settings.preciseMonitor) {
                    Text(
                        "已开启：常驻一条通知，每 60 秒检查一次限额与挑战，亮屏/解锁瞬间立即检查。比默认的 15 分钟周期更及时，代价是常驻通知与更多耗电。",
                        style = MaterialTheme.typography.labelSmall,
                        color = extras.textTertiary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "使用情况访问权限",
                            style = MaterialTheme.typography.titleMedium,
                            color = extras.textPrimary,
                        )
                        Text(
                            if (usageGranted) "已授权，正在统计各应用使用时长" else "未授权，屏幕洞察与日报不可用",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (usageGranted) extras.success else extras.warning,
                        )
                    }
                    if (usageGranted) {
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text("管理") }
                    } else {
                        Button(onClick = { showPermDialog = true }) { Text("去授权") }
                    }
                }
            }
        }

        // ===== 干预：限额与挑战 =====
        item {
            SectionCard {
                Text("干预", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "全部默认关闭。留白只做提醒，不拦截、不锁屏。",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.textTertiary,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenLimits, modifier = Modifier.fillMaxWidth()) {
                    Text("应用每日限额")
                }
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    "连续使用提醒",
                    state.settings.challenge.continuousEnabled,
                ) { on -> vm.setChallenge(context) { it.copy(continuousEnabled = on) } }
                if (state.settings.challenge.continuousEnabled) {
                    NumberRow("超过多少分钟提醒", state.settings.challenge.continuousMin) { v ->
                        vm.setChallenge(context) { it.copy(continuousMin = v.coerceIn(15, 180)) }
                    }
                }
                SwitchRow(
                    "拿起次数提醒",
                    state.settings.challenge.pickupEnabled,
                ) { on -> vm.setChallenge(context) { it.copy(pickupEnabled = on) } }
                if (state.settings.challenge.pickupEnabled) {
                    NumberRow("每日次数上限", state.settings.challenge.pickupCount) { v ->
                        vm.setChallenge(context) { it.copy(pickupCount = v.coerceIn(10, 300)) }
                    }
                }
                SwitchRow(
                    "就寝提醒",
                    state.settings.challenge.bedtimeEnabled,
                ) { on -> vm.setChallenge(context) { it.copy(bedtimeEnabled = on) } }
                if (state.settings.challenge.bedtimeEnabled) {
                    TimeRow(
                        label = "就寝时间",
                        hour = state.settings.challenge.bedtimeHour,
                        minute = state.settings.challenge.bedtimeMinute,
                        onPick = { h, m ->
                            vm.setChallenge(context) {
                                it.copy(bedtimeHour = h, bedtimeMinute = m)
                            }
                        },
                    )
                }
            }
        }

        // ===== 数据 =====
        item {
            SectionCard {
                Text("数据", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { createBackup.launch("liubai-backup-${System.currentTimeMillis()}.json") }) {
                        Text("导出备份")
                    }
                    OutlinedButton(onClick = { openBackup.launch(arrayOf("application/json", "*/*")) }) {
                        Text("导入备份")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "备份包含专注记录、标签与设置，为单个 JSON 文件，不经过任何服务器。",
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.textTertiary,
                )
            }
        }

        // ===== 赞助 =====
        item {
            SectionCard {
                Text("赞助", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "「留白」完全免费，无会员、无广告、无功能门槛。如果它帮到了你，欢迎自愿赞助——赞助不会解锁任何功能，因为所有功能本就免费。",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(extras.window),
                        contentAlignment = Alignment.Center,
                    ) {
                        AssetImage(
                            context = context,
                            assetName = "sponsor_qr.jpg",
                            contentDescription = "微信收款码",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "微信扫码 · 自愿赞助",
                            style = MaterialTheme.typography.bodyMedium,
                            color = extras.textPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "金额随意，一元也是心意。\n不会解锁任何功能——所有功能本来就免费。",
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.textTertiary,
                        )
                    }
                }
            }
        }

        // ===== 关于 =====
        item {
            SectionCard {
                Text("关于", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "1.2.7",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.textSecondary,
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onCheckUpdate) { Text("检查更新") }
                }
                InfoRow("名称", "Margin「留白」")
                InfoRow("官方网站", "margin.liubai.app")
                InfoRow("开源许可", "MIT License")
                InfoRow("数据存储", "仅本机（Room + DataStore）")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onOpenHelp) { Text("帮助与常见问题") }
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://margin-8ux.pages.dev"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("打开官网") }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showPermDialog) {
        PermissionRationaleDialog(
            title = "开启屏幕洞察",
            message = "留白需要「使用情况访问」权限，才能统计各应用的使用时长、" +
                "生成每日报告与限额提醒。\n\n" +
                "这是安卓唯一的官方途径：数据只存在你的手机里，不会上传到任何服务器。" +
                "拒绝也不影响专注、标签与白噪音功能。",
            onConfirm = {
                showPermDialog = false
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            onDismiss = { showPermDialog = false },
        )
    }

    toast?.let { msg ->
        AlertDialog(
            onDismissRequest = { toast = null },
            title = { Text(msg) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("好") } },
        )
    }
}

/** 本地文件图片（避免为单张图片引入 Coil） */
/** 从 assets 读取打包进 App 的图片（赞助收款码） */
@Composable
private fun AssetImage(
    context: android.content.Context,
    assetName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val bmp = remember(assetName) {
        runCatching {
            context.assets.open(assetName).use { android.graphics.BitmapFactory.decodeStream(it) }
                ?.asImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun NumberRow(label: String, value: Int, onValue: (Int) -> Unit) {
    val extras = LocalExtraColors.current
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = extras.textSecondary, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it.filter { c -> c.isDigit() }
                text.toIntOrNull()?.let(onValue)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(88.dp),
        )
    }
}

/** 就寝小时的可选范围：当晚 18:00 到次日 03:00 */
private val BEDTIME_HOURS = listOf(18, 19, 20, 21, 22, 23, 0, 1, 2, 3)

/**
 * 一行「就寝时间 23:30」。
 * 之前拆成「就寝小时」+「就寝分钟」两个独立数字框，读起来像在问睡多少分钟，
 * 实际只是设定时刻——合成一个时间控件就没有这个歧义了。
 */
@Composable
private fun TimeRow(
    label: String,
    hour: Int,
    minute: Int,
    onPick: (Int, Int) -> Unit,
) {
    val extras = LocalExtraColors.current
    var open by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = extras.textSecondary,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { open = true }) {
            Text(clockText(hour, minute), style = MaterialTheme.typography.titleMedium)
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("就寝时间") },
            text = {
                Column {
                    Text(
                        "几点上床",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.textSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { onPick(stepHour(hour, -1), minute) }) { Text("−") }
                        Text(
                            clockText(hour, minute),
                            style = MaterialTheme.typography.headlineMedium,
                            color = extras.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { onPick(stepHour(hour, 1), minute) }) { Text("＋") }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "分钟",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.textSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 15, 30, 45).forEach { m ->
                            FilterChip(
                                selected = minute == m,
                                onClick = { onPick(hour, m) },
                                label = { Text(":${m.toString().padStart(2, '0')}") },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "精确到 15 分钟就够了。提醒由系统调度，实际到达可能有几分钟误差。",
                        style = MaterialTheme.typography.labelSmall,
                        color = extras.textTertiary,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("完成") } },
        )
    }
}

private fun stepHour(hour: Int, delta: Int): Int {
    val i = BEDTIME_HOURS.indexOf(hour).takeIf { it >= 0 } ?: BEDTIME_HOURS.indexOf(23)
    return BEDTIME_HOURS[(i + delta + BEDTIME_HOURS.size) % BEDTIME_HOURS.size]
}

private fun clockText(hour: Int, minute: Int): String =
    "${(hour % 24).toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {    val extras = LocalExtraColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = extras.textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val extras = LocalExtraColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = extras.textTertiary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = extras.textSecondary)
    }
}
