package com.liubai.app.lock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.liubai.data.di.ServiceLocator
import com.liubai.data.lock.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 硬锁的落地实现：监听前台窗口变化，发现被锁应用即拉起 [LockActivity]。
 *
 * 只做「拉起锁定界面」这一件事，判定逻辑全部在 [AppLockManager]（data 层）：
 * 总开关 / 密码 / 单应用硬锁 / 今日用量 / 宽限期。
 *
 * 两条触发路径：
 *  1. 窗口切换事件（及时）
 *  2. 30 秒轮询当前前台包（兜底：宽限期在应用内到期时不会再有窗口事件）
 *
 * 注意：无障碍服务可被用户在系统设置里关掉，卸载应用、清除数据同样能绕过——
 * 这一点在设置页对用户明确说明，不做夸大宣传。
 */
class AppLockService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastPkg: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        lastPkg = pkg
        evaluate(pkg)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 兜底轮询：宽限期可能在用户停留于应用内时到期，此时没有新的窗口事件
        scope.launch {
            while (isActive) {
                delay(POLL_MS)
                val pkg = lastPkg ?: continue
                if (pkg == packageName) continue
                evaluate(pkg)
            }
        }
    }

    private fun evaluate(pkg: String) {
        if (pkg == packageName) return
        scope.launch {
            val settings = runCatching { ServiceLocator.settingsRepository.current() }
                .getOrNull() ?: return@launch
            // 总开关关闭：收掉还开着的锁定界面，之后不再拦截
            if (!settings.hardLockEnabled) {
                LockActivity.dismissIfOpen()
                return@launch
            }
            if (settings.lockPasswordHash.isNullOrBlank()) return@launch
            val block = runCatching { AppLockManager.shouldBlock(applicationContext, pkg) }
                .getOrDefault(false)
            if (!block) return@launch
            // 锁定界面已在显示就不再重复拉起（避免按 Home 再回来时叠一层）
            if (LockActivity.isShowingFor(pkg)) return@launch
            startActivity(
                Intent(applicationContext, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(LockActivity.EXTRA_PACKAGE, pkg)
            )
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val POLL_MS = 30_000L
    }
}
