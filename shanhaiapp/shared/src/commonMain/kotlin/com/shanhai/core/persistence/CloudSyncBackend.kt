package com.shanhai.core.persistence

/**
 * 云同步平台层契约（上传 / 下载 + 账号）。
 *
 * 真实的「网络 + 账号」由平台层（Android WorkManager/Http、iOS URLSession）提供；
 * 本契约仅定义 shared 侧接口与数据类，使 Android / iOS 行为完全一致，
 * 并由 Python 等价断言验证方法签名、返回值结构与版本一致性，避免双端漂移。
 *
 * 合并 / 校验等确定性逻辑已在 [CloudSyncEngine] 完成；本契约只负责
 * 「本地存档 ⇄ 服务器信封」的收发边界，不引入任何运行时状态。
 *
 * 接入约定：
 *  - 上行：平台层调用 [CloudSyncEngine.toEnvelope] 包裹本地 [SaveData]，经 [upload] 发往服务器；
 *  - 下行：平台层经 [download] 取回已合并的 [CloudEnvelope]，再由 [CloudSyncEngine.merge] 落地。
 */
interface CloudSyncBackend {

    /** 平台层是否具备云同步能力（已登录账号且网络可用）。 */
    fun isAvailable(): Boolean

    /**
     * 上传本地存档为云信封。
     * @param local        本地 [SaveData]（先经 [CloudSyncEngine.toEnvelope] 包裹）
     * @param deviceId     设备标识（参与 LWW 平局打破）
     * @param accountToken 账号令牌（空串视为未登录 → 回执 accepted=false）
     * @return [UploadAck]；accepted=false 表示未登录 / 网络失败 / 校验失败。
     */
    fun upload(local: SaveData, deviceId: String, accountToken: String): UploadAck

    /**
     * 下载远程云信封（服务器侧已合并双端）。
     * @param accountToken 账号令牌（空串 → 返回 null）
     * @return 远程 [CloudEnvelope]；null 表示无远程 / 解析失败 / 未登录。
     */
    fun download(accountToken: String): CloudSyncEngine.CloudEnvelope?
}

/** 上传回执（平台层 → shared）。 */
data class UploadAck(
    val accepted: Boolean,
    val storedModifiedAt: Long,   // 服务器落库时间（accepted 时 >= 0）
    val checksum: Int,            // 与 [CloudSyncEngine.checksumOf] 对应，用于一致性校验
)
