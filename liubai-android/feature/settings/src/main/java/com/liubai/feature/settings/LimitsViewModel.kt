package com.liubai.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liubai.data.di.ServiceLocator
import com.liubai.data.usage.AppLabelResolver
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
     * 关键点：包枚举在 IO 线程做（几百个包名走 IPC，主线程会掉帧），
     * 且依赖 Manifest 里的 <queries> 声明——没有它，Android 11+ 只返回两三个应用。
     */
    fun loadInstalled(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            loadingFlow.value = true
            val apps = withContext(Dispatchers.IO) {
                runCatching {
                    val pm = appContext.packageManager
                    val launcher = Intent(Intent.ACTION_MAIN, null)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                    val resolver = AppLabelResolver(appContext)
                    pm.queryIntentActivities(launcher, PackageManager.MATCH_DEFAULT_ONLY)
                        .asSequence()
                        .map { it.activityInfo.packageName }
                        .filter { it != appContext.packageName }
                        .distinct()
                        .map { InstalledApp(it, resolver.label(it)) }
                        .sortedBy { it.label.lowercase() }
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
