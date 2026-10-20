package com.liubai.data.focus

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * 程序化白噪音合成器——零音频资产。
 *
 * 生成链路：白噪源 → 分音色滤波/事件层 → 每音色固定增益 → 软限幅(tanh)
 * → 350ms 淡入 → 音量平滑 → 16-bit PCM 写入 AudioTrack。
 *
 * 三条铁律（上一版翻车的地方）：
 *  1. 滤波器状态变量只在积分里推进，增益必须在输出侧乘。把增益乘回状态会形成
 *     正反馈，几十毫秒内发散到满量程并持续削顶，听起来就是"炸麦"。
 *  2. 任何音色最终都要过软限幅，单个水滴/杯碟事件叠加也不该产生硬切方波。
 *  3. 起播必须淡入、停止必须先静音再释放，否则切音色有咔哒声。
 */
class WhiteNoiseSynth(private val sampleRate: Int = 44_100) {

    enum class Sound { RAIN, STREAM, FOREST, OCEAN, CAFE, BROWN }

    @Volatile
    private var running = false

    /** 0f..1f，写入线程每帧做平滑，拖滑块不会有阶梯噪声 */
    @Volatile
    var volume: Float = 0.6f

    private var thread: Thread? = null
    private var track: AudioTrack? = null

    fun start(sound: Sound) {
        stop()
        running = true
        thread = Thread({ runLoop(sound) }, "liubai-noise").apply {
            // 不要 MAX_PRIORITY：write 本身阻塞，优先级过高会和 UI 抢 CPU
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    fun stop() {
        running = false
        val t = track
        track = null
        thread?.interrupt()
        thread = null
        t?.runCatching {
            // 先静音再释放，避免最后一段波形被硬切
            setVolume(0f)
            pause()
            flush()
            release()
        }
    }

    private fun runLoop(sound: Sound) {
        val frames = 1024
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return

        val t = runCatching {
            AudioTrack.Builder()
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
                // 留 2 个最小缓冲，避免下溢造成的周期性咔哒
                .setBufferSizeInBytes(maxOf(minBuf * 2, frames * 2 * 3))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return

        if (t.state != AudioTrack.STATE_INITIALIZED) {
            t.release()
            return
        }

        track = t
        t.play()

        val voice = Voice(sound, sampleRate)
        val chunk = ShortArray(frames)
        val fadeStep = 1f / (sampleRate * 0.35f)
        var fade = 0f
        var smoothVol = volume.coerceIn(0f, 1f)

        while (running && !Thread.currentThread().isInterrupted) {
            val targetVol = volume.coerceIn(0f, 1f)
            for (i in chunk.indices) {
                if (fade < 1f) fade = min(1f, fade + fadeStep)
                // 每帧向目标音量靠近，时间常数约 20ms，听感上无台阶
                smoothVol += (targetVol - smoothVol) * 0.0011f
                val s = voice.next()
                val shaped = tanh(s * 1.2f) * fade * smoothVol * HEADROOM
                chunk[i] = (shaped.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            if (!running) break
            val written = t.write(chunk, 0, frames)
            if (written < 0) break
            if (written == 0) runCatching { Thread.sleep(1) }
        }

        t.runCatching {
            setVolume(0f)
            pause()
            flush()
            release()
        }
    }

    private companion object {
        const val HEADROOM = 0.85f
    }

    /**
     * 单个音色的信号发生器。所有滤波器状态都是"输入驱动态"，增益只作用在输出。
     */
    private class Voice(private val sound: Sound, private val sr: Int) {

        private val rnd = Random(System.nanoTime())

        // 一阶低通的积分状态
        private var lpLow = 0.0
        private var lpMid = 0.0
        private var lpHigh = 0.0
        private var brown = 0.0

        // LFO
        private var lfoPhase = 0.0
        private var lfoPhase2 = 0.0

        // 事件层：水滴 / 鸟鸣 / 杯碟
        private var eventEnv = 0.0
        private var eventPhase = 0.0
        private var eventFreq = 0.0
        private var eventDecay = 0.0
        private var eventFmDepth = 0.0
        private var eventCooldown = 0

        private val aLow = coef(220.0)
        private val aMid = coef(900.0)
        private val aHigh = coef(4200.0)

        private fun coef(fc: Double): Double =
            1.0 - exp(-2.0 * PI * fc / sr.toDouble())

        fun next(): Float {
            val w = rnd.nextDouble() * 2.0 - 1.0

            // leaky integrator：1/1.02 < 1，所以状态永不发散。
            // 3.5 只作用于输出（brownN），绝不写回 brown——写回就是正反馈爆音。
            brown = (brown + 0.02 * w) / 1.02
            val brownN = brown * 3.5

            lpLow += aLow * (w - lpLow)
            lpMid += aMid * (w - lpMid)
            lpHigh += aHigh * (w - lpHigh)

            // 下面的系数都按 8 秒离线仿真实测过：各音色 RMS 落在 0.18–0.22，
            // 峰值基本不超过 1.0，软限幅只在极少数事件叠加时介入。
            val out = when (sound) {
                Sound.BROWN -> brownN * 0.90

                Sound.RAIN -> {
                    // 雨声主体：900Hz 以上的白噪残差，再混一点低频底噪
                    val hiss = (w - lpMid) * 0.36
                    val body = brownN * 0.12
                    body + hiss + drop()
                }

                Sound.STREAM -> {
                    // 700Hz–5kHz 带通 + 缓慢起伏，接近流水
                    val band = (lpHigh - lpLow) * 0.75
                    lfoPhase += 2 * PI * 0.23 / sr
                    if (lfoPhase > 2 * PI) lfoPhase -= 2 * PI
                    val breathe = 0.85 + 0.15 * sin(lfoPhase)
                    band * breathe + brownN * 0.10
                }

                Sound.FOREST -> {
                    lfoPhase += 2 * PI * 0.05 / sr
                    if (lfoPhase > 2 * PI) lfoPhase -= 2 * PI
                    val wind = brownN * (0.66 + 0.18 * (sin(lfoPhase) * 0.5 + 0.5))
                    val leaves = (lpHigh - lpMid) * 0.55
                    wind + leaves + bird()
                }

                Sound.OCEAN -> {
                    lfoPhase += 2 * PI * 0.065 / sr
                    if (lfoPhase > 2 * PI) lfoPhase -= 2 * PI
                    // 慢速潮汐：褐噪振幅随正弦起伏，波峰带一点浪花
                    val tide = (sin(lfoPhase) * 0.5 + 0.5)
                    val swell = 0.25 + 0.75 * tide * tide
                    val foam = (lpHigh - lpLow) * 0.22 * tide * tide
                    brownN * 1.10 * swell + foam
                }

                Sound.CAFE -> {
                    // 人声频段模糊成团 + 稀疏杯碟；不做任何可辨识语音
                    lfoPhase += 2 * PI * 0.11 / sr
                    if (lfoPhase > 2 * PI) lfoPhase -= 2 * PI
                    lfoPhase2 += 2 * PI * 0.037 / sr
                    if (lfoPhase2 > 2 * PI) lfoPhase2 -= 2 * PI
                    val murmur = (lpMid - lpLow) * (1.05 + 0.40 * sin(lfoPhase))
                    val room = brownN * (0.62 + 0.12 * sin(lfoPhase2))
                    val clink = clink()
                    room + murmur + clink * 0.55
                }
            }
            return out.toFloat()
        }

        /** 稀疏水滴：指数衰减的正弦猝发 */
        private fun drop(): Double {
            if (eventCooldown > 0) {
                eventCooldown--
            } else if (rnd.nextDouble() < 0.00016) {
                eventEnv = 1.0
                eventFreq = 1600.0 + rnd.nextDouble() * 2800.0
                eventPhase = 0.0
                eventDecay = 0.99965
                eventCooldown = (sr * 0.05).toInt()
            }
            if (eventEnv <= 0.0005) return 0.0
            eventPhase += 2 * PI * eventFreq / sr
            if (eventPhase > 2 * PI) eventPhase -= 2 * PI
            val s = sin(eventPhase) * eventEnv * 0.10
            eventEnv *= eventDecay
            return s
        }

        /** 鸟鸣：带频率下滑的正弦，起落各有一小段包络 */
        private fun bird(): Double {
            if (eventCooldown > 0) {
                eventCooldown--
                return 0.0
            }
            if (eventEnv <= 0.0005) {
                if (rnd.nextDouble() < 0.000012) {
                    eventEnv = 1.0
                    eventFreq = 1900.0 + rnd.nextDouble() * 1600.0
                    eventPhase = 0.0
                    eventDecay = 0.99978
                    eventFmDepth = 0.35 + rnd.nextDouble() * 0.4
                    eventCooldown = (sr * 1.6).toInt()
                    return 0.0
                }
                return 0.0
            }
            eventFreq -= eventFreq * 0.000035 * eventFmDepth
            eventPhase += 2 * PI * eventFreq / sr
            if (eventPhase > 2 * PI) eventPhase -= 2 * PI
            val s = sin(eventPhase) * eventEnv * 0.13
            eventEnv *= eventDecay
            return s
        }

        /** 杯碟轻响：短促高频，比水滴更干 */
        private fun clink(): Double {
            if (eventCooldown > 0) {
                eventCooldown--
                return 0.0
            }
            if (eventEnv <= 0.0005) {
                if (rnd.nextDouble() < 0.00005) {
                    eventEnv = 1.0
                    eventFreq = 2600.0 + rnd.nextDouble() * 1400.0
                    eventPhase = 0.0
                    eventDecay = 0.99935
                    eventCooldown = (sr * 0.6).toInt()
                    return 0.0
                }
                return 0.0
            }
            eventPhase += 2 * PI * eventFreq / sr
            if (eventPhase > 2 * PI) eventPhase -= 2 * PI
            val s = sin(eventPhase) * eventEnv * 0.09
            eventEnv *= eventDecay
            return s
        }
    }
}
