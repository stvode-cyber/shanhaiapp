package com.shanhai.core.data

import com.shanhai.core.model.*

/**
 * 起始妖池（切片版，17 只，覆盖七行 / 四类别 / 四稀有度，取自 GDD 代表妖）。
 * 完整 100 只按同结构扩展即可。
 */
object MonsterPool {
    private fun m(
        id: String, name: String, category: MonsterCategory, element: Element, rarity: Rarity,
        atk: Int, def: Int, hp: Int, spd: Int, skillIds: List<String>,
        evolveFrom: String? = null, evolveTo: String? = null, isFestival: Boolean = false,
        desc: String = "", clan: String? = null,
    ): Monster = Monster(
        id, name, category, element, rarity, atk, def, hp, spd,
        SkillLibrary.pick(skillIds), evolveFrom, evolveTo, isFestival, desc, clan,
    )

    val all: List<Monster> = listOf(
        // ===== 精（物精 / 草木精） =====
        m("jing_saozhou", "扫帚精", MonsterCategory.JING, Element.WOOD, Rarity.FAN,
            42, 38, 95, 50, listOf("s_qingmu", "s_luoshi", "s_jinzhong", "s_kunyao"), desc = "成精的破扫帚，呆萌胆小。"),
        m("jing_denglong", "灯笼精", MonsterCategory.JING, Element.FIRE, Rarity.LING,
            50, 42, 100, 55, listOf("s_fentian", "s_xuming", "s_xuanbing", "s_dingshen"), desc = "挂灯成妖，暖光护体。"),
        m("jing_tongjing", "铜镜精", MonsterCategory.JING, Element.METAL, Rarity.LING,
            54, 50, 105, 48, listOf("s_liekong", "s_ruijin", "s_fanzhen", "s_wanjian"), desc = "古镜映妖，反光伤敌。"),

        // ===== 怪（异兽凶兽） =====
        m("guai_nianshou", "年兽", MonsterCategory.GUAI, Element.EARTH, Rarity.SHEN,
            78, 70, 160, 60, listOf("s_luoshi", "s_houtu", "s_jinzhong", "s_xueshi"), isFestival = true, desc = "春节限定凶兽，无进化。"),
        m("guai_taotie", "饕餮", MonsterCategory.GUAI, Element.WATER, Rarity.SHEN,
            80, 66, 150, 58, listOf("s_jingtao", "s_hantan", "s_ganlin", "s_xueshi"), desc = "贪食巨兽，吞天噬地。"),
        m("guai_kuiniu", "夔牛", MonsterCategory.GUAI, Element.THUNDER, Rarity.XIAN,
            72, 60, 140, 65, listOf("s_jiuxiao", "s_wulei", "s_gangfeng", "s_jifeng"), desc = "独足雷兽，踏地生雷。"),

        // ===== 妖（动物成妖） =====
        m("yao_she", "蛇妖", MonsterCategory.YAO, Element.WATER, Rarity.FAN,
            46, 40, 100, 62, listOf("s_jingtao", "s_qingmu2", "s_kunyao", "s_ganlin"), evolveTo = "yao_jiao", desc = "初成妖的青蛇。"),
        m("yao_jiao", "蛟", MonsterCategory.YAO, Element.WATER, Rarity.LING,
            60, 52, 130, 70, listOf("s_hantan", "s_jingtao", "s_ganlin", "s_xuanbing"), evolveFrom = "yao_she", evolveTo = "yao_long", desc = "渡劫将化龙。"),
        m("yao_long", "龙", MonsterCategory.YAO, Element.WATER, Rarity.SHEN,
            88, 72, 170, 80, listOf("s_hantan", "s_jingtao", "s_ganlin", "s_wulei"), evolveFrom = "yao_jiao", desc = "司水之神兽。"),
        m("yao_hu", "狐妖", MonsterCategory.YAO, Element.FIRE, Rarity.FAN,
            48, 38, 96, 72, listOf("s_fentian", "s_dingshen", "s_xuming", "s_ganlin"), evolveTo = "yao_yaohu", desc = "山野狐妖，善惑。", clan = "fox"),
        m("yao_yaohu", "妖狐", MonsterCategory.YAO, Element.FIRE, Rarity.LING,
            62, 46, 118, 82, listOf("s_sanmei", "s_fentian", "s_dingshen", "s_xuming"), evolveFrom = "yao_hu", evolveTo = "yao_jiuwei", desc = "修为使然，魅惑更甚。", clan = "fox"),
        m("yao_jiuwei", "九尾狐", MonsterCategory.YAO, Element.FIRE, Rarity.SHEN,
            86, 58, 150, 95, listOf("s_sanmei", "s_fentian", "s_dingshen", "s_ganlin"), evolveFrom = "yao_yaohu", desc = "青丘之主，倾国倾城。", clan = "fox"),
        m("yao_fengli", "风狸", MonsterCategory.YAO, Element.WIND, Rarity.LING,
            56, 44, 104, 88, listOf("s_gangfeng", "s_jifeng", "s_dingshen", "s_ganlin"), desc = "御风而行的灵狸。"),

        // ===== 灵兽（祥瑞） =====
        m("lingshou_qilin", "麒麟", MonsterCategory.LINGSHOU, Element.EARTH, Rarity.SHEN,
            84, 74, 165, 70, listOf("s_houtu", "s_luoshi", "s_jinzhong", "s_qinhe"), desc = "仁兽祥瑞，镇邪纳福。"),
        m("lingshou_baize", "白泽", MonsterCategory.LINGSHOU, Element.METAL, Rarity.XIAN,
            70, 64, 145, 66, listOf("s_ruijin", "s_liekong", "s_wanjian", "s_dingshen"), desc = "通晓万物之兽。"),
        m("lingshou_zhuque", "朱雀", MonsterCategory.LINGSHOU, Element.FIRE, Rarity.SHEN,
            90, 60, 150, 92, listOf("s_zhuque", "s_jinwu", "s_sanmei", "s_fentian"), desc = "四神兽之一，浴火重生。"),

        // ===== 四神兽补全（青龙·白虎·玄武） =====
        m("lingshou_qinglong", "青龙", MonsterCategory.LINGSHOU, Element.WOOD, Rarity.SHEN,
            86, 70, 160, 84, listOf("s_qilin", "s_wanmu", "s_qingmu2", "s_luoshi"), desc = "四神兽之一，东方木德之尊。"),
        m("lingshou_baihu", "白虎", MonsterCategory.LINGSHOU, Element.METAL, Rarity.SHEN,
            92, 68, 155, 90, listOf("s_baihu", "s_jingang", "s_ruijin", "s_liekong"), desc = "四神兽之一，西方金戈之主。"),

        // ===== 龟族进化链：龟妖 → 灵龟 → 玄武 =====
        m("gui_guijia", "龟妖", MonsterCategory.GUAI, Element.WATER, Rarity.FAN,
            44, 52, 130, 28, listOf("s_jingtao", "s_xuanbing", "s_jinzhong", "s_ganlin"), evolveTo = "gui_linggui", desc = "背甲初成的笨拙水妖。"),
        m("gui_linggui", "灵龟", MonsterCategory.GUAI, Element.WATER, Rarity.LING,
            58, 66, 165, 36, listOf("s_hantan", "s_xuanbing", "s_jinzhong", "s_ganlin"), evolveFrom = "gui_guijia", evolveTo = "gui_xuanwu", desc = "通灵老龟，负碑而行。"),
        m("gui_xuanwu", "玄武", MonsterCategory.GUAI, Element.WATER, Rarity.SHEN,
            82, 78, 200, 50, listOf("s_xuanwu", "s_shuimu", "s_hantan", "s_jinzhong"), evolveFrom = "gui_linggui", desc = "四神兽之一，龟蛇合体，镇北方水德。"),

        // ===== 凤族进化链：灵鸟 → 青鸾 → 凤凰 =====
        m("niao_lingniao", "灵鸟", MonsterCategory.YAO, Element.FIRE, Rarity.FAN,
            45, 36, 92, 74, listOf("s_fentian", "s_dingshen", "s_ganlin", "s_jifeng"), evolveTo = "niao_qingluan", desc = "羽毛初染焰色的火禽。"),
        m("niao_qingluan", "青鸾", MonsterCategory.YAO, Element.FIRE, Rarity.LING,
            60, 46, 116, 84, listOf("s_sanmei", "s_fentian", "s_jifeng", "s_ganlin"), evolveFrom = "niao_lingniao", evolveTo = "niao_fenghuang", desc = "五色备举，将化凤凰。"),
        m("niao_fenghuang", "凤凰", MonsterCategory.YAO, Element.FIRE, Rarity.SHEN,
            88, 56, 148, 96, listOf("s_sanmei", "s_fentian", "s_jifeng", "s_ganlin"), evolveFrom = "niao_qingluan", desc = "百鸟之王，浴火不死。"),

        // ===== 凶兽 / 精怪补全 =====
        m("guai_xiangliu", "相柳", MonsterCategory.GUAI, Element.WATER, Rarity.SHEN,
            84, 64, 158, 70, listOf("s_jingtao", "s_hantan", "s_ganlin", "s_xueshi"), desc = "九首毒蛇，水淹九州。"),
        m("jing_shuijing", "水精", MonsterCategory.JING, Element.WATER, Rarity.LING,
            52, 46, 108, 52, listOf("s_jingtao", "s_xuanbing", "s_dingshen", "s_kunyao"), desc = "凝水而成的精灵。"),

        // ===== 狼妖进化链：狼妖 → 赤焰狼 → 天狼（火·妖） =====
        m("lang_yao", "狼妖", MonsterCategory.YAO, Element.FIRE, Rarity.FAN,
            50, 38, 98, 66, listOf("s_fentian", "s_dingshen", "s_gangfeng", "s_ganlin"), evolveTo = "lang_chi", desc = "荒野独行的火毛狼妖。"),
        m("lang_chi", "赤焰狼", MonsterCategory.YAO, Element.FIRE, Rarity.LING,
            64, 50, 126, 74, listOf("s_sanmei", "s_fentian", "s_gangfeng", "s_dingshen"), evolveFrom = "lang_yao", evolveTo = "lang_tianlang", desc = "烈焰缠身的头狼。"),
        m("lang_tianlang", "天狼", MonsterCategory.YAO, Element.FIRE, Rarity.SHEN,
            90, 60, 158, 92, listOf("s_sanmei", "s_fentian", "s_wulei", "s_gangfeng"), evolveFrom = "lang_chi", desc = "坠星之狼，主兵戈。"),

        // ===== 鲤妖进化链：鲤妖 → 龙鲤 → 鳌龙（水·妖，止于仙） =====
        m("li_yao", "鲤妖", MonsterCategory.YAO, Element.WATER, Rarity.FAN,
            44, 42, 102, 58, listOf("s_jingtao", "s_xuanbing", "s_kunyao", "s_ganlin"), evolveTo = "li_li", desc = "溪中赤鲤，逆流经年。"),
        m("li_li", "龙鲤", MonsterCategory.YAO, Element.WATER, Rarity.LING,
            58, 54, 132, 66, listOf("s_hantan", "s_jingtao", "s_ganlin", "s_xuanbing"), evolveFrom = "li_yao", evolveTo = "li_ao", desc = "额生龙纹，将化蛟。"),
        m("li_ao", "鳌龙", MonsterCategory.YAO, Element.WATER, Rarity.XIAN,
            72, 66, 150, 64, listOf("s_hantan", "s_jingtao", "s_luoshi", "s_wulei"), evolveFrom = "li_li", desc = "背负仙山的巨鲤之灵。"),

        // ===== 鼠妖 2 段链：鼠妖 → 飞鼠精（风·妖） =====
        m("shu_yao", "鼠妖", MonsterCategory.YAO, Element.WIND, Rarity.FAN,
            42, 34, 88, 84, listOf("s_jifeng", "s_gangfeng", "s_dingshen", "s_ganlin"), evolveTo = "shu_fei", desc = "洞中偷油的小妖。"),
        m("shu_fei", "飞鼠精", MonsterCategory.YAO, Element.WIND, Rarity.LING,
            54, 44, 100, 92, listOf("s_jifeng", "s_gangfeng", "s_qinhe", "s_dingshen"), evolveFrom = "shu_yao", desc = "胁生双翼，乘风而行。"),

        // ===== 蛙妖 2 段链：蛙妖 → 蟾精（水/土·精） =====
        m("wa_yao", "蛙妖", MonsterCategory.JING, Element.WATER, Rarity.FAN,
            38, 50, 120, 40, listOf("s_jingtao", "s_xuanbing", "s_jinzhong", "s_ganlin"), evolveTo = "wa_chan", desc = "池塘鼓腹的笨妖。"),
        m("wa_chan", "蟾精", MonsterCategory.JING, Element.EARTH, Rarity.LING,
            50, 64, 150, 44, listOf("s_houtu", "s_luoshi", "s_jinzhong", "s_xuanbing"), evolveFrom = "wa_yao", desc = "三足金蟾，吐纳灵气。"),

        // ===== 精怪补全（土系防御向） =====
        m("jing_shanyao", "山魈精", MonsterCategory.JING, Element.EARTH, Rarity.FAN,
            40, 48, 116, 46, listOf("s_luoshi", "s_houtu", "s_jinzhong", "s_kunyao"), desc = "山间独足怪，力大。"),
        m("jing_shijing", "石精", MonsterCategory.JING, Element.EARTH, Rarity.LING,
            52, 64, 140, 42, listOf("s_luoshi", "s_houtu", "s_jinzhong", "s_fanzhen"), desc = "顽石吸纳日月所化。"),

        // ===== 凶兽补全（四凶等） =====
        m("guai_biqiu", "蜚", MonsterCategory.GUAI, Element.EARTH, Rarity.XIAN,
            74, 58, 140, 60, listOf("s_luoshi", "s_houtu", "s_jiuxiao", "s_xueshi"), desc = "行过之处草木尽枯。"),
        m("guai_taowu", "梼杌", MonsterCategory.GUAI, Element.EARTH, Rarity.SHEN,
            84, 76, 175, 56, listOf("s_luoshi", "s_houtu", "s_wanjian", "s_xueshi"), desc = "四凶之一，顽凶难驯。"),
        m("guai_hundun", "混沌", MonsterCategory.GUAI, Element.WIND, Rarity.SHEN,
            82, 70, 168, 78, listOf("s_jifeng", "s_gangfeng", "s_wulei", "s_ganlin"), desc = "四凶之一，无七窍而识万物。"),
        m("guai_qiongqi", "穷奇", MonsterCategory.GUAI, Element.THUNDER, Rarity.SHEN,
            88, 64, 150, 86, listOf("s_wulei", "s_jiuxiao", "s_gangfeng", "s_xueshi"), desc = "四凶之一，状如虎翼，啸如雷。"),

        // ===== 妖（动物成妖）补全 =====
        m("yao_ma", "马妖", MonsterCategory.YAO, Element.WIND, Rarity.FAN,
            48, 36, 96, 86, listOf("s_jifeng", "s_gangfeng", "s_dingshen", "s_ganlin"), desc = "日行千里的骏马成妖。"),
        m("yao_niu", "牛妖", MonsterCategory.YAO, Element.EARTH, Rarity.LING,
            60, 60, 150, 44, listOf("s_luoshi", "s_houtu", "s_jinzhong", "s_fanzhen"), desc = "力能扛鼎的青牛。"),
        m("yao_yu", "鱼妖", MonsterCategory.YAO, Element.WATER, Rarity.FAN,
            44, 40, 104, 62, listOf("s_jingtao", "s_xuanbing", "s_kunyao", "s_ganlin"), desc = "戏水的锦鳞小妖。"),
        m("niao_kongque", "孔雀", MonsterCategory.YAO, Element.FIRE, Rarity.LING,
            64, 48, 120, 84, listOf("s_sanmei", "s_fentian", "s_jifeng", "s_dingshen"), desc = "翎羽流火的灵禽。"),
        m("niao_zhen", "鸩", MonsterCategory.YAO, Element.WOOD, Rarity.LING,
            62, 46, 116, 80, listOf("s_qingmu", "s_qingmu2", "s_dingshen", "s_kunyao"), desc = "羽含剧毒的妖鸟。"),

        // ===== 灵兽补全（祥瑞） =====
        m("lingshou_xiezhi", "獬豸", MonsterCategory.LINGSHOU, Element.METAL, Rarity.XIAN,
            72, 66, 145, 64, listOf("s_ruijin", "s_liekong", "s_wanjian", "s_dingshen"), desc = "能辨曲直的法兽。"),
        m("lingshou_tianma", "天马", MonsterCategory.LINGSHOU, Element.WIND, Rarity.XIAN,
            66, 52, 130, 96, listOf("s_jifeng", "s_gangfeng", "s_qinhe", "s_ganlin"), desc = "踏空而行的祥瑞。"),
        m("lingshou_zouyu", "驺虞", MonsterCategory.LINGSHOU, Element.WOOD, Rarity.SHEN,
            84, 70, 160, 80, listOf("s_qingmu2", "s_qingmu", "s_luoshi", "s_qinhe"), desc = "白质黑纹，仁兽也。"),

        // ===== 燕妖进化链：燕妖 → 玄鸟 → 金乌（火·妖） =====
        m("niao_yan", "燕妖", MonsterCategory.YAO, Element.FIRE, Rarity.FAN,
            46, 36, 96, 82, listOf("s_fentian", "s_dingshen", "s_jifeng", "s_ganlin"), evolveTo = "niao_xuanniao", desc = "檐下衔泥的火羽小妖。"),
        m("niao_xuanniao", "玄鸟", MonsterCategory.YAO, Element.FIRE, Rarity.LING,
            60, 48, 124, 86, listOf("s_sanmei", "s_fentian", "s_jifeng", "s_dingshen"), evolveFrom = "niao_yan", evolveTo = "niao_jinwu", desc = "天命所归的瑞禽。"),
        m("niao_jinwu", "金乌", MonsterCategory.YAO, Element.FIRE, Rarity.SHEN,
            88, 58, 150, 92, listOf("s_sanmei", "s_fentian", "s_wulei", "s_jifeng"), evolveFrom = "niao_xuanniao", desc = "负日而飞的三足金乌。"),

        // ===== 蟹妖进化链：蟹妖 → 巨蟹精 → 蟹将（水·妖） =====
        m("xie_yao", "蟹妖", MonsterCategory.YAO, Element.WATER, Rarity.FAN,
            44, 46, 112, 44, listOf("s_jingtao", "s_xuanbing", "s_kunyao", "s_ganlin"), evolveTo = "xie_juxie", desc = "横行浅滩的甲妖。"),
        m("xie_juxie", "巨蟹精", MonsterCategory.YAO, Element.WATER, Rarity.LING,
            56, 62, 138, 40, listOf("s_hantan", "s_jingtao", "s_jinzhong", "s_xuanbing"), evolveFrom = "xie_yao", evolveTo = "xie_jiang", desc = "双螯生雷的蟹精。"),
        m("xie_jiang", "蟹将", MonsterCategory.YAO, Element.WATER, Rarity.SHEN,
            82, 74, 170, 46, listOf("s_hantan", "s_jingtao", "s_wanjian", "s_xueshi"), evolveFrom = "xie_juxie", desc = "镇守水府的甲胄之将。"),

        // ===== 鹤妖进化链：鹤妖 → 仙鹤 → 黄鹤（风·妖） =====
        m("he_yao", "鹤妖", MonsterCategory.YAO, Element.WIND, Rarity.FAN,
            48, 38, 102, 80, listOf("s_jifeng", "s_gangfeng", "s_dingshen", "s_ganlin"), evolveTo = "he_xian", desc = "芦荡中独步的妖鹤。"),
        m("he_xian", "仙鹤", MonsterCategory.YAO, Element.WIND, Rarity.LING,
            60, 48, 126, 86, listOf("s_jifeng", "s_gangfeng", "s_qinhe", "s_dingshen"), evolveFrom = "he_yao", evolveTo = "he_huang", desc = "羽化将仙的灵鹤。"),
        m("he_huang", "黄鹤", MonsterCategory.YAO, Element.WIND, Rarity.SHEN,
            86, 58, 152, 92, listOf("s_jifeng", "s_gangfeng", "s_qinhe", "s_wulei"), evolveFrom = "he_xian", desc = "一去不还的乘云黄鹤。"),

        // ===== 虎妖 2 段链：虎妖 → 彪（金·妖） =====
        m("hu_yao", "虎妖", MonsterCategory.YAO, Element.METAL, Rarity.FAN,
            52, 42, 118, 66, listOf("s_ruijin", "s_liekong", "s_dingshen", "s_ganlin"), evolveTo = "hu_biao", desc = "啸动山岗的猛妖。"),
        m("hu_biao", "彪", MonsterCategory.YAO, Element.METAL, Rarity.LING,
            66, 56, 140, 72, listOf("s_ruijin", "s_liekong", "s_wanjian", "s_dingshen"), evolveFrom = "hu_yao", desc = "吊睛白额，虎之小弟。"),

        // ===== 兔妖 2 段链：兔妖 → 玉兔（金·妖，月宫） =====
        m("tu_yao", "兔妖", MonsterCategory.YAO, Element.METAL, Rarity.FAN,
            40, 36, 92, 84, listOf("s_ruijin", "s_jifeng", "s_dingshen", "s_ganlin"), evolveTo = "tu_yutu", desc = "捣药成妖的雪团。"),
        m("tu_yutu", "玉兔", MonsterCategory.YAO, Element.METAL, Rarity.XIAN,
            64, 52, 128, 88, listOf("s_ruijin", "s_jifeng", "s_qinhe", "s_dingshen"), evolveFrom = "tu_yao", desc = "广寒宫中捣仙药的玉兔。"),

        // ===== 猪妖 2 段链：猪妖 → 豪彘（土·妖） =====
        m("zhu_yao", "猪妖", MonsterCategory.YAO, Element.EARTH, Rarity.FAN,
            46, 50, 120, 40, listOf("s_luoshi", "s_houtu", "s_jinzhong", "s_fanzhen"), evolveTo = "zhu_haozhi", desc = "贪食嗜睡的肥妖。"),
        m("zhu_haozhi", "豪彘", MonsterCategory.YAO, Element.EARTH, Rarity.LING,
            58, 62, 144, 44, listOf("s_luoshi", "s_houtu", "s_jinzhong", "s_fanzhen"), evolveFrom = "zhu_yao", desc = "拱石为巢的刚鬣之妖。"),

        // ===== 猴妖 2 段链：猴妖 → 灵明石猴（木·妖） =====
        m("hou_yao", "猴妖", MonsterCategory.YAO, Element.WOOD, Rarity.FAN,
            50, 40, 100, 82, listOf("s_qingmu", "s_qingmu2", "s_dingshen", "s_ganlin"), evolveTo = "hou_lingming", desc = "攀枝盗桃的灵猴。"),
        m("hou_lingming", "灵明石猴", MonsterCategory.YAO, Element.WOOD, Rarity.SHEN,
            86, 64, 150, 90, listOf("s_qingmu", "s_qingmu2", "s_wanjian", "s_jifeng"), evolveFrom = "hou_yao", desc = "通变化、识天时之石猴。"),

        // ===== 犬妖 2 段链：犬妖 → 哮天犬（金·妖） =====
        m("quan_yao", "犬妖", MonsterCategory.YAO, Element.METAL, Rarity.FAN,
            50, 40, 104, 78, listOf("s_ruijin", "s_liekong", "s_gangfeng", "s_dingshen"), evolveTo = "quan_xiaotian", desc = "看家成妖的土狗。"),
        m("quan_xiaotian", "哮天犬", MonsterCategory.YAO, Element.METAL, Rarity.SHEN,
            88, 60, 148, 94, listOf("s_ruijin", "s_liekong", "s_wanjian", "s_gangfeng"), evolveFrom = "quan_yao", desc = "随主擒妖的细腰神犬。"),

        // ===== 虫妖 2 段链：虫妖 → 蝶妖（风·妖） =====
        m("chong_yao", "虫妖", MonsterCategory.YAO, Element.WIND, Rarity.FAN,
            40, 34, 88, 80, listOf("s_jifeng", "s_gangfeng", "s_dingshen", "s_ganlin"), evolveTo = "die_yao", desc = "草间蠕动的小妖。"),
        m("die_yao", "蝶妖", MonsterCategory.YAO, Element.WIND, Rarity.LING,
            52, 42, 98, 88, listOf("s_jifeng", "s_gangfeng", "s_qinhe", "s_dingshen"), evolveFrom = "chong_yao", desc = "采花成妖的彩翼精灵。"),

        // ===== 鹰妖 2 段链：鹰妖 → 神鹰（风·妖） =====
        m("ying_yao", "鹰妖", MonsterCategory.YAO, Element.WIND, Rarity.FAN,
            52, 38, 100, 92, listOf("s_jifeng", "s_gangfeng", "s_dingshen", "s_ganlin"), evolveTo = "ying_shen", desc = "盘旋觅食的猛禽妖。"),
        m("ying_shen", "神鹰", MonsterCategory.YAO, Element.WIND, Rarity.SHEN,
            88, 56, 146, 98, listOf("s_jifeng", "s_gangfeng", "s_wulei", "s_dingshen"), evolveFrom = "ying_yao", desc = "搏击长空的千里神鹰。"),

        // ===== 鲲鹏 2 段链：鲲 → 鹏（水 → 风·妖） =====
        m("kun_yao", "鲲", MonsterCategory.YAO, Element.WATER, Rarity.FAN,
            46, 48, 130, 44, listOf("s_jingtao", "s_hantan", "s_kunyao", "s_xuanbing"), evolveTo = "peng_yao", desc = "北冥之鱼，其大不知其几千里。"),
        m("peng_yao", "鹏", MonsterCategory.YAO, Element.WIND, Rarity.SHEN,
            86, 58, 150, 96, listOf("s_jifeng", "s_gangfeng", "s_wulei", "s_qinhe"), evolveFrom = "kun_yao", desc = "怒而飞，其翼若垂天之云。"),

        // ===== 蟒妖 2 段链：蟒妖 → 腾蛇（火·妖） =====
        m("mang_yao", "蟒妖", MonsterCategory.YAO, Element.FIRE, Rarity.FAN,
            50, 42, 112, 62, listOf("s_fentian", "s_sanmei", "s_dingshen", "s_ganlin"), evolveTo = "mang_tengshe", desc = "缠树吐信的巨蟒。"),
        m("mang_tengshe", "腾蛇", MonsterCategory.YAO, Element.FIRE, Rarity.SHEN,
            90, 60, 156, 88, listOf("s_sanmei", "s_fentian", "s_wulei", "s_jifeng"), evolveFrom = "mang_yao", desc = "乘雾而游的火鳞神蛇。"),

        // ===== 灵兽补全（龙子·瑞兽·神兽） =====
        m("lingshou_pixiu", "貔貅", MonsterCategory.LINGSHOU, Element.METAL, Rarity.SHEN,
            84, 72, 168, 58, listOf("s_ruijin", "s_liekong", "s_wanjian", "s_jinzhong"), desc = "有嘴无肛，吞财不泄。"),
        m("lingshou_suanni", "狻猊", MonsterCategory.LINGSHOU, Element.FIRE, Rarity.XIAN,
            72, 60, 140, 70, listOf("s_sanmei", "s_fentian", "s_liekong", "s_dingshen"), desc = "好烟好坐的龙子。"),
        m("lingshou_jiaotu", "椒图", MonsterCategory.LINGSHOU, Element.WATER, Rarity.XIAN,
            66, 66, 144, 60, listOf("s_jingtao", "s_xuanbing", "s_hantan", "s_jinzhong"), desc = "性好闭，衔环守门。"),
        m("lingshou_pulao", "蒲牢", MonsterCategory.LINGSHOU, Element.WATER, Rarity.XIAN,
            68, 58, 138, 72, listOf("s_jingtao", "s_wulei", "s_jiuxiao", "s_gangfeng"), desc = "好鸣好吼的龙子。"),
        m("lingshou_qiuniu", "囚牛", MonsterCategory.LINGSHOU, Element.WOOD, Rarity.XIAN,
            66, 62, 136, 66, listOf("s_qingmu", "s_qingmu2", "s_luoshi", "s_qinhe"), desc = "好音好乐的龙子。"),
        m("lingshou_yazi", "睚眦", MonsterCategory.LINGSHOU, Element.METAL, Rarity.XIAN,
            74, 60, 132, 68, listOf("s_ruijin", "s_liekong", "s_wanjian", "s_gangfeng"), desc = "嗜杀好斗的龙子。"),
        m("lingshou_chaofeng", "嘲风", MonsterCategory.LINGSHOU, Element.WIND, Rarity.XIAN,
            68, 56, 134, 82, listOf("s_jifeng", "s_gangfeng", "s_wulei", "s_qinhe"), desc = "好险好望的龙子。"),
        m("lingshou_dangkang", "当康", MonsterCategory.LINGSHOU, Element.EARTH, Rarity.XIAN,
            66, 64, 146, 58, listOf("s_luoshi", "s_houtu", "s_jinzhong", "s_fanzhen"), desc = "其鸣如雷，兆丰年的瑞兽。"),
        m("lingshou_yingzhao", "英招", MonsterCategory.LINGSHOU, Element.WIND, Rarity.XIAN,
            70, 58, 138, 84, listOf("s_jifeng", "s_gangfeng", "s_qinhe", "s_ganlin"), desc = "马身人面，司帝之平圃。"),
        m("lingshou_luwu", "陆吾", MonsterCategory.LINGSHOU, Element.EARTH, Rarity.SHEN,
            84, 74, 172, 64, listOf("s_luoshi", "s_houtu", "s_wanjian", "s_jinzhong"), desc = "虎身九尾，守昆仑之丘。"),
        m("lingshou_kaiming", "开明兽", MonsterCategory.LINGSHOU, Element.METAL, Rarity.SHEN,
            86, 76, 170, 66, listOf("s_ruijin", "s_liekong", "s_wanjian", "s_jinzhong"), desc = "兽身九首，司昆仑之扉。"),
        m("lingshou_zhulong", "烛龙", MonsterCategory.LINGSHOU, Element.FIRE, Rarity.SHEN,
            88, 70, 174, 72, listOf("s_sanmei", "s_fentian", "s_wulei", "s_jiuxiao"), desc = "衔烛而照幽都，司昼夜四时。"),

        // ===== 精补全（五行精灵） =====
        m("jing_huajing", "花精", MonsterCategory.JING, Element.WOOD, Rarity.FAN,
            40, 44, 104, 56, listOf("s_qingmu", "s_qingmu2", "s_kunyao", "s_ganlin"), desc = "沾露而生的花间精灵。"),
        m("jing_songjing", "松精", MonsterCategory.JING, Element.WOOD, Rarity.FAN,
            42, 48, 112, 42, listOf("s_qingmu", "s_luoshi", "s_jinzhong", "s_kunyao"), desc = "千年古松所化。"),
        m("jing_mujing", "木精", MonsterCategory.JING, Element.WOOD, Rarity.LING,
            54, 58, 132, 50, listOf("s_qingmu", "s_qingmu2", "s_luoshi", "s_jinzhong"), desc = "林深之处游走之精。"),
        m("jing_huoling", "火灵", MonsterCategory.JING, Element.FIRE, Rarity.LING,
            58, 48, 118, 64, listOf("s_fentian", "s_sanmei", "s_dingshen", "s_ganlin"), desc = "燧石迸火所凝之灵。"),
        m("jing_leijing", "雷精", MonsterCategory.JING, Element.THUNDER, Rarity.LING,
            56, 52, 120, 72, listOf("s_wulei", "s_jiuxiao", "s_gangfeng", "s_dingshen"), desc = "云层惊雷所聚之精。"),
        m("jing_xuejing", "雪精", MonsterCategory.JING, Element.WATER, Rarity.LING,
            54, 56, 124, 58, listOf("s_xuanbing", "s_jingtao", "s_hantan", "s_kunyao"), desc = "寒冬飞雪凝成的精灵。"),
        m("jing_shajing", "沙精", MonsterCategory.JING, Element.EARTH, Rarity.FAN,
            42, 46, 108, 52, listOf("s_luoshi", "s_houtu", "s_jinzhong", "s_fanzhen"), desc = "大漠流沙聚成的妖精。"),

        // ===== 怪补全（上古凶怪） =====
        m("guai_yayu", "猰貐", MonsterCategory.GUAI, Element.WATER, Rarity.XIAN,
            74, 60, 146, 64, listOf("s_jingtao", "s_hantan", "s_wulei", "s_xueshi"), desc = "状如牛而赤身，食人迅疾。"),
        m("guai_jiuying", "九婴", MonsterCategory.GUAI, Element.WATER, Rarity.SHEN,
            86, 64, 168, 70, listOf("s_jingtao", "s_hantan", "s_wulei", "s_jiuxiao"), desc = "九首皆有口，吐水喷火。"),
        m("guai_zaochi", "凿齿", MonsterCategory.GUAI, Element.METAL, Rarity.XIAN,
            76, 62, 138, 60, listOf("s_ruijin", "s_liekong", "s_wanjian", "s_gangfeng"), desc = "齿长三尺，持盾而战的巨人。"),
        m("guai_wuzhiqi", "无支祁", MonsterCategory.GUAI, Element.WATER, Rarity.SHEN,
            84, 72, 176, 62, listOf("s_jingtao", "s_hantan", "s_wanjian", "s_xueshi"), desc = "淮水之怪，形若猿猴，力逾九象。"),
    )

    private val byId: Map<String, Monster> = all.associateBy { it.id }

    fun byId(id: String): Monster? = byId[id]

    fun byName(name: String): Monster? = all.firstOrNull { it.name == name }

    /** 按稀有度过滤 */
    fun byRarity(rarity: Rarity): List<Monster> = all.filter { it.rarity == rarity }

    /** 随机遭遇一只（默认权重：凡>灵>仙>神） */
    fun randomEncounter(random: () -> Double = ::defaultRandom): Monster {
        val weights = all.map { monster ->
            when (monster.rarity) {
                Rarity.FAN -> 45.0
                Rarity.LING -> 35.0
                Rarity.XIAN -> 15.0
                Rarity.SHEN -> 5.0
            }
        }
        val total = weights.sum()
        var r = random() * total
        for (i in all.indices) {
            r -= weights[i]
            if (r <= 0.0) return all[i]
        }
        return all.last()
    }

    /** 新手三选一推荐 */
    val starters: List<Monster> = listOf(
        byId("yao_she")!!, byId("jing_saozhou")!!, byId("yao_hu")!!,
    )

    /** 全量妖 id 集合（供 ban/pick 等需要完整池的引擎复用）。 */
    val ALL_IDS: Set<String> = all.map { it.id }.toSet()
}

/** 公共随机源（0..1），stdlib 全平台可用 */
fun defaultRandom(): Double = kotlin.random.Random.nextDouble()
