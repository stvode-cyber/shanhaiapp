#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
《山海妖志》装备祭炼 + 炼妖融合 逻辑验证闸门（与 Kotlin/Swift 共享核心等价）。
本机无 JDK，用此脚本作为唯一可执行的正确性闸门；覆盖：
  - 装备等级加成 / 元素契合 ×1.2 / 祭炼上限
  - 妖实例有效属性（突破 ×(1+bt*0.08) + 装备加成）
  - 炼妖融合（配方 / 通用回退 / 等级上限 / 突破 / 技能并集 / 妖丹消耗）
  - 存档往返（装备 / 装配映射 / 妖丹扣除 全部恢复）

运行：python verify_equipment_fusion.py
"""
import json

# ───────────── 常量（与 Kotlin CultivationEngine / EquipmentEngine / FusionEngine 对齐） ─────────────
BASE_LEVEL_CAP = 30
LEVEL_CAP_PER_BREAK = 5
STAT_MULT_PER_BREAK = 0.08
EVOLVE_LEVEL_REQ = 25
EQUIP_AFFINITY_MULT = 1.2
FORGE_COST_YAODAN = 10
FUSE_COST_YAODAN = 30
FUSE_LEVEL_BONUS = 3

GROWTH = {"FAN": 1.0, "LING": 1.1, "XIAN": 1.25, "SHEN": 1.5}
RARITY_ORD = {"FAN": 0, "LING": 1, "XIAN": 2, "SHEN": 3}


def stats_at_level(base_atk, base_def, base_hp, base_spd, rarity, level):
    g = GROWTH[rarity]
    lc = 1.0 + (level - 1) * 0.02
    return {
        "atk": max(1, int(base_atk * g * lc)),
        "def": max(1, int(base_def * g * lc)),
        "hp": max(1, int(base_hp * g * lc)),
        "spd": max(1, int(base_spd * g)),   # spd 不含等级系数
    }


# ───────────── 数据（仅验证所需子集，字段对齐 MonsterPool / EquipmentLibrary） ─────────────
MONSTERS = {
    "yao_she":     {"element": "WATER", "rarity": "FAN",  "atk": 46,  "def": 40, "hp": 100, "spd": 62,  "evolve_to": "yao_jiao",  "skills": ["s_jingtao", "s_qingmu2"]},
    "yao_hu":      {"element": "FIRE",  "rarity": "FAN",  "atk": 48,  "def": 38, "hp": 96,  "spd": 72,  "skills": ["s_fentian", "s_dingshen"]},
    "yao_jiao":    {"element": "WATER", "rarity": "LING", "atk": 60,  "def": 52, "hp": 130, "spd": 70,  "evolve_from": "yao_she", "evolve_to": "yao_long", "skills": ["s_hantan", "s_jingtao"]},
    "yao_long":    {"element": "WATER", "rarity": "SHEN", "atk": 88,  "def": 72, "hp": 170, "spd": 80,  "skills": ["s_hantan", "s_jingtao", "s_ganlin", "s_wulei"]},
    "gui_guijia":  {"element": "WATER", "rarity": "FAN",  "atk": 44,  "def": 52, "hp": 130, "spd": 28,  "evolve_to": "gui_linggui", "skills": ["s_jingtao", "s_xuanbing"]},
    "gui_linggui": {"element": "WATER", "rarity": "LING", "atk": 58,  "def": 66, "hp": 165, "spd": 36,  "evolve_from": "gui_guijia", "evolve_to": "gui_xuanwu", "skills": ["s_hantan", "s_xuanbing", "s_jinzhong"]},
    "gui_xuanwu":  {"element": "WATER", "rarity": "SHEN", "atk": 82,  "def": 78, "hp": 200, "spd": 50,  "skills": ["s_hantan", "s_xuanbing", "s_jinzhong", "s_luoshi"]},
}
EQUIPMENTS = {
    "eq_sword":   {"slot": "WEAPON",    "element": "METAL",   "rarity": "LING", "atk": 18, "def": 0,  "hp": 0,  "spd": 4,  "bpl": 0.12, "max": 10},
    "eq_armor":   {"slot": "ARMOR",     "element": "WATER",   "rarity": "LING", "atk": 0,  "def": 16, "hp": 30, "spd": 0,  "bpl": 0.12, "max": 10},
    "eq_ring":    {"slot": "ACCESSORY", "element": "THUNDER", "rarity": "XIAN", "atk": 8,  "def": 4,  "hp": 10, "spd": 6,  "bpl": 0.12, "max": 10},
    "eq_pendant": {"slot": "ACCESSORY", "element": "WATER",   "rarity": "LING", "atk": 0,  "def": 6,  "hp": 20, "spd": 4,  "bpl": 0.12, "max": 10},
}

RECIPES = {frozenset(["yao_jiao", "gui_linggui"]): "gui_xuanwu"}


# ───────────── 引擎（与 Kotlin 等价实现） ─────────────
def equip_bonus_at_level(def_id, level, element_matches):
    d = EQUIPMENTS[def_id]
    mult = (1.0 + (level - 1) * d["bpl"]) * (EQUIP_AFFINITY_MULT if element_matches else 1.0)
    return {
        "atk": int(d["atk"] * mult),
        "def": int(d["def"] * mult),
        "hp": int(d["hp"] * mult),
        "spd": int(d["spd"] * mult),
    }


def total_bonus(monster_def_id, owned_equips):
    """owned_equips: list of (def_id, level)"""
    m = MONSTERS[monster_def_id]
    tot = {"atk": 0, "def": 0, "hp": 0, "spd": 0}
    for def_id, level in owned_equips:
        ed = EQUIPMENTS[def_id]
        match = (ed["element"] == m["element"])
        b = equip_bonus_at_level(def_id, level, match)
        for k in tot:
            tot[k] += b[k]
    return tot


def can_forge(equip_def_id, level):
    return level < EQUIPMENTS[equip_def_id]["max"]


def level_cap(breakthrough):
    return BASE_LEVEL_CAP + breakthrough * LEVEL_CAP_PER_BREAK


def stat_mult(breakthrough):
    return 1.0 + breakthrough * STAT_MULT_PER_BREAK


def effective_stats(monster_def_id, level, breakthrough, owned_equips):
    base = stats_at_level(
        MONSTERS[monster_def_id]["atk"], MONSTERS[monster_def_id]["def"],
        MONSTERS[monster_def_id]["hp"], MONSTERS[monster_def_id]["spd"],
        MONSTERS[monster_def_id]["rarity"], level,
    )
    mult = stat_mult(breakthrough)
    eff = {k: max(1, int(base[k] * mult)) for k in base}
    bonus = total_bonus(monster_def_id, owned_equips)
    return {k: eff[k] + bonus[k] for k in eff}


def fusion_plan(a_def, a_level, a_bt, b_def, b_level, b_bt):
    if a_def == b_def:
        return None
    if a_def not in MONSTERS or b_def not in MONSTERS:
        return None
    key = frozenset([a_def, b_def])
    if key in RECIPES:
        new_def = RECIPES[key]
    else:
        da, db = MONSTERS[a_def], MONSTERS[b_def]
        if RARITY_ORD[db["rarity"]] > RARITY_ORD[da["rarity"]]:
            new_def = b_def
        elif RARITY_ORD[da["rarity"]] > RARITY_ORD[db["rarity"]]:
            new_def = a_def
        else:
            sa = sum(da[k] for k in ("atk", "def", "hp", "spd"))
            sb = sum(db[k] for k in ("atk", "def", "hp", "spd"))
            new_def = b_def if sb >= sa else a_def
    bt = min(a_bt, b_bt)
    cap = level_cap(bt)
    new_level = min(max(a_level, b_level) + FUSE_LEVEL_BONUS, cap)
    da, db = MONSTERS[a_def], MONSTERS[b_def]
    skills = []
    for s in da.get("skills", []) + db.get("skills", []):
        if s not in skills:
            skills.append(s)
        if len(skills) >= 4:
            break
    return dict(new_def=new_def, new_level=new_level, new_breakthrough=bt, new_skills=skills)


# ───────────── 极简 GameState 镜像（用于存档往返） ─────────────
class GS:
    def __init__(self):
        self.owned = {}          # instId -> dict(defId, level, exp, bt)
        self.next_inst = 1
        self.equipment = []      # dict(equipId, instanceId, level)
        self.next_eq = 1
        self.equipped = {}       # monsterInstId -> {slot: equipInstanceId}
        self.yaodan = 0

    def catch(self, def_id):
        inst = dict(instanceId=f"i_{def_id}_{self.next_inst}", defId=def_id, level=1, exp=0, bt=0)
        self.next_inst += 1
        self.owned[inst["instanceId"]] = inst
        if not self.equipment:   # 新手装备
            self.grant("eq_sword"); self.grant("eq_armor")
        return inst

    def grant(self, equip_id):
        oe = dict(equipId=equip_id, instanceId=f"e_{self.next_eq}", level=1)
        self.next_eq += 1
        self.equipment.append(oe)
        return oe

    def find_equip(self, eid):
        for e in self.equipment:
            if e["instanceId"] == eid:
                return e
        return None

    def equip(self, inst_id, eid):
        if inst_id not in self.owned or self.find_equip(eid) is None:
            return False
        for mid, slots in self.equipped.items():
            if mid != inst_id and eid in slots.values():
                return False
        oe = self.find_equip(eid)
        slot = EQUIPMENTS[oe["equipId"]]["slot"]
        self.equipped.setdefault(inst_id, {})[slot] = eid
        return True

    def forge(self, eid):
        oe = self.find_equip(eid)
        if oe is None or not can_forge(oe["equipId"], oe["level"]):
            return False
        if self.yaodan < FORGE_COST_YAODAN:
            return False
        self.yaodan -= FORGE_COST_YAODAN
        oe["level"] += 1
        return True

    def fuse(self, a_id, b_id):
        a = self.owned.get(a_id); b = self.owned.get(b_id)
        if a is None or b is None:
            return None
        if a_id == b_id or self.yaodan < FUSE_COST_YAODAN:
            return None
        r = fusion_plan(a["defId"], a["level"], a["bt"], b["defId"], b["level"], b["bt"])
        if r is None:
            return None
        self.yaodan -= FUSE_COST_YAODAN
        self.equipped.pop(a_id, None); self.equipped.pop(b_id, None)
        self.owned.pop(a_id, None); self.owned.pop(b_id, None)
        new = dict(instanceId=f"i_{r['new_def']}_{self.next_inst}", defId=r["new_def"],
                   level=r["new_level"], exp=0, bt=r["new_breakthrough"])
        self.next_inst += 1
        self.owned[new["instanceId"]] = new
        return r

    def to_save(self):
        return dict(
            owned=list(self.owned.values()),
            nextInstSeq=self.next_inst,
            equipment=self.equipment,
            nextEquipSeq=self.next_eq,
            equipped=self.equipped,
            yaodan=self.yaodan,
        )

    def apply_save(self, s):
        self.owned = {o["instanceId"]: o for o in s["owned"]}
        self.next_inst = s["nextInstSeq"]
        self.equipment = [dict(e) for e in s["equipment"]]
        self.next_eq = s["nextEquipSeq"]
        self.equipped = {k: dict(v) for k, v in s["equipped"].items()}
        self.yaodan = s["yaodan"]


# ───────────── 断言框架 ─────────────
passed = 0
failed = 0


def check(name, cond):
    global passed, failed
    if cond:
        passed += 1
    else:
        failed += 1
        print(f"  ✗ FAIL: {name}")


# ───────────── 1. 装备加成 ─────────────
b1 = equip_bonus_at_level("eq_sword", 1, False)
check("青锋剑 Lv1 无契合 atk=18", b1["atk"] == 18 and b1["spd"] == 4 and b1["def"] == 0)
b2 = equip_bonus_at_level("eq_armor", 1, True)   # 水甲给水妖 → 契合
check("玄龟甲 Lv1 水契合 def=19 (16*1.2)", b2["def"] == 19)
check("玄龟甲 Lv1 水契合 hp=36 (30*1.2)", b2["hp"] == 36)
b3 = equip_bonus_at_level("eq_armor", 1, False)  # 给火妖 → 不契合
check("玄龟甲 Lv1 无契合 def=16", b3["def"] == 16)
b4 = equip_bonus_at_level("eq_sword", 5, False)  # (1+4*0.12)=1.48 → 18*1.48=26.64→26
check("青锋剑 Lv5 atk=26 (18*1.48)", b4["atk"] == 26)
check("祭炼上限: eq_sword Lv10 不可再祭炼", not can_forge("eq_sword", 10))
check("祭炼可行: eq_sword Lv1 可祭炼", can_forge("eq_sword", 1))

# ───────────── 2. 有效属性（突破 + 装备） ─────────────
e_lv1 = effective_stats("yao_she", 1, 0, [])
check("蛇妖 Lv1 无突破无装备 atk=46", e_lv1["atk"] == 46)
check("蛇妖 Lv1 无突破无装备 spd=62", e_lv1["spd"] == 62)
e_bt1 = effective_stats("yao_she", 1, 1, [])
check("蛇妖 Lv1 突破1 atk=49 (46*1.08)", e_bt1["atk"] == 49)
check("蛇妖 Lv1 突破1 spd=66 (62*1.08)", e_bt1["spd"] == 66)
# 水妖装备玄龟甲（契合）+ 青锋剑（不契合）
e_eq = effective_stats("gui_guijia", 1, 0, [("eq_armor", 1), ("eq_sword", 1)])
base = stats_at_level(44, 52, 130, 28, "FAN", 1)  # atk44 def52 hp130 spd28
check("龟妖+玄龟甲(契合def19)+青锋剑(不契合atk18) atk=62", e_eq["atk"] == base["atk"] + 18)
check("龟妖 有效 def=52+19=71", e_eq["def"] == base["def"] + 19)
check("龟妖 有效 hp=130+36=166", e_eq["hp"] == base["hp"] + 36)

# ───────────── 3. 炼妖融合 ─────────────
# 同实例不可融合
check("融合: 同实例返回 None", fusion_plan("yao_she", 1, 0, "yao_she", 1, 0) is None)
# 通用回退：两凡妖，基础属性高者胜
r1 = fusion_plan("yao_she", 1, 0, "yao_hu", 1, 0)
check("融合回退: 取属性更高者(yao_hu)", r1["new_def"] == "yao_hu")
check("融合回退: 新等级=1+3=4", r1["new_level"] == 4)
check("融合回退: 突破=min=0", r1["new_breakthrough"] == 0)
# 配方：蛟 + 灵龟 → 玄武
r2 = fusion_plan("yao_jiao", 1, 0, "gui_linggui", 1, 0)
check("融合配方: 蛟+灵龟→玄武", r2["new_def"] == "gui_xuanwu")
# 等级/突破：两 Lv20 bt1 → 新等级 min(23, 35)=23，突破=1
r3 = fusion_plan("yao_long", 20, 1, "gui_xuanwu", 20, 1)
check("融合 双Lv20bt1 新等级=23", r3["new_level"] == 23)
check("融合 双Lv20bt1 突破=1", r3["new_breakthrough"] == 1)
# 技能并集前4
check("融合 技能并集不超过4", len(r3["new_skills"]) <= 4 and len(r3["new_skills"]) >= 1)
# 等级上限封顶：双 Lv30 bt0 → 33 但 cap=30 → 30
r4 = fusion_plan("yao_long", 30, 0, "gui_xuanwu", 30, 0)
check("融合 等级上限封顶=30", r4["new_level"] == 30)

# ───────────── 4. 存档往返（装备 / 装配 / 妖丹） ─────────────
gs = GS()
gs.yaodan = 200
inst = gs.catch("yao_she")              # 同时发放 eq_sword / eq_armor
eid_sword = next(e["instanceId"] for e in gs.equipment if e["equipId"] == "eq_sword")
ok = gs.equip(inst["instanceId"], eid_sword)
check("装备穿戴成功", ok)
gs.forge(eid_sword)                     # 祭炼一次：妖丹 -10
check("祭炼后妖丹=190", gs.yaodan == 190)
lv_after_forge = gs.find_equip(eid_sword)["level"]
check("祭炼后等级=2", lv_after_forge == 2)

raw = json.dumps(gs.to_save())
loaded = json.loads(raw)
gs2 = GS()
gs2.apply_save(loaded)
check("存档恢复: 装备数量一致(2)", len(gs2.equipment) == 2)
check("存档恢复: 妖实例存在", inst["instanceId"] in gs2.owned)
check("存档恢复: 装配映射恢复", gs2.equipped.get(inst["instanceId"], {}).get("WEAPON") == eid_sword)
check("存档恢复: 祭炼等级恢复(2)", gs2.find_equip(eid_sword)["level"] == 2)
check("存档恢复: 妖丹恢复(190)", gs2.yaodan == 190)
check("存档恢复: nextInstSeq 恢复", gs2.next_inst == gs.next_inst)
check("存档恢复: nextEquipSeq 恢复", gs2.next_eq == gs.next_eq)

# ───────────── 5. 融合消耗与存档 ─────────────
gs3 = GS()
gs3.yaodan = 100
a = gs3.catch("yao_she")
b = gs3.catch("yao_hu")
before = gs3.yaodan
r = gs3.fuse(a["instanceId"], b["instanceId"])
check("融合返回结果非 None", r is not None)
check("融合扣除妖丹 30", gs3.yaodan == before - 30)
check("融合后两原实例移除", a["instanceId"] not in gs3.owned and b["instanceId"] not in gs3.owned)
check("融合后新增1个实例", len(gs3.owned) == 1)
raw3 = json.dumps(gs3.to_save())
gs4 = GS(); gs4.apply_save(json.loads(raw3))
check("融合存档恢复: 实例数=1", len(gs4.owned) == 1)
check("融合存档恢复: 妖丹恢复", gs4.yaodan == gs3.yaodan)

# ───────────── 汇总 ─────────────
print(f"\n装备/融合验证：通过 {passed} 项，失败 {failed} 项")
if failed == 0:
    print("✓ 全部断言通过")
    raise SystemExit(0)
else:
    print("✗ 存在失败断言")
    raise SystemExit(1)
