package com.liubai.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liubai.core.common.DateKeys
import com.liubai.data.di.ServiceLocator
import com.liubai.data.lock.AppLockManager
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
    /** 硬锁总开关是否打开 */
    val hardLockEnabled: Boolean = false,
    /** 是否已设置六位解锁密码 */
    val lockPasswordSet: Boolean = false,
    /** 今日已触发锁定的包名（列表角标用） */
    val lockedToday: Set<String> = emptySet(),
) {
    /** 总开关 + 密码都就绪，单应用「达限额后锁定」才可用 */
    val hardLockReady: Boolean get() = hardLockEnabled && lockPasswordSet
}

class LimitsViewModel : ViewModel() {

    private val repo = ServiceLocator.limitsRepository
    private val installedFlow = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loadingFlow = MutableStateFlow(true)
    private val today = DateKeys.today()

    val uiState: StateFlow<LimitsUiState> = combine(
        repo.observeLimits(),
        installedFlow,
        loadingFlow,
        ServiceLocator.settingsRepository.settings,
        ServiceLocator.usageRepository.observeDailyUsage(today),
    ) { limits, installed, loading, settings, usage ->
        val used = usage.associate { it.packageName to it.totalMs }
        val ready = settings.hardLockEnabled && !settings.lockPasswordHash.isNullOrBlank()
        val locked = if (!ready) emptySet() else limits
            .filter { it.enabled && it.hardLock }
            .filter { (used[it.packageName] ?: 0L) >= it.limitMs }
            .map { it.packageName }
            .toSet()
        LimitsUiState(
            limits = limits,
            installed = installed,
            loadingApps = loading,
            hardLockEnabled = settings.hardLockEnabled,
            lockPasswordSet = !settings.lockPasswordHash.isNullOrBlank(),
            lockedToday = locked,
        )
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

    /** 新增 / 更新限额。hardLock = 达到限额后锁定该应用 */
    fun setLimit(packageName: String, minutes: Int, hardLock: Boolean = false) {
        viewModelScope.launch {
            val existing = repo.limitOf(packageName)
            repo.setLimit(
                AppLimit(
                    packageName = packageName,
                    limitMs = minutes * 60_000L,
                    enabled = existing?.enabled ?: true,
                    hardLock = hardLock,
                )
            )
        }
    }

    /** 单应用硬锁开关（仅在总开关已开、密码已设时可操作） */
    fun setHardLock(packageName: String, on: Boolean) {
        viewModelScope.launch {
            val cur = repo.limitOf(packageName) ?: return@launch
            repo.setLimit(cur.copy(hardLock = on))
            // 关掉单应用锁定时清掉宽限期，避免「关了就放行十分钟」的漏洞
            if (!on) AppLockManager.clearGrace(ServiceLocator.appContext, packageName)
        }
    }

    fun remove(packageName: String) {
        viewModelScope.launch {
            AppLockManager.clearGrace(ServiceLocator.appContext, packageName)
            repo.removeLimit(packageName)
        }
    }

    /**
     * 添加限额时引导开启硬锁：先打开总开关，缺密码则回调要求设置。
     * 总开关只在这里由用户确认后打开，不会偷偷自动开。
     */
    fun ensureHardLockReady(onReady: () -> Unit, onNeedPassword: () -> Unit) {
        viewModelScope.launch {
            ServiceLocator.settingsRepository.update { it.copy(hardLockEnabled = true) }
            val cur = ServiceLocator.settingsRepository.current()
            if (cur.lockPasswordHash.isNullOrBlank()) onNeedPassword() else onReady()
        }
    }

    /** 设置 / 修改六位解锁密码（只存哈希） */
    fun setLockPassword(pin: String) {
        viewModelScope.launch {
            ServiceLocator.settingsRepository.update {
                it.copy(lockPasswordHash = AppLockManager.hashPin(pin))
            }
        }
    }
}
