package com.liubai.data.usage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** 包名 → 应用名/图标（带缓存，列表滚动时避免重复 IPC） */
class AppLabelResolver(context: Context) {

    private val pm: PackageManager = context.packageManager
    private val nameCache = mutableMapOf<String, String>()
    private val iconCache = mutableMapOf<String, Drawable?>()

    fun label(packageName: String): String = nameCache.getOrPut(packageName) {
        runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    fun icon(packageName: String): Drawable? = iconCache.getOrPut(packageName) {
        runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
    }

    /** 过滤：仅统计用户可见应用（排除无图标的系统组件） */
    fun isUserVisible(packageName: String): Boolean = runCatching {
        val info = pm.getApplicationInfo(packageName, 0)
        info.flags and ApplicationInfo.FLAG_SYSTEM == 0 || info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
    }.getOrDefault(true)
}
