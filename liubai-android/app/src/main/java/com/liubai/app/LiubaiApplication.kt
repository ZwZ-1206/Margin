package com.liubai.app

import android.app.Application
import com.liubai.data.di.ServiceLocator
import com.liubai.data.focus.FocusService
import com.liubai.data.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LiubaiApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        WorkScheduler.scheduleUsageCollect(this)
        WorkScheduler.scheduleDailyReport(this)

        // 进程被杀后重启：若有进行中的专注，恢复前台服务（引擎将从断点继续）
        appScope.launch {
            val checkpoint = ServiceLocator.focusRepository.loadCheckpoint()
            if (checkpoint != null && checkpoint.isActive) {
                startForegroundService(
                    FocusService.actionIntent(this@LiubaiApplication, FocusService.ACTION_RESTORE)
                )
            }
        }
    }
}
