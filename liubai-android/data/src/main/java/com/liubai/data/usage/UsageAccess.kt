package com.liubai.data.usage

import android.app.AppOpsManager
import android.content.Context
import android.os.Process

/** 判断「使用情况访问」是否真的已授予（AppOps 检查，而不是只看服务是否存在） */
object UsageAccess {

    fun isGranted(context: Context): Boolean = runCatching {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}
