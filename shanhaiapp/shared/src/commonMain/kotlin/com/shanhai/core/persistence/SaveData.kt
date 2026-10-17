package com.shanhai.core.persistence

import com.shanhai.core.model.EquipSlot
import com.shanhai.core.model.MonsterInstance
import com.shanhai.core.model.OwnedEquip
import com.shanhai.core.quest.QuestState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * 单个任务的存档切片（与 QuestProgress 解耦，仅存可变部分）。
 * state 用枚举名序列化，便于任务库更新后向前兼容。
 */
@Serializable
data class QuestSave(
    val id: String,
    val current: List<Int>,
    val state: QuestState,
)

/**
 * 全局存档（平台无关）。只存「进度」不存「定义」：
 * - 妖怪全量数据来自 MonsterPool，仅存已收/出战妖的 ID；
 * - 任务定义来自 QuestLibrary，仅存每个任务的进度与状态。
 * 这样存档体积小、且与代码版本解耦（新增妖/任务不会让旧存档失效）。
 */
@Serializable
data class SaveData(
    val version: Int = VERSION,
    val caughtRegion: List<String> = emptyList(),   // 地域图鉴已收（计入完成度）
    val caughtSecret: List<String> = emptyList(),   // 秘境所收（不计地域完成度）
    val team: List<String> = emptyList(),            // 出战队伍（≤6 只妖 ID）
    val incense: Int = 0,                            // 香火
    val lingyu: Int = 0,                             // 灵玉
    val yaodan: Int = 0,                             // 妖丹
    val secretRealmLeft: Int = 3,                    // 每日秘境剩余次数
    val lastDailyReset: String = "",                // 上次每日重置日期 yyyy-MM-dd
    val quests: List<QuestSave> = emptyList(),       // 各任务进度快照
    val owned: List<MonsterInstance> = emptyList(),  // 拥有的妖实例（含等级/经验/突破）
    val nextInstSeq: Int = 1,                        // 实例 id 自增序号（保证实例 id 唯一）
    val equipment: List<OwnedEquip> = emptyList(),   // 拥有的装备实例
    val nextEquipSeq: Int = 1,                       // 装备实例 id 自增序号
    val equipped: Map<String, Map<EquipSlot, String>> = emptyMap(), // 妖实例id → 槽位 → 装备实例id
    // ── 祈妖抽卡（多池保底，各池独立计数） ──
    val gachaNormalSinceShen: Int = 0,                    // 通用池：距上次出神已抽次数（硬保底）
    val gachaLimitedSinceShen: Int = 0,                   // 限定神池：距上次出神已抽次数（硬保底）
    val dungeonEntered: Map<String, Int> = emptyMap(),   // 副本 id → 当日进入次数
    val faction: String? = null,                          // 所属派系（MAOSHAN/LONGHUSHAN/GEZAO）
    val strongholdNode: String? = null,                   // 据点战占领的节点名
    val strongholdDay: String = "",                       // 据点战占领日期（每日限一次）
    val shopIncenseBought: Int = 0,                       // 当日引妖香已购份数
    val shopDay: String = "",                             // 商店购买日期（每日重置）
    val reputation: Int = 100,                            // 信誉分（GDD 十六·公平与稳健），默认满分
    // ── 二十·宗门科技树（公会建设 + 科技加成） ──
    val sectTech: Map<String, Int> = emptyMap(),          // 科技节点 id → 等级（默认向后兼容）
    val sectContribution: Int = 0,                        // 宗门贡献（默认 0 向后兼容）
    // ── 十八·新手引导 / 7 日留存 ──
    val tutorialStep: Int = 0,                            // 已完成引导最高步序（0=未开始）
    val tutorialSkipped: Boolean = false,                 // 是否跳过引导
    val signInDay: Int = 0,                               // 累计签到天数（驱动 7 日留存解锁）
    val lastSignIn: String = "",                          // 最近签到日期 yyyy-MM-dd
    // ── 十九·长线留存：家园洞府（放置产香火/妖丹 + 阵法风水加成） ──
    val homesteadLevel: Int = 0,                          // 阵法风水等级
    val lastHomesteadCollect: Long = 0,                   // 上次收取时间戳 ms（0 = 从未收取）
    // ── 十九·长线留存：赛季通行证（90 天双轨） ──
    val seasonStartMs: Long = 0,                          // 当前赛季起点 ms（0 = 未开始）
    val seasonXp: Int = 0,                                // 当前赛季累计经验
    val hasSeasonPass: Boolean = false,                   // 是否已购通行证
    val seasonClaimedFree: List<Int> = emptyList(),       // 已领免费档
    val seasonClaimedPaid: List<Int> = emptyList(),       // 已领付费档
    val seasonIndex: Int = 1,                             // 赛季序号（滚动 +1）
    // ── 十六·公平与稳健：疲劳值 / 单日收益封顶 ──
    val fatigue: Int = 0,                                 // 疲劳值 0..100（每日跨天重置）
    // ── 二十·竞技与荣誉：排位赛段位天梯 ──
    val rankTierIndex: Int = 0,                           // 当前段位索引 0=炼气 … 4=化神
    val rankPoints: Int = 0,                              // 当前段位内积分 0..99
    // ── 二十·竞技与荣誉：成就 / 勋章 / 称号（展示型荣誉） ──
    val tribulationTotal: Int = 0,                       // 累计渡劫飞升次数（称号统计；默认 0 向后兼容）
    val equippedTitleId: String = "",                    // 玩家装备的展示称号 id（""=无；默认向后兼容）
) {
    companion object { const val VERSION = 1 }
}

/**
 * 存档编解码（kotlinx.serialization / JSON）。
 * ignoreUnknownKeys 让旧存档在任务库/字段扩展后仍可兼容读取。
 */
object SaveCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    fun encode(data: SaveData): String = json.encodeToString(data)

    fun decode(text: String): SaveData? =
        runCatching { json.decodeFromString<SaveData>(text) }
            .getOrNull()
            ?.let { SaveIntegrityEngine.sanitize(it) }
}
