package com.liubai.data.lock

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.liubai.core.common.DateKeys
import com.liubai.data.di.ServiceLocator
import java.security.MessageDigest

/**
 * 硬锁管理：达到每日限额后锁定应用，需输入六位数解锁密码。
 *
 * 生效前提（三者缺一不可）：
 *  1. 用户在设置中打开「硬锁」总开关（默认关闭）
 *  2. 已设置六位数解锁密码
 *  3. 该应用的每日限额勾选了「达限额后锁定」
 *
 * 实现方式：AccessibilityService 监听窗口切换，发现被锁应用在前台即拉起 [LockActivity]。
 * 诚实说明：卸载应用、清除 Margin 数据、关闭无障碍服务均可绕过硬锁；
 * 忘记密码只能等到次日零点自动解锁。
 */
object AppLockManager {

    /** 解锁后的宽限期：期间不重复锁定，过期后再次达到限额即重新锁定 */
    const val GRACE_MS = 10 * 60_000L

    private const val PREFS = "app_lock_grace"
    private const val SALT = "liubai:hardlock:v1"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 六位密码 → SHA-256 十六进制串（加固定盐，避免裸彩虹表） */
    fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest((SALT + pin).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, hash: String?): Boolean {
        if (hash.isNullOrBlank() || pin.length != 6) return false
        return hashPin(pin) == hash
    }

    /** 当前是否处于宽限期（解锁后 10 分钟内） */
    fun inGrace(context: Context, packageName: String): Boolean =
        prefs(context).getLong(packageName, 0L) > System.currentTimeMillis()

    /** 宽限期剩余毫秒（<=0 表示不在宽限期） */
    fun graceRemaining(context: Context, packageName: String): Long =
        (prefs(context).getLong(packageName, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    /** 清除单个应用的宽限期（重新锁定） */
    fun clearGrace(context: Context, packageName: String) {
        prefs(context).edit().remove(packageName).apply()
    }

    /** 关闭总开关 / 清除密码时调用：清空所有宽限期 */
    fun clearAllGrace(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /**
     * 该应用当前是否应被锁定。
     * 由 AccessibilityService 在窗口切换时调用（挂起函数，走 ServiceLocator）。
     */
    suspend fun shouldBlock(context: Context, packageName: String): Boolean {
        val settings = ServiceLocator.settingsRepository.current()
        if (!settings.hardLockEnabled) return false
        if (settings.lockPasswordHash.isNullOrBlank()) return false
        val limit = ServiceLocator.limitsRepository.limitOf(packageName) ?: return false
        if (!limit.enabled || !limit.hardLock) return false
        if (inGrace(context, packageName)) return false
        val date = DateKeys.today()
        val used = ServiceLocator.database.usageDao().listRange(date, date)
            .firstOrNull { it.packageName == packageName }?.totalMs ?: 0L
        return used >= limit.limitMs
    }

    /** 某应用今日用量（毫秒） */
    suspend fun todayUsageMs(packageName: String): Long {
        val date = DateKeys.today()
        return ServiceLocator.database.usageDao().listRange(date, date)
            .firstOrNull { it.packageName == packageName }?.totalMs ?: 0L
    }

    /** 某应用今日用量与限额（毫秒），供锁定界面展示 */
    suspend fun usageOf(packageName: String): Pair<Long, Long> {
        val used = todayUsageMs(packageName)
        val limit = ServiceLocator.limitsRepository.limitOf(packageName)?.limitMs ?: 0L
        return used to limit
    }

    /** 应被锁定的应用包名集合（供通知文案 / UI 展示） */
    suspend fun lockedPackages(context: Context): Set<String> {
        val settings = ServiceLocator.settingsRepository.current()
        if (!settings.hardLockEnabled || settings.lockPasswordHash.isNullOrBlank()) return emptySet()
        val limits = ServiceLocator.limitsRepository.listLimits()
            .filter { it.enabled && it.hardLock }
        if (limits.isEmpty()) return emptySet()
        val date = DateKeys.today()
        val used = ServiceLocator.database.usageDao().listRange(date, date)
            .associateBy { it.packageName }
        return limits
            .filter { (used[it.packageName]?.totalMs ?: 0L) >= it.limitMs }
            .filterNot { inGrace(context, it.packageName) }
            .map { it.packageName }
            .toSet()
    }

    /**
     * 尝试解锁。密码正确则进入 10 分钟宽限期并返回 true。
     */
    suspend fun unlock(context: Context, packageName: String, pin: String): Boolean {
        val settings = ServiceLocator.settingsRepository.current()
        if (!settings.hardLockEnabled) return false
        if (!verifyPin(pin, settings.lockPasswordHash)) return false
        prefs(context).edit()
            .putLong(packageName, System.currentTimeMillis() + GRACE_MS)
            .apply()
        return true
    }

    /** 无障碍服务是否已启用（设置页用于提示用户） */
    fun isLockServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        if (enabled.isEmpty()) return false
        val expected = ComponentName(context.packageName, "com.liubai.app.lock.AppLockService")
            .flattenToString()
        val expectedShort = ComponentName(context.packageName, "com.liubai.app.lock.AppLockService")
            .flattenToShortString()
        return enabled.split(':').any { entry ->
            val e = entry.trim()
            e.equals(expected, ignoreCase = true) || e.equals(expectedShort, ignoreCase = true)
        }
    }

    /** 打开系统无障碍设置页（引导用户开启服务） */
    fun openAccessibilitySettings(context: Context) {
        val intent = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
