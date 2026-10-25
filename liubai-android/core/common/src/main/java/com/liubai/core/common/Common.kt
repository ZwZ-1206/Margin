package com.liubai.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** 调度器抽象（便于测试替换） */
data class AppDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
)

object DateKeys {
    private val FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    fun today(zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDate.now(zone).format(FMT)

    fun of(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().format(FMT)

    fun localDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun startOfDay(date: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.parse(date, FMT).atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(date: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.parse(date, FMT).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
}

object DurationFormat {
    private val LOCALE = Locale.CHINA

    /** 紧凑格式：1h52m / 25m / 45s */
    fun short(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> String.format(LOCALE, "%dh%02dm", h, m)
            m > 0 -> String.format(LOCALE, "%dm", m)
            else -> String.format(LOCALE, "%ds", s)
        }
    }

    /** 计时器格式：25:00 / 1:02:03 */
    fun timer(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(LOCALE, "%d:%02d:%02d", h, m, s)
        else String.format(LOCALE, "%02d:%02d", m, s)
    }

    /** 中文描述：1 小时 52 分钟 */
    fun chinese(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h} 小时 ${m} 分钟"
            h > 0 -> "${h} 小时"
            else -> "${m} 分钟"
        }
    }

    /** 紧凑中文（无空格，小部件窄列防换行）：6小时29分 / 6小时 / 45分 */
    fun compact(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h}小时${m}分"
            h > 0 -> "${h}小时"
            else -> "${m}分"
        }
    }
}

/** 统一错误类型（data 层归一，UI 层映射文案） */
sealed class AppError(open val cause: Throwable? = null) {
    data class Network(override val cause: Throwable?) : AppError(cause)
    data class Permission(val what: String) : AppError()
    data class NoData(override val cause: Throwable? = null) : AppError(cause)
    data class Storage(override val cause: Throwable?) : AppError(cause)
    data class Unknown(override val cause: Throwable?) : AppError(cause)
}
