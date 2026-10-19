package com.liubai.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liubai.data.di.ServiceLocator
import com.liubai.data.repository.BackupManager
import com.liubai.data.work.MonitorService
import com.liubai.data.work.WorkScheduler
import com.liubai.domain.model.AppSettings
import com.liubai.domain.model.ChallengeConfig
import com.liubai.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
) {
    val sponsorQrExists: Boolean get() = settings.sponsorQrPath != null
}

class SettingsViewModel : ViewModel() {

    private val settingsRepo = ServiceLocator.settingsRepository

    val uiState: StateFlow<SettingsUiState> = settingsRepo.settings
        .map { SettingsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(mode: ThemeMode) = update { it.copy(theme = mode) }

    fun setDailyGoal(min: Int) = update { it.copy(dailyGoalMin = min.coerceIn(30, 720)) }

    fun setPomodoro(
        focusMin: Int? = null,
        shortBreakMin: Int? = null,
        longBreakMin: Int? = null,
        cycles: Int? = null,
    ) = update { cur ->
        cur.copy(
            pomodoro = cur.pomodoro.copy(
                focusMin = focusMin?.coerceIn(5, 120) ?: cur.pomodoro.focusMin,
                shortBreakMin = shortBreakMin?.coerceIn(1, 30) ?: cur.pomodoro.shortBreakMin,
                longBreakMin = longBreakMin?.coerceIn(5, 60) ?: cur.pomodoro.longBreakMin,
                cyclesBeforeLongBreak = cycles?.coerceIn(2, 8) ?: cur.pomodoro.cyclesBeforeLongBreak,
            )
        )
    }

    fun setCountdownMin(min: Int) = update { it.copy(countdownMin = min.coerceIn(5, 180)) }

    fun setMonitor(enabled: Boolean) = update { it.copy(monitorEnabled = enabled) }

    fun setAutoStartBreak(enabled: Boolean) = update {
        it.copy(pomodoro = it.pomodoro.copy(autoStartBreak = enabled))
    }

    fun setAutoStartNextFocus(enabled: Boolean) = update {
        it.copy(pomodoro = it.pomodoro.copy(autoStartNextFocus = enabled))
    }

    /** 上传赞助收款码：复制到 filesDir，仅存相对路径 */
    fun uploadSponsorQr(context: Context, uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        File(context.filesDir, "sponsor_qr.png").outputStream().use { out ->
                            input.copyTo(out)
                        }
                    } ?: error("无法读取所选图片")
                    true
                }.getOrDefault(false)
            }
            if (ok) update { it.copy(sponsorQrPath = "sponsor_qr.png") }
            onDone(ok)
        }
    }

    fun removeSponsorQr(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { File(context.filesDir, "sponsor_qr.png").delete() }
            }
            update { it.copy(sponsorQrPath = null) }
        }
    }

    fun exportBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) { runCatching { BackupManager.exportJson() }.getOrNull() }
            onResult(json ?: "{}")
        }
    }

    fun importBackup(json: String, onResult: (Int?) -> Unit) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching { BackupManager.importJson(json).getOrNull() }.getOrNull()
            }
            onResult(count)
        }
    }

    /** 更新挑战配置并同步就寝提醒调度 */
    fun setChallenge(
        context: Context,
        transform: (ChallengeConfig) -> ChallengeConfig,
    ) {
        viewModelScope.launch {
            val next = transform(uiState.value.settings.challenge)
            settingsRepo.update { it.copy(challenge = next) }
            WorkScheduler.scheduleBedtime(context, next.bedtimeEnabled, next.bedtimeHour, next.bedtimeMinute)
        }
    }

    /** 精确监控：常驻前台服务每分钟检查一次，代价是常驻通知与额外耗电 */
    fun setPreciseMonitor(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(preciseMonitor = enabled, monitorEnabled = true) }
            runCatching {
                val intent = MonitorService.intent(context, enabled)
                if (enabled) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }
    }

    fun rescheduleWorkers(context: Context) {
        WorkScheduler.scheduleUsageCollect(context)
        WorkScheduler.scheduleDailyReport(context)
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }
}
