package com.liubai.data.repository

import com.liubai.data.db.LiubaiDatabase
import com.liubai.data.db.FocusSessionEntity
import com.liubai.data.db.TagEntity
import com.liubai.data.datastore.SettingsRepositoryImpl
import com.liubai.data.di.ServiceLocator
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地备份：全部专注记录 + 标签导出为单个 JSON 文件。
 * 不涉及任何网络传输，文件由用户通过系统文件选择器自行保管。
 */
object BackupManager {

    private const val SCHEMA = 1

    suspend fun exportJson(): String {
        val db = ServiceLocator.database
        val sessions = db.focusDao().listBetween(0, Long.MAX_VALUE)
        val tags = db.focusDao().listTags()
        val settings = ServiceLocator.settingsRepository.current()

        val root = JSONObject()
        root.put("schema", SCHEMA)
        root.put("app", "liubai")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("settings", JSONObject().apply {
            put("theme", settings.theme.name)
            put("dailyGoalMin", settings.dailyGoalMin)
            put("pomodoro", JSONObject().apply {
                put("focusMin", settings.pomodoro.focusMin)
                put("shortBreakMin", settings.pomodoro.shortBreakMin)
                put("longBreakMin", settings.pomodoro.longBreakMin)
                put("cyclesBeforeLongBreak", settings.pomodoro.cyclesBeforeLongBreak)
                put("autoStartBreak", settings.pomodoro.autoStartBreak)
                put("autoStartNextFocus", settings.pomodoro.autoStartNextFocus)
            })
            put("countdownMin", settings.countdownMin)
            put("monitorEnabled", settings.monitorEnabled)
        })

        val tagArr = JSONArray()
        tags.forEach { t ->
            tagArr.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("emoji", t.emoji)
                put("createdAt", t.createdAt)
            })
        }
        root.put("tags", tagArr)

        val sessionArr = JSONArray()
        sessions.forEach { s ->
            sessionArr.put(JSONObject().apply {
                put("mode", s.mode)
                put("tagId", s.tagId)
                put("startedAt", s.startedAt)
                put("endedAt", s.endedAt)
                put("durationMs", s.durationMs)
                put("status", s.status)
                put("cycles", s.cycles)
            })
        }
        root.put("sessions", sessionArr)
        return root.toString(2)
    }

    /** 导入：合并写入（同 startedAt+duration 视为重复，跳过） */
    suspend fun importJson(json: String): Result<Int> = runCatching {
        val root = JSONObject(json)
        require(root.optString("app") == "liubai") { "不是「留白」的备份文件" }
        val db = ServiceLocator.database
        var imported = 0

        val tagArr = root.optJSONArray("tags") ?: JSONArray()
        val tagIdMap = mutableMapOf<Long, Long>()
        for (i in 0 until tagArr.length()) {
            val t = tagArr.getJSONObject(i)
            val oldId = t.getLong("id")
            val newId = db.focusDao().insertTag(
                TagEntity(
                    name = t.getString("name"),
                    emoji = t.optString("emoji", "🎯"),
                    createdAt = t.optLong("createdAt", System.currentTimeMillis()),
                )
            )
            tagIdMap[oldId] = newId
        }

        val sessionArr = root.optJSONArray("sessions") ?: JSONArray()
        val existing = db.focusDao().listBetween(0, Long.MAX_VALUE)
            .map { it.startedAt to it.durationMs }.toSet()
        for (i in 0 until sessionArr.length()) {
            val s = sessionArr.getJSONObject(i)
            val startedAt = s.getLong("startedAt")
            val durationMs = s.getLong("durationMs")
            if ((startedAt to durationMs) in existing) continue
            db.focusDao().insert(
                FocusSessionEntity(
                    mode = s.optString("mode", "COUNTDOWN"),
                    tagId = s.optLong("tagId", 0L).takeIf { it != 0L }?.let { tagIdMap[it] },
                    startedAt = startedAt,
                    endedAt = s.optLong("endedAt", startedAt + durationMs),
                    durationMs = durationMs,
                    status = s.optString("status", "INTERRUPTED"),
                    cycles = s.optInt("cycles", 0),
                )
            )
            imported++
        }

        // 设置仅在导入文件包含时覆盖
        root.optJSONObject("settings")?.let { st ->
            ServiceLocator.settingsRepository.update { cur ->
                cur.copy(
                    dailyGoalMin = st.optInt("dailyGoalMin", cur.dailyGoalMin),
                    countdownMin = st.optInt("countdownMin", cur.countdownMin),
                    monitorEnabled = st.optBoolean("monitorEnabled", cur.monitorEnabled),
                    pomodoro = st.optJSONObject("pomodoro")?.let { p ->
                        cur.pomodoro.copy(
                            focusMin = p.optInt("focusMin", cur.pomodoro.focusMin),
                            shortBreakMin = p.optInt("shortBreakMin", cur.pomodoro.shortBreakMin),
                            longBreakMin = p.optInt("longBreakMin", cur.pomodoro.longBreakMin),
                            cyclesBeforeLongBreak = p.optInt("cyclesBeforeLongBreak", cur.pomodoro.cyclesBeforeLongBreak),
                            autoStartBreak = p.optBoolean("autoStartBreak", cur.pomodoro.autoStartBreak),
                            autoStartNextFocus = p.optBoolean("autoStartNextFocus", cur.pomodoro.autoStartNextFocus),
                        )
                    } ?: cur.pomodoro,
                )
            }
        }
        imported
    }
}
