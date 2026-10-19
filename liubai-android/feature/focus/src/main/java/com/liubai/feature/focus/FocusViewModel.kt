package com.liubai.feature.focus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liubai.data.di.ServiceLocator
import com.liubai.data.focus.FocusService
import com.liubai.data.focus.WhiteNoiseSynth
import com.liubai.domain.model.AppSettings
import com.liubai.domain.model.FocusMode
import com.liubai.domain.model.Tag
import com.liubai.domain.model.TimerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FocusUiState(
    val settings: AppSettings = AppSettings(),
    val tags: List<Tag> = emptyList(),
    val selectedTagId: Long? = null,
    val timer: TimerState = TimerState(),
    val countdownMin: Int = 30,
    val newTagName: String = "",
) {
    val hasActiveSession: Boolean get() = timer.isActive
}

class FocusViewModel : ViewModel() {

    private val focusRepo = ServiceLocator.focusRepository
    private val settingsRepo = ServiceLocator.settingsRepository

    val uiState: StateFlow<FocusUiState> = combine(
        settingsRepo.settings,
        focusRepo.observeTags(),
        focusRepo.observeCheckpoint(),
    ) { settings, tags, timer ->
        FocusUiState(
            settings = settings,
            tags = tags,
            selectedTagId = timer?.tagId,
            timer = timer ?: TimerState(),
            countdownMin = settings.countdownMin,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun selectMode(mode: FocusMode) {
        viewModelScope.launch { settingsRepo.update { it.copy(pomodoro = it.pomodoro) } }
        // 模式仅本次生效，存本地字段
        pendingMode = mode
    }

    private var pendingMode: FocusMode = FocusMode.POMODORO

    fun selectTag(id: Long?) {
        viewModelScope.launch {
            // 标签选择通过 settings 之外的本地状态保存：直接改 checkpoint 前的选择存 DataStore 会污染模型，
            // 这里用简单内存态 + 下次 start 传入
            selectedTag = id
        }
    }

    private var selectedTag: Long? = null

    fun addTag(name: String, emoji: String) {
        viewModelScope.launch {
            focusRepo.saveTag(Tag(name = name, emoji = emoji, createdAt = System.currentTimeMillis()))
        }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch { focusRepo.deleteTag(id) }
    }

    fun setNoise(context: Context, sound: WhiteNoiseSynth.Sound?) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(whiteNoise = sound?.name) }
        }
    }

    fun setVolume(volume: Int) {
        viewModelScope.launch { settingsRepo.update { it.copy(whiteNoiseVolume = volume.coerceIn(0, 100)) } }
    }

    fun start(context: Context) {
        val state = uiState.value
        val mode = pendingMode
        val tagId = selectedTag
        val intent = FocusService.startIntent(
            context = context,
            mode = mode,
            tagId = tagId,
            countdownMin = state.countdownMin,
        )
        context.startForegroundService(intent)
    }

    fun pause(context: Context) {
        context.startForegroundService(FocusService.actionIntent(context, FocusService.ACTION_PAUSE))
    }

    fun resume(context: Context) {
        context.startForegroundService(FocusService.actionIntent(context, FocusService.ACTION_RESUME))
    }

    fun stop(context: Context) {
        context.startForegroundService(FocusService.actionIntent(context, FocusService.ACTION_STOP))
    }

    /** 用户在主界面关闭完成态 */
    fun dismissFinished(context: Context) {
        context.startForegroundService(FocusService.actionIntent(context, FocusService.ACTION_DISMISS))
    }
}
