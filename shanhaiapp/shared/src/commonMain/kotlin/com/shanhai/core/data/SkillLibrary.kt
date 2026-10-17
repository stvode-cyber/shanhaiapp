package com.shanhai.core.data

import com.shanhai.core.model.*

/**
 * 中式技能池（取自 GDD 七大类，切片仅收代表性技能）。
 * 实际游戏扩到 73 式；此处保证每类/每行都有代表，足够驱动核心循环。
 */
object SkillLibrary {
    private val all: List<Skill> = listOf(
        // 攻伐
        Skill("s_liekong", "裂空斩", SkillType.ATTACK, Element.METAL, 78, true, 0.05, "金行利刃斩击"),
        Skill("s_jingtao", "惊涛拍岸", SkillType.ATTACK, Element.WATER, 75, true, 0.0, "水行巨浪拍击"),
        Skill("s_fentian", "焚天烈焰", SkillType.ATTACK, Element.FIRE, 80, true, 0.05, "火行焚烧"),
        Skill("s_wanjian", "万剑归宗", SkillType.ATTACK, Element.METAL, 88, true, 0.1, "金行剑阵"),
        Skill("s_jiuxiao", "九霄雷劫", SkillType.ATTACK, Element.THUNDER, 85, true, 0.08, "雷行天罚"),
        Skill("s_qingmu", "青木缠", SkillType.ATTACK, Element.WOOD, 70, true, 0.0, "木行缠击"),
        Skill("s_luoshi", "落石术", SkillType.ATTACK, Element.EARTH, 72, true, 0.0, "土行坠石"),
        Skill("s_gangfeng", "罡风斩", SkillType.ATTACK, Element.WIND, 76, true, 0.06, "风行切割"),
        // 守御
        Skill("s_jinzhong", "金钟罩", SkillType.DEFENSE, Element.METAL, 0, false, 0.0, "提升防御"),
        Skill("s_xuanbing", "玄冰壁", SkillType.DEFENSE, Element.WATER, 0, false, 0.0, "寒冰护壁"),
        Skill("s_fanzhen", "反震荆甲", SkillType.DEFENSE, Element.WOOD, 0, false, 0.0, "反伤护甲"),
        // 术法（控制）
        Skill("s_kunyao", "捆妖索", SkillType.SPELL, Element.WOOD, 0, false, 0.0, "束缚敌人"),
        Skill("s_dingshen", "定身符", SkillType.SPELL, Element.METAL, 0, false, 0.0, "定身控制"),
        // 辅助
        Skill("s_ganlin", "甘霖普润", SkillType.SUPPORT, Element.WATER, 0, false, 0.0, "恢复气血"),
        Skill("s_xuming", "续命灯", SkillType.SUPPORT, Element.FIRE, 0, false, 0.0, "持续回复"),
        // 五行专精
        Skill("s_sanmei", "三昧真火", SkillType.ELEMENT, Element.FIRE, 92, true, 0.1, "火行极致"),
        Skill("s_hantan", "寒潭玄冰", SkillType.ELEMENT, Element.WATER, 90, true, 0.08, "水行极致"),
        Skill("s_wulei", "五雷正法", SkillType.ELEMENT, Element.THUNDER, 94, true, 0.12, "雷行极致"),
        Skill("s_qingmu2", "青木长生", SkillType.ELEMENT, Element.WOOD, 82, true, 0.0, "木行滋养"),
        Skill("s_houtu", "厚土术", SkillType.ELEMENT, Element.EARTH, 84, true, 0.0, "土行厚重"),
        Skill("s_ruijin", "锐金诀", SkillType.ELEMENT, Element.METAL, 86, true, 0.06, "金行锐利"),
        Skill("s_jifeng", "疾风诀", SkillType.ELEMENT, Element.WIND, 83, true, 0.06, "风行迅疾"),
        // 心法被动
        Skill("s_xueshi", "嗜血", SkillType.PASSIVE, Element.FIRE, 0, false, 0.0, "吸血增益"),
        Skill("s_qinhe", "五行亲和", SkillType.PASSIVE, Element.WOOD, 0, false, 0.0, "五行增伤"),
        // —— 以下为扩池至 73 式新增（山海经题材，覆盖 6 大类）——
        // 攻伐 +9
        Skill("s_pojia", "破甲击", SkillType.ATTACK, Element.METAL, 82, true, 0.08, "金行破甲斩击"),
        Skill("s_bingpo", "冰魄寒光", SkillType.ATTACK, Element.WATER, 78, true, 0.06, "水行寒魄击"),
        Skill("s_lieyan", "烈焰焚", SkillType.ATTACK, Element.FIRE, 84, true, 0.08, "火行烈焚烧"),
        Skill("s_leiting", "雷霆万钧", SkillType.ATTACK, Element.THUNDER, 90, true, 0.1, "雷行轰击"),
        Skill("s_wanmu", "万木穿刺", SkillType.ATTACK, Element.WOOD, 76, true, 0.04, "木行穿刺"),
        Skill("s_yanshi", "岩崩", SkillType.ATTACK, Element.EARTH, 80, true, 0.05, "土行坠岩"),
        Skill("s_xuanfeng", "旋风斩", SkillType.ATTACK, Element.WIND, 79, true, 0.07, "风行旋斩"),
        Skill("s_canjian", "残月斩", SkillType.ATTACK, Element.METAL, 86, true, 0.09, "金行月刃"),
        Skill("s_chihuo", "赤火燎原", SkillType.ATTACK, Element.FIRE, 88, true, 0.1, "火行燎原"),
        // 守御 +7
        Skill("s_tieshan", "铁山靠", SkillType.DEFENSE, Element.EARTH, 0, false, 0.0, "土行护体"),
        Skill("s_shuimu", "水幕天华", SkillType.DEFENSE, Element.WATER, 0, false, 0.0, "水行屏障"),
        Skill("s_huoji", "火盾", SkillType.DEFENSE, Element.FIRE, 0, false, 0.0, "火行护盾"),
        Skill("s_muya", "木芽护", SkillType.DEFENSE, Element.WOOD, 0, false, 0.0, "木行护盾"),
        Skill("s_fengqiang", "风墙", SkillType.DEFENSE, Element.WIND, 0, false, 0.0, "风行屏障"),
        Skill("s_leijia", "雷甲", SkillType.DEFENSE, Element.THUNDER, 0, false, 0.0, "雷行护甲"),
        Skill("s_jingang", "金刚不坏", SkillType.DEFENSE, Element.METAL, 0, false, 0.0, "金行坚壁"),
        // 术法（控制）+8
        Skill("s_hunshen", "昏睡咒", SkillType.SPELL, Element.WOOD, 0, false, 0.0, "木行催眠"),
        Skill("s_liuhun", "六魂符", SkillType.SPELL, Element.METAL, 0, false, 0.0, "金行封印"),
        Skill("s_bingfeng", "冰封", SkillType.SPELL, Element.WATER, 0, false, 0.0, "水行冻结"),
        Skill("s_huoyu", "惑心焰", SkillType.SPELL, Element.FIRE, 0, false, 0.0, "火行惑乱"),
        Skill("s_shilei", "摄魂雷", SkillType.SPELL, Element.THUNDER, 0, false, 0.0, "雷行震慑"),
        Skill("s_xuanyun", "眩晕风", SkillType.SPELL, Element.WIND, 0, false, 0.0, "风行眩晕"),
        Skill("s_dunxing", "遁形", SkillType.SPELL, Element.EARTH, 0, false, 0.0, "土行隐遁"),
        Skill("s_suoming", "锁命咒", SkillType.SPELL, Element.WOOD, 0, false, 0.0, "木行禁锢"),
        // 辅助 +8
        Skill("s_hudun", "护盾术", SkillType.SUPPORT, Element.METAL, 0, false, 0.0, "金行护盾"),
        Skill("s_qingxin", "清心咒", SkillType.SUPPORT, Element.WATER, 0, false, 0.0, "水行净化"),
        Skill("s_yangjiu", "养就灵泉", SkillType.SUPPORT, Element.WOOD, 0, false, 0.0, "木行治疗"),
        Skill("s_fuhuo", "回春术", SkillType.SUPPORT, Element.FIRE, 0, false, 0.0, "火行回春"),
        Skill("s_tishen", "替身土偶", SkillType.SUPPORT, Element.EARTH, 0, false, 0.0, "土行替身"),
        Skill("s_jisuz", "疾速咒", SkillType.SUPPORT, Element.WIND, 0, false, 0.0, "风行加速"),
        Skill("s_leibu", "雷部号令", SkillType.SUPPORT, Element.THUNDER, 0, false, 0.0, "雷行增益"),
        Skill("s_jingqi", "精气诀", SkillType.SUPPORT, Element.METAL, 0, false, 0.0, "金行聚气"),
        // 五行专精 +10
        Skill("s_jinwu", "金乌曜日", SkillType.ELEMENT, Element.FIRE, 96, true, 0.12, "火行极致·日曜"),
        Skill("s_xuanwu", "玄武镇海", SkillType.ELEMENT, Element.WATER, 92, true, 0.08, "水行极致·镇海"),
        Skill("s_qinglong", "青龙破浪", SkillType.ELEMENT, Element.WATER, 90, true, 0.08, "水行极致·龙波"),
        Skill("s_baihu", "白虎裂空", SkillType.ELEMENT, Element.METAL, 95, true, 0.12, "金行极致·裂空"),
        Skill("s_zhuque", "朱雀焚天", SkillType.ELEMENT, Element.FIRE, 97, true, 0.14, "火行极致·焚天"),
        Skill("s_qilin", "麒麟圣光", SkillType.ELEMENT, Element.WOOD, 88, true, 0.06, "木行极致·圣光"),
        Skill("s_huanglong", "黄龙厚土", SkillType.ELEMENT, Element.EARTH, 90, true, 0.06, "土行极致·厚土"),
        Skill("s_jiufeng", "九凤雷音", SkillType.ELEMENT, Element.THUNDER, 93, true, 0.1, "雷行极致·凤鸣"),
        Skill("s_taiji", "太极罡风", SkillType.ELEMENT, Element.WIND, 89, true, 0.06, "风行极致·太极"),
        Skill("s_tianlei", "天雷破", SkillType.ELEMENT, Element.THUNDER, 98, true, 0.15, "雷行极致·天罚"),
        // 心法被动 +7
        Skill("s_taimo", "泰魔护体", SkillType.PASSIVE, Element.METAL, 0, false, 0.0, "金行减伤"),
        Skill("s_huigen", "慧根", SkillType.PASSIVE, Element.WOOD, 0, false, 0.0, "木行增疗"),
        Skill("s_wuxing", "五行逆转", SkillType.PASSIVE, Element.FIRE, 0, false, 0.0, "火行反噬"),
        Skill("s_lingshou", "灵兽之威", SkillType.PASSIVE, Element.EARTH, 0, false, 0.0, "土行威压"),
        Skill("s_fengxing", "风行疾", SkillType.PASSIVE, Element.WIND, 0, false, 0.0, "风行增速"),
        Skill("s_shuixi", "水流不息", SkillType.PASSIVE, Element.WATER, 0, false, 0.0, "水行续航"),
        Skill("s_leiling", "雷灵", SkillType.PASSIVE, Element.THUNDER, 0, false, 0.0, "雷行蓄电"),
    )

    private val byId: Map<String, Skill> = all.associateBy { it.id }

    /** 全量技能（供 [com.shanhai.core.data.DataIntegrityEngine] 做定义层引用校验）。 */
    val allSkills: List<Skill> get() = all

    fun get(id: String): Skill = byId[id]
        ?: error("未知技能: $id")

    fun find(id: String): Skill? = byId[id]

    /** 取一组技能（按 id 列表） */
    fun pick(ids: List<String>): List<Skill> = ids.map { get(it) }
}
