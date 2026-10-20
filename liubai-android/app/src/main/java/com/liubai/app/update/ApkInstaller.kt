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
import java.security.MessageDigest

/**
 * 应用内更新：直接下载 APK 并调起安装，不经过浏览器。
 *
 * - 下载走 HttpURLConnection 流式写入 app 专属外部目录（无需存储权限）
 * - 下载后做三重校验：字节数 → ZIP 魔数 → SHA-256。**这一步不能省**：
 *   站点返回 404 页面、CDN 缓存污染、下载中断，都会产出「看起来下完了」的垃圾文件，
 *   直接交给系统安装器只会得到一句「安装包已损坏」，用户完全不知道发生了什么。
 * - 安装通过 FileProvider 暴露给系统安装器
 * - Android 8+ 若未开启「安装未知应用」，先引导到该应用的授权页
 */
object ApkInstaller {

    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val MIN_APK_BYTES = 100 * 1024L

    sealed interface Outcome {
        data class Success(val file: File) : Outcome
        data class Failure(val reason: Reason) : Outcome
    }

    enum class Reason {
        /** version.json 里没有可用的直链 */
        NO_URL,

        /** 网络层失败：连不上、超时、非 200 */
        NETWORK,

        /** 落盘字节数与 version.json 声明的不一致（下载被截断或内容被替换） */
        SIZE_MISMATCH,

        /** 不是 ZIP 容器——典型情况是下到了 HTML 错误页 */
        NOT_APK,

        /** SHA-256 与发布清单不一致 */
        CHECKSUM,
    }

    fun apkDir(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "").apply { mkdirs() }

    /** 下载并校验 APK；[onProgress] 回调 0..100，-1 表示未知总长度 */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        expectedSha256: String? = null,
        expectedSize: Long = 0L,
        onProgress: (Int) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext Outcome.Failure(Reason.NO_URL)

        val target = File(apkDir(context), fileName)
        if (target.exists()) target.delete()

        val transferred = runCatching { fetchTo(url, target, onProgress) }.getOrElse { false }
        if (!transferred) {
            target.delete()
            return@withContext Outcome.Failure(Reason.NETWORK)
        }

        if (expectedSize > 0 && target.length() != expectedSize) {
            target.delete()
            return@withContext Outcome.Failure(Reason.SIZE_MISMATCH)
        }
        if (!looksLikeApk(target)) {
            target.delete()
            return@withContext Outcome.Failure(Reason.NOT_APK)
        }
        if (!expectedSha256.isNullOrBlank() && !sha256Matches(target, expectedSha256)) {
            target.delete()
            return@withContext Outcome.Failure(Reason.CHECKSUM)
        }
        Outcome.Success(target)
    }

    /** 逐跳跟随重定向并写入文件；返回是否完整拿到 200 响应体 */
    private fun fetchTo(url: String, target: File, onProgress: (Int) -> Unit): Boolean {
        var current = url
        var hops = 0
        while (hops <= 6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                // 自己跟跳转：需要跨域（官网 → GitHub Release），也要限制跳转次数
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.android.package-archive,*/*")
                setRequestProperty("User-Agent", "Margin-Updater")
            }
            val code = runCatching { conn.responseCode }.getOrElse {
                conn.disconnect()
                return false
            }
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) return false
                current = runCatching { URL(URL(current), location).toString() }.getOrNull() ?: return false
                hops++
                continue
            }
            if (code != 200) {
                conn.disconnect()
                return false
            }
            return try {
                conn.inputStream.use { input ->
                    val total = conn.contentLengthLong
                    var read = 0L
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            if (n == 0) continue
                            out.write(buf, 0, n)
                            read += n
                            onProgress(if (total > 0) ((read * 100) / total).toInt() else -1)
                        }
                    }
                    read > 0
                }
            } catch (_: Exception) {
                false
            } finally {
                conn.disconnect()
            }
        }
        return false
    }

    /** APK 本质是 ZIP，头四字节为 PK\x03\x04；HTML 页面开头永远是 '<' */
    private fun looksLikeApk(file: File): Boolean {
        if (file.length() < MIN_APK_BYTES) return false
        return runCatching {
            file.inputStream().use { ins ->
                val head = ByteArray(2)
                ins.read(head) == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
            }
        }.getOrDefault(false)
    }

    private fun sha256Matches(file: File, expected: String): Boolean = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        actual.equals(expected, ignoreCase = true)
    }.getOrDefault(false)

    /** 调起系统安装器；返回 false 表示需要先授权「安装未知应用」 */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists()) return false
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
