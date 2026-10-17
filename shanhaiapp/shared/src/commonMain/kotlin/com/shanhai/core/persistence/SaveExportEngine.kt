package com.shanhai.core.persistence

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 存档导出 / 导入（跨端云同步的基础层）。
 *
 * 在裸 [SaveData] JSON 外套一层便携包装，明确「导出版本」与「存档体」，
 * 使 Android / iOS 导出的字符串结构完全一致（均为 {"v":1,"save":{...}}），
 * 未来接入云同步时双端可互相读取同一份备份。
 *
 * 真实的上传 / 下载由平台层（网络 + 账号）负责；本引擎只做本地
 * portable 字符串的编解码，复用 [SaveCodec] 既有的容错约定
 * （ignoreUnknownKeys + encodeDefaults），不新增任何运行时状态。
 */
object SaveExportEngine {

    /** 便携导出格式版本（传输层，独立于 SaveData.VERSION 业务版本）。 */
    const val EXPORT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * 导出：SaveData → 便携 JSON 字符串 `{"v":1,"save":{...}}`。
     * 双端调用后输出结构一致，可直接作为云同步的上行载荷。
     */
    fun toPortable(save: SaveData): String =
        json.encodeToString(PortableSave.serializer(), PortableSave(EXPORT_VERSION, save))

    /**
     * 导入：便携 JSON 字符串 → SaveData。
     * - 解析失败（非法 JSON / 缺 save 字段）→ 返回 null
     * - 导出版本不匹配（v != [EXPORT_VERSION]）→ 返回 null（调用方回退本地存档）
     * - 正常 → 返回内部 SaveData（其字段缺失时由 SaveData 默认值容错）
     */
    fun fromPortable(text: String): SaveData? {
        val portable = runCatching { json.decodeFromString<PortableSave>(text) }.getOrNull()
            ?: return null
        if (portable.v != EXPORT_VERSION) return null
        return SaveIntegrityEngine.sanitize(portable.save)
    }
}

/**
 * 便携包装体：传输层版本号 + 业务存档体。
 * 复用 [SaveData] 的 @Serializable，使其可被同一 Json 实例编解码。
 */
@Serializable
data class PortableSave(val v: Int, val save: SaveData)
