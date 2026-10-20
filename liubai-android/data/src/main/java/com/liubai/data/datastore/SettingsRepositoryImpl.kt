package com.liubai.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.liubai.domain.model.AppSettings
import com.liubai.domain.model.ChallengeConfig
import com.liubai.domain.model.PomodoroConfig
import com.liubai.domain.model.ThemeMode
import com.liubai.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DAILY_GOAL = intPreferencesKey("daily_goal_min")
        val FOCUS_MIN = intPreferencesKey("pomodoro_focus_min")
        val SHORT_BREAK = intPreferencesKey("pomodoro_short_break_min")
        val LONG_BREAK = intPreferencesKey("pomodoro_long_break_min")
        val CYCLES = intPreferencesKey("pomodoro_cycles")
        val COUNTDOWN_MIN = intPreferencesKey("countdown_min")
        /** 形如 "3:25,7:45"，标签 id → 上次使用的倒计时分钟 */
        val COUNTDOWN_BY_TAG = stringPreferencesKey("countdown_min_by_tag")
        val AUTO_BREAK = booleanPreferencesKey("auto_start_break")
        val AUTO_NEXT = booleanPreferencesKey("auto_start_next_focus")
        val MONITOR = booleanPreferencesKey("monitor_enabled")
        val PRECISE = booleanPreferencesKey("precise_monitor")
        val NOISE = stringPreferencesKey("white_noise")
        val VOLUME = intPreferencesKey("white_noise_volume")
        val SPONSOR_QR = stringPreferencesKey("sponsor_qr_path")
        val CH_CONTINUOUS_ON = booleanPreferencesKey("ch_continuous_on")
        val CH_CONTINUOUS_MIN = intPreferencesKey("ch_continuous_min")
        val CH_PICKUP_ON = booleanPreferencesKey("ch_pickup_on")
        val CH_PICKUP_COUNT = intPreferencesKey("ch_pickup_count")
        val CH_BED_ON = booleanPreferencesKey("ch_bedtime_on")
        val CH_BED_HOUR = intPreferencesKey("ch_bedtime_hour")
        val CH_BED_MIN = intPreferencesKey("ch_bedtime_minute")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.theme,
            dailyGoalMin = p[Keys.DAILY_GOAL] ?: defaults.dailyGoalMin,
            pomodoro = PomodoroConfig(
                focusMin = p[Keys.FOCUS_MIN] ?: 25,
                shortBreakMin = p[Keys.SHORT_BREAK] ?: 5,
                longBreakMin = p[Keys.LONG_BREAK] ?: 15,
                cyclesBeforeLongBreak = p[Keys.CYCLES] ?: 4,
                autoStartBreak = p[Keys.AUTO_BREAK] ?: true,
                autoStartNextFocus = p[Keys.AUTO_NEXT] ?: false,
            ),
            countdownMin = p[Keys.COUNTDOWN_MIN] ?: 30,
            countdownMinByTag = decodeTagMinutes(p[Keys.COUNTDOWN_BY_TAG]),
            monitorEnabled = p[Keys.MONITOR] ?: true,
            preciseMonitor = p[Keys.PRECISE] ?: false,
            whiteNoise = p[Keys.NOISE],
            whiteNoiseVolume = p[Keys.VOLUME] ?: 60,
            sponsorQrPath = p[Keys.SPONSOR_QR],
            challenge = ChallengeConfig(
                continuousEnabled = p[Keys.CH_CONTINUOUS_ON] ?: false,
                continuousMin = p[Keys.CH_CONTINUOUS_MIN] ?: 45,
                pickupEnabled = p[Keys.CH_PICKUP_ON] ?: false,
                pickupCount = p[Keys.CH_PICKUP_COUNT] ?: 60,
                bedtimeEnabled = p[Keys.CH_BED_ON] ?: false,
                bedtimeHour = p[Keys.CH_BED_HOUR] ?: 23,
                bedtimeMinute = p[Keys.CH_BED_MIN] ?: 30,
            ),
        )
    }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(current())
        context.dataStore.edit { p ->
            p[Keys.THEME] = next.theme.name
            p[Keys.DAILY_GOAL] = next.dailyGoalMin
            p[Keys.FOCUS_MIN] = next.pomodoro.focusMin
            p[Keys.SHORT_BREAK] = next.pomodoro.shortBreakMin
            p[Keys.LONG_BREAK] = next.pomodoro.longBreakMin
            p[Keys.CYCLES] = next.pomodoro.cyclesBeforeLongBreak
            p[Keys.COUNTDOWN_MIN] = next.countdownMin
            p[Keys.COUNTDOWN_BY_TAG] = encodeTagMinutes(next.countdownMinByTag)
            p[Keys.AUTO_BREAK] = next.pomodoro.autoStartBreak
            p[Keys.AUTO_NEXT] = next.pomodoro.autoStartNextFocus
            p[Keys.MONITOR] = next.monitorEnabled
            p[Keys.PRECISE] = next.preciseMonitor
            next.whiteNoise?.let { p[Keys.NOISE] = it } ?: p.remove(Keys.NOISE)
            p[Keys.VOLUME] = next.whiteNoiseVolume
            next.sponsorQrPath?.let { p[Keys.SPONSOR_QR] = it } ?: p.remove(Keys.SPONSOR_QR)
            p[Keys.CH_CONTINUOUS_ON] = next.challenge.continuousEnabled
            p[Keys.CH_CONTINUOUS_MIN] = next.challenge.continuousMin
            p[Keys.CH_PICKUP_ON] = next.challenge.pickupEnabled
            p[Keys.CH_PICKUP_COUNT] = next.challenge.pickupCount
            p[Keys.CH_BED_ON] = next.challenge.bedtimeEnabled
            p[Keys.CH_BED_HOUR] = next.challenge.bedtimeHour
            p[Keys.CH_BED_MIN] = next.challenge.bedtimeMinute
        }
    }

    private fun encodeTagMinutes(map: Map<Long, Int>): String =
        map.entries.joinToString(",") { "${it.key}:${it.value}" }

    private fun decodeTagMinutes(raw: String?): Map<Long, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(',')
            .mapNotNull { pair ->
                val sep = pair.indexOf(':')
                if (sep <= 0) return@mapNotNull null
                val id = pair.substring(0, sep).toLongOrNull() ?: return@mapNotNull null
                val min = pair.substring(sep + 1).toIntOrNull() ?: return@mapNotNull null
                id to min
            }
            .toMap()
    }
}
