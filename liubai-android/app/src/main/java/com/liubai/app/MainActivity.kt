package com.liubai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.liubai.data.report.DailyReportNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.liubai.core.designsystem.theme.LiubaiTheme
import com.liubai.data.di.ServiceLocator
import com.liubai.domain.model.ThemeMode
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.liubai.app.update.ApkInstaller
import com.liubai.app.widget.MarginWidgetProvider
import com.liubai.data.update.UpdateChecker
import kotlinx.coroutines.launch
import com.liubai.feature.focus.FocusScreen
import com.liubai.feature.home.HomeScreen
import com.liubai.feature.settings.HelpScreen
import com.liubai.feature.settings.LimitsScreen
import com.liubai.feature.settings.SettingsScreen
import com.liubai.feature.stats.StatsScreen

class MainActivity : ComponentActivity() {

    /** Android 13+ 通知权限：没有它，限额提醒、日报、专注通知都发不出来 */
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()
        // 次日首次打开推昨日报告（同一天只推一次，昨天无数据则静默）
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { DailyReportNotifier.notifyYesterdayIfNeeded(this@MainActivity) }
        }

        setContent {
            val settings by ServiceLocator.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val dark = when (settings?.theme ?: ThemeMode.SYSTEM) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            // 每次进入 App 顺带刷新桌面小部件（系统定时之外的数据新鲜度保障）
            MarginWidgetProvider.updateAll(this)

            LiubaiTheme(darkTheme = dark) {
                AppRoot(
                    dark = dark,
                    onGrantUsagePermission = {
                        startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                )
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
private fun AppRoot(dark: Boolean, onGrantUsagePermission: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    // 每次启动检查一次；失败静默，不打扰
    LaunchedEffect(Unit) {
        release = runCatching { UpdateChecker.hasUpdate(BuildConfig.VERSION_CODE) }.getOrNull()
    }

    fun checkManually() {
        scope.launch {
            val found = runCatching { UpdateChecker.hasUpdate(BuildConfig.VERSION_CODE) }.getOrNull()
            if (found == null) message = "已是最新版本 v${BuildConfig.VERSION_NAME}"
            else release = found
        }
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    val tabs = listOf(
        Tab("home", "首页") { Icon(Icons.Filled.Home, contentDescription = null) },
        Tab("focus", "专注") { Icon(IconMarginRing, contentDescription = null) },
        Tab("stats", "统计") { Icon(Icons.Filled.DateRange, contentDescription = null) },
        Tab("settings", "设置") { Icon(Icons.Filled.Settings, contentDescription = null) },
    )

    release?.let { r ->
        AlertDialog(
            onDismissRequest = { if (progress == null) release = null },
            title = { Text("发现新版本 v${r.versionName}") },
            text = {
                Column {
                    Text(
                        r.notes.ifBlank { "修复问题并改进体验。" },
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                    if (progress != null) {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { (progress ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("正在下载 ${progress ?: 0}%")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = progress == null,
                    onClick = {
                        scope.launch {
                            progress = 0
                            val outcome = ApkInstaller.download(
                                context = context,
                                url = r.downloadUrl,
                                fileName = "margin-${r.versionName}.apk",
                                expectedSha256 = r.sha256,
                                expectedSize = r.sizeBytes,
                            ) { p -> progress = p.coerceAtLeast(0) }
                            progress = null
                            when (outcome) {
                                is ApkInstaller.Outcome.Success -> {
                                    val ok = runCatching { ApkInstaller.install(context, outcome.file) }
                                        .getOrDefault(false)
                                    if (!ok) message = "请先允许「安装未知应用」，然后重新点击更新"
                                }
                                is ApkInstaller.Outcome.Failure -> {
                                    message = when (outcome.reason) {
                                        ApkInstaller.Reason.NO_URL ->
                                            "没有找到安装包地址，请到官网手动下载"
                                        ApkInstaller.Reason.NETWORK ->
                                            "下载失败，请检查网络后重试"
                                        else ->
                                            "安装包校验未通过（文件不完整或被替换），请到官网手动下载"
                                    }
                                }
                            }
                            release = null
                        }
                    },
                ) { Text(if (progress != null) "下载中…" else "立即更新") }
            },
            dismissButton = {
                TextButton(onClick = { release = null }, enabled = progress == null) { Text("稍后") }
            },
        )
    }

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("好") } },
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = tab.icon,
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    onStartFocus = { navController.navigate("focus") },
                    onOpenStats = { navController.navigate("stats") },
                    onGrantPermission = onGrantUsagePermission,
                )
            }
            composable("focus") { FocusScreen() }
            composable("stats") {
                StatsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    onOpenLimits = { navController.navigate("limits") },
                    onOpenHelp = { navController.navigate("help") },
                    onCheckUpdate = { checkManually() },
                )
            }
            composable("limits") {
                LimitsScreen(onBack = { navController.popBackStack() })
            }
            composable("help") {
                HelpScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
