package com.liubai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val limitMs: Long,
    val enabled: Boolean = true,
)
