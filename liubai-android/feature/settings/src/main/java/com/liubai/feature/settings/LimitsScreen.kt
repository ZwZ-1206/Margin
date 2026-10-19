package com.liubai.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liubai.core.designsystem.component.SectionCard
import com.liubai.core.designsystem.theme.LocalExtraColors
import com.liubai.data.usage.AppLabelResolver

private val DURATION_OPTIONS = listOf(15, 30, 45, 60, 90, 120)

@Composable
fun LimitsScreen(
    modifier: Modifier = Modifier,
    vm: LimitsViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val extras = LocalExtraColors.current
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var pickingApp by remember { mutableStateOf<InstalledApp?>(null) }

    LaunchedEffect(Unit) { vm.loadInstalled(context) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("应用限额", style = MaterialTheme.typography.displaySmall, color = extras.textPrimary)
            Text(
                "给某个应用设一个每日上限，快到时提醒一次，超过后再提醒一次。默认全部关闭——需要时再打开。",
                style = MaterialTheme.typography.bodyMedium,
                color = extras.textTertiary,
            )
        }

        item {
            Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
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
                            )
                            Text(
                                "每日 ${limit.limitMs / 60000} 分钟",
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.textTertiary,
                            )
                        }
                        TextButton(onClick = { vm.remove(limit.packageName) }) { Text("移除") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // 选择应用
    if (showPicker && pickingApp == null) {
        var query by remember { mutableStateOf("") }
        val filtered = state.installed.filter {
            query.isBlank() || it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择应用") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜索") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            TextButton(
                                onClick = { pickingApp = app },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    app.label,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = extras.textPrimary,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        )
    }

    // 选择时长
    pickingApp?.let { app ->
        AlertDialog(
            onDismissRequest = { pickingApp = null },
            title = { Text("${app.label} 的每日限额") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DURATION_OPTIONS.take(3).forEach { min ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    vm.setLimit(app.packageName, min)
                                    pickingApp = null
                                    showPicker = false
                                },
                                label = { Text("$min 分钟") },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DURATION_OPTIONS.drop(3).forEach { min ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    vm.setLimit(app.packageName, min)
                                    pickingApp = null
                                    showPicker = false
                                },
                                label = { Text("$min 分钟") },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickingApp = null }) { Text("取消") } },
        )
    }
}
