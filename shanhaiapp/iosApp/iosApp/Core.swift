import Foundation

// MARK: - 七行元素
enum Element: Int, CaseIterable, Identifiable {
    case metal, wood, earth, water, fire, wind, thunder
    var id: Int { rawValue }
    var label: String {
        switch self {
        case .metal: return "金"
        case .wood: return "木"
        case .earth: return "土"
        case .water: return "水"
        case .fire: return "火"
        case .wind: return "风"
        case .thunder: return "雷"
        }
    }
    static func multiplier(attacker: Element, defender: Element) -> Double {
        if beatsMap[attacker]?.contains(defender) == true { return 1.6 }
        if beatsMap[defender]?.contains(attacker) == true { return 0.6 }
        return 1.0
    }
    static func multiplierLabel(attacker: Element, defender: Element) -> String {
        let m = multiplier(attacker: attacker, defender: defender)
        if m > 1.0 { return "\(attacker.label)克\(defender.label) ×\(m)" }
        if m < 1.0 { return "\(defender.label)克\(attacker.label) ×\(m)" }
        return "无克制 ×1.0"
    }
    private static let beatsMap: [Element: Set<Element>] = [
        .metal: [.wood, .wind], .wood: [.earth, .fire], .earth: [.water, .thunder],
        .water: [.fire, .metal], .fire: [.metal, .wind], .wind: [.earth, .thunder],
        .thunder: [.wood, .water],
    ]
    static func beats(attacker: Element, defender: Element) -> Bool {
        beatsMap[attacker]?.contains(defender) == true
    }
    static func beatenBy(attacker: Element, defender: Element) -> Bool {
        beatsMap[defender]?.contains(attacker) == true
    }
}

// MARK: - 妖怪类别（精/怪/妖/灵兽）克制环 ×1.3
enum MonsterCategory: Int, CaseIterable, Identifiable {
    case jing, guai, yao, lingshou
    var id: Int { rawValue }
    var label: String {
        switch self {
        case .jing: return "精"
        case .guai: return "怪"
        case .yao: return "妖"
        case .lingshou: return "灵兽"
        }
    }
    static func multiplier(attacker: MonsterCategory, defender: MonsterCategory) -> Double {
        let ring: [MonsterCategory: MonsterCategory] = [.yao: .jing, .jing: .guai, .guai: .lingshou, .lingshou: .yao]
        if ring[attacker] == defender { return 1.3 }
        if ring[defender] == attacker { return 1.0 / 1.3 }
        return 1.0
    }
}

// MARK: - 稀有度与成长率
enum Rarity: Int, Identifiable {
    case fan, ling, xian, shen
    var id: Int { rawValue }
    var label: String {
        switch self {
        case .fan: return "凡"
        case .ling: return "灵"
        case .xian: return "仙"
        case .shen: return "神"
        }
    }
    var growth: Double {
        switch self {
        case .fan: return 1.0
        case .ling: return 1.1
        case .xian: return 1.25
        case .shen: return 1.5
        }
    }
}

// MARK: - 技能 / 属性 / 妖怪
enum SkillType { case attack, defense, spell, support, element, passive }

struct Skill: Identifiable {
    let id: String
    let name: String
    let type: SkillType
    let element: Element
    let power: Int
    let isDamage: Bool
    let critBonus: Double
}

struct Stats { let atk: Int; let def: Int; let hp: Int; let spd: Int }

struct Monster: Identifiable {
    let id: String
    let name: String
    let category: MonsterCategory
    let element: Element
    let rarity: Rarity
    let baseAtk, baseDef, baseHp, baseSpd: Int
    let skills: [Skill]
    let evolveFrom: String?
    let evolveTo: String?
    let desc: String
    let isFestival: Bool = false   // 节日限定妖（春节年兽等），用于 LBS 节气刷怪加成
    let clan: String? = nil         // 族群（如 "fox" 狐族），用于阵容羁绊判定；定义数据不入库

    func statsAtLevel(_ level: Int) -> Stats {
        let g = rarity.growth
        let lc = 1.0 + Double(level - 1) * 0.02
        return Stats(
            atk: max(1, Int(Double(baseAtk) * g * lc)),
            def: max(1, Int(Double(baseDef) * g * lc)),
            hp: max(1, Int(Double(baseHp) * g * lc)),
            spd: max(1, Int(Double(baseSpd) * g))
        )
    }
}

// MARK: - 拥有的妖实例（区别于静态 Monster 定义，含养成数据）
struct MonsterInstance: Codable, Identifiable {
    let instanceId: String
    let defId: String
    let level: Int
    let exp: Int
    let breakthrough: Int
    let tribulations: Int
    let talents: [String: Int]   // 天赋树分配：分支名(ATTACK/DEFENSE/SUPPORT)→已点层数，默认空
    let moralChoice: MoralChoice?  // 道德选择标记（镇压SUPPRESS/超度TRANSCEND，GDD 十七）；旧档缺省 nil
    var id: String { instanceId }
    init(instanceId: String, defId: String, level: Int, exp: Int, breakthrough: Int, tribulations: Int = 0, talents: [String: Int] = [:], moralChoice: MoralChoice? = nil) {
        self.instanceId = instanceId; self.defId = defId; self.level = level
        self.exp = exp; self.breakthrough = breakthrough; self.tribulations = tribulations
        self.talents = talents; self.moralChoice = moralChoice
    }
    // 旧存档可能不含 tribulations / talents / moralChoice，解码容错（缺失视为 0 / 空 / nil）
    private enum CodingKeys: String, CodingKey { case instanceId, defId, level, exp, breakthrough, tribulations, talents, moralChoice }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        instanceId = try c.decode(String.self, forKey: .instanceId)
        defId = try c.decode(String.self, forKey: .defId)
        level = try c.decode(Int.self, forKey: .level)
        exp = try c.decode(Int.self, forKey: .exp)
        breakthrough = try c.decode(Int.self, forKey: .breakthrough)
        tribulations = try c.decodeIfPresent(Int.self, forKey: .tribulations) ?? 0
        talents = try c.decodeIfPresent([String: Int].self, forKey: .talents) ?? [:]  // 旧档缺默认空
        moralChoice = try c.decodeIfPresent(MoralChoice.self, forKey: .moralChoice)  // 旧档缺默认 nil
    }
}

// 道德选择（镜像 Kotlin enum class MoralChoice：镇压 SUPPRESS / 超度 TRANSCEND）
enum MoralChoice: String, Codable, CaseIterable {
    case suppress = "SUPPRESS"    // 镇压：强攻
    case transcend = "TRANSCEND"  // 超度：净化
}

// 道德选择属性倍率（镜像 Kotlin StatMods / MoralChoiceEngine.kt）
struct StatMods { let atk: Double; let def: Double; let hp: Double }

// MARK: - 道德选择引擎（镇压/超度 → 有效属性倍率，镜像 MoralChoiceEngine.kt）
struct MoralChoiceEngine {
    static func modifiers(_ choice: MoralChoice?) -> StatMods {
        switch choice {
        case .suppress:  return StatMods(atk: 1.10, def: 1.00, hp: 1.00)   // 镇压：强攻，攻击+10%
        case .transcend: return StatMods(atk: 1.00, def: 1.08, hp: 1.08)   // 超度：净化，防御生命+8%
        default:         return StatMods(atk: 1.00, def: 1.00, hp: 1.00)   // 中性
        }
    }
}

// MARK: - 妖怪养成引擎（等级 / 经验 / 突破 / 进化 / 渡劫）
struct CultivationEngine {
    static let BASE_LEVEL_CAP = 30
    static let MAX_BREAKTHROUGH = 5
    static let LEVEL_CAP_PER_BREAK = 5
    static let STAT_MULT_PER_BREAK = 0.08
    static let EVOLVE_LEVEL_REQ = 25
    static let TRIBULATE_STAT_BONUS = 0.25
    static let TRIBULATE_COST_YAODAN = 200

    static func levelCap(_ inst: MonsterInstance) -> Int { BASE_LEVEL_CAP + inst.breakthrough * LEVEL_CAP_PER_BREAK }
    static func statMult(_ inst: MonsterInstance) -> Double { 1.0 + Double(inst.breakthrough) * STAT_MULT_PER_BREAK }
    static func expForLevel(_ level: Int) -> Int { let lv = level < 1 ? 1 : level; return 100 + (lv - 1) * 25 }

    static func gainExp(_ inst: MonsterInstance, _ amount: Int) -> MonsterInstance {
        guard amount > 0 else { return inst }
        var lvl = inst.level
        var xp = inst.exp + amount
        let cap = levelCap(inst)
        while lvl < cap && xp >= expForLevel(lvl) {
            xp -= expForLevel(lvl)
            lvl += 1
        }
        if lvl >= cap { let top = expForLevel(max(cap - 1, 1)); xp = min(xp, top) }
        return MonsterInstance(instanceId: inst.instanceId, defId: inst.defId, level: lvl, exp: xp, breakthrough: inst.breakthrough)
    }
    static func breakthroughLevelReq(_ currentBreakthrough: Int) -> Int { 20 + currentBreakthrough * 5 }
    static func canBreakthrough(_ inst: MonsterInstance) -> Bool {
        inst.breakthrough < MAX_BREAKTHROUGH && inst.level >= breakthroughLevelReq(inst.breakthrough)
    }
    static func breakthroughCost(_ currentBreakthrough: Int) -> Int { 20 + currentBreakthrough * 15 }
    static func breakthrough(_ inst: MonsterInstance) -> MonsterInstance? {
        guard canBreakthrough(inst) else { return nil }
        return MonsterInstance(instanceId: inst.instanceId, defId: inst.defId, level: inst.level, exp: inst.exp, breakthrough: inst.breakthrough + 1)
    }
    static func canEvolve(_ inst: MonsterInstance) -> Bool {
        guard let def = MonsterPool.byId(inst.defId) else { return false }
        return def.evolveTo != nil && inst.level >= EVOLVE_LEVEL_REQ
    }
    static func evolve(_ inst: MonsterInstance) -> MonsterInstance? {
        guard let def = MonsterPool.byId(inst.defId), let to = def.evolveTo, inst.level >= EVOLVE_LEVEL_REQ else { return nil }
        guard let newDef = MonsterPool.byId(to) else { return nil }
        // 进化后稀有度可能升高，maxTribulations 随之降低；钳制 tribulations 避免非法存档与属性越界
        let newTrib = min(inst.tribulations, maxTribulations(newDef.rarity))
        return MonsterInstance(instanceId: inst.instanceId, defId: to, level: inst.level, exp: inst.exp, breakthrough: inst.breakthrough, tribulations: newTrib)
    }
    // ── 渡劫天劫（养成线终章，保证成功 / 必成） ──
    static func tribulateMult(_ inst: MonsterInstance) -> Double { 1.0 + Double(inst.tribulations) * TRIBULATE_STAT_BONUS }
    static func maxTribulations(_ rarity: Rarity) -> Int { max(0, Rarity.shen.rawValue - rarity.rawValue) }
    static func currentRarity(_ inst: MonsterInstance) -> Rarity? {
        guard let def = MonsterPool.byId(inst.defId) else { return nil }
        let idx = min(def.rarity.rawValue + inst.tribulations, Rarity.shen.rawValue)
        return Rarity(rawValue: idx)
    }
    static func canTribulate(_ inst: MonsterInstance) -> Bool {
        guard let def = MonsterPool.byId(inst.defId) else { return false }
        if inst.tribulations >= maxTribulations(def.rarity) { return false }
        return inst.breakthrough >= MAX_BREAKTHROUGH && inst.level >= levelCap(inst)
    }
    static func tribulateCost(_ inst: MonsterInstance) -> Int { TRIBULATE_COST_YAODAN * (inst.tribulations + 1) }
    static func tribulate(_ inst: MonsterInstance) -> MonsterInstance? {
        guard canTribulate(inst) else { return nil }
        return MonsterInstance(instanceId: inst.instanceId, defId: inst.defId, level: inst.level, exp: inst.exp,
                               breakthrough: inst.breakthrough, tribulations: inst.tribulations + 1)
    }
    static func effectiveStats(_ inst: MonsterInstance) -> Stats? {
        guard let def = MonsterPool.byId(inst.defId) else { return nil }
        let base = def.statsAtLevel(inst.level)
        let mult = statMult(inst) * tribulateMult(inst)
        return Stats(atk: max(1, Int(Double(base.atk) * mult * TalentEngine.atkMult(inst))),
                     def: max(1, Int(Double(base.def) * mult * TalentEngine.defMult(inst))),
                     hp: max(1, Int(Double(base.hp) * mult * TalentEngine.hpMult(inst))),
                     spd: max(1, Int(Double(base.spd) * mult * TalentEngine.spdMult(inst))))
    }
    static func evolutionChain(_ startDefId: String) -> [String] {
        var chain: [String] = []
        guard var cur = MonsterPool.byId(startDefId) else { return chain }
        var seen = Set<String>()
        while !seen.contains(cur.id) {
            chain.append(cur.id); seen.insert(cur.id)
            if let to = cur.evolveTo, let nxt = MonsterPool.byId(to) { cur = nxt } else { break }
        }
        return chain
    }
}

// MARK: - 装备 / 祭炼（GDD 十六·养成线）
enum EquipSlot: String, Codable, CaseIterable { case weapon, armor, accessory }

enum EquipStat: String, Codable, CaseIterable { case atk, def, hp, spd }
struct SubStat: Codable, Identifiable { let stat: EquipStat; let value: Int; var id: String { "\(stat.rawValue)_\(value)" } }

// MARK: - 天赋 / 命格（GDD 十六·养成扩展：每只妖点 3 层天赋树 攻/防/辅）
enum TalentBranch: String, Codable, CaseIterable { case attack = "ATTACK", defense = "DEFENSE", support = "SUPPORT" }

struct TalentEngine {
    static let MAX_LAYER = 3
    static let BASE_TALENT = 3
    static let TALENT_PER_BREAK = 1
    static let ATK_PCT_PER_LAYER = 0.06
    static let DEF_PCT_PER_LAYER = 0.06
    static let HP_PCT_PER_LAYER = 0.04
    static let SPD_PCT_PER_LAYER = 0.05

    static func talentCap(_ inst: MonsterInstance) -> Int { BASE_TALENT + inst.breakthrough * TALENT_PER_BREAK }
    static func layers(of inst: MonsterInstance, _ b: TalentBranch) -> Int { inst.talents[b.rawValue] ?? 0 }
    /// 1:1 镜像 Kotlin TalentEngine.layersOf(inst, b)（委托到 Swift 原生 camel 拼写）
    static func layersOf(_ inst: MonsterInstance, _ b: TalentBranch) -> Int { layers(of: inst, b) }
    static func allocated(_ inst: MonsterInstance) -> Int { inst.talents.values.reduce(0, +) }
    static func canAssign(_ inst: MonsterInstance, _ b: TalentBranch) -> Bool {
        layers(of: inst, b) < MAX_LAYER && allocated(inst) < talentCap(inst)
    }
    static func assign(_ inst: MonsterInstance, _ b: TalentBranch) -> MonsterInstance? {
        guard canAssign(inst, b) else { return nil }
        var t = inst.talents; t[b.rawValue] = layers(of: inst, b) + 1
        return MonsterInstance(instanceId: inst.instanceId, defId: inst.defId, level: inst.level, exp: inst.exp,
                               breakthrough: inst.breakthrough, tribulations: inst.tribulations, talents: t)
    }
    static func atkMult(_ inst: MonsterInstance) -> Double { 1.0 + ATK_PCT_PER_LAYER * Double(layers(of: inst, .attack)) }
    static func defMult(_ inst: MonsterInstance) -> Double { 1.0 + DEF_PCT_PER_LAYER * Double(layers(of: inst, .defense)) }
    static func hpMult(_ inst: MonsterInstance) -> Double { 1.0 + HP_PCT_PER_LAYER * Double(layers(of: inst, .defense)) }
    static func spdMult(_ inst: MonsterInstance) -> Double { 1.0 + SPD_PCT_PER_LAYER * Double(layers(of: inst, .support)) }
}

struct EquipDef: Codable {
    let id: String; let name: String; let slot: EquipSlot
    let element: Element?; let rarity: Rarity
    let baseAtk: Int; let baseDef: Int; let baseHp: Int; let baseSpd: Int
    let bonusPerLevel: Double; let maxLevel: Int
    init(id: String, name: String, slot: EquipSlot, element: Element?, rarity: Rarity,
         baseAtk: Int, baseDef: Int, baseHp: Int, baseSpd: Int,
         bonusPerLevel: Double = 0.12, maxLevel: Int = 10) {
        self.id = id; self.name = name; self.slot = slot; self.element = element; self.rarity = rarity
        self.baseAtk = baseAtk; self.baseDef = baseDef; self.baseHp = baseHp; self.baseSpd = baseSpd
        self.bonusPerLevel = bonusPerLevel; self.maxLevel = maxLevel
    }
}

struct OwnedEquip: Codable, Identifiable {
    let equipId: String; let instanceId: String; var id: String { instanceId }
    var level: Int
    var stars: Int
    var tier: Int
    var substats: [SubStat]
    init(equipId: String, instanceId: String, level: Int = 1, stars: Int = 1, tier: Int = 1, substats: [SubStat] = []) {
        self.equipId = equipId; self.instanceId = instanceId; self.level = level
        self.stars = stars; self.tier = tier; self.substats = substats
    }
    // 容错旧档：缺失 stars/tier/substats 时默认（向后兼容）
    private enum CodingKeys: String, CodingKey { case equipId, instanceId, level, stars, tier, substats }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        equipId = try c.decode(String.self, forKey: .equipId)
        instanceId = try c.decode(String.self, forKey: .instanceId)
        level = try c.decodeIfPresent(Int.self, forKey: .level) ?? 1
        stars = try c.decodeIfPresent(Int.self, forKey: .stars) ?? 1
        tier = try c.decodeIfPresent(Int.self, forKey: .tier) ?? 1
        substats = try c.decodeIfPresent([SubStat].self, forKey: .substats) ?? []
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(equipId, forKey: .equipId)
        try c.encode(instanceId, forKey: .instanceId)
        try c.encode(level, forKey: .level)
        try c.encode(stars, forKey: .stars)
        try c.encode(tier, forKey: .tier)
        try c.encode(substats, forKey: .substats)
    }
}

struct EquipmentLibrary {
    static let all: [EquipDef] = [
        EquipDef(id: "eq_sword", name: "青锋剑", slot: .weapon, element: .metal, rarity: .ling, baseAtk: 18, baseDef: 0, baseHp: 0, baseSpd: 4),
        EquipDef(id: "eq_staff", name: "桃木杖", slot: .weapon, element: .wood, rarity: .ling, baseAtk: 14, baseDef: 0, baseHp: 0, baseSpd: 6),
        EquipDef(id: "eq_bow", name: "落霞弓", slot: .weapon, element: .wind, rarity: .ling, baseAtk: 16, baseDef: 0, baseHp: 0, baseSpd: 5),
        EquipDef(id: "eq_trident", name: "分水刺", slot: .weapon, element: .water, rarity: .ling, baseAtk: 15, baseDef: 2, baseHp: 0, baseSpd: 3),
        EquipDef(id: "eq_armor", name: "玄龟甲", slot: .armor, element: .water, rarity: .ling, baseAtk: 0, baseDef: 16, baseHp: 30, baseSpd: 0),
        EquipDef(id: "eq_robe", name: "朱雀羽衣", slot: .armor, element: .fire, rarity: .ling, baseAtk: 0, baseDef: 12, baseHp: 24, baseSpd: 2),
        EquipDef(id: "eq_ring", name: "雷罡环", slot: .accessory, element: .thunder, rarity: .xian, baseAtk: 8, baseDef: 4, baseHp: 10, baseSpd: 6),
        EquipDef(id: "eq_pendant", name: "避水珠", slot: .accessory, element: .water, rarity: .ling, baseAtk: 0, baseDef: 6, baseHp: 20, baseSpd: 4),
        EquipDef(id: "eq_charm", name: "五雷符", slot: .accessory, element: .thunder, rarity: .ling, baseAtk: 10, baseDef: 0, baseHp: 0, baseSpd: 8),
    ]
    static func byId(_ id: String) -> EquipDef? { all.first { $0.id == id } }
}

struct EquipmentEngine {
    static let AFFINITY_MULT = 1.2
    static let FORGE_COST_YAODAN = 10
    static let MAX_STAR = 5
    static let STAR_BONUS = 0.10
    static let TIER_LEVEL_BONUS = 10
    static let TIER_PERLEVEL_BONUS = 0.03
    static let MAX_SUBSTATS = 4
    static let SUB_CAP = 15
    static let TEMPER_STEP = 3
    static let STAR_COST_BASE = 50
    static let TIER_COST_BASE = 80
    static let TEMPER_COST_BASE = 30
    static let INHERIT_COST_YAODAN = 20
    static let SUB_ORDER: [EquipStat] = [.atk, .def, .hp, .spd]

    static func maxTier(_ rarity: Rarity) -> Int {
        switch rarity { case .fan: return 1; case .ling: return 2; case .xian: return 3; case .shen: return 4 }
    }
    static func maxLevelOf(_ def: EquipDef, _ oe: OwnedEquip) -> Int {
        def.maxLevel + (oe.tier - 1) * TIER_LEVEL_BONUS
    }
    static func effectivePerLevel(_ def: EquipDef, _ oe: OwnedEquip) -> Double {
        def.bonusPerLevel + Double(oe.tier - 1) * TIER_PERLEVEL_BONUS
    }
    static func starMult(_ oe: OwnedEquip) -> Double { 1.0 + Double(oe.stars - 1) * STAR_BONUS }
    static func bonus(_ oe: OwnedEquip, _ def: EquipDef, _ elementMatches: Bool) -> Stats {
        let mult = (1.0 + Double(max(0, oe.level - 1)) * effectivePerLevel(def, oe))
            * starMult(oe) * (elementMatches ? AFFINITY_MULT : 1.0)
        let main = Stats(atk: Int(Double(def.baseAtk) * mult), def: Int(Double(def.baseDef) * mult),
                         hp: Int(Double(def.baseHp) * mult), spd: Int(Double(def.baseSpd) * mult))
        return addStats(main, subStatsBonus(oe))
    }
    static func subStatsBonus(_ oe: OwnedEquip) -> Stats {
        var atk = 0, dfn = 0, hp = 0, spd = 0
        for s in oe.substats {
            switch s.stat {
            case .atk: atk += s.value
            case .def: dfn += s.value
            case .hp: hp += s.value
            case .spd: spd += s.value
            }
        }
        return Stats(atk: atk, def: dfn, hp: hp, spd: spd)
    }
    static func bonusAtLevel(_ def: EquipDef, _ level: Int, _ elementMatches: Bool) -> Stats {
        bonus(OwnedEquip(equipId: def.id, instanceId: "x", level: level), def, elementMatches)
    }
    static func addStats(_ a: Stats, _ b: Stats) -> Stats {
        Stats(atk: a.atk + b.atk, def: a.def + b.def, hp: a.hp + b.hp, spd: a.spd + b.spd)
    }
    static func totalBonus(_ monster: Monster, _ equips: [OwnedEquip]) -> Stats {
        var total = Stats(atk: 0, def: 0, hp: 0, spd: 0)
        for o in equips {
            guard let d = EquipmentLibrary.byId(o.equipId) else { continue }
            total = addStats(total, bonus(o, d, d.element == monster.element))
        }
        return total
    }
    // 升星
    static func canStarUp(_ oe: OwnedEquip) -> Bool { oe.stars < MAX_STAR }
    static func starCost(_ oe: OwnedEquip) -> Int { STAR_COST_BASE * oe.stars }
    static func starUp(_ oe: OwnedEquip) -> OwnedEquip? {
        guard canStarUp(oe) else { return nil }
        return OwnedEquip(equipId: oe.equipId, instanceId: oe.instanceId, level: oe.level, stars: oe.stars + 1, tier: oe.tier, substats: oe.substats)
    }
    // 突破
    static func canBreakthrough(_ oe: OwnedEquip, _ def: EquipDef) -> Bool { oe.tier < maxTier(def.rarity) }
    static func tierCost(_ oe: OwnedEquip) -> Int { TIER_COST_BASE * oe.tier }
    static func breakthrough(_ oe: OwnedEquip, _ def: EquipDef) -> OwnedEquip? {
        guard canBreakthrough(oe, def) else { return nil }
        return OwnedEquip(equipId: oe.equipId, instanceId: oe.instanceId, level: oe.level, stars: oe.stars, tier: oe.tier + 1, substats: oe.substats)
    }
    // 淬炼
    static func canTemper(_ oe: OwnedEquip) -> Bool {
        if oe.substats.count >= MAX_SUBSTATS { return oe.substats.contains { $0.value < SUB_CAP } }
        return true
    }
    static func temperUsed(_ oe: OwnedEquip) -> Int {
        oe.substats.reduce(0) { $0 + ($1.value + TEMPER_STEP - 1) / TEMPER_STEP }
    }
    static func temperCost(_ oe: OwnedEquip) -> Int { TEMPER_COST_BASE * (temperUsed(oe) + 1) }
    static func temper(_ oe: OwnedEquip) -> OwnedEquip? {
        guard canTemper(oe) else { return nil }
        let existing = Set(oe.substats.map { $0.stat })
        if let nxt = SUB_ORDER.first(where: { !existing.contains($0) }), oe.substats.count < MAX_SUBSTATS {
            return OwnedEquip(equipId: oe.equipId, instanceId: oe.instanceId, level: oe.level, stars: oe.stars, tier: oe.tier,
                              substats: oe.substats + [SubStat(stat: nxt, value: TEMPER_STEP)])
        } else {
            guard let idx = oe.substats.firstIndex(where: { $0.value < SUB_CAP }) else { return nil }
            var arr = oe.substats
            let s = arr[idx]
            arr[idx] = SubStat(stat: s.stat, value: min(s.value + TEMPER_STEP, SUB_CAP))
            return OwnedEquip(equipId: oe.equipId, instanceId: oe.instanceId, level: oe.level, stars: oe.stars, tier: oe.tier, substats: arr)
        }
    }
    // 继承
    static func canInherit(_ src: OwnedEquip, _ dst: OwnedEquip) -> Bool { src.instanceId != dst.instanceId }
    static func inherit(_ src: OwnedEquip, _ dst: OwnedEquip) -> OwnedEquip? {
        guard canInherit(src, dst) else { return nil }
        var mp: [EquipStat: Int] = [:]
        for s in dst.substats { mp[s.stat] = s.value }
        for s in src.substats { mp[s.stat] = max(mp[s.stat] ?? 0, s.value) }
        var merged: [SubStat] = []
        for st in SUB_ORDER {
            guard let v = mp[st] else { continue }
            merged.append(SubStat(stat: st, value: v))
            if merged.count >= MAX_SUBSTATS { break }
        }
        return OwnedEquip(equipId: dst.equipId, instanceId: dst.instanceId,
                          level: max(dst.level, src.level), stars: max(dst.stars, src.stars),
                          tier: max(dst.tier, src.tier), substats: merged)
    }
    static func canForge(_ o: OwnedEquip) -> Bool {
        guard let d = EquipmentLibrary.byId(o.equipId) else { return false }
        return o.level < maxLevelOf(d, o)
    }
}

// MARK: - 炼妖融合（GDD 十六·养成线）
struct FusionEngine {
    static let FUSE_COST_YAODAN = 30
    static let LEVEL_BONUS = 3
    static let RECIPES: [Set<String>: String] = [
        Set(["yao_jiao", "gui_linggui"]): "gui_xuanwu"
    ]
    /// 配方产物 defId 列表（供 DataIntegrityEngine 校验产物均属妖池，防死配方）
    static let recipeOutputs: [String] = Array(RECIPES.values)
    struct FusionResult { let newDefId: String; let newLevel: Int; let newBreakthrough: Int; let newSkills: [String] }
    static func canFuse(_ a: MonsterInstance, _ b: MonsterInstance, _ yaodan: Int) -> Bool {
        a.instanceId != b.instanceId && yaodan >= FUSE_COST_YAODAN
    }
    static func plan(_ a: MonsterInstance, _ b: MonsterInstance) -> FusionResult? {
        guard a.instanceId != b.instanceId,
              let da = MonsterPool.byId(a.defId), let db = MonsterPool.byId(b.defId) else { return nil }
        let newDefId: String
        if let r = RECIPES[Set([a.defId, b.defId])] { newDefId = r }
        else if db.rarity.rawValue > da.rarity.rawValue { newDefId = b.defId }
        else if da.rarity.rawValue > db.rarity.rawValue { newDefId = a.defId }
        else {
            let sa = da.baseAtk + da.baseDef + da.baseHp + da.baseSpd
            let sb = db.baseAtk + db.baseDef + db.baseHp + db.baseSpd
            newDefId = sb >= sa ? b.defId : a.defId
        }
        guard let newDef = MonsterPool.byId(newDefId) else { return nil }
        let bt = min(a.breakthrough, b.breakthrough)
        let cap = CultivationEngine.BASE_LEVEL_CAP + bt * CultivationEngine.LEVEL_CAP_PER_BREAK
        let newLevel = min(max(a.level, b.level) + LEVEL_BONUS, cap)
        let skills = Array(Set(da.skills.map { $0.id } + db.skills.map { $0.id })).prefix(4)
        return FusionResult(newDefId: newDefId, newLevel: newLevel, newBreakthrough: bt, newSkills: Array(skills))
    }
}

// MARK: - 技能库
private let S = { (id: String, name: String, type: SkillType, e: Element, p: Int, dmg: Bool, crit: Double = 0) in
    Skill(id: id, name: name, type: type, element: e, power: p, isDamage: dmg, critBonus: crit)
}
let SK = [
    S("s_liekong","裂空斩",.attack,.metal,78,true,0.05),
    S("s_jingtao","惊涛拍岸",.attack,.water,75,true),
    S("s_fentian","焚天烈焰",.attack,.fire,80,true,0.05),
    S("s_jiuxiao","九霄雷劫",.attack,.thunder,85,true,0.08),
    S("s_qingmu","青木缠",.attack,.wood,70,true),
    S("s_luoshi","落石术",.attack,.earth,72,true),
    S("s_gangfeng","罡风斩",.attack,.wind,76,true,0.06),
    S("s_jinzhong","金钟罩",.defense,.metal,0,false),
    S("s_xuanbing","玄冰壁",.defense,.water,0,false),
    S("s_kunyao","捆妖索",.spell,.wood,0,false),
    S("s_dingshen","定身符",.spell,.metal,0,false),
    S("s_ganlin","甘霖普润",.support,.water,0,false),
    S("s_xuming","续命灯",.support,.fire,0,false),
    S("s_sanmei","三昧真火",.element,.fire,92,true,0.1),
    S("s_hantan","寒潭玄冰",.element,.water,90,true,0.08),
    S("s_wulei","五雷正法",.element,.thunder,94,true,0.12),
    S("s_qingmu2","青木长生",.element,.wood,82,true),
    S("s_houtu","厚土术",.element,.earth,84,true),
    S("s_ruijin","锐金诀",.element,.metal,86,true,0.06),
    S("s_jifeng","疾风诀",.element,.wind,83,true,0.06),
    // —— 补回原本缺失的 4 个原始技能（与 Kotlin SkillLibrary 对齐）——
    S("s_fanzhen","反震荆甲",.defense,.wood,0,false),
    S("s_qinhe","五行亲和",.passive,.wood,0,false),
    S("s_wanjian","万剑归宗",.attack,.metal,88,true,0.1),
    S("s_xueshi","嗜血",.passive,.fire,0,false),
    // —— 扩池至 73 式新增（山海经题材，覆盖 6 大类）——
    // 攻伐 +9
    S("s_pojia","破甲击",.attack,.metal,82,true,0.08),
    S("s_bingpo","冰魄寒光",.attack,.water,78,true,0.06),
    S("s_lieyan","烈焰焚",.attack,.fire,84,true,0.08),
    S("s_leiting","雷霆万钧",.attack,.thunder,90,true,0.1),
    S("s_wanmu","万木穿刺",.attack,.wood,76,true,0.04),
    S("s_yanshi","岩崩",.attack,.earth,80,true,0.05),
    S("s_xuanfeng","旋风斩",.attack,.wind,79,true,0.07),
    S("s_canjian","残月斩",.attack,.metal,86,true,0.09),
    S("s_chihuo","赤火燎原",.attack,.fire,88,true,0.1),
    // 守御 +7
    S("s_tieshan","铁山靠",.defense,.earth,0,false),
    S("s_shuimu","水幕天华",.defense,.water,0,false),
    S("s_huoji","火盾",.defense,.fire,0,false),
    S("s_muya","木芽护",.defense,.wood,0,false),
    S("s_fengqiang","风墙",.defense,.wind,0,false),
    S("s_leijia","雷甲",.defense,.thunder,0,false),
    S("s_jingang","金刚不坏",.defense,.metal,0,false),
    // 术法（控制）+8
    S("s_hunshen","昏睡咒",.spell,.wood,0,false),
    S("s_liuhun","六魂符",.spell,.metal,0,false),
    S("s_bingfeng","冰封",.spell,.water,0,false),
    S("s_huoyu","惑心焰",.spell,.fire,0,false),
    S("s_shilei","摄魂雷",.spell,.thunder,0,false),
    S("s_xuanyun","眩晕风",.spell,.wind,0,false),
    S("s_dunxing","遁形",.spell,.earth,0,false),
    S("s_suoming","锁命咒",.spell,.wood,0,false),
    // 辅助 +8
    S("s_hudun","护盾术",.support,.metal,0,false),
    S("s_qingxin","清心咒",.support,.water,0,false),
    S("s_yangjiu","养就灵泉",.support,.wood,0,false),
    S("s_fuhuo","回春术",.support,.fire,0,false),
    S("s_tishen","替身土偶",.support,.earth,0,false),
    S("s_jisuz","疾速咒",.support,.wind,0,false),
    S("s_leibu","雷部号令",.support,.thunder,0,false),
    S("s_jingqi","精气诀",.support,.metal,0,false),
    // 五行专精 +10
    S("s_jinwu","金乌曜日",.element,.fire,96,true,0.12),
    S("s_xuanwu","玄武镇海",.element,.water,92,true,0.08),
    S("s_qinglong","青龙破浪",.element,.water,90,true,0.08),
    S("s_baihu","白虎裂空",.element,.metal,95,true,0.12),
    S("s_zhuque","朱雀焚天",.element,.fire,97,true,0.14),
    S("s_qilin","麒麟圣光",.element,.wood,88,true,0.06),
    S("s_huanglong","黄龙厚土",.element,.earth,90,true,0.06),
    S("s_jiufeng","九凤雷音",.element,.thunder,93,true,0.1),
    S("s_taiji","太极罡风",.element,.wind,89,true,0.06),
    S("s_tianlei","天雷破",.element,.thunder,98,true,0.15),
    // 心法被动 +7
    S("s_taimo","泰魔护体",.passive,.metal,0,false),
    S("s_huigen","慧根",.passive,.wood,0,false),
    S("s_wuxing","五行逆转",.passive,.fire,0,false),
    S("s_lingshou","灵兽之威",.passive,.earth,0,false),
    S("s_fengxing","风行疾",.passive,.wind,0,false),
    S("s_shuixi","水流不息",.passive,.water,0,false),
    S("s_leiling","雷灵",.passive,.thunder,0,false),
].reduce(into: [String: Skill]()) { $0[$1.id] = $1 }

func pick(_ ids: [String]) -> [Skill] { ids.compactMap { SK[$0] } }

// MARK: - 妖池（切片，覆盖七行/四类别/四稀有度）
struct MonsterPool {
    static let all: [Monster] = [
        Monster(id:"jing_saozhou",name:"扫帚精",category:.jing,element:.wood,rarity:.fan,baseAtk:42,baseDef:38,baseHp:95,baseSpd:50,skills:pick(["s_qingmu","s_luoshi","s_jinzhong","s_kunyao"]),evolveFrom:nil,evolveTo:nil,desc:"成精的破扫帚。"),
        Monster(id:"jing_denglong",name:"灯笼精",category:.jing,element:.fire,rarity:.ling,baseAtk:50,baseDef:42,baseHp:100,baseSpd:55,skills:pick(["s_fentian","s_xuming","s_xuanbing","s_dingshen"]),desc:"挂灯成妖。"),
        Monster(id:"jing_tongjing",name:"铜镜精",category:.jing,element:.metal,rarity:.ling,baseAtk:54,baseDef:50,baseHp:105,baseSpd:48,skills:pick(["s_liekong","s_ruijin","s_jinzhong","s_dingshen"]),desc:"古镜映妖。"),
        Monster(id:"guai_nianshou",name:"年兽",category:.guai,element:.earth,rarity:.shen,baseAtk:78,baseDef:70,baseHp:160,baseSpd:60,skills:pick(["s_luoshi","s_houtu","s_jinzhong"]),desc:"春节限定凶兽，无进化。",isFestival:true),
        Monster(id:"guai_taotie",name:"饕餮",category:.guai,element:.water,rarity:.shen,baseAtk:80,baseDef:66,baseHp:150,baseSpd:58,skills:pick(["s_jingtao","s_hantan","s_ganlin"]),desc:"贪食巨兽。"),
        Monster(id:"guai_kuiniu",name:"夔牛",category:.guai,element:.thunder,rarity:.xian,baseAtk:72,baseDef:60,baseHp:140,baseSpd:65,skills:pick(["s_jiuxiao","s_wulei","s_gangfeng"]),desc:"独足雷兽。"),
        Monster(id:"yao_she",name:"蛇妖",category:.yao,element:.water,rarity:.fan,baseAtk:46,baseDef:40,baseHp:100,baseSpd:62,skills:pick(["s_jingtao","s_qingmu2","s_kunyao","s_ganlin"]),evolveFrom:nil,evolveTo:"yao_jiao",desc:"初成妖的青蛇。"),
        Monster(id:"yao_jiao",name:"蛟",category:.yao,element:.water,rarity:.ling,baseAtk:60,baseDef:52,baseHp:130,baseSpd:70,skills:pick(["s_hantan","s_jingtao","s_ganlin","s_xuanbing"]),evolveFrom:"yao_she",evolveTo:"yao_long",desc:"渡劫将化龙。"),
        Monster(id:"yao_long",name:"龙",category:.yao,element:.water,rarity:.shen,baseAtk:88,baseDef:72,baseHp:170,baseSpd:80,skills:pick(["s_hantan","s_jingtao","s_ganlin","s_wulei"]),evolveFrom:"yao_jiao",desc:"司水之神兽。"),
        Monster(id:"yao_hu",name:"狐妖",category:.yao,element:.fire,rarity:.fan,baseAtk:48,baseDef:38,baseHp:96,baseSpd:72,skills:pick(["s_fentian","s_dingshen","s_xuming","s_ganlin"]),evolveFrom:nil,evolveTo:"yao_yaohu",desc:"山野狐妖。",clan:"fox"),
        Monster(id:"yao_yaohu",name:"妖狐",category:.yao,element:.fire,rarity:.ling,baseAtk:62,baseDef:46,baseHp:118,baseSpd:82,skills:pick(["s_sanmei","s_fentian","s_dingshen","s_xuming"]),evolveFrom:"yao_hu",evolveTo:"yao_jiuwei",desc:"修为使然，魅惑更甚。",clan:"fox"),
        Monster(id:"yao_jiuwei",name:"九尾狐",category:.yao,element:.fire,rarity:.shen,baseAtk:86,baseDef:58,baseHp:150,baseSpd:95,skills:pick(["s_sanmei","s_fentian","s_dingshen","s_ganlin"]),evolveFrom:"yao_yaohu",desc:"青丘之主。",clan:"fox"),
        Monster(id:"yao_fengli",name:"风狸",category:.yao,element:.wind,rarity:.ling,baseAtk:56,baseDef:44,baseHp:104,baseSpd:88,skills:pick(["s_gangfeng","s_jifeng","s_dingshen","s_ganlin"]),desc:"御风灵狸。"),
        Monster(id:"lingshou_qilin",name:"麒麟",category:.lingshou,element:.earth,rarity:.shen,baseAtk:84,baseDef:74,baseHp:165,baseSpd:70,skills:pick(["s_houtu","s_luoshi","s_jinzhong"]),desc:"仁兽祥瑞。"),
        Monster(id:"lingshou_zhuque",name:"朱雀",category:.lingshou,element:.fire,rarity:.shen,baseAtk:90,baseDef:60,baseHp:150,baseSpd:92,skills:pick(["s_zhuque","s_jinwu","s_sanmei","s_fentian"]),desc:"四神兽之一。"),
        Monster(id:"lingshou_qinglong",name:"青龙",category:.lingshou,element:.wood,rarity:.shen,baseAtk:86,baseDef:70,baseHp:160,baseSpd:84,skills:pick(["s_qilin","s_wanmu","s_qingmu2","s_luoshi"]),evolveFrom:nil,evolveTo:nil,desc:"四神兽之一，东方木德之尊。"),
        Monster(id:"lingshou_baihu",name:"白虎",category:.lingshou,element:.metal,rarity:.shen,baseAtk:92,baseDef:68,baseHp:155,baseSpd:90,skills:pick(["s_baihu","s_jingang","s_ruijin","s_liekong"]),evolveFrom:nil,evolveTo:nil,desc:"四神兽之一，西方金戈之主。"),
        Monster(id:"gui_guijia",name:"龟妖",category:.guai,element:.water,rarity:.fan,baseAtk:44,baseDef:52,baseHp:130,baseSpd:28,skills:pick(["s_jingtao","s_xuanbing","s_jinzhong","s_ganlin"]),evolveFrom:nil,evolveTo:"gui_linggui",desc:"背甲初成的笨拙水妖。"),
        Monster(id:"gui_linggui",name:"灵龟",category:.guai,element:.water,rarity:.ling,baseAtk:58,baseDef:66,baseHp:165,baseSpd:36,skills:pick(["s_hantan","s_xuanbing","s_jinzhong","s_ganlin"]),evolveFrom:"gui_guijia",evolveTo:"gui_xuanwu",desc:"通灵老龟，负碑而行。"),
        Monster(id:"gui_xuanwu",name:"玄武",category:.guai,element:.water,rarity:.shen,baseAtk:82,baseDef:78,baseHp:200,baseSpd:50,skills:pick(["s_xuanwu","s_shuimu","s_hantan","s_jinzhong"]),evolveFrom:"gui_linggui",evolveTo:nil,desc:"四神兽之一，龟蛇合体，镇北方水德。"),
        Monster(id:"niao_lingniao",name:"灵鸟",category:.yao,element:.fire,rarity:.fan,baseAtk:45,baseDef:36,baseHp:92,baseSpd:74,skills:pick(["s_fentian","s_dingshen","s_ganlin","s_jifeng"]),evolveFrom:nil,evolveTo:"niao_qingluan",desc:"羽毛初染焰色的火禽。"),
        Monster(id:"niao_qingluan",name:"青鸾",category:.yao,element:.fire,rarity:.ling,baseAtk:60,baseDef:46,baseHp:116,baseSpd:84,skills:pick(["s_sanmei","s_fentian","s_jifeng","s_ganlin"]),evolveFrom:"niao_lingniao",evolveTo:"niao_fenghuang",desc:"五色备举，将化凤凰。"),
        Monster(id:"niao_fenghuang",name:"凤凰",category:.yao,element:.fire,rarity:.shen,baseAtk:88,baseDef:56,baseHp:148,baseSpd:96,skills:pick(["s_sanmei","s_fentian","s_jifeng","s_ganlin"]),evolveFrom:"niao_qingluan",evolveTo:nil,desc:"百鸟之王，浴火不死。"),
        Monster(id:"guai_xiangliu",name:"相柳",category:.guai,element:.water,rarity:.shen,baseAtk:84,baseDef:64,baseHp:158,baseSpd:70,skills:pick(["s_jingtao","s_hantan","s_ganlin","s_jifeng"]),evolveFrom:nil,evolveTo:nil,desc:"九首毒蛇，水淹九州。"),
        Monster(id:"jing_shuijing",name:"水精",category:.jing,element:.water,rarity:.ling,baseAtk:52,baseDef:46,baseHp:108,baseSpd:52,skills:pick(["s_jingtao","s_xuanbing","s_dingshen","s_kunyao"]),evolveFrom:nil,evolveTo:nil,desc:"凝水而成的精灵。"),
        Monster(id:"lingshou_baize",name:"白泽",category:.lingshou,element:.metal,rarity:.xian,baseAtk:70,baseDef:64,baseHp:145,baseSpd:66,skills:pick(["s_ruijin","s_liekong","s_dingshen"]),desc:"通晓万物之兽。"),
        // ===== 狼妖进化链：狼妖 → 赤焰狼 → 天狼（火·妖） =====
        Monster(id:"lang_yao",name:"狼妖",category:.yao,element:.fire,rarity:.fan,baseAtk:50,baseDef:38,baseHp:98,baseSpd:66,skills:pick(["s_fentian","s_dingshen","s_gangfeng","s_ganlin"]),evolveFrom:nil,evolveTo:"lang_chi",desc:"荒野独行的火毛狼妖。"),
        Monster(id:"lang_chi",name:"赤焰狼",category:.yao,element:.fire,rarity:.ling,baseAtk:64,baseDef:50,baseHp:126,baseSpd:74,skills:pick(["s_sanmei","s_fentian","s_gangfeng","s_dingshen"]),evolveFrom:"lang_yao",evolveTo:"lang_tianlang",desc:"烈焰缠身的头狼。"),
        Monster(id:"lang_tianlang",name:"天狼",category:.yao,element:.fire,rarity:.shen,baseAtk:90,baseDef:60,baseHp:158,baseSpd:92,skills:pick(["s_sanmei","s_fentian","s_wulei","s_gangfeng"]),evolveFrom:"lang_chi",desc:"坠星之狼，主兵戈。"),
        // ===== 鲤妖进化链：鲤妖 → 龙鲤 → 鳌龙（水·妖，止于仙） =====
        Monster(id:"li_yao",name:"鲤妖",category:.yao,element:.water,rarity:.fan,baseAtk:44,baseDef:42,baseHp:102,baseSpd:58,skills:pick(["s_jingtao","s_xuanbing","s_kunyao","s_ganlin"]),evolveFrom:nil,evolveTo:"li_li",desc:"溪中赤鲤，逆流经年。"),
        Monster(id:"li_li",name:"龙鲤",category:.yao,element:.water,rarity:.ling,baseAtk:58,baseDef:54,baseHp:132,baseSpd:66,skills:pick(["s_hantan","s_jingtao","s_ganlin","s_xuanbing"]),evolveFrom:"li_yao",evolveTo:"li_ao",desc:"额生龙纹，将化蛟。"),
        Monster(id:"li_ao",name:"鳌龙",category:.yao,element:.water,rarity:.xian,baseAtk:72,baseDef:66,baseHp:150,baseSpd:64,skills:pick(["s_hantan","s_jingtao","s_luoshi","s_wulei"]),evolveFrom:"li_li",desc:"背负仙山的巨鲤之灵。"),
        // ===== 鼠妖 2 段链：鼠妖 → 飞鼠精（风·妖） =====
        Monster(id:"shu_yao",name:"鼠妖",category:.yao,element:.wind,rarity:.fan,baseAtk:42,baseDef:34,baseHp:88,baseSpd:84,skills:pick(["s_jifeng","s_gangfeng","s_dingshen","s_ganlin"]),evolveFrom:nil,evolveTo:"shu_fei",desc:"洞中偷油的小妖。"),
        Monster(id:"shu_fei",name:"飞鼠精",category:.yao,element:.wind,rarity:.ling,baseAtk:54,baseDef:44,baseHp:100,baseSpd:92,skills:pick(["s_jifeng","s_gangfeng","s_qinhe","s_dingshen"]),evolveFrom:"shu_yao",desc:"胁生双翼，乘风而行。"),
        // ===== 蛙妖 2 段链：蛙妖 → 蟾精（水/土·精） =====
        Monster(id:"wa_yao",name:"蛙妖",category:.jing,element:.water,rarity:.fan,baseAtk:38,baseDef:50,baseHp:120,baseSpd:40,skills:pick(["s_jingtao","s_xuanbing","s_jinzhong","s_ganlin"]),evolveFrom:nil,evolveTo:"wa_chan",desc:"池塘鼓腹的笨妖。"),
        Monster(id:"wa_chan",name:"蟾精",category:.jing,element:.earth,rarity:.ling,baseAtk:50,baseDef:64,baseHp:150,baseSpd:44,skills:pick(["s_houtu","s_luoshi","s_jinzhong","s_xuanbing"]),evolveFrom:"wa_yao",desc:"三足金蟾，吐纳灵气。"),
        // ===== 精怪补全（土系防御向） =====
        Monster(id:"jing_shanyao",name:"山魈精",category:.jing,element:.earth,rarity:.fan,baseAtk:40,baseDef:48,baseHp:116,baseSpd:46,skills:pick(["s_luoshi","s_houtu","s_jinzhong","s_kunyao"]),desc:"山间独足怪，力大。"),
        Monster(id:"jing_shijing",name:"石精",category:.jing,element:.earth,rarity:.ling,baseAtk:52,baseDef:64,baseHp:140,baseSpd:42,skills:pick(["s_luoshi","s_houtu","s_jinzhong","s_fanzhen"]),desc:"顽石吸纳日月所化。"),
        // ===== 凶兽补全（四凶等） =====
        Monster(id:"guai_biqiu",name:"蜚",category:.guai,element:.earth,rarity:.xian,baseAtk:74,baseDef:58,baseHp:140,baseSpd:60,skills:pick(["s_luoshi","s_houtu","s_jiuxiao","s_xueshi"]),desc:"行过之处草木尽枯。"),
        Monster(id:"guai_taowu",name:"梼杌",category:.guai,element:.earth,rarity:.shen,baseAtk:84,baseDef:76,baseHp:175,baseSpd:56,skills:pick(["s_luoshi","s_houtu","s_wanjian","s_xueshi"]),desc:"四凶之一，顽凶难驯。"),
        Monster(id:"guai_hundun",name:"混沌",category:.guai,element:.wind,rarity:.shen,baseAtk:82,baseDef:70,baseHp:168,baseSpd:78,skills:pick(["s_jifeng","s_gangfeng","s_wulei","s_ganlin"]),desc:"四凶之一，无七窍而识万物。"),
        Monster(id:"guai_qiongqi",name:"穷奇",category:.guai,element:.thunder,rarity:.shen,baseAtk:88,baseDef:64,baseHp:150,baseSpd:86,skills:pick(["s_wulei","s_jiuxiao","s_gangfeng","s_xueshi"]),desc:"四凶之一，状如虎翼，啸如雷。"),
        // ===== 妖（动物成妖）补全 =====
        Monster(id:"yao_ma",name:"马妖",category:.yao,element:.wind,rarity:.fan,baseAtk:48,baseDef:36,baseHp:96,baseSpd:86,skills:pick(["s_jifeng","s_gangfeng","s_dingshen","s_ganlin"]),desc:"日行千里的骏马成妖。"),
        Monster(id:"yao_niu",name:"牛妖",category:.yao,element:.earth,rarity:.ling,baseAtk:60,baseDef:60,baseHp:150,baseSpd:44,skills:pick(["s_luoshi","s_houtu","s_jinzhong","s_fanzhen"]),desc:"力能扛鼎的青牛。"),
        Monster(id:"yao_yu",name:"鱼妖",category:.yao,element:.water,rarity:.fan,baseAtk:44,baseDef:40,baseHp:104,baseSpd:62,skills:pick(["s_jingtao","s_xuanbing","s_kunyao","s_ganlin"]),desc:"戏水的锦鳞小妖。"),
        Monster(id:"niao_kongque",name:"孔雀",category:.yao,element:.fire,rarity:.ling,baseAtk:64,baseDef:48,baseHp:120,baseSpd:84,skills:pick(["s_sanmei","s_fentian","s_jifeng","s_dingshen"]),desc:"翎羽流火的灵禽。"),
        Monster(id:"niao_zhen",name:"鸩",category:.yao,element:.wood,rarity:.ling,baseAtk:62,baseDef:46,baseHp:116,baseSpd:80,skills:pick(["s_qingmu","s_qingmu2","s_dingshen","s_kunyao"]),desc:"羽含剧毒的妖鸟。"),
        // ===== 灵兽补全（祥瑞） =====
        Monster(id:"lingshou_xiezhi",name:"獬豸",category:.lingshou,element:.metal,rarity:.xian,baseAtk:72,baseDef:66,baseHp:145,baseSpd:64,skills:pick(["s_ruijin","s_liekong","s_wanjian","s_dingshen"]),desc:"能辨曲直的法兽。"),
        Monster(id:"lingshou_tianma",name:"天马",category:.lingshou,element:.wind,rarity:.xian,baseAtk:66,baseDef:52,baseHp:130,baseSpd:96,skills:pick(["s_jifeng","s_gangfeng","s_qinhe","s_ganlin"]),desc:"踏空而行的祥瑞。"),
        Monster(id:"lingshou_zouyu",name:"驺虞",category:.lingshou,element:.wood,rarity:.shen,baseAtk:84,baseDef:70,baseHp:160,baseSpd:80,skills:pick(["s_qingmu2","s_qingmu","s_luoshi","s_qinhe"]),desc:"白质黑纹，仁兽也。"),

        // 燕妖进化链：燕妖 → 玄鸟 → 金乌（火·妖）
        Monster(id:"niao_yan",name:"燕妖",category:.yao,element:.fire,rarity:.fan,baseAtk:46,baseDef:36,baseHp:96,baseSpd:82,skills:pick(["s_fentian","s_dingshen","s_jifeng","s_ganlin"]),evolveTo:"niao_xuanniao",desc:"檐下衔泥的火羽小妖。"),
        Monster(id:"niao_xuanniao",name:"玄鸟",category:.yao,element:.fire,rarity:.ling,baseAtk:60,baseDef:48,baseHp:124,baseSpd:86,skills:pick(["s_sanmei","s_fentian","s_jifeng","s_dingshen"]),evolveFrom:"niao_yan",evolveTo:"niao_jinwu",desc:"天命所归的瑞禽。"),
        Monster(id:"niao_jinwu",name:"金乌",category:.yao,element:.fire,rarity:.shen,baseAtk:88,baseDef:58,baseHp:150,baseSpd:92,skills:pick(["s_sanmei","s_fentian","s_wulei","s_jifeng"]),evolveFrom:"niao_xuanniao",desc:"负日而飞的三足金乌。"),
        // 蟹妖进化链：蟹妖 → 巨蟹精 → 蟹将（水·妖）
        Monster(id:"xie_yao",name:"蟹妖",category:.yao,element:.water,rarity:.fan,baseAtk:44,baseDef:46,baseHp:112,baseSpd:44,skills:pick(["s_jingtao","s_xuanbing","s_kunyao","s_ganlin"]),evolveTo:"xie_juxie",desc:"横行浅滩的甲妖。"),
        Monster(id:"xie_juxie",name:"巨蟹精",category:.yao,element:.water,rarity:.ling,baseAtk:56,baseDef:62,baseHp:138,baseSpd:40,skills:pick(["s_hantan","s_jingtao","s_jinzhong","s_xuanbing"]),evolveFrom:"xie_yao",evolveTo:"xie_jiang",desc:"双螯生雷的蟹精。"),
        Monster(id:"xie_jiang",name:"蟹将",category:.yao,element:.water,rarity:.shen,baseAtk:82,baseDef:74,baseHp:170,baseSpd:46,skills:pick(["s_hantan","s_jingtao","s_wanjian","s_xueshi"]),evolveFrom:"xie_juxie",desc:"镇守水府的甲胄之将。"),
        // 鹤妖进化链：鹤妖 → 仙鹤 → 黄鹤（风·妖）
        Monster(id:"he_yao",name:"鹤妖",category:.yao,element:.wind,rarity:.fan,baseAtk:48,baseDef:38,baseHp:102,baseSpd:80,skills:pick(["s_jifeng","s_gangfeng","s_dingshen","s_ganlin"]),evolveTo:"he_xian",desc:"芦荡中独步的妖鹤。"),
        Monster(id:"he_xian",name:"仙鹤",category:.yao,element:.wind,rarity:.ling,baseAtk:60,baseDef:48,baseHp:126,baseSpd:86,skills:pick(["s_jifeng","s_gangfeng","s_qinhe","s_dingshen"]),evolveFrom:"he_yao",evolveTo:"he_huang",desc:"羽化将仙的灵鹤。"),
        Monster(id:"he_huang",name:"黄鹤",category:.yao,element:.wind,rarity:.shen,baseAtk:86,baseDef:58,baseHp:152,baseSpd:92,skills:pick(["s_jifeng","s_gangfeng","s_qinhe","s_wulei"]),evolveFrom:"he_xian",desc:"一去不还的乘云黄鹤。"),
        // 虎妖 2 段链：虎妖 → 彪（金·妖）
        Monster(id:"hu_yao",name:"虎妖",category:.yao,element:.metal,rarity:.fan,baseAtk:52,baseDef:42,baseHp:118,baseSpd:66,skills:pick(["s_ruijin","s_liekong","s_dingshen","s_ganlin"]),evolveTo:"hu_biao",desc:"啸动山岗的猛妖。"),
        Monster(id:"hu_biao",name:"彪",category:.yao,element:.metal,rarity:.ling,baseAtk:66,baseDef:56,baseHp:140,baseSpd:72,skills:pick(["s_ruijin","s_liekong","s_wanjian","s_dingshen"]),evolveFrom:"hu_yao",desc:"吊睛白额，虎之小弟。"),
        // 兔妖 2 段链：兔妖 → 玉兔（金·妖）
        Monster(id:"tu_yao",name:"兔妖",category:.yao,element:.metal,rarity:.fan,baseAtk:40,baseDef:36,baseHp:92,baseSpd:84,skills:pick(["s_ruijin","s_jifeng","s_dingshen","s_ganlin"]),evolveTo:"tu_yutu",desc:"捣药成妖的雪团。"),
        Monster(id:"tu_yutu",name:"玉兔",category:.yao,element:.metal,rarity:.xian,baseAtk:64,baseDef:52,baseHp:128,baseSpd:88,skills:pick(["s_ruijin","s_jifeng","s_qinhe","s_dingshen"]),evolveFrom:"tu_yao",desc:"广寒宫中捣仙药的玉兔。"),
        // 猪妖 2 段链：猪妖 → 豪彘（土·妖）
        Monster(id:"zhu_yao",name:"猪妖",category:.yao,element:.earth,rarity:.fan,baseAtk:46,baseDef:50,baseHp:120,baseSpd:40,skills:pick(["s_luoshi","s_houtu","s_jinzhong","s_fanzhen"]),evolveTo:"zhu_haozhi",desc:"贪食嗜睡的肥妖。"),
        Monster(id:"zhu_haozhi",name:"豪彘",category:.yao,element:.earth,rarity:.ling,baseAtk:58,baseDef:62,baseHp:144,baseSpd:44,skills:pick(["s_luoshi","s_houtu","s_jinzhong","s_fanzhen"]),evolveFrom:"zhu_yao",desc:"拱石为巢的刚鬣之妖。"),
        // 猴妖 2 段链：猴妖 → 灵明石猴（木·妖）
        Monster(id:"hou_yao",name:"猴妖",category:.yao,element:.wood,rarity:.fan,baseAtk:50,baseDef:40,baseHp:100,baseSpd:82,skills:pick(["s_qingmu","s_qingmu2","s_dingshen","s_ganlin"]),evolveTo:"hou_lingming",desc:"攀枝盗桃的灵猴。"),
        Monster(id:"hou_lingming",name:"灵明石猴",category:.yao,element:.wood,rarity:.shen,baseAtk:86,baseDef:64,baseHp:150,baseSpd:90,skills:pick(["s_qingmu","s_qingmu2","s_wanjian","s_jifeng"]),evolveFrom:"hou_yao",desc:"通变化、识天时之石猴。"),
        // 犬妖 2 段链：犬妖 → 哮天犬（金·妖）
        Monster(id:"quan_yao",name:"犬妖",category:.yao,element:.metal,rarity:.fan,baseAtk:50,baseDef:40,baseHp:104,baseSpd:78,skills:pick(["s_ruijin","s_liekong","s_gangfeng","s_dingshen"]),evolveTo:"quan_xiaotian",desc:"看家成妖的土狗。"),
        Monster(id:"quan_xiaotian",name:"哮天犬",category:.yao,element:.metal,rarity:.shen,baseAtk:88,baseDef:60,baseHp:148,baseSpd:94,skills:pick(["s_ruijin","s_liekong","s_wanjian","s_gangfeng"]),evolveFrom:"quan_yao",desc:"随主擒妖的细腰神犬。"),
        // 虫妖 2 段链：虫妖 → 蝶妖（风·妖）
        Monster(id:"chong_yao",name:"虫妖",category:.yao,element:.wind,rarity:.fan,baseAtk:40,baseDef:34,baseHp:88,baseSpd:80,skills:pick(["s_jifeng","s_gangfeng","s_dingshen","s_ganlin"]),evolveTo:"die_yao",desc:"草间蠕动的小妖。"),
        Monster(id:"die_yao",name:"蝶妖",category:.yao,element:.wind,rarity:.ling,baseAtk:52,baseDef:42,baseHp:98,baseSpd:88,skills:pick(["s_jifeng","s_gangfeng","s_qinhe","s_dingshen"]),evolveFrom:"chong_yao",desc:"采花成妖的彩翼精灵。"),
        // 鹰妖 2 段链：鹰妖 → 神鹰（风·妖）
        Monster(id:"ying_yao",name:"鹰妖",category:.yao,element:.wind,rarity:.fan,baseAtk:52,baseDef:38,baseHp:100,baseSpd:92,skills:pick(["s_jifeng","s_gangfeng","s_dingshen","s_ganlin"]),evolveTo:"ying_shen",desc:"盘旋觅食的猛禽妖。"),
        Monster(id:"ying_shen",name:"神鹰",category:.yao,element:.wind,rarity:.shen,baseAtk:88,baseDef:56,baseHp:146,baseSpd:98,skills:pick(["s_jifeng","s_gangfeng","s_wulei","s_dingshen"]),evolveFrom:"ying_yao",desc:"搏击长空的千里神鹰。"),
        // 鲲鹏 2 段链：鲲 → 鹏（水 → 风·妖）
        Monster(id:"kun_yao",name:"鲲",category:.yao,element:.water,rarity:.fan,baseAtk:46,baseDef:48,baseHp:130,baseSpd:44,skills:pick(["s_jingtao","s_hantan","s_kunyao","s_xuanbing"]),evolveTo:"peng_yao",desc:"北冥之鱼，其大不知其几千里。"),
        Monster(id:"peng_yao",name:"鹏",category:.yao,element:.wind,rarity:.shen,baseAtk:86,baseDef:58,baseHp:150,baseSpd:96,skills:pick(["s_jifeng","s_gangfeng","s_wulei","s_qinhe"]),evolveFrom:"kun_yao",desc:"怒而飞，其翼若垂天之云。"),
        // 蟒妖 2 段链：蟒妖 → 腾蛇（火·妖）
        Monster(id:"mang_yao",name:"蟒妖",category:.yao,element:.fire,rarity:.fan,baseAtk:50,baseDef:42,baseHp:112,baseSpd:62,skills:pick(["s_fentian","s_sanmei","s_dingshen","s_ganlin"]),evolveTo:"mang_tengshe",desc:"缠树吐信的巨蟒。"),
        Monster(id:"mang_tengshe",name:"腾蛇",category:.yao,element:.fire,rarity:.shen,baseAtk:90,baseDef:60,baseHp:156,baseSpd:88,skills:pick(["s_sanmei","s_fentian","s_wulei","s_jifeng"]),evolveFrom:"mang_yao",desc:"乘雾而游的火鳞神蛇。"),
        // 灵兽补全（龙子·瑞兽·神兽）
        Monster(id:"lingshou_pixiu",name:"貔貅",category:.lingshou,element:.metal,rarity:.shen,baseAtk:84,baseDef:72,baseHp:168,baseSpd:58,skills:pick(["s_ruijin","s_liekong","s_wanjian","s_jinzhong"]),desc:"有嘴无肛，吞财不泄。"),
        Monster(id:"lingshou_suanni",name:"狻猊",category:.lingshou,element:.fire,rarity:.xian,baseAtk:72,baseDef:60,baseHp:140,baseSpd:70,skills:pick(["s_sanmei","s_fentian","s_liekong","s_dingshen"]),desc:"好烟好坐的龙子。"),
        Monster(id:"lingshou_jiaotu",name:"椒图",category:.lingshou,element:.water,rarity:.xian,baseAtk:66,baseDef:66,baseHp:144,baseSpd:60,skills:pick(["s_jingtao","s_xuanbing","s_hantan","s_jinzhong"]),desc:"性好闭，衔环守门。"),
        Monster(id:"lingshou_pulao",name:"蒲牢",category:.lingshou,element:.water,rarity:.xian,baseAtk:68,baseDef:58,baseHp:138,baseSpd:72,skills:pick(["s_jingtao","s_wulei","s_jiuxiao","s_gangfeng"]),desc:"好鸣好吼的龙子。"),
        Monster(id:"lingshou_qiuniu",name:"囚牛",category:.lingshou,element:.wood,rarity:.xian,baseAtk:66,baseDef:62,baseHp:136,baseSpd:66,skills:pick(["s_qingmu","s_qingmu2","s_luoshi","s_qinhe"]),desc:"好音好乐的龙子。"),
        Monster(id:"lingshou_yazi",name:"睚眦",category:.lingshou,element:.metal,rarity:.xian,baseAtk:74,baseDef:60,baseHp:132,baseSpd:68,skills:pick(["s_ruijin","s_liekong","s_wanjian","s_gangfeng"]),desc:"嗜杀好斗的龙子。"),
        Monster(id:"lingshou_chaofeng",name:"嘲风",category:.lingshou,element:.wind,rarity:.xian,baseAtk:68,baseDef:56,baseHp:134,baseSpd:82,skills:pick(["s_jifeng","s_gangfeng","s_wulei","s_qinhe"]),desc:"好险好望的龙子。"),
        Monster(id:"lingshou_dangkang",name:"当康",category:.lingshou,element:.earth,rarity:.xian,baseAtk:66,baseDef:64,baseHp:146,baseSpd:58,skills:pick(["s_luoshi","s_houtu","s_jinzhong","s_fanzhen"]),desc:"其鸣如雷，兆丰年的瑞兽。"),
        Monster(id:"lingshou_yingzhao",name:"英招",category:.lingshou,element:.wind,rarity:.xian,baseAtk:70,baseDef:58,baseHp:138,baseSpd:84,skills:pick(["s_jifeng","s_gangfeng","s_qinhe","s_ganlin"]),desc:"马身人面，司帝之平圃。"),
        Monster(id:"lingshou_luwu",name:"陆吾",category:.lingshou,element:.earth,rarity:.shen,baseAtk:84,baseDef:74,baseHp:172,baseSpd:64,skills:pick(["s_luoshi","s_houtu","s_wanjian","s_jinzhong"]),desc:"虎身九尾，守昆仑之丘。"),
        Monster(id:"lingshou_kaiming",name:"开明兽",category:.lingshou,element:.metal,rarity:.shen,baseAtk:86,baseDef:76,baseHp:170,baseSpd:66,skills:pick(["s_ruijin","s_liekong","s_wanjian","s_jinzhong"]),desc:"兽身九首，司昆仑之扉。"),
        Monster(id:"lingshou_zhulong",name:"烛龙",category:.lingshou,element:.fire,rarity:.shen,baseAtk:88,baseDef:70,baseHp:174,baseSpd:72,skills:pick(["s_sanmei","s_fentian","s_wulei","s_jiuxiao"]),desc:"衔烛而照幽都，司昼夜四时。"),
        // 精补全（五行精灵）
        Monster(id:"jing_huajing",name:"花精",category:.jing,element:.wood,rarity:.fan,baseAtk:40,baseDef:44,baseHp:104,baseSpd:56,skills:pick(["s_qingmu","s_qingmu2","s_kunyao","s_ganlin"]),desc:"沾露而生的花间精灵。"),
        Monster(id:"jing_songjing",name:"松精",category:.jing,element:.wood,rarity:.fan,baseAtk:42,baseDef:48,baseHp:112,baseSpd:42,skills:pick(["s_qingmu","s_luoshi","s_jinzhong","s_kunyao"]),desc:"千年古松所化。"),
        Monster(id:"jing_mujing",name:"木精",category:.jing,element:.wood,rarity:.ling,baseAtk:54,baseDef:58,baseHp:132,baseSpd:50,skills:pick(["s_qingmu","s_qingmu2","s_luoshi","s_jinzhong"]),desc:"林深之处游走之精。"),
        Monster(id:"jing_huoling",name:"火灵",category:.jing,element:.fire,rarity:.ling,baseAtk:58,baseDef:48,baseHp:118,baseSpd:64,skills:pick(["s_fentian","s_sanmei","s_dingshen","s_ganlin"]),desc:"燧石迸火所凝之灵。"),
        Monster(id:"jing_leijing",name:"雷精",category:.jing,element:.thunder,rarity:.ling,baseAtk:56,baseDef:52,baseHp:120,baseSpd:72,skills:pick(["s_wulei","s_jiuxiao","s_gangfeng","s_dingshen"]),desc:"云层惊雷所聚之精。"),
        Monster(id:"jing_xuejing",name:"雪精",category:.jing,element:.water,rarity:.ling,baseAtk:54,baseDef:56,baseHp:124,baseSpd:58,skills:pick(["s_xuanbing","s_jingtao","s_hantan","s_kunyao"]),desc:"寒冬飞雪凝成的精灵。"),
        Monster(id:"jing_shajing",name:"沙精",category:.jing,element:.earth,rarity:.fan,baseAtk:42,baseDef:46,baseHp:108,baseSpd:52,skills:pick(["s_luoshi","s_houtu","s_jinzhong","s_fanzhen"]),desc:"大漠流沙聚成的妖精。"),
        // 怪补全（上古凶怪）
        Monster(id:"guai_yayu",name:"猰貐",category:.guai,element:.water,rarity:.xian,baseAtk:74,baseDef:60,baseHp:146,baseSpd:64,skills:pick(["s_jingtao","s_hantan","s_wulei","s_xueshi"]),desc:"状如牛而赤身，食人迅疾。"),
        Monster(id:"guai_jiuying",name:"九婴",category:.guai,element:.water,rarity:.shen,baseAtk:86,baseDef:64,baseHp:168,baseSpd:70,skills:pick(["s_jingtao","s_hantan","s_wulei","s_jiuxiao"]),desc:"九首皆有口，吐水喷火。"),
        Monster(id:"guai_zaochi",name:"凿齿",category:.guai,element:.metal,rarity:.xian,baseAtk:76,baseDef:62,baseHp:138,baseSpd:60,skills:pick(["s_ruijin","s_liekong","s_wanjian","s_gangfeng"]),desc:"齿长三尺，持盾而战的巨人。"),
        Monster(id:"guai_wuzhiqi",name:"无支祁",category:.guai,element:.water,rarity:.shen,baseAtk:84,baseDef:72,baseHp:176,baseSpd:62,skills:pick(["s_jingtao","s_hantan","s_wanjian","s_xueshi"]),desc:"淮水之怪，形若猿猴，力逾九象。"),
    ]
    static func byId(_ id: String) -> Monster? { all.first { $0.id == id } }
    static func byRarity(_ r: Rarity) -> [Monster] { all.filter { $0.rarity == r } }
    /// 1:1 镜像 Kotlin MonsterPool.byName(name)
    static func byName(_ name: String) -> Monster? { all.first { $0.name == name } }
    /// 全量妖 id 集合（供 ban/pick 等需要完整池的引擎复用）
    static let allIds: Set<String> = Set(all.map { $0.id })
    /// 1:1 镜像 Kotlin MonsterPool.starters（新手三选一推荐）
    static let starters: [Monster] = [
        byId("yao_she")!, byId("jing_saozhou")!, byId("yao_hu")!,
    ]
    static func randomEncounter() -> Monster {
        let weights: [Double] = all.map { m in
            switch m.rarity { case .fan: return 45; case .ling: return 35; case .xian: return 15; case .shen: return 5 }
        }
        let total = weights.reduce(0, +)
        var r = Double.random(in: 0...total)
        for i in all.indices { r -= weights[i]; if r <= 0 { return all[i] } }
        return all.last!
    }
}

// MARK: - 收妖引擎
struct CaptureResult { let success: Bool; let probability: Double; let struggled: Bool; let accuracy: Double }

struct CaptureEngine {
    static func ringCycleMs(rarity: Rarity, assist: Bool) -> Int {
        let base: Int = { switch rarity { case .fan: return 2600; case .ling: return 2200; case .xian: return 1900; case .shen: return 1600 } }()
        return Int(Double(base) * (assist ? 1.4 : 1.0))
    }
    static func accuracy(tapRadius: Float, minRadius: Float, maxRadius: Float, assist: Bool) -> Double {
        let span = max(1, maxRadius - minRadius)
        let raw = min(1.0, max(0.0, 1.0 - Double((tapRadius - minRadius) / span)))
        return assist ? min(1.0, max(0.0, raw * 0.8 + 0.2)) : raw
    }
    static func successProbability(accuracy: Double, rarity: Rarity, assist: Bool, captureMul: Double = 1.0) -> Double {
        let rf: Double = { switch rarity { case .fan: return 1.0; case .ling: return 0.9; case .xian: return 0.8; case .shen: return 0.7 } }()
        let eff = min(1.0, max(0.0, assist ? accuracy + 0.18 : accuracy))
        return min(1.0, max(0.0, (eff * rf * 0.92 + 0.06) * captureMul))
    }
    static func resolve(accuracy: Double, rarity: Rarity, assist: Bool, rng: () -> Double = { Double.random(in: 0...1) }, captureMul: Double = 1.0) -> CaptureResult {
        let p = successProbability(accuracy: accuracy, rarity: rarity, assist: assist, captureMul: captureMul)
        let s = rng() < p
        return CaptureResult(success: s, probability: p, struggled: !s, accuracy: accuracy)
    }
}

// MARK: - 对战引擎
struct Combatant: Identifiable {
    let id = UUID()
    let monster: Monster
    let level: Int
    let equipped: [Skill]
    let breakthrough: Int
    let effective: Stats
    var hp: Int
    var maxHp: Int { effective.hp }
    var isAlive: Bool { hp > 0 }
    init(monster: Monster, level: Int, equipped: [Skill], breakthrough: Int = 0, effective: Stats? = nil) {
        self.monster = monster; self.level = level; self.equipped = equipped
        self.breakthrough = breakthrough
        let eff = effective ?? monster.statsAtLevel(level)
        self.effective = eff
        self.hp = eff.hp
    }
}

struct DamageResult { let damage: Int; let elementMult: Double; let categoryMult: Double; let envMul: Double; let crit: Bool; let skill: Skill; let label: String }

struct BattleEngine {
    static func levelCoef(_ level: Int) -> Double { 1.0 + Double(level - 1) * 0.02 }
    static func bestCounterSkill(attacker: Combatant, defender: Combatant) -> Skill? {
        guard !attacker.equipped.isEmpty else { return nil }
        return attacker.equipped.max { s1, s2 in
            let v1 = Element.multiplier(attacker: s1.element, defender: defender.monster.element) *
                     MonsterCategory.multiplier(attacker: attacker.monster.category, defender: defender.monster.category) *
                     (s1.isDamage ? Double(s1.power) : 1)
            let v2 = Element.multiplier(attacker: s2.element, defender: defender.monster.element) *
                     MonsterCategory.multiplier(attacker: attacker.monster.category, defender: defender.monster.category) *
                     (s2.isDamage ? Double(s2.power) : 1)
            return v1 < v2
        }
    }
    static func damage(attacker: Combatant, defender: Combatant, skill: Skill, teamAttackMul: Double = 1.0, envMul: Double = 1.0, sectAttackMul: Double = 1.0) -> DamageResult {
        let atk = Double(attacker.effective.atk)
        let def = max(1.0, Double(defender.effective.def))
        let elem = Element.multiplier(attacker: skill.element, defender: defender.monster.element)
        let cat = MonsterCategory.multiplier(attacker: attacker.monster.category, defender: defender.monster.category)
        let critChance = min(1.0, max(0.0, 0.05 + skill.critBonus))
        let rnd = Double.random(in: 0.9...1.0)
        let crit = Double.random(in: 0...1) < critChance
        let critMult = crit ? 1.5 : 1.0
        let raw = (atk / def) * Double(skill.power) * levelCoef(attacker.level) * elem * cat * critMult * rnd * teamAttackMul * envMul * sectAttackMul
        let dmg = max(1, Int(raw.rounded(.down)))
        return DamageResult(damage: dmg, elementMult: elem, categoryMult: cat, envMul: envMul, crit: crit, skill: skill,
                            label: Element.multiplierLabel(attacker: skill.element, defender: defender.monster.element))
    }
}

enum Side { case player, enemy }
enum BattleOutcome { case ongoing, playerWin, enemyWin }

// MARK: - 阵容羁绊（GDD 七·技能系统：队伍组合技 / 阵容）
enum SynergyId { case yinyang, baiyao, sancai, xiangsheng, qingqiu }
struct Synergy { let id: SynergyId; let name: String; let atkMul: Double; let ultimateCharges: Int; let desc: String }

struct FormationEngine {
    static let perSynergyMul: Double = 0.12   // 每个激活羁绊的攻击增伤
    static let maxMul: Double = 0.60          // 全队攻击增伤上限（+60%）
    static let fiveElements: Set<Element> = [.metal, .wood, .water, .fire, .earth]

    /** 评估一支队伍激活的羁绊（可能 0~5 条同时激活）。 */
    static func evaluate(_ team: [Monster]) -> [Synergy] {
        guard !team.isEmpty else { return [] }
        var syn: [Synergy] = []
        let elements = team.map { $0.element }
        let water = elements.filter { $0 == .water }.count
        let fire = elements.filter { $0 == .fire }.count
        if water >= 2 && fire >= 2 {
            syn.append(Synergy(id: .yinyang, name: "阴阳调和", atkMul: perSynergyMul, ultimateCharges: 1,
                               desc: "水火既济，攻守相济（水≥2 且 火≥2）"))
        }
        let catCount = Dictionary(grouping: team, by: { $0.category }).mapValues { $0.count }
        if let top = catCount.max(by: { $0.value < $1.value }), top.value >= 3 {
            syn.append(Synergy(id: .baiyao, name: "百妖夜行", atkMul: perSynergyMul, ultimateCharges: 1,
                               desc: "同类成群，势如破竹（\(top.key.label)≥3）"))
        }
        if catCount.count >= 3 {
            syn.append(Synergy(id: .sancai, name: "三才阵", atkMul: perSynergyMul, ultimateCharges: 1,
                               desc: "三才并济，阵法自成（≥3 类别）"))
        }
        if Set(elements).isSuperset(of: fiveElements) {
            syn.append(Synergy(id: .xiangsheng, name: "相生阵", atkMul: perSynergyMul, ultimateCharges: 2,
                               desc: "五行相生，生生不息（金木水火土齐全）"))
        }
        let fox = team.filter { $0.clan == "fox" }.count
        if fox >= 3 {
            syn.append(Synergy(id: .qingqiu, name: "青丘幻月", atkMul: perSynergyMul, ultimateCharges: 2,
                               desc: "青丘幻月，魅惑九霄（狐族≥3）"))
        }
        return syn
    }

    /** 全队攻击增伤倍率（叠加上限 maxMul）。 */
    static func teamAttackMultiplier(_ syn: [Synergy]) -> Double {
        let total = min(syn.reduce(0) { $0 + $1.atkMul }, maxMul)
        return 1.0 + total
    }
}
// MARK: - 七·连携组合技（同妖技能对）
enum ComboKind { case damageMul, healPercent, control }
struct ComboEffect { let kind: ComboKind; let value: Double }
struct Combo { let id: String; let name: String; let required: [String]; let effect: ComboEffect; let cd: Int }
struct ComboEngine {
    static let COMBOS: [Combo] = [
        Combo(id: "fuyaoganlu",        name: "缚妖甘露", required: ["s_kunyao","s_ganlin"],  effect: ComboEffect(kind: .healPercent, value: 0.25), cd: 3),
        Combo(id: "kuangzhanlianzhan", name: "狂战连斩", required: ["s_liekong","s_wanjian"], effect: ComboEffect(kind: .damageMul,  value: 1.3),  cd: 2),
        Combo(id: "xuanbinghanbi",      name: "玄冰寒壁", required: ["s_hantan","s_xuanbing"], effect: ComboEffect(kind: .damageMul,  value: 1.2),  cd: 3),
        Combo(id: "lianyanfentian",    name: "烈焰焚天", required: ["s_fentian","s_sanmei"],  effect: ComboEffect(kind: .damageMul,  value: 1.35), cd: 2),
    ]
    static func availableCombos(_ equipped: [String]) -> [Combo] {
        let set = Set(equipped)
        return COMBOS.filter { $0.required.allSatisfy { set.contains($0) } }
    }
}

struct BattleEvent { let side: Side; let attackerName: String; let defenderName: String; let skillName: String; let damage: Int; let elementLabel: String; let crit: Bool; let defenderHpLeft: Int; let comboName: String? = nil; let envMul: Double = 1.0 }

class BattleSession {
    static let maxTurns = 100   // 单场回合上限：超过按剩余血量比例裁决，保证战斗必然终结
    var playerTeam: [Combatant]
    var enemyTeam: [Combatant]
    var playerTeamMul: Double = 1.0   // 玩家阵容羁绊带来的全队攻击增伤倍率
    var weather: Weather = .clear    // 天气（GDD 十九·天气地形战斗）
    var terrainBias: Element? = nil   // 地形五行偏向（复用 LocationEngine.regionElementBias）
    var playerSectAttackMul: Double = 1.0   // 宗门科技树带来的全队攻击增伤倍率
    var activePlayer = 0
    var activeEnemy = 0
    var turn = 0
    var log: [BattleEvent] = []
    var outcome: BattleOutcome = .ongoing
    var comboCd: [Int: Int] = [:]   // 连携冷却：玩家妖索引 → 剩余冷却回合（GDD 七，每场限次）
    init(playerTeam: [Combatant], enemyTeam: [Combatant], playerTeamMul: Double = 1.0, weather: Weather = .clear, terrainBias: Element? = nil, playerSectAttackMul: Double = 1.0) {
        self.playerTeam = playerTeam; self.enemyTeam = enemyTeam; self.playerTeamMul = playerTeamMul
        self.weather = weather; self.terrainBias = terrainBias; self.playerSectAttackMul = playerSectAttackMul
    }
    func currentPlayer() -> Combatant { playerTeam[activePlayer] }
    func currentEnemy() -> Combatant { enemyTeam[activeEnemy] }
    private func nextAlive(_ team: [Combatant]) -> Int { for i in team.indices where team[i].isAlive { return i }; return -1 }
    @discardableResult
    func step() -> BattleEvent? {
        guard outcome == .ongoing else { return nil }
        turn += 1
        var atk = currentPlayer(); var def = currentEnemy()
        guard let skill = BattleEngine.bestCounterSkill(attacker: atk, defender: def) else { return nil }
        let pEnv = WeatherEngine.envMultiplier(atk.monster.element, weather, terrainBias)
        let res = BattleEngine.damage(attacker: atk, defender: def, skill: skill, teamAttackMul: playerTeamMul, envMul: pEnv, sectAttackMul: playerSectAttackMul)
        // 连携组合技（GDD 七）：每玩家回合递减 CD，就绪则触发首个可用连携
        for k in comboCd.keys { comboCd[k] = (comboCd[k] ?? 0) - 1 }
        var comboName: String? = nil
        var finalDmg = res.damage
        let cd = comboCd[activePlayer] ?? 0
        if cd <= 0 {
            let combos = ComboEngine.availableCombos(atk.monster.skills.prefix(4).map { $0.id })
            if let c = combos.first {
                comboName = c.name
                switch c.effect.kind {
                case .damageMul: finalDmg = max(1, Int(Double(res.damage) * c.effect.value))
                case .healPercent:
                    let heal = Int(Double(atk.maxHp) * c.effect.value)
                    atk.hp = min(atk.maxHp, atk.hp + heal)
                    playerTeam[activePlayer] = atk   // 值类型：回写治疗结果
                case .control: break  // 占位：敌方硬控由演出层处理
                }
                comboCd[activePlayer] = c.cd
            }
        }
        def.hp -= finalDmg; enemyTeam[activeEnemy] = def
        let ev = BattleEvent(side: .player, attackerName: atk.monster.name, defenderName: def.monster.name,
                             skillName: skill.name, damage: finalDmg, elementLabel: res.label, crit: res.crit, defenderHpLeft: def.hp, comboName: comboName, envMul: pEnv)
        log.append(ev)
        if !def.isAlive {
            let ni = nextAlive(enemyTeam)
            if ni == -1 { outcome = .playerWin; return ev }
            activeEnemy = ni
        }
        var eatk = currentEnemy(); var edef = currentPlayer()
        guard let eskill = BattleEngine.bestCounterSkill(attacker: eatk, defender: edef) else { return ev }
        let eEnv = WeatherEngine.envMultiplier(eatk.monster.element, weather, terrainBias)
        let eres = BattleEngine.damage(attacker: eatk, defender: edef, skill: eskill, envMul: eEnv)
        edef.hp -= eres.damage; playerTeam[activePlayer] = edef
        log.append(BattleEvent(side: .enemy, attackerName: eatk.monster.name, defenderName: edef.monster.name,
                               skillName: eskill.name, damage: eres.damage, elementLabel: eres.label, crit: eres.crit, defenderHpLeft: edef.hp, envMul: eEnv))
        if !edef.isAlive {
            let ni = nextAlive(playerTeam)
            if ni == -1 { outcome = .enemyWin; return ev }
            activePlayer = ni
        }
        if turn >= BattleSession.maxTurns && outcome == .ongoing { resolveByHp() }
        return ev
    }

    /// 按双方剩余血量比例裁决胜负（平局判玩家胜，确保必有结果）。
    private func resolveByHp() {
        let pf = playerTeam.reduce(0.0) { $0 + max(0, Double($1.hp)) / Double($1.maxHp) }
        let ef = enemyTeam.reduce(0.0) { $0 + max(0, Double($1.hp)) / Double($1.maxHp) }
        outcome = pf >= ef ? .playerWin : .enemyWin
    }

    /// 一次性模拟到结束（快速结算 / 测试）。
    @discardableResult
    func simulateToEnd(maxTurns: Int = BattleSession.maxTurns) -> [BattleEvent] {
        var n = 0
        while outcome == .ongoing && n < maxTurns { if step() == nil { break }; n += 1 }
        return log
    }
}

// MARK: - 战斗自检测引擎（GDD 十五·数值基础 + 七行平衡）
struct BattleIntegrityEngine {
    static let referenceLevel = 50

    /// 为某元素构造等属性基准妖（单一本属攻击技，便于隔离五行效果）。
    static func referenceMonster(_ element: Element) -> Monster {
        let skill = Skill(id: "ref_\(element)", name: "\(element.label)击", type: .element, element: element, power: 100, isDamage: true, critBonus: 0.0)
        return Monster(id: "ref_\(element)", name: "\(element.label)基准妖", category: .yao,
                       element: element, rarity: .ling,
                       baseAtk: 100, baseDef: 100, baseHp: 100, baseSpd: 100, skills: [skill],
                       evolveFrom: nil, evolveTo: nil, desc: "")
    }

    /// 构造单妖基准队伍：player 用元素 a，enemy 用元素 b（属性完全对称，仅五行不同）。
    static func buildMatchup(_ a: Element, _ b: Element) -> ([Combatant], [Combatant]) {
        let ma = referenceMonster(a); let mb = referenceMonster(b)
        let ca = Combatant(monster: ma, level: referenceLevel, equipped: ma.skills, effective: ma.statsAtLevel(referenceLevel))
        let cb = Combatant(monster: mb, level: referenceLevel, equipped: mb.skills, effective: mb.statsAtLevel(referenceLevel))
        return ([ca], [cb])
    }

    /// 跑一场战斗，返回终局。
    /// 注：Swift 侧伤害随机使用系统源（非种子化）；本合成对局结果对随机数不敏感
    /// （克制方/被克方均一发带走，中性对局由先手方胜出），故报告标志稳定。
    static func runMatchup(_ a: Element, _ b: Element, seed: Int = 1) -> BattleOutcome {
        let (p, e) = buildMatchup(a, b)
        var session = BattleSession(playerTeam: p, enemyTeam: e)
        session.simulateToEnd()
        return session.outcome
    }

    /// 七行相克胜率矩阵：matrix[a][b] = A 作为玩家对 B 的胜率（1.0 胜 / 0.0 负；自对弈记 0.5）。
    static func elementMatchupMatrix(seed: Int = 1) -> [Element: [Element: Double]] {
        let elems = Element.allCases
        var m: [Element: [Element: Double]] = [:]
        for a in elems {
            var row: [Element: Double] = [:]
            for b in elems {
                if b == a { row[b] = 0.5 }
                else { row[b] = runMatchup(a, b, seed: seed) == .playerWin ? 1.0 : 0.0 }
            }
            m[a] = row
        }
        return m
    }

    struct ElementBalance {
        let element: Element
        let avgWinRate: Double
        let beatsWinRate: Double
        let beatenWinRate: Double
        let matchCount: Int
    }
    struct BalanceReport {
        let matrix: [Element: [Element: Double]]
        let perElement: [ElementBalance]
        let allTerminated: Bool
        let noDegenerateDominance: Bool
        let beatsAdvantage: Bool
        let beatenDisadvantage: Bool
    }

    static func balanceReport(seed: Int = 1) -> BalanceReport {
        let m = elementMatchupMatrix(seed: seed)
        let elems = Element.allCases
        let per = elems.map { a -> ElementBalance in
            let opp = elems.filter { $0 != a }
            let beats = opp.filter { Element.beats(attacker: a, defender: $0) }
            let beaten = opp.filter { Element.beatenBy(attacker: a, defender: $0) }
            let avg = opp.map { m[a]![$0]! }.reduce(0.0, +) / Double(opp.count)
            let beatsWR = beats.isEmpty ? 1.0 : beats.map { m[a]![$0]! }.reduce(0.0, +) / Double(beats.count)
            let beatenWR = beaten.isEmpty ? 0.0 : beaten.map { m[a]![$0]! }.reduce(0.0, +) / Double(beaten.count)
            return ElementBalance(element: a, avgWinRate: avg, beatsWinRate: beatsWR, beatenWinRate: beatenWR, matchCount: opp.count)
        }
        let allTerm = elems.allSatisfy { a in elems.allSatisfy { b in b == a || m[a]![b]! == 0.0 || m[a]![b]! == 1.0 } }
        let noDom = per.allSatisfy { $0.avgWinRate < 1.0 && $0.avgWinRate > 0.0 }
        let beatsAdv = per.allSatisfy { $0.beatsWinRate >= 0.8 }
        let beatenDis = per.allSatisfy { $0.beatenWinRate <= 0.2 }
        return BalanceReport(matrix: m, perElement: per, allTerminated: allTerm, noDegenerateDominance: noDom, beatsAdvantage: beatsAdv, beatenDisadvantage: beatenDis)
    }
}

// MARK: - 任务 / 成就系统
enum QuestCategory: Int, CaseIterable { case daily, story, trial, achievement, event }
enum ObjectiveType: Int { case catchTotal, catchElement, catchRarity, winBattle, winWithElement, enterSecret, codexRatio, trainLevel, breakthroughTotal, evolveTotal, equipTotal, forgeTotal, fuseTotal, tribulateTotal, talentTotal, gachaTotal, dungeonTotal }

struct QuestReward { let incense: Int; let lingyu: Int; let yaodan: Int; let text: String }
struct ObjectiveDef { let type: ObjectiveType; let target: Int; let ref: String; let label: String }

enum QuestState: Int, Codable { case locked=0, active=1, completed=2, claimed=3 }

struct QuestDef {
    let id: String; let title: String; let desc: String
    let category: QuestCategory; let objectives: [ObjectiveDef]; let reward: QuestReward; let daily: Bool
}

class QuestProgress: Identifiable {
    let id = UUID()
    let def: QuestDef
    var current: [Int]
    var state: QuestState
    init(def: QuestDef) {
        self.def = def
        self.current = def.objectives.map { _ in 0 }
        self.state = .active
    }
    var isComplete: Bool { def.objectives.indices.allSatisfy { current[$0] >= def.objectives[$0].target } }
    func ratio(_ i: Int) -> Float { let t = def.objectives[i].target; return t <= 0 ? 1 : Float(min(current[i], t)) / Float(t) }
    func objectiveText(_ i: Int) -> String {
        let o = def.objectives[i]
        if o.type == .codexRatio { return "图鉴完成度 \(min(current[i], o.target))% / \(o.target)%" }
        return "\(o.label)  \(min(current[i], o.target))/\(o.target)"
    }
}

enum GameEvent {
    case monsterCaught(element: Element, rarity: Rarity, secret: Bool)
    case battleWon(element: Element?)
    case secretEntered
    case codexUpdated(ratio: Int)
    case monsterTrained(level: Int)
    case monsterBrokenThrough
    case monsterEvolved
    case monsterEquipped
    case monsterForged
    case monsterFused
    case monsterTribulated
    case monsterTalentAssigned(branch: TalentBranch)
    case monsterGachaPulled
    case dungeonEntered
}

func elementNamed(_ s: String) -> Element? {
    switch s {
    case "METAL": return .metal; case "WOOD": return .wood; case "EARTH": return .earth
    case "WATER": return .water; case "FIRE": return .fire; case "WIND": return .wind; case "THUNDER": return .thunder
    default: return nil
    }
}
func rarityNamed(_ s: String) -> Rarity? {
    switch s {
    case "FAN": return .fan; case "LING": return .ling; case "XIAN": return .xian; case "SHEN": return .shen
    default: return nil
    }
}

struct QuestLibrary {
    static let all: [QuestDef] = [
        // 今日目标（每日重置）
        QuestDef(id: "d_catch3", title: "今日·收妖", desc: "今日收服 3 只妖", category: .daily,
                 objectives: [ObjectiveDef(type: .catchTotal, target: 3, ref: "", label: "收服任意妖")],
                 reward: QuestReward(incense: 30, lingyu: 0, yaodan: 0, text: "香火+30"), daily: true),
        QuestDef(id: "d_win2", title: "今日·试炼", desc: "今日赢得 2 场对战", category: .daily,
                 objectives: [ObjectiveDef(type: .winBattle, target: 2, ref: "", label: "赢得对战")],
                 reward: QuestReward(incense: 0, lingyu: 5, yaodan: 0, text: "灵玉+5"), daily: true),
        QuestDef(id: "d_secret1", title: "今日·秘境", desc: "今日进入秘境 1 次", category: .daily,
                 objectives: [ObjectiveDef(type: .enterSecret, target: 1, ref: "", label: "进入秘境")],
                 reward: QuestReward(incense: 0, lingyu: 0, yaodan: 20, text: "妖丹+20"), daily: true),
        // 山海异闻（剧情）
        QuestDef(id: "s_explore5", title: "初探山海", desc: "收服 5 只妖，踏遍山海", category: .story,
                 objectives: [ObjectiveDef(type: .catchTotal, target: 5, ref: "", label: "累计收服")],
                 reward: QuestReward(incense: 0, lingyu: 0, yaodan: 0, text: "十连祈妖券"), daily: false),
        QuestDef(id: "s_fire_win", title: "五行初识", desc: "用火行妖赢下一场对战", category: .story,
                 objectives: [ObjectiveDef(type: .winWithElement, target: 1, ref: "FIRE", label: "用火行获胜")],
                 reward: QuestReward(incense: 0, lingyu: 0, yaodan: 0, text: "星辉砂×10"), daily: false),
        QuestDef(id: "s_evolve", title: "蜕凡成圣", desc: "让一只妖进化一次（需达 25 级）", category: .story,
                 objectives: [ObjectiveDef(type: .evolveTotal, target: 1, ref: "", label: "进化次数")],
                 reward: QuestReward(incense: 0, lingyu: 30, yaodan: 0, text: "灵玉+30"), daily: false),
        // 灵脉试炼（日常资源）
        QuestDef(id: "t_ling", title: "灵脉修行", desc: "收服 1 只灵级（及以上）妖", category: .trial,
                 objectives: [ObjectiveDef(type: .catchRarity, target: 1, ref: "LING", label: "收服灵级妖")],
                 reward: QuestReward(incense: 0, lingyu: 0, yaodan: 30, text: "妖丹+30"), daily: false),
        QuestDef(id: "t_secret3", title: "秘境采集", desc: "累计进入秘境 3 次", category: .trial,
                 objectives: [ObjectiveDef(type: .enterSecret, target: 3, ref: "", label: "进入秘境")],
                 reward: QuestReward(incense: 0, lingyu: 0, yaodan: 0, text: "渡劫材料×5"), daily: false),
        QuestDef(id: "t_train", title: "勤修苦练", desc: "将一只妖培养到 15 级", category: .trial,
                 objectives: [ObjectiveDef(type: .trainLevel, target: 15, ref: "", label: "培养到 Lv.15")],
                 reward: QuestReward(incense: 60, lingyu: 0, yaodan: 40, text: "香火+60 妖丹+40"), daily: false),
        QuestDef(id: "t_break", title: "破而后立", desc: "完成 1 次突破（消耗妖丹）", category: .trial,
                 objectives: [ObjectiveDef(type: .breakthroughTotal, target: 1, ref: "", label: "突破次数")],
                 reward: QuestReward(incense: 0, lingyu: 20, yaodan: 0, text: "灵玉+20"), daily: false),
        QuestDef(id: "t_equip", title: "披甲上阵", desc: "为妖装备 1 件装备", category: .trial,
                 objectives: [ObjectiveDef(type: .equipTotal, target: 1, ref: "", label: "装备次数")],
                 reward: QuestReward(incense: 0, lingyu: 0, yaodan: 20, text: "妖丹+20"), daily: false),
        QuestDef(id: "t_forge", title: "祭炼之道", desc: "祭炼（升级）装备 1 次", category: .trial,
                 objectives: [ObjectiveDef(type: .forgeTotal, target: 1, ref: "", label: "祭炼次数")],
                 reward: QuestReward(incense: 40, lingyu: 0, yaodan: 0, text: "香火+40"), daily: false),
        QuestDef(id: "t_fuse", title: "炼妖初成", desc: "炼妖融合 1 次（消耗妖丹）", category: .trial,
                 objectives: [ObjectiveDef(type: .fuseTotal, target: 1, ref: "", label: "融合次数")],
                 reward: QuestReward(incense: 0, lingyu: 30, yaodan: 0, text: "灵玉+30"), daily: false),
        QuestDef(id: "t_tribulate", title: "渡劫飞升", desc: "渡劫飞升 1 次（满级满突破 + 耗妖丹）", category: .trial,
                 objectives: [ObjectiveDef(type: .tribulateTotal, target: 1, ref: "", label: "渡劫次数")],
                 reward: QuestReward(incense: 0, lingyu: 50, yaodan: 50, text: "灵玉+50 妖丹+50"), daily: false),
        QuestDef(id: "t_talent", title: "天赋初开", desc: "为妖点天赋 3 次（攻/防/辅 build）", category: .trial,
                 objectives: [ObjectiveDef(type: .talentTotal, target: 3, ref: "", label: "点天赋次数")],
                 reward: QuestReward(incense: 0, lingyu: 30, yaodan: 30, text: "灵玉+30 妖丹+30"), daily: false),
        // 成就
        QuestDef(id: "a_codex30", title: "图鉴新秀", desc: "图鉴完成度达到 30%", category: .achievement,
                 objectives: [ObjectiveDef(type: .codexRatio, target: 30, ref: "", label: "图鉴完成度")],
                 reward: QuestReward(incense: 0, lingyu: 0, yaodan: 0, text: "称号「巡妖使」"), daily: false),
        QuestDef(id: "a_catch10", title: "收妖大师", desc: "累计收服 10 只妖", category: .achievement,
                 objectives: [ObjectiveDef(type: .catchTotal, target: 10, ref: "", label: "累计收服")],
                 reward: QuestReward(incense: 0, lingyu: 0, yaodan: 0, text: "自选灵妖"), daily: false),
        QuestDef(id: "a_thunder", title: "元素调和", desc: "用雷行妖赢下一场对战", category: .achievement,
                 objectives: [ObjectiveDef(type: .winWithElement, target: 1, ref: "THUNDER", label: "用雷行获胜")],
                 reward: QuestReward(incense: 0, lingyu: 50, yaodan: 0, text: "灵玉+50"), daily: false),
        // 年节限时（活动）
        QuestDef(id: "e_lantern", title: "上元灯会", desc: "收服 1 只火行妖", category: .event,
                 objectives: [ObjectiveDef(type: .catchElement, target: 1, ref: "FIRE", label: "收服火行妖")],
                 reward: QuestReward(incense: 0, lingyu: 0, yaodan: 0, text: "节日外观·灯神"), daily: false),
    ]
}

class QuestLog {
    var quests: [QuestProgress]
    init() { quests = QuestLibrary.all.map { QuestProgress(def: $0) } }

    @discardableResult
    func record(_ event: GameEvent) -> [String] {
        var completed: [String] = []
        for q in quests {
            guard q.state == .active else { continue }
            for i in q.def.objectives.indices {
                let o = q.def.objectives[i]
                switch o.type {
                case .catchTotal:
                    if case .monsterCaught = event { q.current[i] = min(q.current[i] + 1, o.target) }
                case .catchElement:
                    if case let .monsterCaught(element, _, _) = event, elementNamed(o.ref) == element { q.current[i] = o.target }
                case .catchRarity:
                    if case let .monsterCaught(_, rarity, _) = event, rarityNamed(o.ref) == rarity { q.current[i] = o.target }
                case .winBattle:
                    if case .battleWon = event { q.current[i] = min(q.current[i] + 1, o.target) }
                case .winWithElement:
                    if case let .battleWon(element) = event, elementNamed(o.ref) == element { q.current[i] = o.target }
                case .enterSecret:
                    if case .secretEntered = event { q.current[i] = min(q.current[i] + 1, o.target) }
                case .codexRatio:
                    if case let .codexUpdated(ratio) = event { q.current[i] = max(q.current[i], min(ratio, o.target)) }
                case .trainLevel:
                    if case let .monsterTrained(level) = event { q.current[i] = max(q.current[i], min(level, o.target)) }
                case .breakthroughTotal:
                    if case .monsterBrokenThrough = event { q.current[i] = min(q.current[i] + 1, o.target) }
                case .evolveTotal:
                    if case .monsterEvolved = event { q.current[i] = min(q.current[i] + 1, o.target) }
                case .equipTotal:
                    if case .monsterEquipped = event { q.current[i] = min(q.current[i] + 1, o.target) }
                case .forgeTotal:
                    if case .monsterForged = event { q.current[i] = min(q.current[i] + 1, o.target) }
                case .fuseTotal:
                    if case .monsterFused = event { q.current[i] = min(q.current[i] + 1, o.target) }
                case .tribulateTotal:
                    if case .monsterTribulated = event { q.current[i] = min(q.current[i] + 1, o.target) }
                case .talentTotal:
                    if case .monsterTalentAssigned = event { q.current[i] = min(q.current[i] + 1, o.target) }
                case .gachaTotal:
                    if case .monsterGachaPulled = event { q.current[i] = min(q.current[i] + 1, o.target) }
                case .dungeonTotal:
                    if case .dungeonEntered = event { q.current[i] = min(q.current[i] + 1, o.target) }
                }
            }
            if q.isComplete && q.state == .active { q.state = .completed; completed.append(q.def.title) }
        }
        return completed
    }

    func claim(id: String) -> QuestReward? {
        guard let q = quests.first(where: { $0.def.id == id }), q.state == .completed else { return nil }
        q.state = .claimed
        return q.def.reward
    }
    func byCategory(_ c: QuestCategory) -> [QuestProgress] { quests.filter { $0.def.category == c } }
    var completableCount: Int { quests.filter { $0.state == .completed }.count }
    var activeCount: Int { quests.filter { $0.state == .active }.count }
    func resetDaily() {
        for q in quests where q.def.daily && q.state != .locked {
            for i in q.current.indices { q.current[i] = 0 }
            q.state = .active
        }
    }
    func snapshot() -> [QuestSave] { quests.map { QuestSave(id: $0.def.id, current: $0.current, state: $0.state) } }
    func applySnapshot(_ list: [QuestSave]) {
        for s in list {
            guard let q = quests.first(where: { $0.def.id == s.id }) else { continue }
            let capped = Array(s.current.prefix(q.current.count))
            for i in q.current.indices { q.current[i] = capped.count > i ? capped[i] : q.current[i] }
            q.state = s.state
        }
    }
}

// MARK: - ② LBS / AR 收妖（GDD 十六·探索）
enum SolarTerm: String, Codable { case none="none", zhongyuan="zhongyuan", chunjie="chunjie" }
enum RegionTheme: String, Codable, CaseIterable { case shanlin="shanlin", shuiyu="shuiyu", gumu="gumu", donghai="donghai" }

struct SpawnContext {
    let lat: Double
    let lng: Double
    let hourOfDay: Int
    let solarTerm: SolarTerm = .none
    let ts: Int64 = 0          // 当前定位 epoch 秒（GPS 防作弊用）
    let prevLat: Double? = nil // 上一次定位纬度
    let prevLng: Double? = nil // 上一次定位经度
    let prevTs: Int64 = 0      // 上一次定位 epoch 秒
}

// GPS 防作弊判定档位（GDD 十六·探索 / 户外灵脉）
enum IntegrityTier { case normal, degraded, rejected }

struct IntegrityResult {
    let ok: Bool            // tier != .rejected 即可收妖（.degraded 仍可收但降品质）
    let reason: String
    let speedKmh: Double
    let tier: IntegrityTier
    let degraded: Bool      // 降权标记（.degraded 或 .rejected 均为 true）
}

// 信誉分（GDD 十六·公平与稳健）：与 GPS 防作弊（LocationEngine.spawnIntegrity）闭环
enum ReputationTier { case excellent, observed, restricted }
enum CheatKind { case teleport, speed, unknown }

struct ReputationEngine {
    static let DEFAULT_SCORE = 100
    static let MAX_SCORE = 100
    static let MIN_SCORE = 0
    static let RESTRICTED_BELOW = 60
    static let OBSERVED_BELOW = 80
    static let PENALTY: [CheatKind: Int] = [.teleport: 18, .speed: 12, .unknown: 10]

    static func tier(_ score: Int) -> ReputationTier {
        let s = min(MAX_SCORE, max(MIN_SCORE, score))
        if s < RESTRICTED_BELOW { return .restricted }
        if s < OBSERVED_BELOW { return .observed }
        return .excellent
    }
    static func tierLabel(_ score: Int) -> String {
        switch tier(score) {
        case .excellent: return "优秀"
        case .observed: return "观察"
        case .restricted: return "受限"
        }
    }
    static func rareSpawnMultiplier(_ score: Int) -> Double {
        return tier(score) == .observed ? 0.7 : 1.0
    }
    static func canExploreLbs(_ score: Int) -> Bool {
        return tier(score) != .restricted
    }
    static func applyPenalty(_ score: Int, _ kind: CheatKind) -> Int {
        let p = PENALTY[kind] ?? 10
        return min(MAX_SCORE, max(MIN_SCORE, score - p))
    }
    static func dailyRecover(_ score: Int) -> Int {
        return min(MAX_SCORE, max(MIN_SCORE, score + 1))
    }
}

struct LocationEngine {
    static let SOFT_SPEED_KMH = 30.0   // 软档阈值：>30km/h 降品质（神不出、仙×0.2），不阻断、不扣分
    static let TELEPORT_METERS = 5000.0
    static let TELEPORT_WINDOW_SEC: Int64 = 180   // GDD 瞬移<3min 取上限 180s
    // 全部合法灵脉节点名（据点战占领目标白名单，与 nearbyNode 地图同值，单一真源）
    static let strongholdNodes: Set<String> = ["城隍桥", "古庙", "老槐树", "东海之滨"]

    // 灵脉节点坐标真源（与 nearbyNode 同值，单一真源）
    static let nodeEntries: [MapNode] = [
        MapNode(lat: 31.23, lng: 121.47, name: "城隍桥"),
        MapNode(lat: 30.25, lng: 120.16, name: "古庙"),
        MapNode(lat: 25.03, lng: 102.71, name: "老槐树"),
        MapNode(lat: 30.62, lng: 122.08, name: "东海之滨"),
    ]
    static func nodeCoordinates() -> [MapNode] { nodeEntries }

    static func regionElementBias(_ theme: RegionTheme) -> Element {
        switch theme { case .shanlin: return .wood; case .shuiyu: return .water; case .gumu: return .earth; case .donghai: return .water }
    }
    static func resolveRegion(_ ctx: SpawnContext) -> RegionTheme {
        let a = (Int(ctx.lat * 1000)) ^ (Int(ctx.lng * 1000))
        let idx = abs(a) % RegionTheme.allCases.count
        return RegionTheme.allCases[idx]
    }
    static func isNight(_ ctx: SpawnContext) -> Bool { ctx.hourOfDay == 23 || ctx.hourOfDay == 0 }
    static func rarityWeight(_ r: Rarity) -> Double { switch r { case .fan: return 45; case .ling: return 35; case .xian: return 15; case .shen: return 5 } }
    static func weightOf(_ m: Monster, _ ctx: SpawnContext, _ degraded: Bool = false) -> Double {
        var w = rarityWeight(m.rarity)
        if degraded {
            switch m.rarity {
            case .shen: return 0.0                 // 神不出
            case .xian: w *= 0.2                   // 仙降品质
            default: break                          // 凡/灵不变
            }
        }
        if m.element == regionElementBias(resolveRegion(ctx)) { w *= 1.8 }
        if ctx.solarTerm == .zhongyuan && m.category == .guai { w *= 2.0 }
        if ctx.solarTerm == .chunjie && m.isFestival { w *= 3.0 }
        if isNight(ctx) && m.category == .guai { w *= 1.6 }
        return w
    }
    static func nearbySpawns(_ ctx: SpawnContext, _ count: Int, _ rng: () -> Double = { Double.random(in: 0...1) }, _ degraded: Bool = false) -> [String] {
        let weights = MonsterPool.all.map { weightOf($0, ctx, degraded) }
        let total = weights.reduce(0, +)
        var out: [String] = []
        for _ in 0..<count {
            var r = rng() * total
            var chosen = MonsterPool.all.last!
            for i in MonsterPool.all.indices { r -= weights[i]; if r <= 0 { chosen = MonsterPool.all[i]; break } }
            out.append(chosen.id)
        }
        return out
    }
    // 给定坐标是否靠近某灵脉节点（切片：固定 POI；真实实现换地图 SDK 距离查询）
    static func nearbyNode(_ ctx: SpawnContext) -> String? {
        let key = String(format: "%.2f:%.2f", ctx.lat, ctx.lng)
        let map: [String: String] = [
            "31.23:121.47": "城隍桥",
            "30.25:120.16": "古庙",
            "25.03:102.71": "老槐树",
            "30.62:122.08": "东海之滨",
        ]
        return map[key]
    }
    // Haversine 球面距离（米）
    static func haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double) -> Double {
        let R = 6_371_000.0
        let dLat = (lat2 - lat1) * .pi / 180
        let dLng = (lng2 - lng1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * sin(dLng / 2) * sin(dLng / 2)
        return R * 2 * asin(sqrt(a))
    }
    // GPS 防作弊：相邻定位间速度 / 瞬移检测（三档）
    static func spawnIntegrity(_ ctx: SpawnContext) -> IntegrityResult {
        guard let pLat = ctx.prevLat, let pLng = ctx.prevLng else { return IntegrityResult(ok: true, reason: "首次定位", speedKmh: 0, tier: .normal, degraded: false) }
        let dt = ctx.ts - ctx.prevTs
        if dt <= 0 { return IntegrityResult(ok: true, reason: "时间无效-跳过", speedKmh: 0, tier: .normal, degraded: false) }
        let dist = haversine(lat1: pLat, lng1: pLng, lat2: ctx.lat, lng2: ctx.lng)
        let speed = dist / Double(dt) * 3.6
        if dist >= TELEPORT_METERS && dt <= TELEPORT_WINDOW_SEC {
            return IntegrityResult(ok: false, reason: String(format: "检测到瞬移：%.0f 米 / %d 秒", dist, Int(dt)), speedKmh: speed, tier: .rejected, degraded: true)
        }
        if speed > SOFT_SPEED_KMH {
            return IntegrityResult(ok: true, reason: String(format: "速度过快(降品质)：%.0f km/h", speed), speedKmh: speed, tier: .degraded, degraded: true)
        }
        return IntegrityResult(ok: true, reason: "正常", speedKmh: speed, tier: .normal, degraded: false)
    }
    // 受信任刷怪抽样：rejected 返回空；degraded 按降品质权重；normal 正常
    static func safeSpawns(_ ctx: SpawnContext, _ count: Int, _ rng: () -> Double = { Double.random(in: 0...1) }) -> [String] {
        let res = spawnIntegrity(ctx)
        switch res.tier {
        case .rejected: return []
        default: return nearbySpawns(ctx, count, rng, res.degraded)
        }
    }
}

// 灵脉节点坐标真源（派生自 LocationEngine.nodeEntries 单一真源）
struct MapNode {
    let lat: Double
    let lng: Double
    let name: String
}

enum PoiKind { case lingmai, stronghold }

struct MapPoi {
    let id: String
    let name: String
    let lat: Double
    let lng: Double
    let kind: PoiKind
}

// 真实地图 SDK 适配契约（GDD·LBS 探索双模式）；运行时由平台层接入（ARKit/MapKit/高德/腾讯地图）
struct MapSdkAdapter {
    static func isAvailable() -> Bool { false }   // 占位：需地图 SDK
    static func overlayPois(_ pois: [MapPoi]) -> Bool { false }
    static func setPlayerLayer(lat: Double, lng: Double) -> Bool { false }
    static func focusRegion(lat: Double, lng: Double, zoom: Int) -> Bool { false }
    // 复用 LocationEngine 真源构建 POI（灵脉全量 + 据点白名单标记）
    static func buildPois() -> [MapPoi] {
        return LocationEngine.nodeEntries.map { node in
            MapPoi(
                id: node.name,
                name: node.name,
                lat: node.lat,
                lng: node.lng,
                kind: LocationEngine.strongholdNodes.contains(node.name) ? .stronghold : .lingmai
            )
        }
    }
}

// MARK: - 三·在家秘境刷妖引擎（GDD 三·探索双模式·在家秘境）
struct SecretRealmResult {
    let theme: RegionTheme
    let element: Element
    let spawns: [String]
}

struct SecretRealmEngine {
    static let SPAWN_COUNT = 6
    static let INCENSE_COST = 20

    // 秘境稀有度权重：凡/灵主导（对比 LBS 凡45/灵35/仙15/神5，秘境更压低高阶）
    static func rarityWeight(_ r: Rarity) -> Double {
        switch r { case .fan: return 60; case .ling: return 30; case .xian: return 8; case .shen: return 2 }
    }
    static let ELEMENT_BIAS = 3.0

    static func themeElementBias(_ theme: RegionTheme) -> Element { LocationEngine.regionElementBias(theme) }

    static func pickTheme(_ rng: () -> Double = { Double.random(in: 0...1) }) -> RegionTheme {
        let idx = Int(rng() * Double(RegionTheme.allCases.count)) % RegionTheme.allCases.count
        return RegionTheme.allCases[idx]
    }
    static func weightOf(_ m: Monster, _ theme: RegionTheme) -> Double {
        let base = rarityWeight(m.rarity)
        return m.element == themeElementBias(theme) ? base * ELEMENT_BIAS : base
    }
    static func generateSpawns(_ theme: RegionTheme, _ count: Int, _ rng: () -> Double = { Double.random(in: 0...1) }) -> [String] {
        let weights = MonsterPool.all.map { weightOf($0, theme) }
        let total = weights.reduce(0, +)
        var out: [String] = []
        for _ in 0..<count {
            var r = rng() * total
            var chosen = MonsterPool.all.last!
            for i in MonsterPool.all.indices { r -= weights[i]; if r <= 0 { chosen = MonsterPool.all[i]; break } }
            out.append(chosen.id)
        }
        return out
    }
}

// ── 十八·新手引导 / 7 日留存（TutorialEngine） ──
enum TutorialStep: Int, CaseIterable {
    case catchStep=1, explore=2, battle=3, faction=4, checkin=5
    var order: Int { rawValue }
    var forceable: Bool { rawValue <= 3 }
    var title: String {
        switch self {
        case .catchStep: return "撞见凡级妖，点按+光环收服"
        case .explore:   return "查看灵脉地图，入秘境"
        case .battle:    return "首场 2.5D 对战，高亮克制技"
        case .faction:   return "三派择门（茅山/龙虎山/阁皂）"
        case .checkin:   return "签到点 + 图鉴 + 今日目标"
        }
    }
    static let byOrder = TutorialStep.allCases.sorted { $0.order < $1.order }
    static let lastOrder = TutorialStep.byOrder.last!.order
}
enum RealmFeature: Int, CaseIterable {
    case realm=2, dungeon=3, gacha=4, social=5
    var unlockDay: Int { rawValue }
}
struct TutorialEngine {
    static func currentStep(_ completedOrder: Int) -> TutorialStep? {
        TutorialStep.byOrder.first { $0.order > completedOrder }
    }
    static func isForced(_ step: TutorialStep) -> Bool { step.order <= 3 }
    static func allDone(_ completedOrder: Int, _ skipped: Bool) -> Bool {
        completedOrder >= TutorialStep.lastOrder || skipped
    }
    static func unlockedFeatures(_ day: Int) -> Set<RealmFeature> {
        Set(RealmFeature.allCases.filter { $0.unlockDay <= day })
    }
    static func signInReward(_ day: Int) -> Int {
        min(10 * day, 70)
    }
}

// MARK: - 十九·长线留存：家园洞府（放置产香火/妖丹 + 阵法风水加成）
struct HomesteadResult { let incense: Int; let pills: Int; let hours: Double; let capped: Bool }
struct HomesteadEngine {
    static let incensePerHourBase = 20.0, pillPerHourBase = 1.0
    static let maxOfflineHours = 12.0, hourMs = 3_600_000.0
    static let fengshuiIncenseMul = 0.10, fengshuiPillPerLevel = 0.1, fengshuiAttackPerLevel = 0.02
    static func incenseRate(_ level: Int) -> Double { incensePerHourBase * (1.0 + fengshuiIncenseMul * Double(level)) }
    static func pillRate(_ level: Int) -> Double { pillPerHourBase + fengshuiPillPerLevel * Double(level) }
    static func fengshuiAttackBonus(_ level: Int) -> Double { fengshuiAttackPerLevel * Double(level) }
    static func upgradeCost(_ level: Int) -> Int { 100 * (level + 1) }
    static func compute(_ level: Int, _ nowMs: Int64, _ lastMs: Int64) -> HomesteadResult {
        if lastMs <= 0 || nowMs <= lastMs { return HomesteadResult(incense: 0, pills: 0, hours: 0.0, capped: false) }
        let elapsed = Double(nowMs - lastMs) / hourMs
        let cappedHours = min(elapsed, maxOfflineHours)
        let incense = Int(incenseRate(level) * cappedHours)
        let pills = Int(pillRate(level) * cappedHours)
        return HomesteadResult(incense: incense, pills: pills, hours: cappedHours, capped: elapsed > maxOfflineHours)
    }
}

// MARK: - 十九·长线留存：赛季通行证（90 天双轨）
struct SeasonReward { let incense: Int; let lingyu: Int; let yaodan: Int }
struct SeasonClaimResult {
    let tier: Int
    let claimedFree: Bool
    let claimedPaid: Bool
    let incense: Int
    let lingyu: Int
    let yaodan: Int
}
struct SeasonEngine {
    static let seasonLengthDays = 90
    static let xpPerTier = 100
    static let tiers = 60
    static let dayMs = 86_400_000.0
    static let xpCatch = 5, xpBattle = 10, xpDungeon = 15, xpSignin = 8
    /// 赛季内第几天：未开始返回 0；进行中 1..seasonLengthDays；已结束 > 长度。
    static func dayIndex(_ startMs: Int64, _ nowMs: Int64) -> Int {
        if startMs <= 0 || nowMs < startMs { return 0 }
        return Int((nowMs - startMs) / Int64(dayMs)) + 1
    }
    static func inSeason(_ startMs: Int64, _ nowMs: Int64) -> Bool {
        let d = dayIndex(startMs, nowMs); return d >= 1 && d <= seasonLengthDays
    }
    static func daysRemaining(_ startMs: Int64, _ nowMs: Int64) -> Int {
        let d = dayIndex(startMs, nowMs)
        if d <= 0 { return seasonLengthDays }
        if d > seasonLengthDays { return 0 }
        return seasonLengthDays - d + 1
    }
    static func tierFromXp(_ xp: Int) -> Int { min(xp / xpPerTier, tiers) }
    static func xpForTier(_ tier: Int) -> Int { tier * xpPerTier }
    static func freeReward(_ tier: Int) -> SeasonReward {
        if tier % 10 == 0 { return SeasonReward(incense: 200, lingyu: 0, yaodan: 15) }
        return SeasonReward(incense: 30, lingyu: 0, yaodan: 3)
    }
    static func paidReward(_ tier: Int) -> SeasonReward {
        if tier % 10 == 0 { return SeasonReward(incense: 0, lingyu: 60, yaodan: 20) }
        return SeasonReward(incense: 0, lingyu: 10, yaodan: 5)
    }
}

// MARK: - 十六·公平与稳健：疲劳值 / 单日收益封顶
struct FatigueEngine {
    static let maxFatigue = 100
    static let softCap = 60
    static let fatigueCatch = 2, fatigueBattle = 4, fatigueDungeon = 8, fatigueSignin = 3
    /// 当前疲劳对应的收益倍率：封顶 0.0 / 疲劳 0.5 / 充沛 1.0
    static func incomeMul(_ fatigue: Int) -> Double {
        if fatigue >= maxFatigue { return 0.0 }
        if fatigue >= softCap { return 0.5 }
        return 1.0
    }
    static func addFatigue(_ current: Int, _ delta: Int) -> Int {
        min(max(current + delta, 0), maxFatigue)
    }
    static func resetDaily() -> Int { 0 }
    static func label(_ fatigue: Int) -> String {
        if fatigue >= maxFatigue { return "透支·收益封顶" }
        if fatigue >= softCap { return "疲劳·收益减半" }
        return "充沛"
    }
}

// MARK: - 每日 0 点静默重置（GDD 十六/十九/二十「每日」语义收口）
enum DailyResetTarget: String, CaseIterable {
    case quests = "quests"
    case secretRealm = "secret_realm"
    case dungeon = "dungeon"
    case shop = "shop"
    case stronghold = "stronghold"
    case reputation = "reputation"
    case fatigue = "fatigue"
    var desc: String {
        switch self {
        case .quests:      return "每日任务刷新"
        case .secretRealm: return "在家秘境免费次数回满 (3)"
        case .dungeon:     return "副本当日进入计数清零"
        case .shop:        return "引妖香每日购买上限重置"
        case .stronghold:  return "据点战占领节点重置"
        case .reputation:  return "信誉分每日缓慢恢复 +1"
        case .fatigue:     return "疲劳值每日重置为 0"
        }
    }
}

struct DailyResetEngine {
    /// 每日重置的权威系统清单
    static let targets: [DailyResetTarget] = DailyResetTarget.allCases
    static let targetCount: Int = targets.count
    /// 跨天判定（纯函数）：首启空串视为跨天；否则以「上次重置日 != 今日」为判据
    static func isCrossDay(_ lastResetDay: String, _ today: String) -> Bool {
        return lastResetDay.isEmpty || lastResetDay != today
    }
}

// MARK: - 每日静默重置调度契约（GDD·十六/十九/二十：跨天 + 赛季推进）

/// 调度状态（与平台后台能力对应）
enum SchedulerStatus: String, CaseIterable {
    case idle = "idle"
    case registered = "registered"
    case suspended = "suspended"       // 低电量 / 后台受限
    case unavailable = "unavailable"
}

/// 每日静默重置——平台调度契约镜像（判定收敛在 shared，平台层只负责「何时触发」）
struct DailyResetScheduler {
    /// 平台是否具备后台定时 / 0 点推送能力（占位：平台层接入后返回真实能力）
    static func isAvailable() -> Bool { return false }
    /// 注册每日 0 点静默重置任务（幂等）
    static func register() -> Bool { return false }
    /// 注销调度（幂等）
    static func unregister() {}
    /// 当前调度状态
    static func status() -> SchedulerStatus { return .unavailable }
    /// 复用 DailyResetEngine.isCrossDay 判据：跨天返回 targets，否则空列表
    static func resolvePending(_ lastResetDay: String, _ today: String) -> [DailyResetTarget] {
        return DailyResetEngine.isCrossDay(lastResetDay, today) ? DailyResetEngine.targets : []
    }
}

/// 每日静默重置——赛季推进纯函数（复用 SeasonEngine 周期，便于等价验证）
struct DailyResetSchedulerContracts {
    /// 返回已跨过的赛季数（0 表示仍在同一赛季）
    static func seasonsElapsed(_ seasonStartMs: Int64, _ nowMs: Int64) -> Int {
        if nowMs < seasonStartMs { return 0 }
        let elapsedDays = (nowMs - seasonStartMs) / Int64(SeasonEngine.dayMs)
        return Int(elapsedDays) / Int(SeasonEngine.seasonLengthDays)
    }
}

// MARK: - 排位赛段位天梯（GDD 二十·竞技与荣誉）
struct RankEngine {
    static let tiers = ["炼气", "筑基", "金丹", "元婴", "化神"]
    static let minTierIndex = 0
    static let maxTierIndex = 4
    static let pointsToPromote = 100
    static let winPoints = 20
    static let lossPoints = 15
    static func tierIndex(_ name: String) -> Int {
        if let i = tiers.firstIndex(of: name) { return i } else { return minTierIndex }
    }
    static func tierName(_ index: Int) -> String {
        let i = min(max(index, minTierIndex), maxTierIndex)
        return tiers[i]
    }
    /// 升段荣誉奖励（按新段位发少量货币，展示型荣誉为主）
    static func rankReward(_ tierIndex: Int) -> RankReward {
        switch tierIndex {
        case 1:  return RankReward(incense: 30, lingyu: 0,  yaodan: 0)
        case 2:  return RankReward(incense: 0,  lingyu: 30, yaodan: 0)
        case 3:  return RankReward(incense: 60, lingyu: 30, yaodan: 0)
        case 4:  return RankReward(incense: 0,  lingyu: 80, yaodan: 10)
        default: return RankReward(incense: 0,  lingyu: 0,  yaodan: 0)
        }
    }
    /// 应用一场对局结果：胜加点满100升段、负扣点不足0降段、化神封顶/炼气保底
    static func applyResult(_ tierIndex: Int, _ points: Int, _ won: Bool) -> RankResult {
        var t = min(max(tierIndex, minTierIndex), maxTierIndex)
        var p = min(max(points, 0), pointsToPromote - 1)   // 段内积分合法区间，抵御坏档/越界输入
        var promoted = false
        var demoted = false
        if won {
            p += winPoints
            while p >= pointsToPromote && t < maxTierIndex {
                p -= pointsToPromote
                t += 1
                promoted = true
            }
            if t == maxTierIndex { p = min(p, pointsToPromote - 1) }
        } else {
            p -= lossPoints
            while p < 0 && t > minTierIndex {
                p += pointsToPromote
                t -= 1
                demoted = true
            }
            if t == minTierIndex { p = max(p, 0) }
        }
        return RankResult(tierIndex: t, points: p, promoted: promoted, demoted: demoted)
    }
}
struct RankResult { let tierIndex: Int; let points: Int; let promoted: Bool; let demoted: Bool }
struct RankReward { let incense: Int; let lingyu: Int; let yaodan: Int }

// MARK: - 排位赛 Ban/Pick 选将（GDD 二十·竞技与荣誉，补全 RankEngine 注释中 ban/pick 缺口）
enum RBPPSide: String, CaseIterable { case A, B }
enum RBPPResult: String, CaseIterable {
    case ok, invalidMonster, alreadyBanned, banLimit, pickBanned, alreadyPicked, pickLimit
}
struct RBPPDraftState {
    var bannedA: Set<String> = []
    var bannedB: Set<String> = []
    var pickedA: [String] = []
    var pickedB: [String] = []
    var denied: Set<String> { bannedA.union(bannedB) }
}
struct RankBanPickEngine {
    static let banPerSide = 2
    static let pickPerSide = 3
    /// 当前可用池 = 妖池 − 双方 ban 并集
    static func availablePool(_ s: RBPPDraftState) -> Set<String> {
        MonsterPool.allIds.subtracting(s.denied)
    }
    private static func mineBan(_ s: RBPPDraftState, _ side: RBPPSide) -> Set<String> {
        side == .A ? s.bannedA : s.bannedB
    }
    private static func minePick(_ s: RBPPDraftState, _ side: RBPPSide) -> [String] {
        side == .A ? s.pickedA : s.pickedB
    }
    /// 任一方 ban 一只妖；成功返回 (新 state, .ok)，非法返回 (原 state, 原因)
    static func ban(_ s: RBPPDraftState, _ side: RBPPSide, _ id: String) -> (RBPPDraftState, RBPPResult) {
        if !MonsterPool.allIds.contains(id) { return (s, .invalidMonster) }
        if s.denied.contains(id) { return (s, .alreadyBanned) }
        let mine = mineBan(s, side)
        if mine.count >= banPerSide { return (s, .banLimit) }
        var ns = s
        if side == .A { ns.bannedA = mine.union([id]) } else { ns.bannedB = mine.union([id]) }
        return (ns, .ok)
    }
    /// 任一方 pick 一只妖（必须从可用池、无重复、未超上限）
    static func pick(_ s: RBPPDraftState, _ side: RBPPSide, _ id: String) -> (RBPPDraftState, RBPPResult) {
        if !MonsterPool.allIds.contains(id) { return (s, .invalidMonster) }
        if s.denied.contains(id) { return (s, .pickBanned) }
        let mine = minePick(s, side)
        if mine.contains(id) { return (s, .alreadyPicked) }
        if mine.count >= pickPerSide { return (s, .pickLimit) }
        var ns = s
        if side == .A { ns.pickedA = mine + [id] } else { ns.pickedB = mine + [id] }
        return (ns, .ok)
    }
    /// 选将是否完成：双方都 ban 满且 pick 满
    static func isComplete(_ s: RBPPDraftState) -> Bool {
        s.bannedA.count == banPerSide && s.bannedB.count == banPerSide &&
        s.pickedA.count == pickPerSide && s.pickedB.count == pickPerSide
    }
    /// 入站队伍合法性防护（坏档 / 外部输入）：不含被 ban 妖、无重复、规模不超 pickPerSide
    static func isTeamValid(_ s: RBPPDraftState, _ side: RBPPSide, _ team: [String]) -> Bool {
        if team.contains(where: { s.denied.contains($0) }) { return false }
        if team.count != Set(team).count { return false }
        if team.count > pickPerSide { return false }
        return true
    }
}

// MARK: - 成就 / 勋章 / 称号（GDD 二十·竞技与荣誉·展示型荣誉）
struct HonorEngine {
    struct HonorProfile {
        let achievementCount: Int
        let achievementTotal: Int
        let rankTierIndex: Int
        let codexRatio: Int
        let tribulationTotal: Int
    }
    struct Medal { let id: String; let name: String; let desc: String; let req: Int }
    static let medals: [Medal] = [
        Medal(id: "medal_bronze",  name: "青铜妖士", desc: "达成 1 项成就", req: 1),
        Medal(id: "medal_silver",  name: "白银妖师", desc: "达成 4 项成就", req: 4),
        Medal(id: "medal_gold",    name: "黄金妖尊", desc: "达成 8 项成就", req: 8),
        Medal(id: "medal_diamond", name: "钻石妖圣", desc: "达成全部成就",  req: Int.max),
    ]
    static func unlockedMedals(_ p: HonorProfile) -> [Medal] {
        medals.filter {
            if $0.req == Int.max { return p.achievementTotal > 0 && p.achievementCount >= p.achievementTotal }
            return p.achievementCount >= $0.req
        }
    }
    struct Title { let id: String; let name: String; let desc: String }
    static let titles: [Title] = [
        Title(id: "title_shouyao",  name: "收妖大师", desc: "达成任意 1 项成就"),
        Title(id: "title_xunyao",   name: "巡妖使",   desc: "图鉴完成度 ≥ 30%"),
        Title(id: "title_tuojie",   name: "渡劫真君", desc: "完成 1 次渡劫飞升"),
        Title(id: "title_huashen",  name: "化神尊者", desc: "段位达到化神"),
        Title(id: "title_tujian",   name: "山海通志", desc: "图鉴完成度 100%"),
        Title(id: "title_dacheng",  name: "妖志大成", desc: "达成全部成就"),
        Title(id: "title_yingxiong",name: "山海英雄", desc: "达成 6 项成就"),
        Title(id: "title_zhuanji",  name: "妖志宗师", desc: "图鉴完成度 ≥ 80%"),
        Title(id: "title_jiuzhuan", name: "九转真君", desc: "完成 3 次渡劫飞升"),
        Title(id: "title_jindan",   name: "金丹道君", desc: "段位达到金丹"),
        Title(id: "title_yuanying", name: "元婴老祖", desc: "段位达到元婴"),
        Title(id: "title_zhongliu", name: "中流砥柱", desc: "达成 12 项成就"),
    ]
    static let noTitle = ""
    /// 判定某个称号是否解锁（基于画像）
    static func isUnlocked(_ id: String, _ p: HonorProfile) -> Bool {
        switch id {
        case "title_shouyao":  return p.achievementCount >= 1
        case "title_xunyao":   return p.codexRatio >= 30
        case "title_tuojie":   return p.tribulationTotal >= 1
        case "title_huashen":  return p.rankTierIndex >= 4
        case "title_tujian":   return p.codexRatio >= 100
        case "title_dacheng":  return p.achievementTotal > 0 && p.achievementCount >= p.achievementTotal
        case "title_yingxiong":return p.achievementCount >= 6
        case "title_zhuanji":  return p.codexRatio >= 80
        case "title_jiuzhuan": return p.tribulationTotal >= 3
        case "title_jindan":   return p.rankTierIndex >= 2
        case "title_yuanying": return p.rankTierIndex >= 3
        case "title_zhongliu": return p.achievementCount >= 12
        default: return false
        }
    }
    static func unlockedTitles(_ p: HonorProfile) -> [Title] {
        titles.filter { isUnlocked($0.id, p) }
    }
}

// MARK: - 天气地形战斗（GDD 十九·长线留存）
enum Weather: String, CaseIterable, Codable { case clear="clear", rain="rain", snow="snow", windy="windy" }
struct WeatherEngine {
    /// 某行元素在指定天气下的攻击偏置倍率（无偏置为 1.0）
    static func weatherAttackMul(_ elem: Element, _ weather: Weather) -> Double {
        switch weather {
        case .clear: return 1.0
        case .rain:  return (elem == .water) ? 1.3 : (elem == .fire ? 0.8 : 1.0)
        case .snow:  return (elem == .fire) ? 0.7 : (elem == .water ? 1.2 : 1.0)
        case .windy: return (elem == .wind) ? 1.3 : (elem == .earth ? 0.8 : 1.0)
        }
    }
    /// 地形偏向乘子：攻击元素 == 地形偏向元素 → ×1.2，否则 ×1.0
    static func terrainAttackMul(_ elem: Element, _ bias: Element?) -> Double {
        guard let b = bias, b == elem else { return 1.0 }
        return 1.2
    }
    /// 综合环境乘子（天气 × 地形）
    static func envMultiplier(_ elem: Element, _ weather: Weather, _ bias: Element?) -> Double {
        weatherAttackMul(elem, weather) * terrainAttackMul(elem, bias)
    }
}

enum ARStatus: String, Codable { case unsupported="unsupported", permissionDenied="permissionDenied", ready="ready" }
struct ARCaptureController {
    static func status() -> ARStatus { .unsupported }   // 占位：平台层应返回真实状态
    static func resolveAtAnchor(accuracy: Double, rarity: Rarity, assist: Bool, rng: () -> Double = { Double.random(in: 0...1) }) -> CaptureResult {
        CaptureEngine.resolve(accuracy: accuracy, rarity: rarity, assist: assist, rng: rng)
    }
}

// MARK: - AR 摄像头后端契约（平台层实现，复用 ARStatus / CaptureEngine）
struct ArCaptureBackend {
    static func isCameraAvailable() -> Bool { false }              // 占位：平台层检测摄像头
    static func requestPermission() -> ARStatus { .unsupported }   // 占位：平台层驱动系统授权
    static func beginSession(_ anchorMonsterId: String) -> Bool { false }  // 占位：未接入会话
    static func endSession() {}                                     // 占位
    static func resolveCapture(accuracy: Double, rarity: Rarity, assist: Bool, rng: () -> Double = { Double.random(in: 0...1) }) -> CaptureResult {
        CaptureEngine.resolve(accuracy: accuracy, rarity: rarity, assist: assist, rng: rng)
    }
}

// MARK: - ③ 祈妖抽卡（GDD 十四·经济与变现）
enum SignTier: String, Codable { case zhong="zhong", shang="shang", shangShang="shang_shang" }
struct GachaState { var sinceShen: Int = 0 }
struct GachaResult { let monsterId: String; let rarity: Rarity; let sign: SignTier }

struct GachaEngine {
    static let poolLing = 0.40, poolXian = 0.45, poolShen = 0.15
    static let softStart = 49, hardPity = 90, softStep = 0.02
    /// 1:1 镜像 Kotlin GachaEngine.SOFT_PITY_START / SOFT_PITY_STEP（命名对齐）
    static let softPityStart = softStart
    static let softPityStep = softStep
    static let singleLingyu = 60, tenLingyu = 540, duplicateYaodan = 10
    static func signFor(_ r: Rarity) -> SignTier { switch r { case .shen: return .shangShang; case .xian: return .shang; default: return .zhong } }
    static func rollRarity(_ state: inout GachaState, _ rng: () -> Double = { Double.random(in: 0...1) }) -> Rarity {
        let pity = state.sinceShen
        let shenRate: Double
        if pity >= hardPity - 1 { shenRate = 1.0 }
        else if pity >= softStart { shenRate = min(poolShen + Double(pity - softStart + 1) * softStep, 1.0) }
        else { shenRate = poolShen }
        let r = rng()
        if r < shenRate { return .shen }
        if r < shenRate + poolXian { return .xian }
        return .ling
    }
    static func pickFrom(_ rarity: Rarity, _ rng: () -> Double) -> String {
        let pool = MonsterPool.byRarity(rarity)
        if pool.isEmpty { return MonsterPool.randomEncounter().id }
        let idx = min(Int(rng() * Double(pool.count)), pool.count - 1)
        return pool[idx].id
    }
    static func pull(_ state: inout GachaState, _ rng: () -> Double = { Double.random(in: 0...1) }) -> GachaResult {
        let rarity = rollRarity(&state, rng)
        if rarity == .shen { state.sinceShen = 0 } else { state.sinceShen += 1 }
        let id = pickFrom(rarity, rng)
        return GachaResult(monsterId: id, rarity: rarity, sign: signFor(rarity))
    }
    static func tenPull(_ state: inout GachaState, _ rng: () -> Double = { Double.random(in: 0...1) }) -> [GachaResult] {
        var results = (0..<10).map { _ in pull(&state, rng) }
        if !results.contains(where: { $0.rarity >= .ling }) {
            let last = results.count - 1
            results[last] = GachaResult(monsterId: pickFrom(.ling, rng), rarity: .ling, sign: .zhong)
        }
        return results
    }
}

// MARK: - ③ 副本妖境（GDD 十二·副本）
enum DungeonType: String, Codable { case yiwen="yiwen", shilian="shilian", duojie="duojie", nianjie="nianjie" }
struct DungeonDef {
    let id: String; let type: DungeonType; let name: String; let desc: String
    let costIncense: Int; let dailyLimit: Int
    let yaodanRange: ClosedRange<Int>; let materialRange: ClosedRange<Int>
    let talismanChance: Double; let skinChance: Double
    let bossMonsterId: String?; let bossCaptureChance: Double
}
struct DungeonLibrary {
    static let all: [DungeonDef] = [
        DungeonDef(id:"d_yiwen_taotie", type:.yiwen, name:"饕餮之祸", desc:"山海异闻·封印贪食巨兽",
            costIncense:20, dailyLimit:3, yaodanRange:5...10, materialRange:0...0,
            talismanChance:0.3, skinChance:0.1, bossMonsterId:"guai_taotie", bossCaptureChance:0.15),
        DungeonDef(id:"d_shilian_lingmai", type:.shilian, name:"灵脉试炼", desc:"日常刷妖丹与符纸",
            costIncense:20, dailyLimit:5, yaodanRange:8...15, materialRange:0...2,
            talismanChance:0.5, skinChance:0.0, bossMonsterId:nil, bossCaptureChance:0.0),
        DungeonDef(id:"d_duojie_lei", type:.duojie, name:"渡劫秘境·雷", desc:"产出渡劫材料（雷系）",
            costIncense:30, dailyLimit:3, yaodanRange:6...12, materialRange:2...4,
            talismanChance:0.4, skinChance:0.05, bossMonsterId:nil, bossCaptureChance:0.0),
        DungeonDef(id:"d_nianjie_chun", type:.nianjie, name:"年节·除岁", desc:"春节限时活动副本",
            costIncense:30, dailyLimit:1, yaodanRange:10...20, materialRange:1...3,
            talismanChance:0.6, skinChance:0.2, bossMonsterId:"guai_nianshou", bossCaptureChance:0.25),
    ]
    static func byId(_ id: String) -> DungeonDef? { all.first { $0.id == id } }
}
struct DungeonRunState { var enteredToday: [String: Int] = [:] }
struct DungeonReward { let yaodan: Int; let material: Int; let talisman: Bool; let skin: Bool; let bossCaptured: Bool; let bossMonsterId: String? }
struct DungeonEngine {
    static func canEnter(_ def: DungeonDef, _ state: DungeonRunState, _ incense: Int) -> Bool {
        if incense < def.costIncense { return false }
        return (state.enteredToday[def.id] ?? 0) < def.dailyLimit
    }
    static func enter(_ def: DungeonDef, _ state: DungeonRunState, _ rng: () -> Double = { Double.random(in: 0...1) }) -> DungeonReward {
        let yaodan = def.yaodanRange.lowerBound + Int(rng() * Double(def.yaodanRange.upperBound - def.yaodanRange.lowerBound + 1))
        let material = def.materialRange.upperBound > 0 ? def.materialRange.lowerBound + Int(rng() * Double(def.materialRange.upperBound - def.materialRange.lowerBound + 1)) : 0
        let talisman = rng() < def.talismanChance
        let skin = rng() < def.skinChance
        let boss = (def.bossMonsterId != nil) && (rng() < def.bossCaptureChance)
        return DungeonReward(yaodan: yaodan, material: material, talisman: talisman, skin: skin, bossCaptured: boss, bossMonsterId: def.bossMonsterId)
    }
}

// MARK: - ③ 社交（三派 / 据点战）+ 经济（引妖香）
enum Faction: String, Codable, CaseIterable { case maoshan="maoshan", longhushan="longhushan", gezao="gezao" }
struct SocialEngine {
    static let strongholdAttackBonus = 1.10
    struct StrongholdState { var ownedNode: String? = nil; var lastCaptureDay: String = "" }
    static func canCapture(_ state: StrongholdState, _ today: String) -> Bool { state.lastCaptureDay != today }
    static func capture(_ state: inout StrongholdState, _ node: String, _ today: String) -> Bool {
        if !canCapture(state, today) { return false }
        state.ownedNode = node; state.lastCaptureDay = today; return true
    }
    static func attackBonus(_ owned: Bool) -> Double { owned ? strongholdAttackBonus : 1.0 }
    static func canGiftDaily() -> Bool { false }   // 占位：需要好友列表（服务器）
}

// MARK: - 社交服务接口（服务器权威占位）+ GPS 防作弊服务端契约

/// 团战贡献回执（服务端权威确认）
struct RaidReceipt {
    let accepted: Bool
    let bossId: String
    let creditedContribution: Int   // 服务端实际计入（≤ 申请，防刷）
    let totalContribution: Int      // 该 boss 当前全服累计
    let serverTs: Int64
}

/// 宗门排行榜条目
struct FactionRankEntry {
    let playerId: String
    let faction: Faction
    let score: Int
    let rank: Int
}

/// 社交服务接口（服务器权威占位）Swift 镜像（方法签名与 shared 一一对应）
struct SocialService {
    static func friends() -> [String] { return [] }
    static func giftYaodan(_ friendId: String) -> Bool { return false }
    static func raidBoss(_ bossId: String, _ contribution: Int) -> RaidReceipt {
        return RaidReceipt(accepted: false, bossId: bossId, creditedContribution: 0, totalContribution: 0, serverTs: 0)
    }
    static func factionRank(_ faction: Faction) -> Int { return 0 }
    static func factionRankboard(_ faction: Faction) -> [FactionRankEntry] { return [] }
}

/// 客户端上报的定位轨迹点（服务端不盲信 speedKmh，独立重算）
struct GpsSample {
    let lat: Double
    let lng: Double
    let ts: Int64
    let speedKmh: Double?
}

/// GPS 防作弊——服务端权威裁决
struct AntiCheatVerdict {
    let trusted: Bool          // 由 tier 派生（tier != .rejected 即可信）
    let reason: String
    let maxSpeedKmh: Double
    let teleportDetected: Bool
    let tier: IntegrityTier
}

/// GPS 防作弊——服务端权威校验契约 Swift 镜像
struct GpsAntiCheatVerifier {
    static func isAvailable() -> Bool { return false }
    /// 占位：平台层接入后端 API 后实现跨样本权威裁决
    static func verify(_ samples: [GpsSample]) -> AntiCheatVerdict {
        return AntiCheatVerdict(trusted: true, reason: "未接入服务端", maxSpeedKmh: 0, teleportDetected: false, tier: .normal)
    }
    /// 单次定位点校验（复用 LocationEngine 同套判据，双端一致）
    static func verifyPoint(_ prev: GpsSample?, _ cur: GpsSample) -> IntegrityResult {
        guard let p = prev else { return IntegrityResult(ok: true, reason: "首次定位", speedKmh: 0, tier: .normal, degraded: false) }
        let dt = cur.ts - p.ts
        if dt <= 0 { return IntegrityResult(ok: true, reason: "时间无效-跳过", speedKmh: 0, tier: .normal, degraded: false) }
        let dist = LocationEngine.haversine(lat1: p.lat, lng1: p.lng, lat2: cur.lat, lng2: cur.lng)
        let speed = dist / Double(dt) * 3.6
        if dist >= LocationEngine.TELEPORT_METERS && dt <= LocationEngine.TELEPORT_WINDOW_SEC {
            return IntegrityResult(ok: false, reason: String(format: "检测到瞬移：%.0f 米 / %d 秒", dist, Int(dt)), speedKmh: speed, tier: .rejected, degraded: true)
        }
        if speed > LocationEngine.SOFT_SPEED_KMH {
            return IntegrityResult(ok: true, reason: String(format: "速度过快(降品质)：%.0f km/h", speed), speedKmh: speed, tier: .degraded, degraded: true)
        }
        return IntegrityResult(ok: true, reason: "正常", speedKmh: speed, tier: .normal, degraded: false)
    }
}

// MARK: - 宗门科技树（GDD 二十·公会建设 + 科技加成）
enum SectBonusType { case attack, defense, hp, capture, exp, income, stronghold }

struct SectNode {
    let id: String; let name: String; let desc: String
    let type: SectBonusType; let maxLevel: Int
    let perLevel: Double; let costBase: Int; let costGrowth: Int
    let prereq: [String]; let faction: Faction?
    init(id: String, name: String, desc: String, type: SectBonusType, maxLevel: Int, perLevel: Double, costBase: Int, costGrowth: Int, prereq: [String] = [], faction: Faction? = nil) {
        self.id = id; self.name = name; self.desc = desc; self.type = type
        self.maxLevel = maxLevel; self.perLevel = perLevel; self.costBase = costBase
        self.costGrowth = costGrowth; self.prereq = prereq; self.faction = faction
    }
}

struct SectBonuses {
    var attack: Double = 0; var defense: Double = 0; var hp: Double = 0
    var capture: Double = 0; var exp: Double = 0; var income: Double = 0; var stronghold: Double = 0
}

struct SectTechEngine {
    static let contributionPerCapture = 50
    static let NODES: [SectNode] = [
        SectNode(id: "tech_gongfa",     name: "攻伐之术", desc: "提升全队攻击伤害",        type: .attack,     maxLevel: 5, perLevel: 0.04, costBase: 20, costGrowth: 10),
        SectNode(id: "tech_shouyu",     name: "守御心经", desc: "提升妖怪防御",            type: .defense,    maxLevel: 5, perLevel: 0.04, costBase: 20, costGrowth: 10),
        SectNode(id: "tech_tipu",       name: "体魄淬炼", desc: "提升妖怪体力上限",        type: .hp,         maxLevel: 5, perLevel: 0.04, costBase: 20, costGrowth: 10),
        SectNode(id: "tech_yuyao",      name: "御妖真诀", desc: "提升收妖成功率",          type: .capture,    maxLevel: 4, perLevel: 0.05, costBase: 30, costGrowth: 12),
        SectNode(id: "tech_jingxiu",    name: "经修玄法", desc: "提升修炼获得的经验",      type: .exp,        maxLevel: 5, perLevel: 0.05, costBase: 25, costGrowth: 12),
        SectNode(id: "tech_ziliao",     name: "资粮广积", desc: "提升副本/签到收益",       type: .income,     maxLevel: 5, perLevel: 0.04, costBase: 25, costGrowth: 12),
        SectNode(id: "tech_judi",       name: "据点经营", desc: "占领据点获得额外宗门贡献", type: .stronghold, maxLevel: 3, perLevel: 0.10, costBase: 40, costGrowth: 20, prereq: ["tech_gongfa"]),
        SectNode(id: "tech_maoshan_fu", name: "茅山符阵", desc: "茅山专属：额外攻击加成",   type: .attack,     maxLevel: 3, perLevel: 0.06, costBase: 60, costGrowth: 30, prereq: ["tech_gongfa"],  faction: .maoshan),
        SectNode(id: "tech_longhu_dan", name: "龙虎丹道", desc: "龙虎山专属：额外体力加成", type: .hp,         maxLevel: 3, perLevel: 0.06, costBase: 60, costGrowth: 30, prereq: ["tech_tipu"],   faction: .longhushan),
        SectNode(id: "tech_gezao_qi",   name: "阁皂奇门", desc: "阁皂山专属：额外收妖加成", type: .capture,    maxLevel: 3, perLevel: 0.06, costBase: 60, costGrowth: 30, prereq: ["tech_yuyao"],  faction: .gezao),
    ]
    static func node(_ id: String) -> SectNode? { NODES.first { $0.id == id } }
    /// 1:1 镜像 Kotlin SectTechEngine.nodeIds()（委托到 NODES 属性）
    static func nodeIds() -> [String] { NODES.map { $0.id } }
    static func researchCost(level: Int, _ node: SectNode) -> Int {
        guard level < node.maxLevel else { return Int.max }
        return node.costBase + node.costGrowth * level
    }
    static func prereqMet(_ node: SectNode, _ levels: [String: Int]) -> Bool {
        node.prereq.allSatisfy { (levels[$0] ?? 0) >= 1 }
    }
    static func canResearch(_ id: String, _ levels: [String: Int], _ faction: Faction?, _ contribution: Int) -> Bool {
        guard let node = node(id) else { return false }
        let lv = levels[id] ?? 0
        guard lv < node.maxLevel else { return false }
        guard prereqMet(node, levels) else { return false }
        if let f = node.faction, f != faction { return false }
        return contribution >= researchCost(level: lv, node)
    }
    static func research(_ id: String, _ levels: [String: Int], _ faction: Faction?, _ contribution: Int) -> ([String: Int], Int)? {
        guard canResearch(id, levels, faction, contribution) else { return nil }
        guard let node = node(id) else { return nil }
        let lv = levels[id] ?? 0
        let cost = researchCost(level: lv, node)
        var next = levels
        next[id] = lv + 1
        return (next, contribution - cost)
    }
    static func bonuses(_ levels: [String: Int], _ faction: Faction?) -> SectBonuses {
        var b = SectBonuses()
        for node in NODES {
            if let f = node.faction, f != faction { continue }
            let lv = levels[node.id] ?? 0
            guard lv > 0 else { continue }
            let v = node.perLevel * Double(lv)
            switch node.type {
            case .attack: b.attack += v
            case .defense: b.defense += v
            case .hp: b.hp += v
            case .capture: b.capture += v
            case .exp: b.exp += v
            case .income: b.income += v
            case .stronghold: b.stronghold += v
            }
        }
        return b
    }
}

// 活动系统（GDD「活动·节气复玩 / 年节限时」+ 运营活动）。纯函数引擎：日期区间触发，叠加收妖/经验/收益乘子。
enum ActivityKind: String {
    case festival = "FESTIVAL"
    case captureRally = "CAPTURE_RALLY"
    case battleRally = "BATTLE_RALLY"
    case login = "LOGIN"
}
struct Activity {
    let id: String
    let name: String
    let desc: String
    let kind: ActivityKind
    let start: String
    let end: String
    let captureMul: Double
    let expMul: Double
    let incomeMul: Double
}
struct ActivityBonuses {
    let captureMul: Double
    let expMul: Double
    let incomeMul: Double
}
struct ActivityEngine {
    static func dateLE(_ a: String, _ b: String) -> Bool { a <= b }
    static func isActive(_ a: Activity, _ today: String) -> Bool { today >= a.start && today <= a.end }
    static func active(_ today: String) -> [Activity] { ACTIVITIES.filter { isActive($0, today) } }
    static func aggregate(_ list: [Activity]) -> ActivityBonuses {
        var c = 1.0, e = 1.0, inc = 1.0
        for a in list { c *= a.captureMul; e *= a.expMul; inc *= a.incomeMul }
        return ActivityBonuses(captureMul: c, expMul: e, incomeMul: inc)
    }
    static let ACTIVITIES: [Activity] = [
        Activity(id: "act_zhongyuan", name: "中元鬼节", desc: "鬼门开，百鬼夜行，收妖成功率大涨", kind: .festival, start: "2026-08-28", end: "2026-09-01", captureMul: 1.2, expMul: 1.0, incomeMul: 1.0),
        Activity(id: "act_chunjie", name: "新春嘉年华", desc: "年兽限时现身，收妖成功率大涨", kind: .festival, start: "2026-02-16", end: "2026-02-22", captureMul: 1.3, expMul: 1.0, incomeMul: 1.0),
        Activity(id: "act_capture_rally", name: "收妖大作战", desc: "七日收妖狂欢，收妖成功率提升", kind: .captureRally, start: "2026-08-15", end: "2026-08-21", captureMul: 1.15, expMul: 1.0, incomeMul: 1.0),
        Activity(id: "act_battle_rally", name: "论道大会", desc: "七日论道，对战经验与收益提升", kind: .battleRally, start: "2026-08-15", end: "2026-08-21", captureMul: 1.0, expMul: 1.25, incomeMul: 1.2),
        Activity(id: "act_login", name: "勤修有礼", desc: "活动期间签到香火额外加成", kind: .login, start: "2026-08-15", end: "2026-08-21", captureMul: 1.0, expMul: 1.0, incomeMul: 1.1),
        Activity(id: "act_zhongqiu", name: "中秋月圆", desc: "皓月当空，收妖成功率提升", kind: .festival, start: "2026-09-25", end: "2026-09-27", captureMul: 1.25, expMul: 1.0, incomeMul: 1.0),
        Activity(id: "act_guoqing", name: "国庆盛典", desc: "普天同庆，对战经验与收益提升", kind: .battleRally, start: "2026-10-01", end: "2026-10-07", captureMul: 1.0, expMul: 1.2, incomeMul: 1.15),
    ]
}

struct ShopEngine {
    static let incenseRefillCostLingyu = 30, incenseRefillAmount = 60, incenseDailyBuyCap = 3
    struct ShopState { var incenseBoughtToday: Int = 0; var lastBuyDay: String = "" }
    static func canBuyIncense(_ state: ShopState, _ today: String, _ lingyu: Int) -> Bool {
        if lingyu < incenseRefillCostLingyu { return false }
        let bought = (state.lastBuyDay == today) ? state.incenseBoughtToday : 0
        return bought < incenseDailyBuyCap
    }
    static func buyIncense(_ state: inout ShopState, _ today: String, _ lingyu: Int) -> (Int, Int)? {
        if !canBuyIncense(state, today, lingyu) { return nil }
        if state.lastBuyDay != today { state.lastBuyDay = today; state.incenseBoughtToday = 0 }
        state.incenseBoughtToday += 1
        return (incenseRefillAmount, incenseRefillCostLingyu)
    }
}

// MARK: - 存档（Codable，与 Android 共享 JSON 字段名）
struct QuestSave: Codable {
    let id: String
    let current: [Int]
    let state: QuestState
}

struct SaveData: Codable {
    let version: Int = 1
    let caughtRegion: [String]
    let caughtSecret: [String]
    let team: [String]
    var incense: Int
    var lingyu: Int
    var yaodan: Int
    let secretRealmLeft: Int
    let lastDailyReset: String
    let quests: [QuestSave]
    let owned: [MonsterInstance] = []   // 拥有的妖实例（含等级/经验/突破）
    let nextInstSeq: Int = 1            // 实例 id 自增序号
    let equipment: [OwnedEquip] = []    // 拥有的装备实例
    let nextEquipSeq: Int = 1           // 装备实例 id 自增序号
    let equipped: [String: [EquipSlot: String]] = [:] // 妖实例id → 槽位 → 装备实例id
    // ── ② LBS·AR / ③ 祈妖·副本·社交·经济 ──
    let gachaSinceShen: Int = 0
    let dungeonEntered: [String: Int] = [:]
    let faction: String? = nil
    let strongholdNode: String? = nil
    let strongholdDay: String = ""
    let shopIncenseBought: Int = 0
    let shopDay: String = ""
    let reputation: Int = 100   // 信誉分（GDD 十六·公平与稳健），默认满分
    // ── 十八·新手引导 / 7 日留存 ──
    let tutorialStep: Int = 0
    let tutorialSkipped: Bool = false
    let signInDay: Int = 0
    let lastSignIn: String = ""
    let homesteadLevel: Int = 0
    let lastHomesteadCollect: Int64 = 0
    // ── 十九·长线留存：赛季通行证（90 天双轨） ──
    let seasonStartMs: Int64 = 0
    let seasonXp: Int = 0
    let hasSeasonPass: Bool = false
    let seasonClaimedFree: [Int] = []
    let seasonClaimedPaid: [Int] = []
    let seasonIndex: Int = 1
    let fatigue: Int = 0   // 疲劳值 0..100（每日跨天重置）
    // ── 二十·竞技与荣誉：排位赛段位天梯 ──
    let rankTierIndex: Int = 0   // 当前段位索引 0=炼气 … 4=化神
    let rankPoints: Int = 0      // 当前段位内积分 0..99
    // ── 二十·竞技与荣誉：成就 / 勋章 / 称号（展示型荣誉） ──
    let tribulationTotal: Int = 0   // 累计渡劫飞升次数（称号统计；默认 0 向后兼容）
    let equippedTitleId: String = ""   // 玩家装备的展示称号 id（""=无；默认向后兼容）
    // ── 二十·宗门科技树（公会建设 + 科技加成） ──
    let sectTech: [String: Int] = [:]           // 科技节点 id → 等级（默认向后兼容）
    let sectContribution: Int = 0               // 宗门贡献（默认 0 向后兼容）
}

// 自定义解码放在 extension 中：保留结构体的自动成员初始化器（toSaveData 依赖）
extension SaveData {
    // 旧存档缺失新增字段时回退默认值（与 Android ignoreUnknownKeys 对齐）
    enum CodingKeys: String, CodingKey {
        case version, caughtRegion, caughtSecret, team, incense, lingyu, yaodan
        case secretRealmLeft, lastDailyReset, quests, owned, nextInstSeq
        case equipment, nextEquipSeq, equipped
        case gachaSinceShen, dungeonEntered, faction, strongholdNode
        case strongholdDay, shopIncenseBought, shopDay, reputation
        case tutorialStep, tutorialSkipped, signInDay, lastSignIn
        case homesteadLevel, lastHomesteadCollect
        case seasonStartMs, seasonXp, hasSeasonPass, seasonClaimedFree, seasonClaimedPaid, seasonIndex
        case fatigue
        case rankTierIndex, rankPoints
        case tribulationTotal, equippedTitleId
        case sectTech, sectContribution
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version          = (try? c.decodeIfPresent(Int.self,           forKey: .version))          ?? 1
        caughtRegion     = (try? c.decodeIfPresent([String].self,      forKey: .caughtRegion))     ?? []
        caughtSecret     = (try? c.decodeIfPresent([String].self,      forKey: .caughtSecret))     ?? []
        team             = (try? c.decodeIfPresent([String].self,      forKey: .team))             ?? []
        incense          = (try? c.decodeIfPresent(Int.self,           forKey: .incense))          ?? 0
        lingyu           = (try? c.decodeIfPresent(Int.self,           forKey: .lingyu))           ?? 0
        yaodan           = (try? c.decodeIfPresent(Int.self,           forKey: .yaodan))           ?? 0
        secretRealmLeft  = (try? c.decodeIfPresent(Int.self,           forKey: .secretRealmLeft))  ?? 0
        lastDailyReset   = (try? c.decodeIfPresent(String.self,        forKey: .lastDailyReset))   ?? ""
        quests           = (try? c.decodeIfPresent([QuestSave].self,   forKey: .quests))           ?? []
        owned            = (try? c.decodeIfPresent([MonsterInstance].self, forKey: .owned))        ?? []
        nextInstSeq      = (try? c.decodeIfPresent(Int.self,           forKey: .nextInstSeq))      ?? 1
        equipment        = (try? c.decodeIfPresent([OwnedEquip].self,  forKey: .equipment))        ?? []
        nextEquipSeq     = (try? c.decodeIfPresent(Int.self,           forKey: .nextEquipSeq))     ?? 1
        equipped         = (try? c.decodeIfPresent([String: [EquipSlot: String]].self, forKey: .equipped)) ?? [:]
        gachaSinceShen   = (try? c.decodeIfPresent(Int.self,           forKey: .gachaSinceShen))   ?? 0
        dungeonEntered   = (try? c.decodeIfPresent([String: Int].self, forKey: .dungeonEntered))   ?? [:]
        faction          = try? c.decodeIfPresent(String.self,         forKey: .faction)
        strongholdNode   = try? c.decodeIfPresent(String.self,         forKey: .strongholdNode)
        strongholdDay    = (try? c.decodeIfPresent(String.self,        forKey: .strongholdDay))    ?? ""
        shopIncenseBought = (try? c.decodeIfPresent(Int.self,         forKey: .shopIncenseBought)) ?? 0
        shopDay          = (try? c.decodeIfPresent(String.self,        forKey: .shopDay))          ?? ""
        reputation       = (try? c.decodeIfPresent(Int.self,           forKey: .reputation))       ?? 100
        tutorialStep    = (try? c.decodeIfPresent(Int.self,           forKey: .tutorialStep))    ?? 0
        tutorialSkipped = (try? c.decodeIfPresent(Bool.self,          forKey: .tutorialSkipped)) ?? false
        signInDay       = (try? c.decodeIfPresent(Int.self,           forKey: .signInDay))       ?? 0
        lastSignIn      = (try? c.decodeIfPresent(String.self,        forKey: .lastSignIn))      ?? ""
        homesteadLevel  = (try? c.decodeIfPresent(Int.self,           forKey: .homesteadLevel))  ?? 0
        lastHomesteadCollect = (try? c.decodeIfPresent(Int64.self,    forKey: .lastHomesteadCollect)) ?? 0
        seasonStartMs   = (try? c.decodeIfPresent(Int64.self,         forKey: .seasonStartMs))   ?? 0
        seasonXp        = (try? c.decodeIfPresent(Int.self,           forKey: .seasonXp))        ?? 0
        hasSeasonPass   = (try? c.decodeIfPresent(Bool.self,          forKey: .hasSeasonPass))   ?? false
        seasonClaimedFree  = (try? c.decodeIfPresent([Int].self,      forKey: .seasonClaimedFree))  ?? []
        seasonClaimedPaid  = (try? c.decodeIfPresent([Int].self,      forKey: .seasonClaimedPaid))  ?? []
        seasonIndex     = (try? c.decodeIfPresent(Int.self,           forKey: .seasonIndex))     ?? 1
        fatigue         = (try? c.decodeIfPresent(Int.self,           forKey: .fatigue))        ?? 0
        rankTierIndex   = (try? c.decodeIfPresent(Int.self,           forKey: .rankTierIndex))  ?? 0
        rankPoints      = (try? c.decodeIfPresent(Int.self,           forKey: .rankPoints))     ?? 0
        tribulationTotal = (try? c.decodeIfPresent(Int.self,          forKey: .tribulationTotal)) ?? 0
        equippedTitleId = (try? c.decodeIfPresent(String.self,        forKey: .equippedTitleId)) ?? ""
        sectTech        = (try? c.decodeIfPresent([String: Int].self,  forKey: .sectTech))        ?? [:]
        sectContribution = (try? c.decodeIfPresent(Int.self,          forKey: .sectContribution)) ?? 0
    }
}

// MARK: - 存档导出 / 导入（跨端云同步基础层）
/// 便携包装体：传输层版本号 + 业务存档体（与 Android SaveExportEngine 导出结构一致）
struct PortableSave: Codable {
    let v: Int
    let save: SaveData
}
/// 存档导出 / 导入引擎（跨端云同步的 shared 基础层）
struct SaveExportEngine {
    static let EXPORT_VERSION = 1
    /// 导出：SaveData → 便携 JSON 字符串 {"v":1,"save":{...}}
    static func toPortable(_ save: SaveData) -> String {
        let p = PortableSave(v: EXPORT_VERSION, save: save)
        guard let data = try? JSONEncoder().encode(p) else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }
    /// 导入：便携 JSON 字符串 → SaveData；解析失败或版本不符返回 nil
    static func fromPortable(_ text: String) -> SaveData? {
        guard let data = text.data(using: .utf8) else { return nil }
        guard let p = try? JSONDecoder().decode(PortableSave.self, from: data) else { return nil }
        if p.v != EXPORT_VERSION { return nil }
        return SaveIntegrityEngine.sanitize(p.save)
    }
}

// MARK: - 跨端云同步：冲突合并与完整性校验（shared 侧纯函数）
/// 云信封：设备标识 + 修改时间 + 便携存档体（与 Android CloudSyncEngine 一致）
struct CloudEnvelope: Codable {
    let deviceId: String
    let modifiedAt: Int64
    let v: Int
    let save: SaveData
}
/// 跨端云同步引擎：LWW 合并 + 货币 max + FNV-1a 校验（shared 侧纯函数，与 Android 一致）
struct CloudSyncEngine {
    /// 本地存档 → 云信封 JSON（附带设备标识与修改时间）
    static func toEnvelope(_ save: SaveData, _ deviceId: String, _ modifiedAt: Int64) -> String {
        let e = CloudEnvelope(deviceId: deviceId, modifiedAt: modifiedAt, v: SaveExportEngine.EXPORT_VERSION, save: save)
        guard let data = try? JSONEncoder().encode(e) else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }
    /// 云信封 JSON → 结构；解析失败或版本不符返回 nil
    static func fromEnvelope(_ text: String) -> CloudEnvelope? {
        guard let data = text.data(using: .utf8) else { return nil }
        guard let e = try? JSONDecoder().decode(CloudEnvelope.self, from: data) else { return nil }
        if e.v != SaveExportEngine.EXPORT_VERSION { return nil }
        return e
    }
    /// 合并本地与远程信封，返回胜方（货币取 max）。任一方为 nil 返回另一方。
    static func merge(_ local: CloudEnvelope?, _ remote: CloudEnvelope?) -> CloudEnvelope? {
        guard let local = local else { return remote }
        guard let remote = remote else { return local }
        let (winner, loser): (CloudEnvelope, CloudEnvelope)
        if remote.modifiedAt > local.modifiedAt {
            (winner, loser) = (remote, local)
        } else if local.modifiedAt > remote.modifiedAt {
            (winner, loser) = (local, remote)
        } else if remote.deviceId > local.deviceId {
            (winner, loser) = (remote, local)
        } else {
            (winner, loser) = (local, remote)
        }
        var s = winner.save
        s.lingyu = max(winner.save.lingyu, loser.save.lingyu)
        s.yaodan = max(winner.save.yaodan, loser.save.yaodan)
        s.incense = max(winner.save.incense, loser.save.incense)
        return CloudEnvelope(deviceId: winner.deviceId, modifiedAt: winner.modifiedAt, v: winner.v, save: SaveIntegrityEngine.sanitize(s))
    }
    /// FNV-1a 32-bit 校验和（作用于字符串的 UTF-16 码元）
    static func checksum(_ text: String) -> Int {
        var hash: UInt32 = 0x811c9dc5
        for cu in text.utf16 {
            hash ^= cu & 0xff
            hash = hash &* 0x01000193
        }
        return Int(bitPattern: hash)
    }
    /// 对存档计算校验和（基于规范便携字符串，确定性可复现）
    static func checksumOf(_ save: SaveData) -> Int {
        return checksum(SaveExportEngine.toPortable(save))
    }
    /// 校验存档与校验和是否一致
    static func verify(_ save: SaveData, _ expected: Int) -> Bool {
        return checksumOf(save) == expected
    }
}

/// 上传回执（平台层 → shared）。与 Android UploadAck 对应。
struct UploadAck {
    let accepted: Bool
    let storedModifiedAt: Int64
    let checksum: Int
}

/// 云同步平台层契约（上传 / 下载 + 账号）。与 Android CloudSyncBackend 一一对应。
/// 真实网络 + 账号由平台层实现；此处仅定义契约镜像，避免双端漂移。
struct CloudSyncBackend {
    /// 平台层是否具备云同步能力（已登录账号且网络可用）。
    static func isAvailable() -> Bool { false }   // 占位：平台层接入

    /// 上传本地存档为云信封。accountToken 空串视为未登录 → accepted=false。
    static func upload(_ local: SaveData, _ deviceId: String, _ accountToken: String) -> UploadAck {
        guard !accountToken.isEmpty else {
            return UploadAck(accepted: false, storedModifiedAt: 0, checksum: 0)
        }
        // 占位：平台层应以真实 epoch ms 替换 0
        let env = CloudSyncEngine.toEnvelope(local, deviceId, 0)
        return UploadAck(accepted: true, storedModifiedAt: env.modifiedAt, checksum: CloudSyncEngine.checksumOf(local))
    }

    /// 下载远程云信封（服务器侧已合并双端）。accountToken 空串 → nil。
    static func download(_ accountToken: String) -> CloudEnvelope? {
        guard !accountToken.isEmpty else { return nil }
        return nil   // 占位：平台层网络实现
    }
}

// MARK: - 存档语义完整性校验 + 自愈（GDD·稳健性收口）
struct Violation: Identifiable {
    let code: String
    let message: String
    var id: String { code }
}

struct SaveIntegrityEngine {
    /// 出战队伍上限（妖 ≤ 6）
    static let teamCap = 6
    /// 已知派系
    private static let factions: Set<String> = ["maoshan", "longhushan", "gezao"]
    /// 合法天赋分支
    private static let talentBranches: Set<String> = ["ATTACK", "DEFENSE", "SUPPORT"]

    private static let monsterIds: Set<String> = Set(MonsterPool.all.map { $0.id })
    private static let questIds: Set<String> = Set(QuestLibrary.all.map { $0.id })
    private static let equipmentIds: Set<String> = Set(EquipmentLibrary.all.map { $0.id })
    private static let sectTechMax: [String: Int] = Dictionary(uniqueKeysWithValues: SectTechEngine.NODES.map { ($0.id, $0.maxLevel) })

    /// 校验整份存档，返回所有违规（可能为空）；不修改入参。
    static func validate(_ save: SaveData) -> [Violation] {
        var v: [Violation] = []
        func add(_ code: String, _ msg: String) { v.append(Violation(code: code, message: msg)) }

        if save.version != 1 { add("VERSION", "存档版本 \(save.version) != 当前 1") }

        if save.incense < 0 { add("NEG_INCENSE", "香火为负 (\(save.incense))") }
        if save.lingyu < 0 { add("NEG_LINGYU", "灵玉为负 (\(save.lingyu))") }
        if save.yaodan < 0 { add("NEG_YAODAN", "妖丹为负 (\(save.yaodan))") }

        if !(0...FatigueEngine.maxFatigue).contains(save.fatigue)
            { add("FATIGUE_RANGE", "疲劳 \(save.fatigue) 越界 [0,\(FatigueEngine.maxFatigue)]") }
        if !(ReputationEngine.MIN_SCORE...ReputationEngine.MAX_SCORE).contains(save.reputation)
            { add("REP_RANGE", "信誉分 \(save.reputation) 越界 [\(ReputationEngine.MIN_SCORE),\(ReputationEngine.MAX_SCORE)]") }
        if !(RankEngine.minTierIndex...RankEngine.maxTierIndex).contains(save.rankTierIndex)
            { add("RANK_TIER", "段位索引 \(save.rankTierIndex) 越界 [\(RankEngine.minTierIndex),\(RankEngine.maxTierIndex)]") }
        if !(0..<RankEngine.pointsToPromote).contains(save.rankPoints)
            { add("RANK_POINTS", "段位积分 \(save.rankPoints) 越界 [0,\(RankEngine.pointsToPromote - 1)]") }
        if save.tribulationTotal < 0 { add("NEG_TRIB", "渡劫次数负 (\(save.tribulationTotal))") }
        if save.secretRealmLeft < 0 { add("NEG_SECRET", "秘境次数负 (\(save.secretRealmLeft))") }
        if save.homesteadLevel < 0 { add("NEG_HOME", "洞府等级负 (\(save.homesteadLevel))") }
        if save.seasonXp < 0 { add("NEG_SEASONXP", "赛季经验负 (\(save.seasonXp))") }
        if save.signInDay < 0 { add("NEG_SIGNIN", "签到天数负 (\(save.signInDay))") }
        if save.tutorialStep < 0 { add("NEG_TUT", "引导步序负 (\(save.tutorialStep))") }
        if save.nextInstSeq < 1 { add("SEQ_INST", "实例序号 \(save.nextInstSeq) < 1") }
        if save.nextEquipSeq < 1 { add("SEQ_EQUIP", "装备序号 \(save.nextEquipSeq) < 1") }

        var ownedIds = Set<String>()
        var dupWarned = false
        for inst in save.owned {
            if inst.instanceId.isEmpty { add("INST_ID_EMPTY", "存在空 instanceId 的妖实例") }
            if ownedIds.contains(inst.instanceId) {
                if !dupWarned { add("INST_DUP", "妖实例 instanceId 重复: \(inst.instanceId)"); dupWarned = true }
            } else { ownedIds.insert(inst.instanceId) }
            let defOk = monsterIds.contains(inst.defId)
            if !defOk { add("INST_DEF", "妖实例 \(inst.instanceId) 的 defId 不存在: \(inst.defId)") }

            let bt = min(max(inst.breakthrough, 0), CultivationEngine.maxBreakthrough)
            let cap = CultivationEngine.baseLevelCap + bt * CultivationEngine.levelCapPerBreak
            if inst.level < 1 { add("INST_LVL", "妖实例 \(inst.instanceId) 等级 \(inst.level) < 1") }
            else if inst.level > cap { add("INST_LVL", "妖实例 \(inst.instanceId) 等级 \(inst.level) 超上限 \(cap)") }
            if inst.breakthrough < 0 || inst.breakthrough > CultivationEngine.maxBreakthrough
                { add("INST_BREAK", "妖实例 \(inst.instanceId) 突破 \(inst.breakthrough) 越界 [0,\(CultivationEngine.maxBreakthrough)]") }
            if inst.exp < 0 { add("INST_EXP", "妖实例 \(inst.instanceId) 经验负 (\(inst.exp))") }

            if defOk, let monster = MonsterPool.byId(inst.defId) {
                let maxTrib = CultivationEngine.maxTribulations(monster.rarity)
                if inst.tribulations < 0 || inst.tribulations > maxTrib
                    { add("INST_TRIB", "妖实例 \(inst.instanceId) 渡劫 \(inst.tribulations) 越界 [0,\(maxTrib)]") }
            }

            var allocated = 0
            for (branch, layers) in inst.talents {
                if !talentBranches.contains(branch)
                    { add("INST_TALENT_BRANCH", "妖实例 \(inst.instanceId) 天赋分支非法: \(branch)") }
                if layers < 0 || layers > TalentEngine.maxLayer
                    { add("INST_TALENT_LAYER", "妖实例 \(inst.instanceId) 天赋层 \(layers) 越界 [0,\(TalentEngine.maxLayer)]") }
                allocated += max(0, layers)
            }
            let talentCap = TalentEngine.baseTalent + bt * TalentEngine.talentPerBreak
            if allocated > talentCap
                { add("INST_TALENT_CAP", "妖实例 \(inst.instanceId) 天赋点 \(allocated) 超容量 \(talentCap)") }
        }

        let teamUnknown = save.team.filter { !ownedIds.contains($0) }
        if !teamUnknown.isEmpty { add("TEAM_REF", "出战队伍引用未知实例: \(teamUnknown)") }
        if save.team.count > teamCap { add("TEAM_CAP", "出战队伍 \(save.team.count) 超上限 \(teamCap)") }

        var equipIds = Set<String>()
        var eqDup = false
        for eq in save.equipment {
            if equipIds.contains(eq.instanceId) {
                if !eqDup { add("EQUIP_DUP", "装备 instanceId 重复: \(eq.instanceId)"); eqDup = true }
            } else { equipIds.insert(eq.instanceId) }
            if eq.level < 1 { add("EQUIP_LVL", "装备 \(eq.instanceId) 祭炼等级 \(eq.level) < 1") }
            if eq.stars < 1 { add("EQUIP_STAR", "装备 \(eq.instanceId) 星级 \(eq.stars) < 1") }
            if eq.tier < 1 { add("EQUIP_TIER", "装备 \(eq.instanceId) 突破阶 \(eq.tier) < 1") }
            if !equipmentIds.contains(eq.equipId) { add("EQUIP_DEF", "装备 \(eq.instanceId) 的 equipId 不存在: \(eq.equipId)") }
            // 上界钳制（与 sanitize 对应）：星级/突破阶/副属性 越界会被坏档/外挂用来过度膨胀战力
            if let eqDef = EquipmentLibrary.byId(eq.equipId) {
                if eq.stars > EquipmentEngine.MAX_STAR
                    { add("EQUIP_STAR_HI", "装备 \(eq.instanceId) 星级 \(eq.stars) 超上限 \(EquipmentEngine.MAX_STAR)") }
                let maxT = EquipmentEngine.maxTier(eqDef.rarity)
                if eq.tier > maxT
                    { add("EQUIP_TIER_HI", "装备 \(eq.instanceId) 突破阶 \(eq.tier) 超上限 \(maxT)") }
            }
            if eq.substats.count > EquipmentEngine.MAX_SUBSTATS
                { add("EQUIP_SUBN", "装备 \(eq.instanceId) 副属性 \(eq.substats.count) 超上限 \(EquipmentEngine.MAX_SUBSTATS)") }
            for s in eq.substats where s.value > EquipmentEngine.SUB_CAP
                { add("EQUIP_SUBVAL", "装备 \(eq.instanceId) 副属性 \(s.stat) 值 \(s.value) 超上限 \(EquipmentEngine.SUB_CAP)") }
        }

        var badEquipRef: [String] = []
        for (instId, slots) in save.equipped {
            if !ownedIds.contains(instId) { badEquipRef.append("妖 \(instId) 不存在"); continue }
            for (_, eqId) in slots where !equipIds.contains(eqId) { badEquipRef.append("装备 \(eqId) 不存在") }
        }
        if !badEquipRef.isEmpty { add("EQUIP_REF", "装配引用悬空: \(badEquipRef)") }

        let badQuest = save.quests.filter { !questIds.contains($0.id) }
        if !badQuest.isEmpty { add("QUEST_ID", "任务 id 不存在: \(badQuest.map { $0.id })") }
        for q in save.quests where q.current.contains(where: { $0 < 0 }) { add("QUEST_NEG", "任务 \(q.id) 进度含负值"); break }

        let badCodex = (save.caughtRegion + save.caughtSecret).filter { !monsterIds.contains($0) }
        if !badCodex.isEmpty { add("CODEX_ID", "图鉴 id 不存在: \(badCodex)") }

        if let f = save.faction, !factions.contains(f) { add("FACTION", "未知派系: \(f)") }

        let badTech = save.sectTech.filter { !sectTechMax.keys.contains($0.key) }
        if !badTech.isEmpty { add("SECTTECH_ID", "宗门科技节点不存在: \(Array(badTech.keys))") }
        for (id, lv) in save.sectTech {
            if lv < 0 { add("SECTTECH_NEG", "宗门科技等级含负值"); break }
            if let mx = sectTechMax[id], lv > mx { add("SECTTECH_LVL", "宗门科技 \(id) 等级 \(lv) 超节点上限 \(mx)") }
        }

        return v
    }

    /// 是否无违规
    static func isClean(_ save: SaveData) -> Bool { validate(save).isEmpty() }

    /// 自愈：修复安全子集，返回新的合法存档（不动定义、尽量保留进度）。
    static func sanitize(_ save: SaveData) -> SaveData {
        let incense = max(0, save.incense)
        let lingyu = max(0, save.lingyu)
        let yaodan = max(0, save.yaodan)

        let fatigue = min(max(save.fatigue, 0), FatigueEngine.maxFatigue)
        let reputation = min(max(save.reputation, ReputationEngine.minScore), ReputationEngine.maxScore)
        let rankTierIndex = min(max(save.rankTierIndex, RankEngine.minTierIndex), RankEngine.maxTierIndex)
        let rankPoints = min(max(save.rankPoints, 0), RankEngine.pointsToPromote - 1)
        let tribulationTotal = max(0, save.tribulationTotal)
        let secretRealmLeft = max(0, save.secretRealmLeft)
        let homesteadLevel = max(0, save.homesteadLevel)
        let seasonXp = max(0, save.seasonXp)
        let signInDay = max(0, save.signInDay)
        let tutorialStep = max(0, save.tutorialStep)
        let nextInstSeq = max(1, save.nextInstSeq)
        let nextEquipSeq = max(1, save.nextEquipSeq)

        var seenInst = Set<String>()
        var owned: [MonsterInstance] = []
        for inst in save.owned {
            if inst.instanceId.isEmpty { continue }
            if !monsterIds.contains(inst.defId) { continue }
            if seenInst.contains(inst.instanceId) { continue }
            seenInst.insert(inst.instanceId)
            owned.append(sanitizeInstance(inst))
        }
        let ownedIdSet = Set(owned.map { $0.instanceId })

        var seenEq = Set<String>()
        var equipment: [OwnedEquip] = []
        for eq in save.equipment {
            if !equipmentIds.contains(eq.equipId) { continue }   // 丢弃 defId 不存在的死装备（自愈）
            if seenEq.contains(eq.instanceId) { continue }
            seenEq.insert(eq.instanceId)
            // 上界钳制（与 validate 对应）：星级/突破阶/副属性 越界 → 钳到合法上限，杜绝坏档过度膨胀战力
            let eqDef = EquipmentLibrary.byId(eq.equipId)!
            let maxT = EquipmentEngine.maxTier(eqDef.rarity)
            let safeTier = min(eq.tier, maxT)
            let lvlCap = eqDef.maxLevel + (safeTier - 1) * EquipmentEngine.TIER_LEVEL_BONUS
            let subs = eq.substats
                .map { s in SubStat(stat: s.stat, value: min(max(0, s.value), EquipmentEngine.SUB_CAP)) }
                .prefix(EquipmentEngine.MAX_SUBSTATS)
            equipment.append(OwnedEquip(equipId: eq.equipId, instanceId: eq.instanceId,
                                        level: min(max(1, eq.level), lvlCap),
                                        stars: min(max(1, eq.stars), EquipmentEngine.MAX_STAR),
                                        tier: min(max(1, eq.tier), maxT), substats: Array(subs)))
        }
        let equipIdSet = Set(equipment.map { $0.instanceId })

        let filteredTeam = save.team.filter { ownedIdSet.contains($0) }
        var seenT = Set<String>()
        let team = Array(filteredTeam.filter { seenT.insert($0).inserted }.prefix(teamCap))

        let equipped = save.equipped
            .filter { ownedIdSet.contains($0.key) }
            .mapValues { $0.filter { equipIdSet.contains($1) } }
            .filter { !$0.value.isEmpty }

        let quests = save.quests
            .filter { questIds.contains($0.id) }
            .map { qs in QuestSave(id: qs.id, current: qs.current.map { max(0, $0) }, state: qs.state) }

        let caughtRegion = save.caughtRegion.filter { monsterIds.contains($0) }
        let caughtSecret = save.caughtSecret.filter { monsterIds.contains($0) }

        let faction = (save.faction).flatMap { factions.contains($0) ? $0 : nil }
        let sectTech = save.sectTech
            .filter { sectTechMax.keys.contains($0.key) }
            .mapValues { min(max($0.value, 0), sectTechMax[$0.key] ?? 0) }

        return SaveData(
            version: 1,
            caughtRegion: caughtRegion, caughtSecret: caughtSecret, team: team,
            incense: incense, lingyu: lingyu, yaodan: yaodan,
            secretRealmLeft: save.secretRealmLeft, lastDailyReset: save.lastDailyReset,
            quests: quests, owned: owned, nextInstSeq: nextInstSeq,
            equipment: equipment, nextEquipSeq: nextEquipSeq, equipped: equipped,
            gachaSinceShen: save.gachaSinceShen, dungeonEntered: save.dungeonEntered,
            faction: faction, strongholdNode: save.strongholdNode, strongholdDay: save.strongholdDay,
            shopIncenseBought: save.shopIncenseBought, shopDay: save.shopDay, reputation: reputation,
            tutorialStep: tutorialStep, tutorialSkipped: save.tutorialSkipped,
            signInDay: signInDay, lastSignIn: save.lastSignIn,
            homesteadLevel: homesteadLevel, lastHomesteadCollect: save.lastHomesteadCollect,
            seasonStartMs: save.seasonStartMs, seasonXp: seasonXp, hasSeasonPass: save.hasSeasonPass,
            seasonClaimedFree: save.seasonClaimedFree, seasonClaimedPaid: save.seasonClaimedPaid,
            seasonIndex: save.seasonIndex, fatigue: fatigue,
            rankTierIndex: rankTierIndex, rankPoints: rankPoints,
            tribulationTotal: tribulationTotal, equippedTitleId: save.equippedTitleId,
            sectTech: sectTech, sectContribution: save.sectContribution
        )
    }

    /// 单只妖实例养成越界钳制（前置：defId 已在 MonsterPool 中）。
    private static func sanitizeInstance(_ inst: MonsterInstance) -> MonsterInstance {
        let bt = min(max(inst.breakthrough, 0), CultivationEngine.maxBreakthrough)
        let cap = CultivationEngine.baseLevelCap + bt * CultivationEngine.levelCapPerBreak
        let lvl = min(max(inst.level, 1), cap)
        let maxExp = lvl < cap ? CultivationEngine.expForLevel(lvl) - 1 : CultivationEngine.expForLevel(max(1, cap - 1))
        let exp = min(max(inst.exp, 0), max(0, maxExp))
        let rarity = MonsterPool.byId(inst.defId)!.rarity
        let trib = min(max(inst.tribulations, 0), CultivationEngine.maxTribulations(rarity))
        let talentCap = TalentEngine.baseTalent + bt * TalentEngine.talentPerBreak
        var cleaned = inst.talents
            .filter { talentBranches.contains($0.key) }
            .mapValues { min(max($0.value, 0), TalentEngine.maxLayer) }
        var allocated = cleaned.values.reduce(0, +)
        var order = cleaned.sorted { $0.value > $1.value }.map { $0.key }
        while allocated > talentCap && !order.isEmpty {
            let b = order[0]
            let cur = cleaned[b] ?? 0
            if cur <= 0 { order.remove(at: 0); continue }
            cleaned[b] = cur - 1
            allocated -= 1
        }
        return MonsterInstance(instanceId: inst.instanceId, defId: inst.defId, level: lvl, exp: exp,
                               breakthrough: bt, tribulations: trib, talents: cleaned)
    }
}

// MARK: - 静态数据定义完整性校验（GDD·稳健性收口，定义层）
/// 与 Kotlin `DataIntegrityEngine` 一致：校验妖池↔技能池、进化链、连携技能、任务 ref。
/// 双端同跑把「数据 bug 在两端失败模式分歧」变为确定性失败。
struct DataIntegrityEngine {
    struct Violation: Identifiable {
        let code: String
        let message: String
        var id: String { code }
    }

    private static let skillIds: Set<String> = Set(SK.keys)
    private static let monsterIds: Set<String> = Set(MonsterPool.all.map { $0.id })
    private static let elementNames: Set<String> = ["METAL", "WOOD", "EARTH", "WATER", "FIRE", "WIND", "THUNDER"]
    private static let rarityNames: Set<String> = ["FAN", "LING", "XIAN", "SHEN"]
    private static let elementRefTypes: Set<ObjectiveType> = [.catchElement, .winWithElement]
    private static let rarityRefTypes: Set<ObjectiveType> = [.catchRarity]

    static func validate() -> [Violation] {
        var v: [Violation] = []
        // 1) 妖池技能 ⊆ 技能池
        for m in MonsterPool.all {
            for s in m.skills {
                if !skillIds.contains(s.id) {
                    v.append(Violation(code: "MON_SKILL", message: "妖 \(m.id) 技能 \(s.id) 不存在于技能池"))
                }
            }
        }
        // 2) 进化链 ⊆ 妖池
        for m in MonsterPool.all {
            if let to = m.evolveTo, !monsterIds.contains(to) {
                v.append(Violation(code: "EVO_TO", message: "妖 \(m.id) evolveTo 不存在: \(to)"))
            }
            if let from = m.evolveFrom, !monsterIds.contains(from) {
                v.append(Violation(code: "EVO_FROM", message: "妖 \(m.id) evolveFrom 不存在: \(from)"))
            }
        }
        // 3) 连携技能 ⊆ 技能池
        for c in ComboEngine.COMBOS {
            for sid in c.required {
                if !skillIds.contains(sid) {
                    v.append(Violation(code: "COMBO_SKILL", message: "连携 \(c.id) 技能 \(sid) 不存在于技能池"))
                }
            }
        }
        // 4) 任务 ref ∈ 合法枚举名
        for q in QuestLibrary.all {
            for o in q.objectives {
                if o.ref.isEmpty { continue }
                if elementRefTypes.contains(o.type) && !elementNames.contains(o.ref) {
                    v.append(Violation(code: "QUEST_REF_ELEM", message: "任务 \(q.id) \(o.type) ref 非法元素: \(o.ref)"))
                }
                if rarityRefTypes.contains(o.type) && !rarityNames.contains(o.ref) {
                    v.append(Violation(code: "QUEST_REF_RAR", message: "任务 \(q.id) \(o.type) ref 非法稀有度: \(o.ref)"))
                }
            }
        }
        // 5) 炼妖融合配方产物 ⊆ 妖池（防止「玄武」类配方指向不存在的妖 → 融合静默失败）
        for out in FusionEngine.recipeOutputs {
            if !monsterIds.contains(out) {
                v.append(Violation(code: "RECIPE_OUT", message: "融合配方产物 \(out) 不存在于妖池"))
            }
        }
        return v
    }

    static func isClean() -> Bool { validate().isEmpty() }

    static func assertClean() {
        let v = validate()
        if !v.isEmpty {
            fatalError("静态数据完整性校验失败: " + v.map { "\($0.code):\($0.message)" }.joined(separator: "; "))
        }
    }
}

// MARK: - 图鉴 / 游戏状态
struct CodexEntry { let monster: Monster; let caught: Bool }
struct Milestone { let threshold: Int; let reward: String; let reached: Bool }

class Codex {
    let all: [Monster]
    private var caughtRegion = Set<String>()
    private var caughtSecret = Set<String>()
    init(all: [Monster]) { self.all = all }
    func `catch`(id: String, secret: Bool = false) { secret ? caughtSecret.insert(id) : caughtRegion.insert(id) }
    func hasInRegion(_ id: String) -> Bool { caughtRegion.contains(id) }
    func hasAny(_ id: String) -> Bool { caughtRegion.contains(id) || caughtSecret.contains(id) }
    var regionCaught: Int { caughtRegion.count }
    func regionCompletion() -> Double { all.isEmpty ? 0 : Double(caughtRegion.count) / Double(all.count) }
    func byCategory(_ c: MonsterCategory) -> [CodexEntry] {
        all.filter { $0.category == c }.map { CodexEntry(monster: $0, caught: caughtRegion.contains($0.id)) }
    }
    // 存档快照 / 恢复
    func snapshotRegion() -> [String] { Array(caughtRegion) }
    func snapshotSecret() -> [String] { Array(caughtSecret) }
    func load(region: [String], secret: [String]) { caughtRegion = Set(region); caughtSecret = Set(secret) }

    // 1:1 镜像 Kotlin Codex.MILESTONES（阈值 → 奖励文案）
    static let MILESTONE_DEFS: [(threshold: Int, reward: String)] = [
        (1, "初入巡妖使（香火+20）"),
        (5, "小有所成（妖丹×50）"),
        (10, "妖王克星（十连祈妖券）"),
        (20, "图鉴近全（妖丹×100）"),
    ]
    /// 镜像 Kotlin milestones()：每项里程碑含「是否已达成」
    func milestones() -> [Milestone] {
        MILESTONE_DEFS.map { Milestone(threshold: $0.threshold, reward: $0.reward, reached: caughtRegion.count >= $0.threshold) }
    }
    /// 镜像 Kotlin newlyUnlocked(prevCount)：本次收妖后是否越过新阈值（用于弹奖励）
    func newlyUnlocked(_ prevCount: Int) -> Milestone? {
        MILESTONE_DEFS
            .first(where: { $0.threshold > prevCount && caughtRegion.count >= $0.threshold })
            .map { Milestone(threshold: $0.threshold, reward: $0.reward, reached: true) }
    }
}

class GameState: ObservableObject {
    let codex: Codex
    let quests = QuestLog()
    @Published var team: [String] = []            // 出战队伍（≤6，存「妖实例 id」）
    @Published var owned: [String: MonsterInstance] = [:] // 拥有的妖实例（按实例 id 索引）
    @Published var nextInstSeq: Int = 1           // 实例 id 自增序号
    @Published var equipment: [OwnedEquip] = []    // 拥有的装备实例
    @Published var nextEquipSeq: Int = 1           // 装备实例 id 自增序号
    @Published var equipped: [String: [EquipSlot: String]] = [:] // 妖实例id → 槽位 → 装备实例id
    @Published var incense = 120
    @Published var lingyu = 0
    @Published var yaodan = 0
    @Published var secretRealmLeft = 3
    @Published var lastDailyReset = ""   // yyyy-MM-dd
    @Published var questVersion = 0   // 触发任务相关视图刷新
    // ── ② LBS·AR / ③ 祈妖·副本·社交·经济 ──
    @Published var gachaSinceShen = 0
    @Published var dungeonEnteredToday: [String: Int] = [:]
    @Published var faction: String? = nil
    @Published var strongholdNode: String? = nil
    @Published var strongholdDay = ""
    @Published var shopIncenseBought = 0
    @Published var shopDay = ""
    @Published var reputation = 100   // 信誉分（GDD 十六·公平与稳健）
    // ── 十九·天气地形战斗：运行时环境变量（不进存档，由平台提供真实天气/地形） ──
    @Published var currentWeather: Weather = .clear
    @Published var currentTerrain: RegionTheme? = nil
    // ── 十八·新手引导 / 7 日留存 ──
    @Published var tutorialStep = 0
    @Published var tutorialSkipped = false
    @Published var signInDay = 0
    @Published var lastSignIn = ""
    // ── 十九·长线留存：家园洞府（放置产香火/妖丹 + 阵法风水加成） ──
    @Published var homesteadLevel = 0
    @Published var lastHomesteadCollect: Int64 = 0
    // ── 十九·长线留存：赛季通行证（90 天双轨） ──
    @Published var seasonStartMs: Int64 = 0
    @Published var seasonXp = 0
    @Published var hasSeasonPass = false
    @Published var seasonClaimedFree: Set<Int> = []
    @Published var seasonClaimedPaid: Set<Int> = []
    @Published var seasonIndex = 1
    // ── 十六·公平与稳健：疲劳值 / 单日收益封顶 ──
    @Published var fatigue = 0   // 疲劳值 0..100，每日跨天重置
    // ── 二十·竞技与荣誉：排位赛段位天梯（GDD 二十·段位 炼气→化神） ──
    @Published var rankTierIndex = RankEngine.minTierIndex   // 当前段位索引 0=炼气 … 4=化神
    @Published var rankPoints = 0                                // 当前段位内积分 0..99
    // ── 二十·竞技与荣誉：成就 / 勋章 / 称号（展示型荣誉） ──
    @Published var tribulationTotal = 0   // 累计渡劫飞升次数（称号统计）
    @Published var equippedTitleId = HonorEngine.noTitle   // 玩家装备的展示称号 id（""=无）
    // ── 二十·宗门科技树（公会建设 + 科技加成） ──
    @Published var sectTech: [String: Int] = [:]   // 科技节点 id → 等级
    @Published var sectContribution = 0            // 宗门贡献（占领据点积累）
    init() { codex = Codex(all: MonsterPool.all) }

    func catchMonster(id: String, secret: Bool = false, choice: MoralChoice? = nil) -> [String] {
        // 防御性边界：未知妖种 id（平台误传 / 过期数据 / 外挂注入）直接拒绝，
        // 避免污染图鉴并产生 defId 悬空、属性回退首妖的孤儿实例。零变更返回空列表。
        guard MonsterPool.byId(id) != nil else { return [] }
        codex.catch(id: id, secret: secret)
        let inst = MonsterInstance(instanceId: "i_\(id)_\(nextInstSeq)", defId: id, level: 1, exp: 0, breakthrough: 0, moralChoice: choice)
        nextInstSeq += 1
        owned[inst.instanceId] = inst
        if team.count < 6 { team.append(inst.instanceId) }
        ensureStarterKit()   // 首只妖时发放新手装备，保证「炼妖」页可用
        var completed: [String] = []
        if let m = MonsterPool.byId(id) {
            completed += quests.record(.monsterCaught(element: m.element, rarity: m.rarity, secret: secret))
        }
        completed += quests.record(.codexUpdated(ratio: Int(codex.regionCompletion() * 100)))
        questVersion += 1
        completeTutorialStep(1)   // 引导步 ①：首次收服
        addSeasonXp(SeasonEngine.xpCatch)   // 收妖也给赛季经验
        addFatigue(FatigueEngine.fatigueCatch)   // 收妖累积疲劳
        return completed
    }

    /** 取某拥有实例的镇压/超度有效属性倍率；实例不存在返回 nil（GDD 十七·自由度） */
    func moralModsFor(_ instanceId: String) -> StatMods? {
        guard let inst = owned[instanceId] else { return nil }
        return MoralChoiceEngine.modifiers(inst.moralChoice)
    }

    // ── ② LBS·AR / ③ 祈妖·副本·社交·经济 API ──

    /** 内部：发放一只新妖实例并入队 */
    private func grantMonster(_ id: String) {
        let inst = MonsterInstance(instanceId: "i_\(id)_\(nextInstSeq)", defId: id, level: 1, exp: 0, breakthrough: 0)
        nextInstSeq += 1
        owned[inst.instanceId] = inst
        if team.count < 6 { team.append(inst.instanceId) }
    }

    /** AR 收妖当前可用状态（平台层应覆盖 ARCaptureController.status） */
    func arStatus() -> ARStatus { ARCaptureController.status() }

    /** LBS 防作弊判定（平台在刷新灵脉 / 收妖前应先用此检查） */
    func locationIntegrity(_ ctx: SpawnContext) -> IntegrityResult { LocationEngine.spawnIntegrity(ctx) }

    /** 当前定位是否允许户外收妖：rejected（瞬移）禁止，其余允许（degraded 仍允许但平台应降品质）。 */
    func canCapture(_ ctx: SpawnContext) -> Bool { locationIntegrity(ctx).tier != .rejected }

    // ── 信誉分（GDD 十六·公平与稳健）：与 GPS 防作弊闭环 ──
    func reputationTier() -> ReputationTier { ReputationEngine.tier(reputation) }
    func reputationLabel() -> String { ReputationEngine.tierLabel(reputation) }
    func lbsRareSpawnMul() -> Double { ReputationEngine.rareSpawnMultiplier(reputation) }
    func canExploreLbs() -> Bool { ReputationEngine.canExploreLbs(reputation) }
    /// 上报一次 LBS 防作弊结果：仅 rejected（瞬移）扣信誉分，返回是否实际扣分。
    @discardableResult
    func reportIntegrity(_ result: IntegrityResult) -> Bool {
        if result.tier != .rejected { return false }
        let after = ReputationEngine.applyPenalty(reputation, .teleport)
        let changed = after != reputation
        reputation = after
        return changed
    }

    /** 祈妖抽卡：[ten]=true 十连，否则单抽；灵玉不足返回 nil；重复转妖丹，新妖入队 */
    func pullGacha(ten: Bool) -> [GachaResult]? {
        let cost = ten ? GachaEngine.tenLingyu : GachaEngine.singleLingyu
        if lingyu < cost { return nil }
        lingyu -= cost
        var gstate = GachaState(sinceShen: gachaSinceShen)
        let results = ten ? GachaEngine.tenPull(&gstate) : [GachaEngine.pull(&gstate)]
        gachaSinceShen = gstate.sinceShen
        for r in results {
            let isNew = !codex.hasAny(r.monsterId)
            if isNew {
                codex.catch(id: r.monsterId)
                grantMonster(r.monsterId)
            } else {
                yaodan += GachaEngine.duplicateYaodan
            }
            _ = quests.record(.monsterGachaPulled)
        }
        questVersion += 1
        return results
    }

    /** 通关副本：扣香火、记当日次数、发奖励；条件不足返回 nil */
    func enterDungeon(defId: String, today: String) -> DungeonReward? {
        guard let def = DungeonLibrary.byId(defId) else { return nil }
        let st = DungeonRunState(enteredToday: dungeonEnteredToday)
        guard DungeonEngine.canEnter(def, st, incense) else { return nil }
        incense -= def.costIncense
        let reward = DungeonEngine.enter(def, st)
        dungeonEnteredToday[defId] = (dungeonEnteredToday[defId] ?? 0) + 1
        yaodan += Int(Double(reward.yaodan) * incomeMul() * sectIncomeMul() * activityIncomeMul())   // 副本收益受疲劳封顶 + 宗门科技「资粮」+ 活动加成
        if reward.bossCaptured, let bid = reward.bossMonsterId, !codex.hasAny(bid) {
            codex.catch(id: bid)
            grantMonster(bid)
        }
        _ = quests.record(.dungeonEntered)
        questVersion += 1
        addSeasonXp(SeasonEngine.xpDungeon)   // 副本通关给赛季经验
        addFatigue(FatigueEngine.fatigueDungeon)   // 副本累积疲劳
        return reward
    }

    /** 加入派系（可后期转） */
    func joinFaction(_ f: Faction) { faction = f.rawValue; completeTutorialStep(4) }   // 引导步 ④：三派择门

    /** 占领据点节点（每日限一次），成功则按「据点经营」科技积累宗门贡献 */
    func captureNode(_ node: String, today: String) -> Bool {
        // 防御性边界：未知节点（平台误传 / 过期数据 / 外挂注入）直接拒绝，
        // 避免写入悬空据点引用 + 仍触发虚假 +10% 攻击加成。零变更返回。
        guard LocationEngine.strongholdNodes.contains(node) else { return false }
        var st = SocialEngine.StrongholdState(ownedNode: strongholdNode, lastCaptureDay: strongholdDay)
        let ok = SocialEngine.capture(&st, node, today)
        strongholdNode = st.ownedNode; strongholdDay = st.lastCaptureDay
        if ok {
            let gain = Int(Double(SectTechEngine.contributionPerCapture) * (1.0 + sectStrongholdBonus()))
            addSectContribution(gain)
        }
        questVersion += 1
        return ok
    }

    /** 当前据点攻击加成（占领 → 1.10） */
    func strongholdAttackBonus() -> Double { SocialEngine.attackBonus(strongholdNode != nil) }

    // ── 二十·宗门科技树（GDD 二十·公会建设 + 科技加成） ──
    /// 玩家所属派系（Faction 枚举），未择门返回 nil
    func currentFaction() -> Faction? {
        guard let f = faction else { return nil }
        return Faction(rawValue: f)
    }
    /// 当前科技汇总加成（按已研习等级 + 派系汇总）
    func sectBonuses() -> SectBonuses { SectTechEngine.bonuses(sectTech, currentFaction()) }
    func sectAttackMul() -> Double { 1.0 + sectBonuses().attack }
    func sectDefenseMul() -> Double { 1.0 + sectBonuses().defense }
    func sectHpMul() -> Double { 1.0 + sectBonuses().hp }
    func sectCaptureMul() -> Double { 1.0 + sectBonuses().capture }
    func sectExpMul() -> Double { 1.0 + sectBonuses().exp }
    func sectIncomeMul() -> Double { 1.0 + sectBonuses().income }
    func sectStrongholdBonus() -> Double { sectBonuses().stronghold }
    // ── 活动（运营限时·日期区间触发·不进存档） ──
    /// 当日（lastDailyReset）所有生效活动
    func currentActivities() -> [Activity] { ActivityEngine.active(lastDailyReset) }
    /// 收妖成功率乘子（生效活动乘性叠加）
    func activityCaptureMul() -> Double { ActivityEngine.aggregate(currentActivities()).captureMul }
    /// 经验获取乘子（生效活动乘性叠加）
    func activityExpMul() -> Double { ActivityEngine.aggregate(currentActivities()).expMul }
    /// 收益（香火/妖丹）乘子（生效活动乘性叠加）
    func activityIncomeMul() -> Double { ActivityEngine.aggregate(currentActivities()).incomeMul }
    /// 某科技当前等级
    func sectTechLevel(_ id: String) -> Int { sectTech[id] ?? 0 }
    /// 某科技是否可研习（供 UI 展示按钮可用态）
    func canResearchSectTech(_ id: String) -> Bool {
        SectTechEngine.canResearch(id, sectTech, currentFaction(), sectContribution)
    }
    /// 研习某科技：扣贡献、写等级；成功返回 true，条件不足返回 false
    func researchSectTech(_ id: String) -> Bool {
        guard let res = SectTechEngine.research(id, sectTech, currentFaction(), sectContribution) else { return false }
        sectTech = res.0
        sectContribution = res.1
        questVersion += 1
        return true
    }
    /// 增加宗门贡献（平台在据点战 / 宗门活动时调用）
    func addSectContribution(_ amount: Int) { if amount > 0 { sectContribution += amount } }

    /** 购买引妖香：成功返回 (香火获得, 灵玉花费)，否则 nil */
    func buyIncense(today: String) -> (Int, Int)? {
        var st = ShopEngine.ShopState(incenseBoughtToday: shopIncenseBought, lastBuyDay: shopDay)
        guard let res = ShopEngine.buyIncense(&st, today, lingyu) else { return nil }
        shopIncenseBought = st.incenseBoughtToday; shopDay = st.lastBuyDay
        incense += res.0; lingyu -= res.1
        questVersion += 1
        return res
    }

    // ── 养成 API（等级 / 经验 / 突破 / 进化） ──
    func gainExp(instanceId: String, _ amount: Int) -> [String] {
        guard let inst = owned[instanceId] else { return [] }
        let scaled = Int(Double(amount) * sectExpMul() * activityExpMul())   // 经验受宗门科技「经修」+ 活动加成
        let after = CultivationEngine.gainExp(inst, scaled)
        owned[instanceId] = after
        let completed = quests.record(.monsterTrained(level: after.level))
        questVersion += 1
        return completed
    }
    func breakthroughMonster(instanceId: String) -> Bool {
        guard let inst = owned[instanceId], CultivationEngine.canBreakthrough(inst) else { return false }
        let cost = CultivationEngine.breakthroughCost(inst.breakthrough)
        guard yaodan >= cost else { return false }
        yaodan -= cost
        owned[instanceId] = CultivationEngine.breakthrough(inst) ?? inst
        _ = quests.record(.monsterBrokenThrough)
        questVersion += 1
        return true
    }
    func evolveMonster(instanceId: String) -> Bool {
        guard let inst = owned[instanceId], let after = CultivationEngine.evolve(inst) else { return false }
        owned[instanceId] = after
        _ = quests.record(.monsterEvolved)
        questVersion += 1
        return true
    }
    // ── 渡劫飞升 API ──
    func tribulateMonster(instanceId: String) -> Bool {
        guard let inst = owned[instanceId], CultivationEngine.canTribulate(inst) else { return false }
        let cost = CultivationEngine.tribulateCost(inst)
        guard yaodan >= cost else { return false }
        yaodan -= cost
        owned[instanceId] = CultivationEngine.tribulate(inst) ?? inst
        _ = quests.record(.monsterTribulated)
        tribulationTotal += 1   // 累计渡劫次数（称号解锁统计）
        questVersion += 1
        return true
    }
    // ── 天赋 / 命格（每只妖点 3 层天赋树 攻/防/辅） ──
    func assignTalent(instanceId: String, _ branch: TalentBranch) -> Bool {
        guard let inst = owned[instanceId], TalentEngine.canAssign(inst, branch) else { return false }
        owned[instanceId] = TalentEngine.assign(inst, branch) ?? inst
        _ = quests.record(.monsterTalentAssigned(branch: branch))
        questVersion += 1
        return true
    }
    // ── 装备 / 祭炼 / 炼妖融合 API ──
    func grantEquip(_ equipId: String) -> OwnedEquip? {
        guard EquipmentLibrary.byId(equipId) != nil else { return nil }
        let oe = OwnedEquip(equipId: equipId, instanceId: "e_\(nextEquipSeq)", level: 1)
        nextEquipSeq += 1
        equipment.append(oe)
        return oe
    }
    private func ensureStarterKit() {
        if equipment.isEmpty {
            grantEquip("eq_sword"); grantEquip("eq_armor")
        }
    }
    func findEquip(_ equipEid: String) -> OwnedEquip? { equipment.first { $0.instanceId == equipEid } }
    func equippedItems(_ instanceId: String) -> [OwnedEquip] {
        guard let slots = equipped[instanceId] else { return [] }
        return slots.values.compactMap { findEquip($0) }
    }
    func equipItem(instanceId: String, _ equipEid: String) -> Bool {
        guard owned[instanceId] != nil, let oe = findEquip(equipEid),
              let def = MonsterPool.byId(owned[instanceId]!.defId) else { return false }
        for (mid, slots) in equipped where mid != instanceId {
            if slots.values.contains(equipEid) { return false }
        }
        guard let ed = EquipmentLibrary.byId(oe.equipId) else { return false }
        var map = equipped[instanceId] ?? [:]
        map[ed.slot] = equipEid
        equipped[instanceId] = map
        _ = quests.record(.monsterEquipped)
        questVersion += 1
        return true
    }
    func unequipItem(instanceId: String, _ slot: EquipSlot) -> Bool {
        guard var map = equipped[instanceId] else { return false }
        let removed = map.removeValue(forKey: slot) != nil
        if map.isEmpty { equipped.removeValue(forKey: instanceId) } else { equipped[instanceId] = map }
        return removed
    }
    func forgeEquip(_ equipEid: String) -> Bool {
        guard let idx = equipment.firstIndex(where: { $0.instanceId == equipEid }) else { return false }
        let oe = equipment[idx]
        guard let def = EquipmentLibrary.byId(oe.equipId), oe.level < EquipmentEngine.maxLevelOf(def, oe), yaodan >= EquipmentEngine.FORGE_COST_YAODAN else { return false }
        yaodan -= EquipmentEngine.FORGE_COST_YAODAN
        equipment[idx] = OwnedEquip(equipId: oe.equipId, instanceId: oe.instanceId, level: oe.level + 1, stars: oe.stars, tier: oe.tier, substats: oe.substats)
        _ = quests.record(.monsterForged)
        questVersion += 1
        return true
    }
    // ── 装备五路径（升星 / 突破 / 淬炼 / 继承） ──
    func starUpEquip(_ equipEid: String) -> Bool {
        guard let idx = equipment.firstIndex(where: { $0.instanceId == equipEid }) else { return false }
        let oe = equipment[idx]
        guard EquipmentEngine.canStarUp(oe) else { return false }
        let cost = EquipmentEngine.starCost(oe)
        guard lingyu >= cost else { return false }
        lingyu -= cost
        equipment[idx] = EquipmentEngine.starUp(oe)!
        _ = quests.record(.monsterForged); questVersion += 1
        return true
    }
    func breakthroughEquip(_ equipEid: String) -> Bool {
        guard let idx = equipment.firstIndex(where: { $0.instanceId == equipEid }) else { return false }
        let oe = equipment[idx]
        guard let def = EquipmentLibrary.byId(oe.equipId), EquipmentEngine.canBreakthrough(oe, def) else { return false }
        let cost = EquipmentEngine.tierCost(oe)
        guard lingyu >= cost else { return false }
        lingyu -= cost
        equipment[idx] = EquipmentEngine.breakthrough(oe, def)!
        _ = quests.record(.monsterForged); questVersion += 1
        return true
    }
    func temperEquip(_ equipEid: String) -> Bool {
        guard let idx = equipment.firstIndex(where: { $0.instanceId == equipEid }) else { return false }
        let oe = equipment[idx]
        guard EquipmentEngine.canTemper(oe) else { return false }
        let cost = EquipmentEngine.temperCost(oe)
        guard lingyu >= cost else { return false }
        lingyu -= cost
        equipment[idx] = EquipmentEngine.temper(oe)!
        _ = quests.record(.monsterForged); questVersion += 1
        return true
    }
    func inheritEquip(_ srcEid: String, _ dstEid: String) -> Bool {
        guard srcEid != dstEid,
              let si = equipment.firstIndex(where: { $0.instanceId == srcEid }),
              let di = equipment.firstIndex(where: { $0.instanceId == dstEid }) else { return false }
        let src = equipment[si]; let dst = equipment[di]
        guard yaodan >= EquipmentEngine.INHERIT_COST_YAODAN else { return false }
        for mid in equipped.keys {                       // 源若正被装备，先卸下（避免悬空引用）
            if var slots = equipped[mid], slots.contains(where: { $0.value == srcEid }) {
                slots = slots.filter { $0.value != srcEid }
                equipped[mid] = slots
            }
        }
        yaodan -= EquipmentEngine.INHERIT_COST_YAODAN
        equipment[di] = EquipmentEngine.inherit(src, dst)!
        equipment.removeAll { $0.instanceId == srcEid }
        _ = quests.record(.monsterForged); questVersion += 1
        return true
    }

    func effectiveStatsOf(_ inst: MonsterInstance) -> Stats {
        let base = CultivationEngine.effectiveStats(inst) ?? Stats(atk: 1, def: 1, hp: 1, spd: 1)
        if let m = MonsterPool.byId(inst.defId) {
            let bonus = EquipmentEngine.totalBonus(m, equippedItems(inst.instanceId))
            return Stats(atk: base.atk + bonus.atk,
                         def: Int(Double(base.def + bonus.def) * sectDefenseMul()),     // 宗门科技「守御」
                         hp: Int(Double(base.hp + bonus.hp) * sectHpMul()),             // 宗门科技「体魄」
                         spd: base.spd + bonus.spd)
        }
        return base
    }
    func buildCombatant(_ inst: MonsterInstance) -> Combatant {
        let m = MonsterPool.byId(inst.defId) ?? MonsterPool.all.first!
        let skills = Array(m.skills.prefix(4))
        return Combatant(monster: m, level: inst.level, equipped: skills, breakthrough: inst.breakthrough, effective: effectiveStatsOf(inst))
    }
    func fuse(_ aId: String, _ bId: String) -> FusionEngine.FusionResult? {
        guard let a = owned[aId], let b = owned[bId], FusionEngine.canFuse(a, b, yaodan) else { return nil }
        guard let plan = FusionEngine.plan(a, b) else { return nil }
        yaodan -= FusionEngine.FUSE_COST_YAODAN
        unequipAll(aId); unequipAll(bId)
        owned.removeValue(forKey: aId); owned.removeValue(forKey: bId)
        team = team.filter { $0 != aId && $0 != bId }
        let newInst = MonsterInstance(instanceId: "i_\(plan.newDefId)_\(nextInstSeq)", defId: plan.newDefId, level: plan.newLevel, exp: 0, breakthrough: plan.newBreakthrough)
        nextInstSeq += 1
        owned[newInst.instanceId] = newInst
        if team.count < 6 { team.append(newInst.instanceId) }
        _ = quests.record(.monsterFused)
        questVersion += 1
        return plan
    }
    private func unequipAll(_ instanceId: String) { equipped.removeValue(forKey: instanceId) }
    func onBattleWon(element: Element?) {
        _ = quests.record(.battleWon(element: element))
        questVersion += 1
        addSeasonXp(SeasonEngine.xpBattle)   // 对战胜利给赛季经验
        addFatigue(FatigueEngine.fatigueBattle)   // 对战累积疲劳
    }
    /// 进入秘境（GDD 三·在家秘境）：每日 secretRealmLeft 次优先（免费），耗尽则耗 INCENSE_COST 香火；
    /// 随机主题 → 生成刷妖 → 记 secretEntered 事件。无可用次数且香火不足返回 nil。
    func enterSecretRealm(_ rng: () -> Double = { Double.random(in: 0...1) }) -> SecretRealmResult? {
        let usedIncense = secretRealmLeft <= 0
        if usedIncense {
            if incense < SecretRealmEngine.INCENSE_COST { return nil }
            incense -= SecretRealmEngine.INCENSE_COST
        } else {
            secretRealmLeft -= 1
        }
        let theme = SecretRealmEngine.pickTheme(rng)
        let spawns = SecretRealmEngine.generateSpawns(theme, SecretRealmEngine.SPAWN_COUNT, rng)
        _ = quests.record(.secretEntered)
        questVersion += 1
        completeTutorialStep(2)   // 引导步 ②：入秘境
        return SecretRealmResult(theme: theme, element: SecretRealmEngine.themeElementBias(theme), spawns: spawns)
    }

    // ── 十八·新手引导 / 7 日留存 ──
    func tutorialActive() -> Bool {
        !TutorialEngine.allDone(tutorialStep, tutorialSkipped)
    }
    func currentTutorialStep() -> TutorialStep? {
        TutorialEngine.currentStep(tutorialStep)
    }
    func completeTutorialStep(_ order: Int) {
        if order > tutorialStep { tutorialStep = order }
    }
    func skipTutorial() { tutorialSkipped = true }
    /// 已解锁功能：引导进行中全部开放（引导即教学，含秘境），完成后按签到天逐日解锁。
    func unlockedFeatures() -> Set<RealmFeature> {
        tutorialActive() ? Set(RealmFeature.allCases) : TutorialEngine.unlockedFeatures(signInDay)
    }
    func canSignIn(_ today: String) -> Bool { lastSignIn != today }
    /// 签到：每日首次领取递增奖励（香火），推进 7 日留存并标记引导步 ⑤。
    func claimSignIn(_ today: String) -> Int {
        guard canSignIn(today) else { return 0 }
        signInDay += 1
        lastSignIn = today
        let reward = TutorialEngine.signInReward(signInDay)
        let gained = Int(Double(reward) * incomeMul() * sectIncomeMul() * activityIncomeMul())   // 签到收益受疲劳封顶 + 宗门科技「资粮」+ 活动加成
        incense += gained
        completeTutorialStep(5)   // 引导步 ⑤：签到
        addSeasonXp(SeasonEngine.xpSignin)   // 签到给赛季经验
        addFatigue(FatigueEngine.fatigueSignin)   // 签到累积疲劳
        return gained
    }
    // ── 十九·长线留存：家园洞府（放置产香火/妖丹 + 阵法风水加成） ──
    func homesteadIncenseRate() -> Double { HomesteadEngine.incenseRate(homesteadLevel) }
    func homesteadPillRate() -> Double { HomesteadEngine.pillRate(homesteadLevel) }
    func homesteadAttackBonus() -> Double { HomesteadEngine.fengshuiAttackBonus(homesteadLevel) }
    func homesteadPending(_ nowMs: Int64) -> HomesteadResult {
        HomesteadEngine.compute(homesteadLevel, nowMs, lastHomesteadCollect)
    }
    /// 收取洞府产出（香火/妖丹），并把收取时间戳推进到 nowMs。
    func collectHomestead(_ nowMs: Int64) -> HomesteadResult {
        if lastHomesteadCollect <= 0 {
            // 首次进入：仅启动计时（推进时间戳），不补发历史产出
            lastHomesteadCollect = nowMs
            return HomesteadResult(incense: 0, pills: 0, hours: 0.0, capped: false)
        }
        let r = HomesteadEngine.compute(homesteadLevel, nowMs, lastHomesteadCollect)
        if r.incense <= 0 && r.pills <= 0 { return r }
        incense += r.incense; yaodan += r.pills
        lastHomesteadCollect = nowMs
        return r
    }
    func canUpgradeHomestead() -> Bool { yaodan >= HomesteadEngine.upgradeCost(homesteadLevel) }
    func upgradeHomestead() -> Bool {
        let cost = HomesteadEngine.upgradeCost(homesteadLevel)
        guard yaodan >= cost else { return false }
        yaodan -= cost; homesteadLevel += 1
        return true
    }
    func claimQuest(id: String) -> String? {
        guard let r = quests.claim(id: id) else { return nil }
        incense += r.incense; lingyu += r.lingyu; yaodan += r.yaodan
        questVersion += 1
        return r.text
    }
    func teamMonsters() -> [MonsterInstance] { team.compactMap { owned[$0] } }

    /** 当前出战队伍激活的阵容羁绊（GDD 七·队伍组合技）。 */
    func teamSynergies() -> [Synergy] {
        let monsters = teamMonsters().compactMap { MonsterPool.byId($0.defId) }
        return FormationEngine.evaluate(monsters)
    }

    /** 当前出战队伍的全队攻击增伤倍率（用于 BattleSession）。 */
    func teamAttackMultiplier() -> Double { FormationEngine.teamAttackMultiplier(teamSynergies()) }

    /// 当前地形五行偏向（复用 LocationEngine.regionElementBias），无地形返回 nil。
    func currentTerrainBias() -> Element? {
        guard let t = currentTerrain else { return nil }
        return LocationEngine.regionElementBias(t)
    }

    /// 进入战斗时传给 BattleSession 的玩家全队攻击增伤倍率：阵容羁绊 × 阵法风水增益。
    func battleTeamMul() -> Double { teamAttackMultiplier() * (1.0 + homesteadAttackBonus()) }

    func setWeather(_ w: Weather) { currentWeather = w }
    func setTerrain(_ t: RegionTheme?) { currentTerrain = t }

    // ── 十九·长线留存：赛季通行证（90 天双轨） ──
    private func ensureSeasonStart(_ nowMs: Int64) { if seasonStartMs <= 0 { seasonStartMs = nowMs } }
    /// 赛季推进：超出 90 天则滚动到下一赛季（序号 +1、经验/已领清零、通行证失效）。返回是否滚动。
    func tickSeason(_ nowMs: Int64) -> Bool {
        ensureSeasonStart(nowMs)
        if nowMs - seasonStartMs >= Int64(SeasonEngine.seasonLengthDays) * Int64(SeasonEngine.dayMs) {
            seasonIndex += 1
            seasonStartMs += Int64(SeasonEngine.seasonLengthDays) * Int64(SeasonEngine.dayMs)
            seasonXp = 0
            seasonClaimedFree = []
            seasonClaimedPaid = []
            hasSeasonPass = false
            return true
        }
        return false
    }
    func seasonDayNow(_ nowMs: Int64) -> Int { SeasonEngine.dayIndex(seasonStartMs, nowMs) }
    func seasonDaysRemaining(_ nowMs: Int64) -> Int { SeasonEngine.daysRemaining(seasonStartMs, nowMs) }
    func isSeasonInProgress(_ nowMs: Int64) -> Bool { SeasonEngine.inSeason(seasonStartMs, nowMs) }
    func currentSeasonTier() -> Int { SeasonEngine.tierFromXp(seasonXp) }
    func addSeasonXp(_ amount: Int) { if amount > 0 { seasonXp += amount } }

    // ── 十六·公平与稳健：疲劳值 / 单日收益封顶 ──
    func incomeMul() -> Double { FatigueEngine.incomeMul(fatigue) }
    func addFatigue(_ delta: Int) { fatigue = FatigueEngine.addFatigue(fatigue, delta) }
    func resetFatigueDaily() { fatigue = FatigueEngine.resetDaily() }
    func fatigueLabel() -> String { FatigueEngine.label(fatigue) }

    // ── 二十·竞技与荣誉：排位赛段位天梯 ──
    func rankTierName() -> String { RankEngine.tierName(rankTierIndex) }
    func rankProgress() -> Double { (Double(rankPoints) / Double(RankEngine.pointsToPromote)).clamped(to: 0.0...1.0) }
    /// 记录一场排位对局结果（胜负由平台侧 PvP 结算后回传）；升段按新段位发荣誉奖励
    func rankedBattle(_ won: Bool) -> RankResult {
        let res = RankEngine.applyResult(rankTierIndex, rankPoints, won)
        rankTierIndex = res.tierIndex
        rankPoints = res.points
        if res.promoted {
            let reward = RankEngine.rankReward(res.tierIndex)
            incense += reward.incense
            lingyu += reward.lingyu
            yaodan += reward.yaodan
        }
        return res
    }

    // ── 二十·竞技与荣誉：成就 / 勋章 / 称号（GDD 二十·展示型荣誉） ──
    func achievementCount() -> Int {
        quests.byCategory(.achievement).filter { $0.state == .completed || $0.state == .claimed }.count
    }
    func achievementTotal() -> Int { quests.byCategory(.achievement).count }
    func honorProfile() -> HonorEngine.HonorProfile {
        HonorEngine.HonorProfile(
            achievementCount: achievementCount(),
            achievementTotal: achievementTotal(),
            rankTierIndex: rankTierIndex,
            codexRatio: Int(codex.regionCompletion() * 100),
            tribulationTotal: tribulationTotal
        )
    }
    func medals() -> [HonorEngine.Medal] { HonorEngine.unlockedMedals(honorProfile()) }
    func titles() -> [HonorEngine.Title] { HonorEngine.unlockedTitles(honorProfile()) }
    func unlockedTitleIds() -> Set<String> { Set(titles().map { $0.id }) }
    /// 装备称号：仅当已解锁才成功；传空串取消装备
    func equipTitle(_ id: String) -> Bool {
        if id == HonorEngine.noTitle { equippedTitleId = HonorEngine.noTitle; return true }
        if !HonorEngine.isUnlocked(id, honorProfile()) { return false }
        equippedTitleId = id
        return true
    }
    /// 当前展示称号名（未装备返回空串）
    func displayTitle() -> String {
        if equippedTitleId == HonorEngine.noTitle { return HonorEngine.noTitle }
        return HonorEngine.titles.first(where: { $0.id == equippedTitleId })?.name ?? HonorEngine.noTitle
    }
    func buySeasonPass(cost: Int = 300) -> Bool {
        if lingyu < cost { return false }
        lingyu -= cost
        hasSeasonPass = true
        return true
    }
    func claimSeasonTier(_ tier: Int) -> SeasonClaimResult {
        let cur = currentSeasonTier()
        var claimedFree = false, claimedPaid = false
        var inc = 0, ly = 0, yd = 0
        if tier >= 1 && tier <= cur && !seasonClaimedFree.contains(tier) {
            let r = SeasonEngine.freeReward(tier)
            inc += r.incense; yd += r.yaodan
            incense += r.incense; yaodan += r.yaodan
            seasonClaimedFree.insert(tier)
            claimedFree = true
        }
        if tier >= 1 && tier <= cur && hasSeasonPass && !seasonClaimedPaid.contains(tier) {
            let r = SeasonEngine.paidReward(tier)
            ly += r.lingyu; yd += r.yaodan
            lingyu += r.lingyu; yaodan += r.yaodan
            seasonClaimedPaid.insert(tier)
            claimedPaid = true
        }
        return SeasonClaimResult(tier: tier, claimedFree: claimedFree, claimedPaid: claimedPaid,
                                 incense: inc, lingyu: ly, yaodan: yd)
    }
    func claimAllSeason() -> [SeasonClaimResult] {
        let cur = currentSeasonTier()
        return (1...cur).map { claimSeasonTier($0) }
    }

    /** 某妖（按 defId）当前出战 4 技能可触发的连携组合技（GDD 七·连携）。 */
    func monsterCombos(_ defId: String) -> [Combo] {
        guard let m = MonsterPool.byId(defId) else { return [] }
        return ComboEngine.availableCombos(m.skills.prefix(4).map { $0.id })
    }

    // ---- 持久化 ----
    func toSaveData() -> SaveData {
        SaveData(caughtRegion: codex.snapshotRegion(), caughtSecret: codex.snapshotSecret(),
                 team: team, incense: incense, lingyu: lingyu, yaodan: yaodan,
                 secretRealmLeft: secretRealmLeft, lastDailyReset: lastDailyReset,
                 quests: quests.snapshot(), owned: Array(owned.values), nextInstSeq: nextInstSeq,
                 equipment: equipment, nextEquipSeq: nextEquipSeq, equipped: equipped,
                 gachaSinceShen: gachaSinceShen, dungeonEntered: dungeonEnteredToday,
                 faction: faction, strongholdNode: strongholdNode, strongholdDay: strongholdDay,
                 shopIncenseBought: shopIncenseBought, shopDay: shopDay, reputation: reputation,
                 tutorialStep: tutorialStep, tutorialSkipped: tutorialSkipped,
                 signInDay: signInDay, lastSignIn: lastSignIn,
                 homesteadLevel: homesteadLevel, lastHomesteadCollect: lastHomesteadCollect,
                 seasonStartMs: seasonStartMs, seasonXp: seasonXp, hasSeasonPass: hasSeasonPass,
                 seasonClaimedFree: Array(seasonClaimedFree), seasonClaimedPaid: Array(seasonClaimedPaid),
                 seasonIndex: seasonIndex,                  fatigue: fatigue,
                 rankTierIndex: rankTierIndex, rankPoints: rankPoints,
                 tribulationTotal: tribulationTotal, equippedTitleId: equippedTitleId,
                 sectTech: sectTech, sectContribution: sectContribution)
    }
    func applySaveData(_ s: SaveData) {
        codex.load(region: s.caughtRegion, secret: s.caughtSecret)
        owned = [:]
        for it in s.owned { owned[it.instanceId] = it }
        team = s.team.filter { owned[$0] != nil }
        incense = s.incense; lingyu = s.lingyu; yaodan = s.yaodan
        secretRealmLeft = s.secretRealmLeft; lastDailyReset = s.lastDailyReset
        nextInstSeq = s.nextInstSeq
        equipment = s.equipment
        nextEquipSeq = s.nextEquipSeq
        equipped = s.equipped
        gachaSinceShen = s.gachaSinceShen
        dungeonEnteredToday = s.dungeonEntered
        faction = s.faction
        strongholdNode = s.strongholdNode; strongholdDay = s.strongholdDay
        shopIncenseBought = s.shopIncenseBought; shopDay = s.shopDay
        reputation = s.reputation
        tutorialStep = s.tutorialStep; tutorialSkipped = s.tutorialSkipped
        signInDay = s.signInDay; lastSignIn = s.lastSignIn
        homesteadLevel = s.homesteadLevel; lastHomesteadCollect = s.lastHomesteadCollect
        seasonStartMs = s.seasonStartMs; seasonXp = s.seasonXp; hasSeasonPass = s.hasSeasonPass
        seasonClaimedFree = Set(s.seasonClaimedFree); seasonClaimedPaid = Set(s.seasonClaimedPaid)
        seasonIndex = s.seasonIndex
        fatigue = s.fatigue
        rankTierIndex = s.rankTierIndex
        rankPoints = s.rankPoints
        tribulationTotal = s.tribulationTotal
        equippedTitleId = s.equippedTitleId
        sectTech = s.sectTech
        sectContribution = s.sectContribution
        quests.applySnapshot(s.quests)
    }
    func maybeResetDaily(_ today: String) {
        if DailyResetEngine.isCrossDay(lastDailyReset, today) {
            quests.resetDaily()
            secretRealmLeft = 3
            dungeonEnteredToday = [:]
            if shopDay != today { shopIncenseBought = 0; shopDay = today }
            if strongholdDay != today { strongholdNode = nil; strongholdDay = today }
            reputation = ReputationEngine.dailyRecover(reputation)   // 每日缓慢恢复 +1
            fatigue = FatigueEngine.resetDaily()   // 疲劳每日重置
            lastDailyReset = today
        }
    }
    func save() {
        let data = toSaveData()
        if let json = try? JSONEncoder().encode(data) {
            UserDefaults.standard.set(String(data: json, encoding: .utf8), forKey: "shanhai_save")
        }
    }
    static func loadSave() -> SaveData? {
        guard let s = UserDefaults.standard.string(forKey: "shanhai_save"),
              let d = s.data(using: .utf8) else { return nil }
        guard let save = try? JSONDecoder().decode(SaveData.self, from: d) else { return nil }
        return SaveIntegrityEngine.sanitize(save)
    }
}

func todayString() -> String {
    let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; return f.string(from: Date())
}
