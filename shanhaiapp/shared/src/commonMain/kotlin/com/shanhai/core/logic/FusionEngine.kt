package com.shanhai.core.logic

import com.shanhai.core.data.MonsterPool
import com.shanhai.core.model.MonsterInstance

/**
 * 炼妖融合引擎（GDD 十六·养成线：炼妖融合）。
 *
 * 将两只拥有的妖实例熔炼为一只更强的新实例：
 * - 消耗两只原实例 + [FUSE_COST_YAODAN] 妖丹；
 * - 若两妖 defId 命中 [RECIPES]（无序对）则产出配方指定形态，否则取两者中稀有度更高者
 *   （并列取基础属性总和更高者）；
 * - 新等级 = max(a,b 等级) + [LEVEL_BONUS]，受新形态等级上限约束；
 * - 突破次数 = min(a,b 突破)；技能 = 两妖技能并集取前 4。
 *
 * 本引擎只产出「计划」([plan])，不修改任何状态；实际的扣妖丹/移除实例/入队由 [com.shanhai.core.logic.GameState.fuse] 负责，
 * 以便与任务进度、队伍、存档统一处理。
 */
object FusionEngine {

    /** 融合消耗妖丹 */
    const val FUSE_COST_YAODAN: Int = 30

    /** 融合附加等级 */
    const val LEVEL_BONUS: Int = 3

    /** 配方：无序对 → 产物 defId（可扩展更多跨族合体）。internal 供 CoreTest 遍历。 */
    internal val RECIPES: Map<Set<String>, String> = mapOf(
        // —— 四灵神兽合体 ——
        setOf("yao_jiao", "gui_linggui") to "gui_xuanwu",       // 蛟 + 灵龟 → 玄武（镇北水德）
        setOf("yao_jiuwei", "niao_fenghuang") to "lingshou_zhuque", // 九尾狐 + 凤凰 → 朱雀（南方火德）
        setOf("yao_niu", "jing_shijing") to "lingshou_qilin",   // 牛妖 + 石精 → 麒麟（土系瑞兽）
        // —— 异兽/凶兽合体 ——
        setOf("yao_long", "mang_tengshe") to "guai_jiuying",    // 龙 + 腾蛇 → 九婴（九头水兽）
        setOf("tu_yao", "jing_tongjing") to "lingshou_pixiu",   // 兔妖 + 铜镜精 → 貔貅（金系瑞兽）
        // —— 五行生克合体 ——
        setOf("yao_fengli", "gui_guijia") to "lingshou_baihu",  // 风狸(风) + 龟妖(水) → 白虎(金) 风金生水
    )

    /** 配方产物 defId 列表（供 DataIntegrityEngine 校验产物均属妖池，防死配方） */
    val recipeOutputs: List<String> get() = RECIPES.values.toList()

    data class FusionResult(
        val newDefId: String,
        val newLevel: Int,
        val newBreakthrough: Int,
        val newSkills: List<String>,
    )

    /** 是否满足融合前置（两实例不同 + 妖丹充足） */
    fun canFuse(a: MonsterInstance, b: MonsterInstance, yaodan: Int): Boolean =
        a.instanceId != b.instanceId && yaodan >= FUSE_COST_YAODAN

    /**
     * 计算融合结果；不满足（同一实例 / 妖种缺失）返回 null。
     * 不修改任何状态。
     */
    fun plan(a: MonsterInstance, b: MonsterInstance): FusionResult? {
        if (a.instanceId == b.instanceId) return null
        val da = MonsterPool.byId(a.defId) ?: return null
        val db = MonsterPool.byId(b.defId) ?: return null

        val newDefId = RECIPES[setOf(a.defId, b.defId)] ?: run {
            // 无配方：取稀有度更高者；并列取基础属性总和更高者
            when {
                db.rarity.ordinal > da.rarity.ordinal -> b.defId
                da.rarity.ordinal > db.rarity.ordinal -> a.defId
                else -> {
                    val sa = da.baseAtk + da.baseDef + da.baseHp + da.baseSpd
                    val sb = db.baseAtk + db.baseDef + db.baseHp + db.baseSpd
                    if (sb >= sa) b.defId else a.defId
                }
            }
        }
        val newDef = MonsterPool.byId(newDefId) ?: return null

        val bt = minOf(a.breakthrough, b.breakthrough)
        val cap = CultivationEngine.BASE_LEVEL_CAP + bt * CultivationEngine.LEVEL_CAP_PER_BREAK
        val newLevel = (maxOf(a.level, b.level) + LEVEL_BONUS).coerceAtMost(cap)
        val skills = (da.skills.map { it.id } + db.skills.map { it.id }).distinct().take(4)
        return FusionResult(newDefId, newLevel, bt, skills)
    }
}
