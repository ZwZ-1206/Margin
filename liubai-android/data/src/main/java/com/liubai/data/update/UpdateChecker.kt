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

    private const val VERSION_URL = "https://margin-8ux.pages.dev/downloads/version.json"
    const val DOWNLOAD_PAGE = "https://margin-8ux.pages.dev/#/download"

    data class Release(
        val versionName: String,
        val versionCode: Int,
        val notes: String,
        val downloadUrl: String,
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
                val apkUrl = root.optJSONObject("apk")?.optString("url").orEmpty()
                Release(
                    versionName = name,
                    versionCode = code,
                    notes = root.optString("notes", ""),
                    downloadUrl = if (apkUrl.startsWith("http")) apkUrl else DOWNLOAD_PAGE,
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
