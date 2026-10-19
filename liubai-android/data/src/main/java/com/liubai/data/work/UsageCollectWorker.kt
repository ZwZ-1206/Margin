package com.liubai.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.liubai.data.di.ServiceLocator

/**
 * 周期采集对账：每 30 分钟拉取一次 UsageStats 事件增量。
 * 事件驱动为主、此 worker 兜底（覆盖服务被杀死期间的缺口）。
 */
class UsageCollectWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            ServiceLocator.usageRepository.collectNow()
            InterventionChecker.check(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val WORK_NAME = "usage-collect-periodic"
    }
}
