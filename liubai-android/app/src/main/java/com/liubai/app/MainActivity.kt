package com.liubai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.liubai.app.widget.MarginWidgetProvider
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

            // 首次进入若未授予「使用情况访问」，主动引导一次
            var showUsageDialog by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!com.liubai.data.usage.UsageAccess.isGranted(this@MainActivity)) {
                    val prefs = getSharedPreferences("onboarding", MODE_PRIVATE)
                    val asked = prefs.getBoolean("usage_asked", false)
                    showUsageDialog = !asked
                    if (!asked) prefs.edit().putBoolean("usage_asked", true).apply()
                }
            }

            LiubaiTheme(darkTheme = dark) {
                if (showUsageDialog) {
                    AlertDialog(
                        onDismissRequest = { showUsageDialog = false },
                        title = { Text("开启屏幕洞察") },
                        text = {
                            Text(
                                "留白需要「使用情况访问」权限，才能统计每天各应用的使用时长、生成日报与限额提醒。\n\n" +
                                    "这是安卓唯一的官方途径，数据只在你的手机里，不会上传。拒绝也可以正常使用专注功能。",
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showUsageDialog = false
                                startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }) { Text("去授权") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUsageDialog = false }) { Text("稍后") }
                        },
                    )
                }

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
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    val tabs = listOf(
        Tab("home", "首页") { Icon(Icons.Filled.Home, contentDescription = null) },
        Tab("focus", "专注") { Icon(IconMarginRing, contentDescription = null) },
        Tab("stats", "统计") { Icon(Icons.Filled.DateRange, contentDescription = null) },
        Tab("settings", "设置") { Icon(Icons.Filled.Settings, contentDescription = null) },
    )

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
            composable("stats") { StatsScreen() }
            composable("settings") {
                SettingsScreen(
                    onOpenLimits = { navController.navigate("limits") },
                    onOpenHelp = { navController.navigate("help") },
                )
            }
            composable("limits") { LimitsScreen() }
            composable("help") { HelpScreen() }
        }
    }
}
