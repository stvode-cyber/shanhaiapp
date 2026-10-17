package com.shanhai.yaozhi

import android.content.Context
import android.content.SharedPreferences
import com.shanhai.core.persistence.SaveData
import kotlinx.serialization.json.Json

/**
 * Android 侧存档管理器。
 *
 * 存档格式：SharedPreferences 里存一条 JSON 字符串（key = "save_json"）。
 * 用 kotlinx.serialization.Json 序列化——跟 shared 模块 SaveData @Serializable 对齐。
 *
 * 自动存档：
 *   · 启动：SaveManager.load() → applySaveData() 恢复
 *   · ON_PAUSE：SaveManager.save(gs.toSaveData()) 写盘
 *
 * 手动存档（AboutScreen）：
 *   · 立即存档 / 读档 / 清空
 */
class SaveManager(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true    // 未来 SaveData 加新字段也能读旧档
        encodeDefaults = true       // 默认值也序列化，保证字段完整
        prettyPrint = false         // 紧凑 JSON，省空间
        isLenient = false           // 严格模式，不容忍非法 JSON
    }

    /** 把 SaveData 序列化成 JSON 写进 SharedPreferences。返回写入时间戳 ms。 */
    fun save(data: SaveData): Long {
        val ts = System.currentTimeMillis()
        val jsonStr = json.encodeToString(SaveData.serializer(), data)
        sp.edit()
            .putString(KEY_JSON, jsonStr)
            .putLong(KEY_TS, ts)
            .apply()
        return ts
    }

    /** 读档。返回 null 表示从未存过或存档损坏。 */
    fun load(): SaveData? {
        val jsonStr = sp.getString(KEY_JSON, null) ?: return null
        return try {
            json.decodeFromString(SaveData.serializer(), jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    /** 返回最后存档时间戳（ms）。从未存过返回 0。 */
    fun lastSaveTs(): Long = sp.getLong(KEY_TS, 0L)

    /** 清空存档。 */
    fun reset() {
        sp.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "shanhai_save"
        private const val KEY_JSON = "save_json"
        private const val KEY_TS = "save_ts"
    }
}
