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

    /** 音色标识：BUILTIN:RAIN 或 FILE:rain.mp3 */
    fun keyOfBuiltin(sound: WhiteNoiseSynth.Sound) = "BUILTIN:${sound.name}"
    fun keyOfFile(fileName: String) = "FILE:$fileName"

    val isPlaying: Boolean get() = currentKey != null

    /** 播放指定音色；[volume] 0f..1f */
    fun play(context: Context, key: String?, volume: Float) {
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
