package com.shanhai.core.data

import com.shanhai.core.model.*

/**
 * 装备库（代表性切片，覆盖七行亲和 / 三槽位 / 灵·仙 两档稀有度）。
 * GDD 全量装备（含传说/神话级、套装）后续可在此扩充。
 */
object EquipmentLibrary {
    val all: List<EquipDef> = listOf(
        // ── 武器（主加攻/速） ──
        EquipDef("eq_sword", "青锋剑", EquipSlot.WEAPON, Element.METAL, Rarity.LING, 18, 0, 0, 4),
        EquipDef("eq_staff", "桃木杖", EquipSlot.WEAPON, Element.WOOD, Rarity.LING, 14, 0, 0, 6),
        EquipDef("eq_bow", "落霞弓", EquipSlot.WEAPON, Element.WIND, Rarity.LING, 16, 0, 0, 5),
        EquipDef("eq_trident", "分水刺", EquipSlot.WEAPON, Element.WATER, Rarity.LING, 15, 2, 0, 3),
        // ── 护甲（主加防/血） ──
        EquipDef("eq_armor", "玄龟甲", EquipSlot.ARMOR, Element.WATER, Rarity.LING, 0, 16, 30, 0),
        EquipDef("eq_robe", "朱雀羽衣", EquipSlot.ARMOR, Element.FIRE, Rarity.LING, 0, 12, 24, 2),
        // ── 饰品（少量全能 + 高速度/暴击倾向） ──
        EquipDef("eq_ring", "雷罡环", EquipSlot.ACCESSORY, Element.THUNDER, Rarity.XIAN, 8, 4, 10, 6),
        EquipDef("eq_pendant", "避水珠", EquipSlot.ACCESSORY, Element.WATER, Rarity.LING, 0, 6, 20, 4),
        EquipDef("eq_charm", "五雷符", EquipSlot.ACCESSORY, Element.THUNDER, Rarity.LING, 10, 0, 0, 8),
    )

    fun byId(id: String): EquipDef? = all.firstOrNull { it.id == id }
}
