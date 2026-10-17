#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
装备五路径（升星 / 突破 / 淬炼 / 继承）等价验证。

直接解析 shared 层 EquipmentLibrary.kt 源码抽取 EquipDef（不走手抄镜像，避免漂移），
Python 重实现 EquipmentEngine 五路径规则 + GameState 四方法，断言关键不变量。

不依赖编译器：本机无 JDK 时的替代验证（Kotlin 单测留给用户 .gradlew :shared:test）。
"""
import re
import sys

KOTLIN = "shared/src/commonMain/kotlin/com/shanhai/core/data/EquipmentLibrary.kt"

# ── 枚举映射 ──
RARITY_ORDER = {"FAN": 0, "LING": 1, "XIAN": 2, "SHEN": 3}
ELEMENTS = {"METAL", "WOOD", "WATER", "FIRE", "WIND", "THUNDER", "EARTH"}

# ── 五路径常量（与 EquipmentEngine.kt 一致）──
AFFINITY_MULT = 1.2
FORGE_COST_YAODAN = 10
MAX_STAR = 5
STAR_BONUS = 0.10
TIER_LEVEL_BONUS = 10
TIER_PERLEVEL_BONUS = 0.03
MAX_SUBSTATS = 4
SUB_CAP = 15
TEMPER_STEP = 3
STAR_COST_BASE = 50
TIER_COST_BASE = 80
TEMPER_COST_BASE = 30
INHERIT_COST_YAODAN = 20
SUB_ORDER = ["ATK", "DEF", "HP", "SPD"]

# ── 解析 EquipDef ──
def parse_equip_defs(path):
    text = open(path, encoding="utf-8").read()
    pat = re.compile(
        r'EquipDef\(\s*"([^"]+)"\s*,\s*"[^"]*"\s*,\s*EquipSlot\.(\w+)\s*,\s*'
        r'(?:Element\.(\w+)|null)\s*,\s*Rarity\.(\w+)\s*,\s*'
        r'(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)'
    )
    defs = {}
    for m in pat.finditer(text):
        eid, slot, elem, rar, atk, dfn, hp, spd = m.groups()
        defs[eid] = {
            "slot": slot, "element": None if elem == "null" else elem,
            "rarity": rar, "baseAtk": int(atk), "baseDef": int(dfn),
            "baseHp": int(hp), "baseSpd": int(spd),
            "bonusPerLevel": 0.12, "maxLevel": 10,
        }
    return defs

# ── 五路径规则（镜像）──
def max_tier(rarity):
    return RARITY_ORDER[rarity] + 1

def max_level_of(def_, oe):
    return def_["maxLevel"] + (oe["tier"] - 1) * TIER_LEVEL_BONUS

def effective_per_level(def_, oe):
    return def_["bonusPerLevel"] + (oe["tier"] - 1) * TIER_PERLEVEL_BONUS

def star_mult(oe):
    return 1.0 + (oe["stars"] - 1) * STAR_BONUS

def owned(equip_id, level=1, stars=1, tier=1, substats=None):
    return {"equipId": equip_id, "instanceId": "e_" + equip_id,
            "level": level, "stars": stars, "tier": tier,
            "substats": substats or []}

def owned_from_old(equip_id, level):
    """旧档仅含 level，缺失 stars/tier/substats 时默认（模拟 kotlinx 默认值）"""
    return {"equipId": equip_id, "instanceId": "e_" + equip_id,
            "level": level, "stars": 1, "tier": 1, "substats": []}

def sub_bonus(oe):
    a = d = h = s = 0
    for sub in oe["substats"]:
        if sub["stat"] == "ATK": a += sub["value"]
        elif sub["stat"] == "DEF": d += sub["value"]
        elif sub["stat"] == "HP": h += sub["value"]
        elif sub["stat"] == "SPD": s += sub["value"]
    return {"atk": a, "def": d, "hp": h, "spd": s}

def bonus(oe, def_, element_matches):
    mult = (1.0 + (oe["level"] - 1) * effective_per_level(def_, oe)) \
           * star_mult(oe) * (AFFINITY_MULT if element_matches else 1.0)
    main = {
        "atk": int(def_["baseAtk"] * mult),
        "def": int(def_["baseDef"] * mult),
        "hp": int(def_["baseHp"] * mult),
        "spd": int(def_["baseSpd"] * mult),
    }
    sub = sub_bonus(oe)
    return {k: max(0, main[k] + sub[k]) for k in main}

def total_bonus(monster_elem, equips, defs):
    tot = {"atk": 0, "def": 0, "hp": 0, "spd": 0}
    for oe in equips:
        d = defs.get(oe["equipId"])
        if not d: continue
        b = bonus(oe, d, d["element"] == monster_elem)
        for k in tot: tot[k] += b[k]
    return tot

# 升星
def can_star_up(oe): return oe["stars"] < MAX_STAR
def star_cost(oe): return STAR_COST_BASE * oe["stars"]
def star_up(oe):
    if not can_star_up(oe): return None
    return {**oe, "stars": oe["stars"] + 1}

# 突破
def can_breakthrough(oe, def_): return oe["tier"] < max_tier(def_["rarity"])
def tier_cost(oe): return TIER_COST_BASE * oe["tier"]
def breakthrough(oe, def_):
    if not can_breakthrough(oe, def_): return None
    return {**oe, "tier": oe["tier"] + 1}

# 淬炼
def can_temper(oe):
    if len(oe["substats"]) >= MAX_SUBSTATS:
        return any(s["value"] < SUB_CAP for s in oe["substats"])
    return True
def temper_used(oe):
    return sum((s["value"] + TEMPER_STEP - 1) // TEMPER_STEP for s in oe["substats"])
def temper_cost(oe): return TEMPER_COST_BASE * (temper_used(oe) + 1)
def temper(oe):
    if not can_temper(oe): return None
    existing = {s["stat"] for s in oe["substats"]}
    nxt = next((s for s in SUB_ORDER if s not in existing), None)
    if nxt is not None and len(oe["substats"]) < MAX_SUBSTATS:
        new = oe["substats"] + [{"stat": nxt, "value": TEMPER_STEP}]
    else:
        idx = next((i for i, s in enumerate(oe["substats"]) if s["value"] < SUB_CAP), -1)
        if idx < 0: return None
        new = [s.copy() for s in oe["substats"]]
        new[idx] = {"stat": new[idx]["stat"], "value": min(new[idx]["value"] + TEMPER_STEP, SUB_CAP)}
    return {**oe, "substats": new}

# 继承（合并较优）
def can_inherit(src, dst): return src["instanceId"] != dst["instanceId"]
def inherit(src, dst):
    if not can_inherit(src, dst): return None
    mp = {s["stat"]: s["value"] for s in dst["substats"]}
    for s in src["substats"]:
        mp[s["stat"]] = max(mp.get(s["stat"], 0), s["value"])
    merged = []
    for st in SUB_ORDER:
        if st in mp:
            merged.append({"stat": st, "value": mp[st]})
            if len(merged) >= MAX_SUBSTATS: break
    return {**dst, "level": max(dst["level"], src["level"]),
            "stars": max(dst["stars"], src["stars"]),
            "tier": max(dst["tier"], src["tier"]), "substats": merged}

# ── 简化 GameState（镜像四方法）──
class GameState:
    def __init__(self, defs):
        self.defs = defs
        self.equipment = []
        self.equipped = {}
        self.lingyu = 0
        self.yaodan = 0
    def grant(self, eid):
        oe = owned(eid)
        self.equipment.append(oe)
        return oe
    def find(self, eid): return next((o for o in self.equipment if o["instanceId"] == eid), None)
    def _idx(self, eid): return next((i for i, o in enumerate(self.equipment) if o["instanceId"] == eid), -1)
    def star_up(self, eid):
        i = self._idx(eid)
        if i < 0: return False
        oe = self.equipment[i]
        if not can_star_up(oe): return False
        c = star_cost(oe)
        if self.lingyu < c: return False
        self.lingyu -= c
        self.equipment[i] = star_up(oe)
        return True
    def breakthrough(self, eid):
        i = self._idx(eid)
        if i < 0: return False
        oe = self.equipment[i]
        d = self.defs.get(oe["equipId"])
        if not d: return False
        if not can_breakthrough(oe, d): return False
        c = tier_cost(oe)
        if self.lingyu < c: return False
        self.lingyu -= c
        self.equipment[i] = breakthrough(oe, d)
        return True
    def temper(self, eid):
        i = self._idx(eid)
        if i < 0: return False
        oe = self.equipment[i]
        if not can_temper(oe): return False
        c = temper_cost(oe)
        if self.lingyu < c: return False
        self.lingyu -= c
        self.equipment[i] = temper(oe)
        return True
    def inherit(self, src_eid, dst_eid):
        if src_eid == dst_eid: return False
        si, di = self._idx(src_eid), self._idx(dst_eid)
        if si < 0 or di < 0: return False
        src, dst = self.equipment[si], self.equipment[di]
        if self.yaodan < INHERIT_COST_YAODAN: return False
        for slots in self.equipped.values():
            for k in [kk for kk, v in slots.items() if v == src_eid]:
                del slots[k]
        self.yaodan -= INHERIT_COST_YAODAN
        self.equipment[di] = inherit(src, dst)
        self.equipment = [o for o in self.equipment if o["instanceId"] != src_eid]
        return True

# ── 断言框架 ──
pass_n = 0; fail_n = 0; fails = []
def check(cond, msg):
    global pass_n, fail_n
    if cond: pass_n += 1
    else: fail_n += 1; fails.append(msg)

def main():
    defs = parse_equip_defs(KOTLIN)
    check(len(defs) == 9, f"EquipDef 解析应为 9 件（实际 {len(defs)}）")
    check("eq_sword" in defs and defs["eq_sword"]["element"] == "METAL", "eq_sword 元素 METAL 解析正确")
    check(defs["eq_ring"]["rarity"] == "XIAN", "eq_ring 稀有度 XIAN 解析正确")

    eq = defs["eq_sword"]  # 灵阶武器，maxTier=2

    # 1) maxTier 阶序
    for r, expect in [("FAN", 1), ("LING", 2), ("XIAN", 3), ("SHEN", 4)]:
        check(max_tier(r) == expect, f"maxTier({r})={expect}")

    # 2) maxLevelOf 随 tier 提升
    o1 = owned("eq_sword", level=1, tier=1)
    check(max_level_of(eq, o1) == 10, "tier1 maxLevel=10")
    o2 = owned("eq_sword", level=1, tier=2)
    check(max_level_of(eq, o2) == 20, "tier2 maxLevel=20")

    # 3) effectivePerLevel 随 tier
    check(abs(effective_per_level(eq, o1) - 0.12) < 1e-9, "tier1 perLevel=0.12")
    check(abs(effective_per_level(eq, o2) - 0.15) < 1e-9, "tier2 perLevel=0.15")

    # 4) starMult
    check(abs(star_mult(owned("eq_sword", stars=1)) - 1.0) < 1e-9, "stars1 mult=1.0")
    check(abs(star_mult(owned("eq_sword", stars=5)) - 1.4) < 1e-9, "stars5 mult=1.4")

    # 5) bonus 主属性（level/tier/stars/affinity）
    # 青锋剑 baseAtk=18 金属，妖为 METAL → 亲和
    b1 = bonus(owned("eq_sword", level=1, stars=1, tier=1), eq, True)
    check(b1["atk"] == int(18 * AFFINITY_MULT), f"L1 亲和 atk={int(18*AFFINITY_MULT)}（实际 {b1['atk']}）")
    b2 = bonus(owned("eq_sword", level=2, stars=1, tier=1), eq, True)
    check(b2["atk"] == int(18 * 1.12 * AFFINITY_MULT), f"L2 亲和 atk={int(18*1.12*AFFINITY_MULT)}（实际 {b2['atk']}）")
    b3 = bonus(owned("eq_sword", level=1, stars=2, tier=1), eq, True)
    check(b3["atk"] == int(18 * 1.1 * AFFINITY_MULT), f"stars2 亲和 atk={int(18*1.1*AFFINITY_MULT)}（实际 {b3['atk']}）")
    b4 = bonus(owned("eq_sword", level=1, stars=1, tier=2), eq, True)
    check(b4["atk"] == int(18 * AFFINITY_MULT), f"tier2 对 L1 无效（仍亲和）atk={int(18*AFFINITY_MULT)}（实际 {b4['atk']}）")
    b5 = bonus(owned("eq_sword", level=1, stars=1, tier=1), eq, False)
    check(b5["atk"] == 18, "无亲和时不乘契合倍率")

    # 6) subStatsBonus 累加
    oe_sub = owned("eq_sword", substats=[{"stat": "ATK", "value": 5}, {"stat": "HP", "value": 10}])
    sb = sub_bonus(oe_sub)
    check(sb["atk"] == 5 and sb["hp"] == 10, "副属性 ATK+5 HP+10 累加")

    # 7) totalBonus 含副属性
    t1 = total_bonus("METAL", [owned("eq_sword", substats=[{"stat": "ATK", "value": 5}])], defs)
    check(t1["atk"] == int(18 * AFFINITY_MULT) + 5, f"totalBonus atk={int(18*AFFINITY_MULT)+5}（实际 {t1['atk']}）")
    t2 = total_bonus("METAL", [owned("eq_sword"), owned("eq_armor")], defs)
    # eq_armor baseDef=16 baseHp=30 水亲和(妖METAL否) → 无亲和
    check(t2["def"] == 16 and t2["hp"] == 30, "两件 totalBonus 叠加 def16 hp30")

    # 8) 升星成本递增 + 前置
    for s in range(1, 5):
        check(star_cost(owned("eq_sword", stars=s)) == 50 * s, f"starCost stars{s}={50*s}")
    check(can_star_up(owned("eq_sword", stars=5)) is False, "stars5 不可再升")
    su = star_up(owned("eq_sword", stars=3))
    check(su and su["stars"] == 4, "starUp stars3→4")

    # 9) 突破成本递增 + 前置（灵阶 maxTier=2）
    for t in range(1, 3):
        check(tier_cost(owned("eq_sword", tier=t)) == 80 * t, f"tierCost tier{t}={80*t}")
    check(can_breakthrough(owned("eq_sword", tier=2), eq) is False, "灵阶 tier2 不可再突破")
    bt = breakthrough(owned("eq_sword", tier=1), eq)
    check(bt and bt["tier"] == 2, "breakthrough tier1→2")

    # 10) 淬炼确定性 + 成本递增
    oe = owned("eq_sword")
    seq = []
    costs = []
    for _ in range(20):  # 填满 4×5=20 次
        if not can_temper(oe): break
        costs.append(temper_cost(oe))
        nt = temper(oe)
        assert nt is not None
        oe = nt
        seq.append(oe["substats"][-1]["stat"] if len(oe["substats"]) > len(seq) or True else None)
    check(len(oe["substats"]) == 4, f"淬炼 4 条副属性（实际 {len(oe['substats'])}）")
    check(all(s["value"] == SUB_CAP for s in oe["substats"]), "每条副属性封顶 15")
    check([s["stat"] for s in oe["substats"]] == ["ATK", "DEF", "HP", "SPD"], "副属性顺序 ATK→DEF→HP→SPD")
    # 成本严格递增
    check(all(costs[i] < costs[i+1] for i in range(len(costs)-1)), "淬炼成本严格递增")
    check(temper_cost(oe) == TEMPER_COST_BASE * (temper_used(oe) + 1), "temperCost 公式")
    check(can_temper(oe) is False, "满 4 条且全封顶不可再淬炼")

    # 11) 继承合并较优
    src = owned("eq_sword", level=3, stars=2, tier=1, substats=[{"stat": "ATK", "value": 9}])
    dst = owned("eq_armor", level=5, stars=1, tier=2, substats=[{"stat": "HP", "value": 6}])
    inh = inherit(src, dst)
    check(inh["level"] == 5 and inh["stars"] == 2 and inh["tier"] == 2, "继承取较优 level/stars/tier")
    check(any(s["stat"] == "ATK" and s["value"] == 9 for s in inh["substats"]), "继承合并 src ATK=9")
    check(any(s["stat"] == "HP" and s["value"] == 6 for s in inh["substats"]), "继承保留 dst HP=6")

    # 12) GameState 四方法
    gs = GameState(defs)
    oe = gs.grant("eq_sword")
    gs.lingyu = 1000
    before = gs.lingyu
    check(gs.star_up(oe["instanceId"]), "GameState.star_up 成功")
    check(gs.lingyu == before - 50, "star_up 扣灵玉 50")
    gs.yaodan = 100
    before_y = gs.yaodan
    src2 = gs.grant("eq_armor")
    dst2 = gs.grant("eq_staff")
    n_eq_before = len(gs.equipment)
    check(gs.inherit(src2["instanceId"], dst2["instanceId"]), "GameState.inherit 成功")
    check(len(gs.equipment) == n_eq_before - 1, "继承后源装备被移除")
    check(gs.yaodan == before_y - 20, "inherit 扣妖丹 20")

    # 升星资源不足失败
    gs2 = GameState(defs)
    oe2 = gs2.grant("eq_sword")
    gs2.lingyu = 10
    check(gs2.star_up(oe2["instanceId"]) is False, "灵玉不足升星失败")
    check(gs2.find(oe2["instanceId"])["stars"] == 1, "失败不改动星级")

    # 突破到顶失败（灵阶 tier2）
    gs3 = GameState(defs)
    oe3 = gs3.grant("eq_sword")
    gs3.lingyu = 9999
    check(gs3.breakthrough(oe3["instanceId"]), "灵阶首次突破成功")
    check(gs3.breakthrough(oe3["instanceId"]) is False, "灵阶 tier2 再突破失败")

    # 淬炼到满后不可
    gs4 = GameState(defs)
    oe4 = gs4.grant("eq_sword")
    gs4.lingyu = 99999
    cnt = 0
    while gs4.temper(oe4["instanceId"]):
        cnt += 1
    check(cnt == 20, f"淬炼填满 20 次（实际 {cnt}）")
    check(gs4.temper(oe4["instanceId"]) is False, "满后淬炼失败")

    # 13) 旧档兼容（仅 level，缺失 stars/tier/substats → 默认）
    old = owned_from_old("eq_sword", level=5)
    check(old["stars"] == 1 and old["tier"] == 1 and old["substats"] == [], "旧档默认 stars=1/tier=1/substats=[]")
    check(bonus(old, eq, False)["atk"] == int(18 * (1 + (5 - 1) * 0.12)), "旧档 L5 无星级加成 atk=26")

    print(f"装备五路径验证：通过 {pass_n} 项，失败 {fail_n} 项")
    if fails:
        print("失败项：")
        for f in fails: print("  ✗", f)
        return 1
    print("✓ 全部断言通过")
    return 0

if __name__ == "__main__":
    sys.exit(main())
