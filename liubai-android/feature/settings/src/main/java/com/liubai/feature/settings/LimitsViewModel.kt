package com.liubai.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liubai.data.di.ServiceLocator
import com.liubai.data.usage.AppLabelResolver
import com.liubai.data.usage.UsageAccess
import com.liubai.data.usage.UsageStatsReader
import com.liubai.domain.model.AppLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)

data class LimitsUiState(
    val limits: List<AppLimit> = emptyList(),
    val installed: List<InstalledApp> = emptyList(),
    val loadingApps: Boolean = true,
)

class LimitsViewModel : ViewModel() {

    private val repo = ServiceLocator.limitsRepository
    private val installedFlow = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loadingFlow = MutableStateFlow(true)

    val uiState: StateFlow<LimitsUiState> = combine(
        repo.observeLimits(),
        installedFlow,
        loadingFlow,
    ) { limits, installed, loading ->
        LimitsUiState(limits = limits, installed = installed, loadingApps = loading)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LimitsUiState())

    /**
     * 载入可设置限额的应用。
     *
     * 四个来源合并，缺一不可：
     *  1. LAUNCHER intent —— 有桌面图标的应用（绝大多数）
     *  2. 非系统应用全量 —— 装上但没有图标、也还没用过的应用
     *  3. UsageStats 近 30 天有使用记录的包 —— 有图标但被启动器隐藏的、只有快捷方式的
     *  4. QUERY_ALL_PACKAGES —— 让以上包名的 label / icon 能正确解析（否则只剩包名）
     *
     * 包枚举在 IO 线程做：几百个包名逐个走 IPC，主线程会掉帧。
     */
    fun loadInstalled(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            loadingFlow.value = true
            val apps = withContext(Dispatchers.IO) {
                runCatching {
                    val pm = appContext.packageManager
                    val resolver = AppLabelResolver(appContext)

                    val launcherPkgs = pm.queryIntentActivities(
                        Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER),
                        PackageManager.MATCH_DEFAULT_ONLY,
                    ).asSequence().map { it.activityInfo.packageName }.toList()

                    // 用户自己装的应用：排除纯系统组件，保留被系统升级过的（如系统浏览器）
                    val thirdPartyPkgs = pm.getInstalledApplications(0).asSequence()
                        .filter { info ->
                            info.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                                info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                        }
                        .map { it.packageName }
                        .toList()

                    val usedPkgs = if (UsageAccess.isGranted(appContext)) {
                        runCatching { UsageStatsReader(appContext).packagesWithUsage(30) }
                            .getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }

                    (launcherPkgs + thirdPartyPkgs + usedPkgs).asSequence()
                        .filter { it != appContext.packageName }
                        .distinct()
                        .mapNotNull { pkg ->
                            // 解析不到 ApplicationInfo 的，说明系统里已卸载或不可见，跳过
                            runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                                ?.let { InstalledApp(pkg, resolver.label(pkg)) }
                        }
                        .sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale.CHINA)) { it.label })
                        .toList()
                }.getOrDefault(emptyList())
            }
            installedFlow.value = apps
            loadingFlow.value = false
        }
    }

    fun setLimit(packageName: String, minutes: Int) {
        viewModelScope.launch {
            repo.setLimit(AppLimit(packageName, minutes * 60_000L))
        }
    }

    fun remove(packageName: String) {
        viewModelScope.launch { repo.removeLimit(packageName) }
    }
}
