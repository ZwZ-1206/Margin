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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liubai.core.designsystem.component.SectionCard
import com.liubai.core.designsystem.component.SegmentedToggle
import com.liubai.core.designsystem.theme.LocalExtraColors
import com.liubai.domain.model.ThemeMode
import java.io.File

@Composable
fun SettingsScreen(
    onOpenLimits: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val extras = LocalExtraColors.current
    val context = LocalContext.current
    var showSponsorDialog by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val pickQr = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { vm.uploadSponsorQr(context, it) { ok -> toast = if (ok) "收款码已保存" else "图片读取失败" } }
    }
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
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                ) { Text("检查权限状态") }
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
                    NumberRow("就寝小时（24h）", state.settings.challenge.bedtimeHour) { v ->
                        vm.setChallenge(context) { it.copy(bedtimeHour = v.coerceIn(18, 27) % 24) }
                    }
                    NumberRow("就寝分钟", state.settings.challenge.bedtimeMinute) { v ->
                        vm.setChallenge(context) { it.copy(bedtimeMinute = v.coerceIn(0, 59)) }
                    }
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
                if (state.sponsorQrExists) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                                .background(extras.window)
                        ) {
                            LocalImage(
                                file = File(context.filesDir, "sponsor_qr.png"),
                                contentDescription = "收款码",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("收款码已保存", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { showSponsorDialog = true }) { Text("更换") }
                            TextButton(onClick = { vm.removeSponsorQr(context) }) { Text("移除") }
                        }
                    }
                } else {
                    Button(onClick = { pickQr.launch(arrayOf("image/*")) }) { Text("上传我的收款码") }
                }
            }
        }

        // ===== 关于 =====
        item {
            SectionCard {
                Text("关于", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                InfoRow("版本", "1.1.0")
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

    if (showSponsorDialog) {
        AlertDialog(
            onDismissRequest = { showSponsorDialog = false },
            title = { Text("更换收款码") },
            text = { Text("选择一张新的收款码图片，将替换当前保存的图片。") },
            confirmButton = {
                TextButton(onClick = {
                    showSponsorDialog = false
                    pickQr.launch(arrayOf("image/*"))
                }) { Text("选择图片") }
            },
            dismissButton = { TextButton(onClick = { showSponsorDialog = false }) { Text("取消") } },
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
@Composable
private fun LocalImage(file: File, contentDescription: String?, modifier: Modifier = Modifier) {
    val bmp = remember(file) {
        runCatching {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
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

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val extras = LocalExtraColors.current
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
