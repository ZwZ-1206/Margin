package com.liubai.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本检测：读取官网的 version.json 与本地 versionCode 比较。
 *
 * 刻意做得极简——只用 HttpURLConnection，不引入网络库；
 * 失败（没网、超时、JSON 异常）一律返回 null，绝不打扰用户。
 */
object UpdateChecker {

    private const val BASE_URL = "https://margin-8ux.pages.dev/"
    private const val VERSION_URL = BASE_URL + "downloads/version.json"
    const val DOWNLOAD_PAGE = BASE_URL + "#/download"

    data class Release(
        val versionName: String,
        val versionCode: Int,
        val notes: String,
        val downloadUrl: String,
        val sha256: String? = null,
        val sizeBytes: Long = 0L,
    )

    /** 拉取线上版本信息；任何异常返回 null */
    suspend fun fetch(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val code = root.optInt("versionCode", 0)
                val name = root.optString("version", "")
                if (code <= 0) return@runCatching null

                val apk = root.optJSONObject("apk")
                val rawUrl = apk?.optString("url").orEmpty()
                // version.json 里写的是相对路径（downloads/xxx.apk），必须基于站点根解析成绝对地址。
                // 上一版直接用 startsWith("http") 判断，相对路径会被当成"没有直链"而回退到网页地址，
                // 于是下载器把 SPA 的 HTML 存成 .apk，安装器报「安装包已损坏」。
                val absoluteUrl = when {
                    rawUrl.isBlank() -> ""
                    rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
                    else -> runCatching { URL(URL(BASE_URL), rawUrl).toString() }.getOrDefault("")
                }

                Release(
                    versionName = name,
                    versionCode = code,
                    notes = root.optString("notes", ""),
                    downloadUrl = absoluteUrl,
                    sha256 = apk?.optString("sha256")?.takeIf { it.length == 64 },
                    sizeBytes = apk?.optLong("sizeBytes", 0L) ?: 0L,
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** 是否有比 [currentVersionCode] 更新的版本 */
    suspend fun hasUpdate(currentVersionCode: Int): Release? =
        fetch()?.takeIf { it.versionCode > currentVersionCode }
}
