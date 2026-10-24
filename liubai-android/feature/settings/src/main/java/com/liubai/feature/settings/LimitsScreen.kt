package com.liubai.feature.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liubai.core.designsystem.component.ScreenHeader
import com.liubai.core.designsystem.component.SectionCard
import com.liubai.core.designsystem.theme.LocalExtraColors
import com.liubai.data.usage.AppLabelResolver

private val DURATION_OPTIONS = listOf(15, 30, 45, 60, 90, 120)

/** 自定义时长上限：与每日屏幕目标一致，最多 12 小时 */
private const val CUSTOM_MAX_MINUTES = 720

@Composable
fun LimitsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    vm: LimitsViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val extras = LocalExtraColors.current
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var pickingApp by remember { mutableStateOf<InstalledApp?>(null) }
    // 已选时长、等待「是否硬锁」询问
    var pendingMinutes by remember { mutableStateOf<Int?>(null) }
    var showCustomDuration by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadInstalled(context) }

    fun commit(minutes: Int, hardLock: Boolean, app: InstalledApp) {
        vm.setLimit(app.packageName, minutes, hardLock)
        pendingMinutes = null
        pickingApp = null
        showPicker = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            ScreenHeader(
                title = "应用限额",
                subtitle = "给某个应用设一个每日上限，快到时提醒一次，超过后再提醒一次。" +
                    "添加时也可以选择「达到限额后锁定」——硬锁默认关闭，需要六位解锁密码。",
                onBack = onBack,
            )
        }

        item {
            Button(
                onClick = {
                    pickingApp = null
                    showPicker = true
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("添加限额", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (state.limits.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        "还没有设置任何限额。限额是提醒，不是锁屏——留白不会替你做决定。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = extras.textSecondary,
                    )
                }
            }
        } else {
            items(state.limits, key = { it.packageName }) { limit ->
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                AppLabelResolver(context).label(limit.packageName),
                                style = MaterialTheme.typography.titleLarge,
                                color = extras.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    append("每日 ${limit.limitMs / 60000} 分钟")
                                    if (limit.hardLock) append(" · 达限额后锁定")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.textTertiary,
                            )
                            if (limit.packageName in state.lockedToday) {
                                Text(
                                    "今日已锁定：打开该应用需输入密码，解锁后有 10 分钟宽限",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extras.warning,
                                )
                            } else if (limit.hardLock && !state.hardLockReady) {
                                Text(
                                    "硬锁未生效：请先到「设置 → 干预」开启总开关并设置密码",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extras.warning,
                                )
                            }
                        }
                        if (state.hardLockReady) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Switch(
                                    checked = limit.hardLock,
                                    onCheckedChange = { vm.setHardLock(limit.packageName, it) },
                                )
                                Text(
                                    "锁定",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extras.textTertiary,
                                )
                            }
                        }
                        TextButton(onClick = { vm.remove(limit.packageName) }) { Text("移除") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // 单层全屏面板：第一步选应用，第二步选时长，右上角始终可关闭，左上角可回退
    if (showPicker) {
        val app = pickingApp
        if (app == null) {
            AppPickerPanel(
                apps = state.installed,
                loading = state.loadingApps,
                onClose = { showPicker = false },
                onPick = { pickingApp = it },
            )
        } else {
            DurationPanel(
                app = app,
                currentLimitMs = state.limits.firstOrNull { it.packageName == app.packageName }?.limitMs,
                onBack = { pickingApp = null },
                onClose = {
                    pickingApp = null
                    showPicker = false
                },
                onPickDuration = { min ->
                    // 不直接落库：先问要不要硬锁
                    pendingMinutes = min
                },
                onCustom = { showCustomDuration = true },
            )
        }
    }

    // 自定义时长：小时、分钟分开设
    if (showCustomDuration && pickingApp != null) {
        CustomDurationDialog(
            onConfirm = { min ->
                showCustomDuration = false
                pendingMinutes = min
            },
            onDismiss = { showCustomDuration = false },
        )
    }

    // 达到限额后是否锁定？
    val minutes = pendingMinutes
    val app = pickingApp
    if (minutes != null && app != null) {
        HardLockAskDialog(
            appLabel = app.label,
            hardLockReady = state.hardLockReady,
            onLock = {
                if (state.hardLockReady) {
                    commit(minutes, true, app)
                } else {
                    // 引导：先开总开关，缺密码再补设
                    vm.ensureHardLockReady(
                        onReady = { commit(minutes, true, app) },
                        onNeedPassword = { showPinSetup = true },
                    )
                }
            },
            onSkip = { commit(minutes, false, app) },
            onDismiss = { pendingMinutes = null },
        )
    }

    // 引导流程里补设六位密码
    if (showPinSetup) {
        PinSetupDialog(
            title = "设置解锁密码",
            subtitle = "开启硬锁前，先设一个六位解锁密码。达到限额后锁定应用时，需要输入它才能继续使用。",
            onConfirm = { pin ->
                showPinSetup = false
                vm.setLockPassword(pin)
                val m = pendingMinutes
                val a = pickingApp
                if (m != null && a != null) commit(m, true, a)
            },
            onDismiss = {
                showPinSetup = false
                pendingMinutes = null
            },
        )
    }
}

@Composable
private fun AppPickerPanel(
    apps: List<InstalledApp>,
    loading: Boolean,
    onClose: () -> Unit,
    onPick: (InstalledApp) -> Unit,
) {
    val extras = LocalExtraColors.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }
    val resolver = remember { AppLabelResolver(context) }
    val iconPx = with(LocalDensity.current) { 36.dp.roundToPx() }

    // 系统返回键 = 关闭
    BackHandler(onBack = onClose)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SurfacePanel(onClose = onClose, title = "选择应用") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索应用") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    loading -> "正在读取已安装的应用…"
                    apps.isEmpty() -> "没有读到任何应用，请确认已授予「使用情况访问权限」"
                    else -> "共 ${filtered.size} 个应用"
                },
                style = MaterialTheme.typography.labelSmall,
                color = extras.textTertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(6.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = remember(app.packageName) {
                                resolver.icon(app.packageName)?.toImageBitmap(iconPx)
                            }
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(extras.window, RoundedCornerShape(9.dp)),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = extras.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extras.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider(color = extras.window)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DurationPanel(
    app: InstalledApp,
    currentLimitMs: Long?,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onPickDuration: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    val extras = LocalExtraColors.current
    // 第二步的返回键回到应用列表，而不是直接退出
    BackHandler(onBack = onBack)

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SurfacePanel(onClose = onClose, title = app.label, onBack = onBack) {
            Text(
                "每天最多允许使用多久？选完后会问你要不要「达到限额后锁定」。",
                style = MaterialTheme.typography.bodySmall,
                color = extras.textTertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
            DURATION_OPTIONS.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { min ->
                        FilterChip(
                            selected = currentLimitMs == min * 60_000L,
                            onClick = { onPickDuration(min) },
                            label = {
                                Text("$min 分钟", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                FilterChip(
                    selected = false,
                    onClick = onCustom,
                    label = { Text("自定义时长…") },
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("← 换一个应用") }
        }
    }
}

/** 自定义时长：小时、分钟两个独立输入，避免「130 分钟」这种反人类读法 */
@Composable
private fun CustomDurationDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = LocalExtraColors.current
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    val h = hours.toIntOrNull() ?: 0
    val m = minutes.toIntOrNull() ?: 0
    val total = h * 60 + m
    val valid = total in 1..CUSTOM_MAX_MINUTES && m in 0..59

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义时长") },
        text = {
            Column {
                Text(
                    "小时和分钟分开填。到点后的处理（提醒或锁定）选完时长再决定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("小时") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("分钟") },
                        singleLine = true,
                        isError = (minutes.toIntOrNull() ?: 0) > 59,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        total <= 0 -> "至少 1 分钟"
                        total > CUSTOM_MAX_MINUTES -> "最多 12 小时（720 分钟）"
                        m > 59 -> "分钟要小于 60"
                        else -> "合计 ${total / 60} 小时 ${total % 60} 分钟"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.textTertiary,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(total) }) { Text("下一步") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 统一的底部面板容器：标题栏 + 关闭 / 返回 */
@Composable
private fun SurfacePanel(
    title: String,
    onClose: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val extras = LocalExtraColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = extras.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

private fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap? = runCatching {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    bmp.asImageBitmap()
}.getOrNull()
