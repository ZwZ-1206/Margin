package com.liubai.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.liubai.data.di.ServiceLocator
import com.liubai.data.focus.FocusService
import com.liubai.app.widget.MarginWidgetProvider
import com.liubai.data.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 开机恢复：重排后台任务；若专注进行中则恢复前台服务 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ServiceLocator.init(context)
        WorkScheduler.scheduleUsageCollect(context)
        WorkScheduler.scheduleDailyReport(context)
        MarginWidgetProvider.updateAll(context)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val checkpoint = ServiceLocator.focusRepository.loadCheckpoint()
            if (checkpoint != null && checkpoint.isActive) {
                context.startForegroundService(
                    FocusService.actionIntent(context, FocusService.ACTION_RESTORE)
                )
            }
        }
    }
}
