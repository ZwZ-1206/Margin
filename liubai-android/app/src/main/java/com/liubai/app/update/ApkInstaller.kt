package com.liubai.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：直接下载 APK 并调起安装，不经过浏览器。
 *
 * - 下载走 HttpURLConnection 流式写入 app 专属外部目录（无需存储权限）
 * - 安装通过 FileProvider 暴露给系统安装器
 * - Android 8+ 若未开启「安装未知应用」，先引导到该应用的授权页
 */
object ApkInstaller {

    private const val AUTHORITY_SUFFIX = ".fileprovider"

    fun apkDir(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "").apply { mkdirs() }

    /** 下载 APK；[onProgress] 回调 0..100，-1 表示未知总长度 */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(apkDir(context), fileName)
            if (target.exists()) target.delete()

            var current = url
            var conn: HttpURLConnection
            var redirects = 0
            while (true) {
                conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    current = location
                    if (++redirects > 5) break
                    continue
                }
                break
            }

            conn.inputStream.use { input ->
                val total = conn.contentLengthLong
                var read = 0L
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(if (total > 0) ((read * 100) / total).toInt() else -1)
                    }
                }
            }
            conn.disconnect()
            if (target.length() > 0) target else null
        }.getOrNull()
    }

    /** 调起系统安装器；返回 false 表示需要先授权「安装未知应用」 */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
        return true
    }
}
