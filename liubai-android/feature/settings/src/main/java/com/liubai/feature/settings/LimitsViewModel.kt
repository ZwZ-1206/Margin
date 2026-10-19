package com.liubai.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liubai.data.di.ServiceLocator
import com.liubai.data.usage.AppLabelResolver
import com.liubai.domain.model.AppLimit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InstalledApp(val packageName: String, val label: String)

data class LimitsUiState(
    val limits: List<AppLimit> = emptyList(),
    val installed: List<InstalledApp> = emptyList(),
)

class LimitsViewModel : ViewModel() {

    private val repo = ServiceLocator.limitsRepository
    private val installedFlow = MutableStateFlow<List<InstalledApp>>(emptyList())
    private var loaded = false

    val uiState: StateFlow<LimitsUiState> = combine(
        repo.observeLimits(),
        installedFlow,
    ) { limits, installed ->
        LimitsUiState(limits = limits, installed = installed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LimitsUiState())

    /** 载入可设置限额的应用（有启动入口的用户可见应用） */
    fun loadInstalled(context: Context) {
        if (loaded) return
        loaded = true
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolver = AppLabelResolver(context)
        val apps = pm.queryIntentActivities(launcher, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { InstalledApp(it, resolver.label(it)) }
            .sortedBy { it.label }
        installedFlow.value = apps
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
