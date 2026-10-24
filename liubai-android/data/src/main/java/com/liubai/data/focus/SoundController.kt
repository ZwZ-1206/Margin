package com.liubai.data.focus

import android.content.Context
import android.media.MediaPlayer
import java.io.File

/**
 * 声音控制器：统一管理「内置程序化音色」与「用户导入音频」的播放。
 *
 * - 内置音色用 WhiteNoiseSynth 实时合成，零音频资产
 * - 导入音频用 MediaPlayer 循环播放，文件存在 filesDir/sounds/
 *
 * 单例：同一时刻只播一路声音，切换音色时先停旧的。
 */
object SoundController {

    private val synth = WhiteNoiseSynth()
    private var player: MediaPlayer? = null
    private var currentKey: String? = null

    /**
     * 雨声强度 0f..1f。只对内置雨声生效，其他音色忽略；
     * 切音色时随 play() 一起下发，进程内保持上次的值。
     */
    @Volatile
    private var intensity = 0.5f

    /** 音色标识：BUILTIN:RAIN 或 FILE:rain.mp3 */
    fun keyOfBuiltin(sound: WhiteNoiseSynth.Sound) = "BUILTIN:${sound.name}"
    fun keyOfFile(fileName: String) = "FILE:$fileName"

    val isPlaying: Boolean get() = currentKey != null

    /** 播放指定音色；[volume] 0f..1f，[intensity] 0f..1f（仅内置雨声） */
    fun play(context: Context, key: String?, volume: Float, intensity: Float = this.intensity) {
        this.intensity = intensity.coerceIn(0f, 1f)
        if (key == null) {
            stop()
            return
        }
        // 同一音色只调音量，不重开
        if (key == currentKey) {
            applyVolume(volume)
            return
        }
        stop()
        currentKey = key
        if (key.startsWith("BUILTIN:")) {
            val name = key.removePrefix("BUILTIN:")
            val sound = runCatching { WhiteNoiseSynth.Sound.valueOf(name) }.getOrNull() ?: return
            synth.volume = volume.coerceIn(0f, 1f)
            synth.intensity = this.intensity
            synth.start(sound)
        } else {
            val fileName = key.removePrefix("FILE:")
            val file = File(soundsDir(context), fileName)
            if (!file.exists()) {
                currentKey = null
                return
            }
            runCatching {
                player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    isLooping = true
                    setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
                    prepare()
                    start()
                }
            }.onFailure { currentKey = null }
        }
    }

    fun applyVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        synth.volume = v
        runCatching { player?.setVolume(v, v) }
    }

    /** 实时调节雨声强度，合成线程内平滑，无台阶 */
    fun applyIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
        synth.intensity = intensity
    }

    fun stop() {
        synth.stop()
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        currentKey = null
    }

    fun soundsDir(context: Context): File =
        File(context.filesDir, "sounds").apply { if (!exists()) mkdirs() }

    fun importedFiles(context: Context): List<File> =
        soundsDir(context).listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
}
