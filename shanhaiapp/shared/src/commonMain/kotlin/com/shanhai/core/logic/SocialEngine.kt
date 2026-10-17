package com.shanhai.core.logic

/**
 * 社交引擎（GDD 十三·社交）。
 *
 * 三派：茅山 / 龙虎山 / 阁皂山（影响据点归属，可后期转）。
 * - 据点战：每日占领灵脉节点（本地占位实现；**服务器权威应校验 GPS / 防作弊**）。
 * - 派系加成：占领据点期间攻击 +10%（顶部环境条常驻）。
 *
 * ⚠️ **服务器依赖部分仅接口占位**：好友赠妖丹（每日1）/ 交换 / 团战 raid（全服协作打相柳等）
 * / 宗门排行榜 需要后端，本切片不实现，由 SocialService 接口预留（见文件末）。
 */
enum class Faction { MAOSHAN, LONGHUSHAN, GEZAO }

object SocialEngine {

    /** 据点战本地状态（Daily reset 由 GameState.maybeResetDaily 处理） */
    data class StrongholdState(
        var ownedNode: String? = null,
        var lastCaptureDay: String = "",
    )

    /** 派系加成：占领据点时攻击 ×1.10 */
    const val STRONGHOLD_ATTACK_BONUS = 1.10

    /** 是否可占领（每日限一次：今天尚未占领任何节点） */
    fun canCapture(state: StrongholdState, today: String): Boolean =
        state.lastCaptureDay != today

    /** 占领节点：写入节点名与日期，返回是否成功 */
    fun capture(state: StrongholdState, node: String, today: String): Boolean {
        if (!canCapture(state, today)) return false
        state.ownedNode = node
        state.lastCaptureDay = today
        return true
    }

    /** 当前攻击加成倍率（占领据点 → 1.10，否则 1.0） */
    fun attackBonus(owned: Boolean): Double =
        if (owned) STRONGHOLD_ATTACK_BONUS else 1.0

    /**
     * 好友赠妖丹（每日 1）—— ⚠️ 占位：需要好友列表（服务器）。
     * 当前本地无好友数据，返回 false，待后端接入后启用。
     */
    fun canGiftDaily(): Boolean = false
}

/** 团战贡献回执（服务端权威确认）。 */
data class RaidReceipt(
    val accepted: Boolean,
    val bossId: String,
    val creditedContribution: Int,   // 服务端实际计入（≤ 申请，防刷）
    val totalContribution: Int,      // 该 boss 当前全服累计
    val serverTs: Long,
)

/** 宗门排行榜条目。 */
data class FactionRankEntry(
    val playerId: String,
    val faction: Faction,
    val score: Int,
    val rank: Int,
)

/**
 * 社交服务接口（服务器权威占位）。
 * 真实实现应由后端提供：好友关系、团战 raid、宗门排行榜、GPS 防作弊校验。
 * 本切片不调用，仅定义契约，避免双端逻辑漂移。
 */
interface SocialService {
    fun friends(): List<String>                         // 好友 id 列表（服务器）
    fun giftYaodan(friendId: String): Boolean           // 赠妖丹（每日 1）
    fun raidBoss(bossId: String, contribution: Int): RaidReceipt   // 团战 raid 贡献（返回服务端回执）
    fun factionRank(faction: Faction): Int              // 三派积分排名（单名次）
    fun factionRankboard(faction: Faction): List<FactionRankEntry> // 宗门排行榜（全榜）
}
