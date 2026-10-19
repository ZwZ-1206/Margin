package com.liubai.domain.stats

import com.liubai.domain.model.FocusSessionRecord
import com.liubai.domain.model.SessionStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object FocusStats {
    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun dateKey(date: LocalDate): String = date.format(DATE_FMT)

    /** 连续专注天数：从今天往回数，今天没有则从昨天开始（不断档） */
    fun streakDays(sessions: List<FocusSessionRecord>, today: LocalDate): Int {
        if (sessions.isEmpty()) return 0
        val days = sessions.filter { it.durationMs > 0 }
            .map { LocalDate.parse(dateKeyOf(it.startedAt), DATE_FMT) }
            .toSet()
        var streak = 0
        var cursor = today
        if (!days.contains(cursor)) cursor = cursor.minusDays(1)
        while (days.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun dateKeyOf(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .format(DATE_FMT)

    /** 按日期聚合总专注毫秒 */
    fun dailyTotals(sessions: List<FocusSessionRecord>): Map<String, Long> =
        sessions.groupBy { dateKeyOf(it.startedAt) }
            .mapValues { (_, list) -> list.sumOf { it.durationMs } }

    /** 某日 96 个 15 分钟桶的专注毫秒分布（00:00 起） */
    fun hourHistogram(sessions: List<FocusSessionRecord>, dateKey: String): LongArray {
        val buckets = LongArray(96)
        val zone = java.time.ZoneId.systemDefault()
        sessions.filter { dateKeyOf(it.startedAt) == dateKey }.forEach { s ->
            var cursor = s.startedAt
            val end = s.startedAt + s.durationMs
            while (cursor < end) {
                val dt = java.time.Instant.ofEpochMilli(cursor).atZone(zone)
                val bucket = dt.hour * 4 + dt.minute / 15
                val bucketEnd = dt.toLocalDate().atTime(dt.hour, dt.minute)
                    .plusMinutes((15 - dt.minute % 15).toLong())
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                val slice = minOf(end, bucketEnd) - cursor
                if (bucket in 0..95 && slice > 0) buckets[bucket] += slice
                cursor = bucketEnd
            }
        }
        return buckets
    }

    fun completedCount(sessions: List<FocusSessionRecord>): Int =
        sessions.count { it.status == SessionStatus.COMPLETED }

    fun interruptedCount(sessions: List<FocusSessionRecord>): Int =
        sessions.count { it.status == SessionStatus.INTERRUPTED }
}
