package com.liubai.data.focus

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
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
 *
 * 雨声的「强度」(intensity, 0f..1f) 同时改变五层：嘶声增益、低频底噪、
 * 水滴溅落密度/幅度、微溅纹理密度/幅度、大水滴余振密度。由写入线程按
 * ~90ms 时间常数平滑后传入 Voice，拖滑块听不到台阶。其他音色忽略该参数。
 *
 * 标定方式：tools/ 下的纯 Python 仿真脚本（dsp.py 提供 FFT 频段测量），
 * 目标是各音色 RMS 落在 0.14–0.20、峰值不削顶、高频不过量。
 * 注意：相减做带通时，上下两个滤波器的滚降速率必须匹配（两级减两级），
 * 否则低频那级的高频尾巴会漏出来——咖啡馆初版就这样漏出 19% 的 8kHz 以上能量。
 */
class WhiteNoiseSynth(private val sampleRate: Int = 44_100) {

    enum class Sound { RAIN, STREAM, FOREST, OCEAN, CAFE, BROWN }

    @Volatile
    private var running = false

    /** 0f..1f，写入线程每帧做平滑，拖滑块不会有阶梯噪声 */
    @Volatile
    var volume: Float = 0.6f

    /** 雨声强度 0f..1f，仅 Sound.RAIN 生效；同样在写入线程里平滑 */
    @Volatile
    var intensity: Float = 0.5f

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
        // 强度平滑比音量更慢（~90ms）：雨势变化像天气转变，不该有瞬跳
        var smoothIntensity = intensity.coerceIn(0f, 1f)

        while (running && !Thread.currentThread().isInterrupted) {
            val targetVol = volume.coerceIn(0f, 1f)
            val targetIntensity = intensity.coerceIn(0f, 1f)
            for (i in chunk.indices) {
                if (fade < 1f) fade = min(1f, fade + fadeStep)
                // 每帧向目标音量靠近，时间常数约 20ms，听感上无台阶
                smoothVol += (targetVol - smoothVol) * 0.0011f
                smoothIntensity += (targetIntensity - smoothIntensity) * 0.00025f
                val s = voice.next(smoothIntensity)
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

        // ---- 雨声 v3（离线仿真标定，tools/rain_v3_final.py）----
        /** 水滴溅落：噪声猝发幅度 */
        const val SPLASH_GAIN = 1.8f
        /** 密集微溅：噪声猝发幅度 */
        const val PATTER_GAIN = 0.8f
        /** 大水滴余振：正弦幅度 */
        const val PLINK_GAIN = 0.117f

        // ---- 咖啡馆（离线仿真标定，tools/cafe_sim.py）----
        /** 人声三个子带增益：220–900 / 900–3400 / 3400–5000Hz */
        const val CAFE_G1 = 0.85f
        const val CAFE_G2 = 1.25f
        const val CAFE_G3 = 0.45f
        /** 房间底噪增益 */
        const val CAFE_ROOM = 0.34f
        /** 咖啡机蒸汽幅度 */
        const val STEAM_GAIN = 0.16f
        /** 杯碟正弦幅度 */
        const val CLINK_GAIN = 0.075f
        /** 勺子搅拌噪流幅度 */
        const val SPOON_GAIN = 0.05f
        /** 桌椅低闷声幅度 */
        const val CHAIR_GAIN = 0.30f
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
        private var lpSizzle = 0.0
        private var lpSizzle2 = 0.0
        /** 咖啡馆人声子带用：两级 900Hz / 两级 3400Hz（与 lpSizzle 的两级 5kHz 滚降匹配） */
        private var lp900x2 = 0.0
        private var lp3400 = 0.0
        private var lp3400x2 = 0.0
        private var brown = 0.0

        // LFO
        private var lfoPhase = 0.0
        private var lfoPhase2 = 0.0
        /** 咖啡馆专用：三个子带的音节率相位 + 短语潮汐 + 房间呼吸 */
        private var amP1 = 0.0
        private var amP2 = 0.0
        private var amP3 = 0.0
        private var tideP = 0.0
        private var roomP = 0.0

        // 事件层共享状态（同一时刻只有一个音色在播，互不冲突）
        private var eventEnv = 0.0
        private var eventPhase = 0.0
        private var eventFreq = 0.0
        private var eventDecay = 0.0
        private var eventFmDepth = 0.0
        private var eventCooldown = 0

        // 雨声 v3：水滴溅落 / 密集微溅（各自独立的噪声猝发包络）
        private var splashEnv = 0.0
        private var splashDecay = 0.0
        private var patterEnv = 0.0
        private var patterDecay = 0.0

        // 咖啡馆：蒸汽 / 搅拌 / 桌椅 / 成对杯碟
        private var steamEnv = 0.0
        private var steamLeft = 0
        private var steamGap = 0
        private var spoonEnv = 0.0
        private var chairEnv = 0.0
        private var clinkPending = 0

        private val aLow = coef(220.0)
        private val aMid = coef(900.0)
        private val aHigh = coef(4200.0)
        private val a3400 = coef(3400.0)
        /**
         * 雨声嘶声的上沿：两级串联的 5kHz 一阶低通（-12dB/oct）。
         * 单级只到 -6dB/oct，5kHz 以上仍剩大量白噪残差，那是旧版
         * 「嘶——」的金属刺耳感的来源；两级才把 8kHz 以上压到可听水平以下。
         */
        private val aSizzle = coef(5000.0)

        private fun coef(fc: Double): Double =
            1.0 - exp(-2.0 * PI * fc / sr.toDouble())

        fun next(intensity: Float): Float {
            val w = rnd.nextDouble() * 2.0 - 1.0

            // leaky integrator：1/1.02 < 1，所以状态永不发散。
            // 3.5 只作用于输出（brownN），绝不写回 brown——写回就是正反馈爆音。
            brown = (brown + 0.02 * w) / 1.02
            val brownN = brown * 3.5

            lpLow += aLow * (w - lpLow)
            lpMid += aMid * (w - lpMid)
            lpHigh += aHigh * (w - lpHigh)
            lpSizzle += aSizzle * (w - lpSizzle)
            lpSizzle2 += aSizzle * (lpSizzle - lpSizzle2)
            lp3400 += a3400 * (w - lp3400)
            lp3400x2 += a3400 * (lp3400 - lp3400x2)
            lp900x2 += aMid * (lpMid - lp900x2)

            // 下面的系数都按 8 秒离线仿真实测过：各音色 RMS 落在 0.18–0.22，
            // 峰值基本不超过 1.0，软限幅只在极少数事件叠加时介入。
            val out = when (sound) {
                Sound.BROWN -> brownN * 0.90

                Sound.RAIN -> {
                    // 雨声频谱：连续嘶声床 900Hz–5kHz（两级 5kHz 上沿，-12dB/oct），
                    // 能量集中 1k–4kHz；8kHz 以上只剩瞬态的尾巴（旧版 66% → 现约 11%）。
                    lfoPhase += 2 * PI * 0.07 / sr
                    if (lfoPhase > 2 * PI) lfoPhase -= 2 * PI
                    // 雨势本身在缓慢呼吸，±12%
                    val gust = 0.88 + 0.12 * sin(lfoPhase)
                    val i = intensity.toDouble()
                    val hiss = (lpSizzle2 - lpMid) * (0.80 + 0.65 * i) * gust
                    // 低频底噪（远处落地与地面的轰声），大雨略厚
                    val body = brownN * (0.14 + 0.06 * i)
                    body + hiss + splash(i) + patter(i) + plink(i)
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
                    // 人声嘈杂床：三个子带（220–900 / 900–3400 / 3400–5000Hz）各自按
                    // 4–6Hz 音节率起伏。单频段 + 慢调制听起来像风；多带错相调制才是
                    // "一桌在说、一桌在听"的人群感。子带上下沿都用两级低通，
                    // 滚降速率必须匹配，否则低频那级的高频尾巴会漏出来（实测漏出 19%）。
                    amP1 += 2 * PI * 4.3 / sr
                    if (amP1 > 2 * PI) amP1 -= 2 * PI
                    amP2 += 2 * PI * 5.1 / sr
                    if (amP2 > 2 * PI) amP2 -= 2 * PI
                    amP3 += 2 * PI * 6.4 / sr
                    if (amP3 > 2 * PI) amP3 -= 2 * PI
                    // 短语级人群潮汐 + 房间呼吸（各自的慢周期）
                    tideP += 2 * PI * 0.15 / sr
                    if (tideP > 2 * PI) tideP -= 2 * PI
                    roomP += 2 * PI * 0.037 / sr
                    if (roomP > 2 * PI) roomP -= 2 * PI
                    val tide = 0.75 + 0.25 * sin(tideP)
                    val b1 = lp900x2 - lpLow
                    val b2 = lp3400x2 - lp900x2
                    val b3 = lpSizzle2 - lp3400x2
                    val babble =
                        b1 * (0.45 + 0.55 * (0.5 + 0.5 * sin(amP1))) * tide * CAFE_G1 +
                            b2 * (0.45 + 0.55 * (0.5 + 0.5 * sin(amP2))) * tide * CAFE_G2 +
                            b3 * (0.45 + 0.55 * (0.5 + 0.5 * sin(amP3))) * tide * CAFE_G3
                    // 房间底噪：褐噪 + 慢呼吸，托住声景不飘
                    val room = brownN * CAFE_ROOM * (0.85 + 0.15 * sin(roomP))
                    // 事件层：咖啡机蒸汽是最强的识别点，杯碟常成对，偶尔搅拌与桌椅
                    babble + room +
                        steam(lpSizzle2 - lpMid) +
                        clink() +
                        spoon(lpHigh - lpMid) +
                        chair(b1)
                }
            }
            return out.toFloat()
        }

        /**
         * 水滴溅落：10–25ms 的噪声猝发（900–4200Hz）。
         * v3 用它替代旧版的正弦水滴——正弦余振是"滴滴"的电子声，
         * 噪声猝发才是水砸在硬表面上的听感。强度同时控制密度与幅度。
         */
        private fun splash(i: Double): Double {
            if (splashEnv <= 0.001) {
                if (rnd.nextDouble() < 0.00002 + 0.00013 * i) {
                    splashEnv = 0.7 + 0.3 * rnd.nextDouble()
                    // 衰减到 0.001 用时 10–25ms → dec = 0.001^(1/(T·sr))
                    val tSec = 0.010 + 0.015 * rnd.nextDouble()
                    splashDecay = 0.001.pow(1.0 / (tSec * sr))
                } else {
                    return 0.0
                }
            }
            val s = (lpHigh - lpMid) * splashEnv * SPLASH_GAIN * (0.5 + 0.5 * i)
            splashEnv *= splashDecay
            return s
        }

        /**
         * 密集微溅：7–23ms 的噪声猝发，雨的"纹理"层。
         * 小雨时是隐约的沙沙声，大雨时互相叠成连续的雨幕（每秒最多约 90 次）。
         */
        private fun patter(i: Double): Double {
            if (patterEnv <= 0.001) {
                if (rnd.nextDouble() < 0.0002 + 0.0018 * i) {
                    patterEnv = 0.7 + 0.3 * rnd.nextDouble()
                    val tSec = 0.007 + 0.016 * rnd.nextDouble()
                    patterDecay = 0.001.pow(1.0 / (tSec * sr))
                } else {
                    return 0.0
                }
            }
            val s = (lpSizzle2 - lpMid) * patterEnv * PATTER_GAIN * (0.25 + 0.75 * i)
            patterEnv *= patterDecay
            return s
        }

        /**
         * 大水滴余振：正弦 + 频率下滑，约 250ms 衰完。
         * 稀疏（每秒不到两次），给雨声一点"点状"细节，避免全是糊在一起的噪声。
         */
        private fun plink(i: Double): Double {
            if (eventCooldown > 0) {
                eventCooldown--
            } else if (rnd.nextDouble() < 0.000006 + 0.00003 * i) {
                eventEnv = 1.0
                // 小雨低沉（700–1700Hz），大雨稍亮（700–3100Hz）
                eventFreq = 700.0 + rnd.nextDouble() * (1000.0 + 1400.0 * i)
                eventPhase = 0.0
                eventDecay = 0.9993
                eventCooldown = (sr * 0.03).toInt()
            }
            if (eventEnv <= 0.0005) return 0.0
            // 每样本轻微下滑，水滴余振往下掉
            eventFreq *= 0.99996
            eventPhase += 2 * PI * eventFreq / sr
            if (eventPhase > 2 * PI) eventPhase -= 2 * PI
            val s = sin(eventPhase) * eventEnv * PLINK_GAIN
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

        /**
         * 杯碟轻响：短促高频正弦，比水滴更干。
         * 真实场景里杯子放下常伴第二下（杯沿碰碟边），30% 概率 120ms 后补一下，
         * 一下一下等概率的"叮"反而不像咖啡馆。
         */
        private fun clink(): Double {
            if (eventCooldown > 0) {
                eventCooldown--
            } else if (eventEnv <= 0.0005) {
                if (clinkPending > 0) {
                    clinkPending--
                    if (clinkPending == 0) {
                        triggerClink(0.75)
                        eventCooldown = (sr * 0.05).toInt()
                    }
                } else if (rnd.nextDouble() < 0.000034) {
                    triggerClink(1.0)
                    if (rnd.nextDouble() < 0.30) clinkPending = (sr * 0.12).toInt()
                    eventCooldown = (sr * 0.05).toInt()
                }
            }
            if (eventEnv <= 0.0005) return 0.0
            eventPhase += 2 * PI * eventFreq / sr
            if (eventPhase > 2 * PI) eventPhase -= 2 * PI
            val s = sin(eventPhase) * eventEnv * CLINK_GAIN
            eventEnv *= eventDecay
            return s
        }

        private fun triggerClink(scale: Double) {
            eventEnv = scale
            eventFreq = 2600.0 + rnd.nextDouble() * 1400.0
            eventPhase = 0.0
            eventDecay = 0.99935
        }

        /**
         * 咖啡机蒸汽：意式机打奶泡的"嗤——"，咖啡馆最强的非人声识别点。
         * 一阵 1.5–3s，攻击约 11ms、释放 0.5s，间隔 25–55s。
         * [src] 传 900Hz–5kHz 的带限嘶声。
         */
        private fun steam(src: Double): Double {
            if (steamLeft > 0) {
                steamLeft--
                // 前段保持，最后 0.5s 线性收尾
                val target = if (steamLeft > sr * 0.5) 1.0 else steamLeft / (sr * 0.5)
                steamEnv += (target - steamEnv) * 0.002
            } else {
                steamEnv *= 0.9995
                if (steamGap > 0) {
                    steamGap--
                } else if (rnd.nextDouble() < 0.0000006) {
                    steamLeft = (sr * (1.5 + rnd.nextDouble() * 1.5)).toInt()
                    steamGap = (sr * (25 + rnd.nextDouble() * 30)).toInt()
                }
            }
            return src * steamEnv * STEAM_GAIN
        }

        /** 勺子搅杯：0.35s 的 900–4200Hz 噪流，约每 28s 一次 */
        private fun spoon(src: Double): Double {
            if (spoonEnv > 0.001) {
                spoonEnv *= 0.99987
                return src * spoonEnv * SPOON_GAIN
            }
            if (rnd.nextDouble() < 0.0000008) spoonEnv = 1.0
            return 0.0
        }

        /** 桌椅挪动：60ms 的低闷声（220–900Hz），约每 30s 一次 */
        private fun chair(src: Double): Double {
            if (chairEnv > 0.001) {
                chairEnv *= 0.9990
                return src * chairEnv * CHAIR_GAIN
            }
            if (rnd.nextDouble() < 0.0000008) chairEnv = 1.0
            return 0.0
        }
    }
}
