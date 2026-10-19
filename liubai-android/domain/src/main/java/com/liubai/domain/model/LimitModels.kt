package com.liubai.domain.model

/** 单应用每日限额（零点重置） */
data class AppLimit(
    val packageName: String,
    val limitMs: Long,
    val enabled: Boolean = true,
)

/** 干预挑战配置：全部默认关闭，由用户决定何时需要 */
data class ChallengeConfig(
    val continuousEnabled: Boolean = false,
    val continuousMin: Int = 45,          // 连续使用超过 N 分钟提醒
    val pickupEnabled: Boolean = false,
    val pickupCount: Int = 60,            // 每日拿起次数上限
    val bedtimeEnabled: Boolean = false,
    val bedtimeHour: Int = 23,
    val bedtimeMinute: Int = 30,          // 就寝时间
)

/** 挑战达成结果（用于通知文案） */
data class ChallengeHit(
    val type: ChallengeType,
    val title: String,
    val body: String,
)

enum class ChallengeType { CONTINUOUS, PICKUP, BEDTIME }
