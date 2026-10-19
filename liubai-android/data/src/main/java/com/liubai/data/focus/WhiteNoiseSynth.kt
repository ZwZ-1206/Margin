package com.liubai.data.focus

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 程序化白噪音合成器——零音频资产。
 * 在独立线程实时生成 16-bit PCM，经 AudioTrack 流式播放。
 *
 * 音色：
 *  - rain   雨声：棕噪声 + 随机高频水滴
 *  - stream 溪流：带通噪声（双极点滤波器近似）
 *  - forest 森林：棕噪声底 + 随机鸟鸣（FM 正弦猝发）
 *  - ocean  海浪：棕噪声 + 慢速振幅 LFO
 *  - cafe   咖啡馆：棕噪声底 + 随机杯碟轻响
 *  - brown  纯棕噪声
 */
class WhiteNoiseSynth(private val sampleRate: Int = 44_100) {

    enum class Sound { RAIN, STREAM, FOREST, OCEAN, CAFE, BROWN }

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    @Volatile
    var volume: Float = 0.6f // 0..1

    fun start(sound: Sound) {
        stop()
        running = true
        thread = Thread({
            genLoop(sound)
        }, "liubai-noise").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        track?.runCatching {
            pause()
            flush()
            release()
        }
        track = null
    }

    private fun genLoop(sound: Sound) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufSamples = (minBuf * 2).coerceAtLeast(8192)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSamples * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()

        val rnd = Random(System.nanoTime())
        val chunk = ShortArray(bufSamples)
        var brown = 0.0
        var lfoPhase = 0.0
        var birdCooldown = 0
        var birdPhase = 0.0
        var birdFreq = 0.0
        var clinkCooldown = 0
        var clinkPhase = 0.0

        while (running && !Thread.currentThread().isInterrupted) {
            for (i in chunk.indices) {
                val white = rnd.nextDouble() * 2 - 1
                brown = (brown + 0.02 * white) / 1.02
                brown *= 3.5
                var sample: Double

                when (sound) {
                    Sound.BROWN -> sample = brown
                    Sound.RAIN -> {
                        // 棕噪底 + 稀疏水滴（高频正弦猝发）
                        brown += (rnd.nextDouble() - 0.5) * 0.02
                        var drop = 0.0
                        if (rnd.nextDouble() < 0.0008) {
                            drop = sin(2 * PI * (3000 + rnd.nextDouble() * 3000) * i / sampleRate) * 0.3
                        }
                        sample = brown + drop
                    }
                    Sound.STREAM -> {
                        // 双极点多级近似带通：白噪过两级差分
                        val bp = white - brown * 0.6
                        sample = bp * 0.5 + brown * 0.3
                    }
                    Sound.OCEAN -> {
                        lfoPhase += 2 * PI * 0.07 / sampleRate
                        if (lfoPhase > 2 * PI) lfoPhase -= 2 * PI
                        val lfo = (sin(lfoPhase) * 0.5 + 0.5).pow(1.5)
                        sample = brown * (0.35 + 0.65 * lfo)
                    }
                    Sound.FOREST -> {
                        if (birdCooldown-- <= 0 && rnd.nextDouble() < 0.0004) {
                            birdCooldown = (sampleRate * 0.4).toInt()
                            birdPhase = 0.0
                            birdFreq = 1800 + rnd.nextDouble() * 2200
                        }
                        var bird = 0.0
                        if (birdCooldown > 0) {
                            birdPhase += 2 * PI * birdFreq / sampleRate
                            val env = sin(PI * (1 - birdCooldown / (sampleRate * 0.4)))
                            bird = sin(birdPhase) * env * 0.18
                        }
                        sample = brown * 0.6 + bird
                    }
                    Sound.CAFE -> {
                        if (clinkCooldown-- <= 0 && rnd.nextDouble() < 0.00025) {
                            clinkCooldown = (sampleRate * 0.9).toInt()
                            clinkPhase = 0.0
                        }
                        var clink = 0.0
                        if (clinkCooldown > 0) {
                            clinkPhase += 2 * PI * 2600 / sampleRate
                            val env = sin(PI * (1 - clinkCooldown / (sampleRate * 0.9)))
                            clink = sin(clinkPhase) * env * 0.12
                        }
                        sample = brown * 0.55 + clink
                    }
                }

                val v = (sample * volume).coerceIn(-1.0, 1.0)
                chunk[i] = (v * Short.MAX_VALUE).toInt().toShort()
            }
            if (!running) break
            val written = t.write(chunk, 0, chunk.size)
            if (written < 0) break
        }
        t.runCatching {
            pause()
            flush()
            release()
        }
    }

    private fun Double.pow(p: Double): Double = Math.pow(this, p)
}
