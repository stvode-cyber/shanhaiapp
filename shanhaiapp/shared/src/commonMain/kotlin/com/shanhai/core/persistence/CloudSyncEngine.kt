package com.shanhai.core.persistence

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 跨端云同步：冲突合并与完整性校验（shared 侧，纯函数，无网络/账号依赖）。
 *
 * 真实的「上传 / 下载 + 账号」由平台层（网络 + 账号）负责；本引擎只解决
 * 在 shared 侧确定性地合并双端进度、并给出防篡改校验和，使 Android / iOS
 * 行为完全一致。
 *
 * 合并策略（确定性、可复现）：
 *  1. 以 modifiedAt（epoch ms）做「最后写入优先」LWW；
 *  2. modifiedAt 相同按 deviceId 字典序打破平局，保证确定性；
 *  3. 三种货币（灵玉 / 妖丹 / 香火）为单调累积资源，合并时取两方 max，
 *     降低「双端各自游玩」时的进度丢失；其余字段一律采用 LWW 胜方；
 *  4. 任一方为 null（导入失败 / 无远程）则直接采用另一方。
 */
object CloudSyncEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * 云信封：设备标识 + 修改时间 + 便携存档体。
     * 传输层版本复用 [SaveExportEngine.EXPORT_VERSION]，保证与导出格式一致。
     */
    @Serializable
    data class CloudEnvelope(
        val deviceId: String,
        val modifiedAt: Long,
        val v: Int,
        val save: SaveData,
    )

    /** 本地存档 → 云信封 JSON（附带设备标识与修改时间）。 */
    fun toEnvelope(save: SaveData, deviceId: String, modifiedAt: Long): String =
        json.encodeToString(CloudEnvelope.serializer(), CloudEnvelope(deviceId, modifiedAt, SaveExportEngine.EXPORT_VERSION, save))

    /** 云信封 JSON → 结构；解析失败或版本不符 → null。 */
    fun fromEnvelope(text: String): CloudEnvelope? {
        val e = runCatching { json.decodeFromString<CloudEnvelope>(text) }.getOrNull() ?: return null
        if (e.v != SaveExportEngine.EXPORT_VERSION) return null
        return e
    }

    /**
     * 合并本地与远程两份云信封，返回胜方信封（其货币已取 max）。
     * - local / remote 任一为 null → 返回另一方（可能为 null，由调用方兜底）；
     * - 胜方由 modifiedAt 决定（平局 deviceId 字典序）。
     */
    fun merge(local: CloudEnvelope?, remote: CloudEnvelope?): CloudEnvelope? {
        if (local == null) return remote
        if (remote == null) return local
        val (winner, loser) = if (remote.modifiedAt > local.modifiedAt) {
            remote to local
        } else if (local.modifiedAt > remote.modifiedAt) {
            local to remote
        } else if (remote.deviceId > local.deviceId) {
            remote to local
        } else {
            local to remote
        }
        val mergedSave = winner.save.copy(
            lingyu = maxOf(winner.save.lingyu, loser.save.lingyu),
            yaodan = maxOf(winner.save.yaodan, loser.save.yaodan),
            incense = maxOf(winner.save.incense, loser.save.incense),
        )
        return winner.copy(save = SaveIntegrityEngine.sanitize(mergedSave))
    }

    /** 防篡改校验和（FNV-1a 32-bit，作用于字符串的 UTF-16 码元）。 */
    fun checksum(text: String): Int {
        var hash: Long = 0x811c9dc5L
        for (c in text) {
            hash = (hash xor (c.code.toLong() and 0xffL)) and 0xffffffffL
            hash = (hash * 0x01000193L) and 0xffffffffL
        }
        return hash.toInt()
    }

    /** 对存档计算校验和（基于其规范便携字符串，确定性可复现）。 */
    fun checksumOf(save: SaveData): Int = checksum(SaveExportEngine.toPortable(save))

    /** 校验存档与其校验和是否一致。 */
    fun verify(save: SaveData, expected: Int): Boolean = checksumOf(save) == expected
}
