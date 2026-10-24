package com.liubai.feature.focus

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liubai.data.di.ServiceLocator
import com.liubai.data.focus.FocusService
import com.liubai.data.focus.SoundController
import com.liubai.data.focus.WhiteNoiseSynth
import com.liubai.domain.model.AppSettings
import com.liubai.domain.model.FocusMode
import com.liubai.domain.model.Tag
import com.liubai.domain.model.TimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FocusUiState(
    val settings: AppSettings = AppSettings(),
    val tags: List<Tag> = emptyList(),
    val selectedTagId: Long? = null,
    val timer: TimerState = TimerState(),
    val countdownMin: Int = 30,
    val mode: FocusMode = FocusMode.POMODORO,
    val customSounds: List<String> = emptyList(),
) {
    val hasActiveSession: Boolean get() = timer.isActive

    /** 当前选中的声音：设置里存的是 BUILTIN:xxx 或 FILE:xxx */
    val noiseKey: String? get() = settings.whiteNoise

    val selectedTag: Tag? get() = tags.firstOrNull { it.id == selectedTagId }
}

class FocusViewModel : ViewModel() {

    private val focusRepo = ServiceLocator.focusRepository
    private val settingsRepo = ServiceLocator.settingsRepository

    // 关键：模式与标签必须进状态流，否则 UI 点了没反应
    private val modeFlow = MutableStateFlow(FocusMode.POMODORO)
    private val tagFlow = MutableStateFlow<Long?>(null)
    private val customSoundsFlow = MutableStateFlow<List<String>>(emptyList())

    val uiState: StateFlow<FocusUiState> = combine(
        settingsRepo.settings,
        focusRepo.observeTags(),
        focusRepo.observeCheckpoint(),
        modeFlow,
        tagFlow,
        customSoundsFlow,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val settings = values[0] as AppSettings
        @Suppress("UNCHECKED_CAST")
        val tags = values[1] as List<Tag>
        val timer = values[2] as TimerState?
        val mode = values[3] as FocusMode
        val tag = values[4] as Long?
        @Suppress("UNCHECKED_CAST")
        val custom = values[5] as List<String>
        FocusUiState(
            settings = settings,
            tags = tags,
            selectedTagId = tag,
            timer = timer ?: TimerState(),
            countdownMin = settings.countdownMin,
            mode = mode,
            customSounds = custom,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    init {
        viewModelScope.launch {
            // 已有进行中的专注时，让模式跟随它
            focusRepo.observeCheckpoint().collect { cp ->
                if (cp != null && cp.isActive) modeFlow.value = cp.mode
            }
        }
    }

    fun selectMode(mode: FocusMode) {
        modeFlow.value = mode
    }

    /** 再次点击同一个标签 = 取消选择；选中时套用该标签上次用的倒计时时长 */
    fun selectTag(id: Long?) {
        val next = if (tagFlow.value == id) null else id
        tagFlow.value = next
        if (next == null) return
        val remembered = uiState.value.settings.countdownMinByTag[next] ?: return
        if (modeFlow.value == FocusMode.COUNTDOWN && uiState.value.countdownMin != remembered) {
            viewModelScope.launch {
                settingsRepo.update { it.copy(countdownMin = remembered) }
            }
        }
    }

    fun setCountdownMin(minutes: Int) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(countdownMin = minutes.coerceIn(5, 180)) }
        }
    }

    fun addTag(name: String, emoji: String) {
        viewModelScope.launch {
            focusRepo.saveTag(Tag(name = name, emoji = emoji, createdAt = System.currentTimeMillis()))
        }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch {
            if (tagFlow.value == id) tagFlow.value = null
            focusRepo.deleteTag(id)
        }
    }

    /** 选择音色：立即预览播放，并持久化 */
    fun setNoise(context: Context, key: String?) {
        val volume = (uiState.value.settings.whiteNoiseVolume / 100f).coerceIn(0f, 1f)
        val intensity = (uiState.value.settings.rainIntensity / 100f).coerceIn(0f, 1f)
        viewModelScope.launch {
            settingsRepo.update { it.copy(whiteNoise = key) }
        }
        SoundController.play(context, key, volume, intensity)
    }

    fun setVolume(volume: Int) {
        val v = volume.coerceIn(0, 100)
        viewModelScope.launch { settingsRepo.update { it.copy(whiteNoiseVolume = v) } }
        SoundController.applyVolume(v / 100f)
    }

    /** 雨声强度：实时生效（合成线程内平滑），并持久化供下次专注使用 */
    fun setRainIntensity(value: Int) {
        val v = value.coerceIn(0, 100)
        viewModelScope.launch { settingsRepo.update { it.copy(rainIntensity = v) } }
        SoundController.applyIntensity(v / 100f)
    }

    fun refreshCustomSounds(context: Context) {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                SoundController.importedFiles(context).map { it.name }
            }
            customSoundsFlow.value = files
        }
    }

    /** 导入音频：复制到 filesDir/sounds/<自定义名称>.<ext> */
    fun importSound(context: Context, uri: Uri, name: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val safeName = name.trim().ifBlank { "我的音频" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val ext = context.contentResolver.getType(uri)?.let { mime ->
                        when {
                            mime.contains("mpeg") -> "mp3"
                            mime.contains("mp4") || mime.contains("aac") -> "m4a"
                            mime.contains("ogg") -> "ogg"
                            mime.contains("wav") -> "wav"
                            else -> "mp3"
                        }
                    } ?: "mp3"
                    val file = File(SoundController.soundsDir(context), "$safeName.$ext")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { out -> input.copyTo(out) }
                    } ?: error("无法读取所选音频")
                    file.name
                }.getOrNull()
            }
            if (result != null) refreshCustomSounds(context)
            onDone(result)
        }
    }

    fun deleteSound(context: Context, fileName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { File(SoundController.soundsDir(context), fileName).delete() }
            }
            if (uiState.value.noiseKey == SoundController.keyOfFile(fileName)) {
                settingsRepo.update { it.copy(whiteNoise = null) }
            }
            refreshCustomSounds(context)
        }
    }

    fun start(context: Context) {
        val state = uiState.value
        // 记住「这个标签这次用了多久」，下次选中它直接沿用
        val tagId = state.selectedTagId
        if (state.mode == FocusMode.COUNTDOWN && tagId != null) {
            viewModelScope.launch {
                settingsRepo.update { s ->
                    s.copy(countdownMinByTag = s.countdownMinByTag + (tagId to state.countdownMin))
                }
            }
        }
        val intent = FocusService.startIntent(
            context = context,
            mode = state.mode,
            tagId = if (state.mode == FocusMode.STOPWATCH || state.mode == FocusMode.COUNTDOWN ||
                state.mode == FocusMode.POMODORO
            ) state.selectedTagId else null,
            countdownMin = state.countdownMin,
        )
        runCatching { context.startForegroundService(intent) }
            .onFailure { android.util.Log.e("FocusViewModel", "start service failed", it) }
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

    fun dismissFinished(context: Context) {
        context.startForegroundService(FocusService.actionIntent(context, FocusService.ACTION_DISMISS))
    }

    fun builtinKey(sound: WhiteNoiseSynth.Sound) = SoundController.keyOfBuiltin(sound)

    override fun onCleared() {
        SoundController.stop()
        super.onCleared()
    }
}
