package com.liubai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val limitMs: Long,
    val enabled: Boolean = true,
    /** 硬锁开关：达到限额后锁定该应用，需输入解锁密码。仅在总开关打开且已设密码时生效 */
    val hardLock: Boolean = false,
)
