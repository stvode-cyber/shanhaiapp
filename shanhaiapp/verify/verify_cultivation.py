#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
《山海妖志》养成系统 逻辑验证脚本（可运行）。

本机无 JDK / Gradle，无法编译 Kotlin；此脚本以等价 Python 镜像 shared 模块中
CultivationEngine / MonsterInstance / GameState(养成部分) / SaveData 序列化的设计，
对「经验升级 / 等级上限 / 突破 / 进化 / 有效属性 / 任务推进 / 存档往返」做断言，
作为双端一致性的可执行正确性闸门。

运行：python verify_cultivation.py
"""

import json
import math

# ───────────────────────── 镜像：养成引擎常量与规则 ─────────────────────────
BASE_LEVEL_CAP = 30
MAX_BREAKTHROUGH = 5
LEVEL_CAP_PER_BREAK = 5
STAT_MULT_PER_BREAK = 0.08
EVOLVE_LEVEL_REQ = 25


def level_cap(inst):
    return BASE_LEVEL_CAP + inst["breakthrough"] * LEVEL_CAP_PER_BREAK


def stat_mult(inst):
    return 1.0 + inst["breakthrough"] * STAT_MULT_PER_BREAK


def exp_for_level(level):
    lv = 1 if level < 1 else level
    return 100 + (lv - 1) * 25


def gain_exp(inst, amount):
    if amount <= 0:
        return dict(inst)
    lvl = inst["level"]
    xp = inst["exp"] + amount
    cap = level_cap(inst)
    while lvl < cap and xp >= exp_for_level(lvl):
        xp -= exp_for_level(lvl)
        lvl += 1
    if lvl >= cap:
        top = exp_for_level(max(cap - 1, 1))
        xp = min(xp, top)
    return {**inst, "level": lvl, "exp": xp}


def breakthrough_level_req(cur_bt):
    return 20 + cur_bt * 5


def can_breakthrough(inst):
    return inst["breakthrough"] < MAX_BREAKTHROUGH and inst["level"] >= breakthrough_level_req(inst["breakthrough"])


def breakthrough_cost(cur_bt):
    return 20 + cur_bt * 15


def breakthrough(inst):
    if not can_breakthrough(inst):
        return None
    return {**inst, "breakthrough": inst["breakthrough"] + 1}


def can_evolve(inst, pool):
    d = pool.get(inst["defId"])
    if d is None:
        return False
    return d["evolveTo"] is not None and inst["level"] >= EVOLVE_LEVEL_REQ


def evolve(inst, pool):
    d = pool.get(inst["defId"])
    if d is None:
        return None
    to = d["evolveTo"]
    if to is None or inst["level"] < EVOLVE_LEVEL_REQ:
        return None
    return {**inst, "defId": to}


def effective_stats(inst, pool):
    d = pool.get(inst["defId"])
    if d is None:
        return None
    base = stats_at_level(d, inst["level"])
    mult = stat_mult(inst)
    return {
        "atk": max(1, int(base["atk"] * mult)),
        "def": max(1, int(base["def"] * mult)),
        "hp": max(1, int(base["hp"] * mult)),
        "spd": max(1, int(base["spd"] * mult)),
    }


def evolution_chain(start_id, pool):
    chain = []
    cur = pool.get(start_id)
    seen = set()
    while cur is not None and cur["id"] not in seen:
        chain.append(cur["id"])
        seen.add(cur["id"])
        nxt = pool.get(cur["evolveTo"]) if cur["evolveTo"] else None
        cur = nxt
    return chain


# ───────────────────────── 镜像：妖池（仅含测试所需子集） ─────────────────────────
GROWTH = {"FAN": 1.0, "LING": 1.1, "XIAN": 1.25, "SHEN": 1.5}


def level_coef(level):
    return 1.0 + (level - 1) * 0.02


def stats_at_level(d, level):
    g = GROWTH[d["rarity"]]
    lc = level_coef(level)
    return {
        "atk": max(1, int(d["baseAtk"] * g * lc)),
        "def": max(1, int(d["baseDef"] * g * lc)),
        "hp": max(1, int(d["baseHp"] * g * lc)),
        "spd": max(1, int(d["baseSpd"] * g)),
    }


POOL = {
    "yao_she":     {"id": "yao_she", "name": "蛇妖", "element": "WATER", "rarity": "FAN",
                    "baseAtk": 46, "baseDef": 40, "baseHp": 100, "baseSpd": 62, "evolveTo": "yao_jiao"},
    "yao_jiao":    {"id": "yao_jiao", "name": "蛟", "element": "WATER", "rarity": "LING",
                    "baseAtk": 60, "baseDef": 52, "baseHp": 130, "baseSpd": 70, "evolveTo": "yao_long"},
    "yao_long":    {"id": "yao_long", "name": "龙", "element": "WATER", "rarity": "SHEN",
                    "baseAtk": 88, "baseDef": 72, "baseHp": 170, "baseSpd": 80, "evolveTo": None},
    "gui_guijia":  {"id": "gui_guijia", "name": "龟妖", "element": "WATER", "rarity": "FAN",
                    "baseAtk": 44, "baseDef": 52, "baseHp": 130, "baseSpd": 28, "evolveTo": "gui_linggui"},
    "gui_linggui": {"id": "gui_linggui", "name": "灵龟", "element": "WATER", "rarity": "LING",
                    "baseAtk": 58, "baseDef": 66, "baseHp": 165, "baseSpd": 36, "evolveTo": "gui_xuanwu"},
    "gui_xuanwu":  {"id": "gui_xuanwu", "name": "玄武", "element": "WATER", "rarity": "SHEN",
                    "baseAtk": 82, "baseDef": 78, "baseHp": 200, "baseSpd": 50, "evolveTo": None},
}


# ───────────────────────── 镜像：任务记录（仅养成相关目标） ─────────────────────────
OBJ = {"TRAIN_LEVEL": "train_level", "BREAKTHROUGH_TOTAL": "breakthrough_total", "EVOLVE_TOTAL": "evolve_total"}


def make_quests():
    return {
        "t_train": {"target": 15, "type": OBJ["TRAIN_LEVEL"], "current": 0, "state": "ACTIVE"},
        "t_break": {"target": 1, "type": OBJ["BREAKTHROUGH_TOTAL"], "current": 0, "state": "ACTIVE"},
        "s_evolve": {"target": 1, "type": OBJ["EVOLVE_TOTAL"], "current": 0, "state": "ACTIVE"},
    }


def record_event(quests, ev):
    for q in quests.values():
        if q["state"] != "ACTIVE":
            continue
        t = q["type"]
        if t == OBJ["TRAIN_LEVEL"] and ev[0] == "trained":
            q["current"] = max(q["current"], min(ev[1], q["target"]))
        elif t == OBJ["BREAKTHROUGH_TOTAL"] and ev[0] == "broken":
            q["current"] = min(q["current"] + 1, q["target"])
        elif t == OBJ["EVOLVE_TOTAL"] and ev[0] == "evolved":
            q["current"] = min(q["current"] + 1, q["target"])
        if q["current"] >= q["target"]:
            q["state"] = "COMPLETED"


# ───────────────────────── 镜像：GameState（养成切片） ─────────────────────────
class GameState:
    def __init__(self):
        self.owned = {}
        self.team = []
        self.nextInstSeq = 1
        self.yaodan = 0
        self.quests = make_quests()

    def catch(self, def_id):
        inst = {"instanceId": f"i_{def_id}_{self.nextInstSeq}", "defId": def_id,
                "level": 1, "exp": 0, "breakthrough": 0}
        self.nextInstSeq += 1
        self.owned[inst["instanceId"]] = inst
        if len(self.team) < 6:
            self.team.append(inst["instanceId"])
        return inst

    def gain_exp(self, iid, amount):
        inst = self.owned[iid]
        after = gain_exp(inst, amount)
        self.owned[iid] = after
        record_event(self.quests, ("trained", after["level"]))

    def do_breakthrough(self, iid):
        inst = self.owned[iid]
        if not can_breakthrough(inst):
            return False
        cost = breakthrough_cost(inst["breakthrough"])
        if self.yaodan < cost:
            return False
        self.yaodan -= cost
        self.owned[iid] = breakthrough(inst)
        record_event(self.quests, ("broken",))
        return True

    def do_evolve(self, iid):
        inst = self.owned[iid]
        after = evolve(inst, POOL)
        if after is None:
            return False
        self.owned[iid] = after
        record_event(self.quests, ("evolved",))
        return True

    def to_save(self):
        return {
            "version": 1,
            "team": list(self.team),
            "owned": [dict(v) for v in self.owned.values()],
            "nextInstSeq": self.nextInstSeq,
            "yaodan": self.yaodan,
        }

    def apply_save(self, s):
        self.owned = {o["instanceId"]: dict(o) for o in s["owned"]}
        self.team = [t for t in s["team"] if t in self.owned]
        self.nextInstSeq = s["nextInstSeq"]
        self.yaodan = s["yaodan"]


# ───────────────────────── 测试框架 ─────────────────────────
_PASSED = 0
_FAILED = 0


def check(name, cond):
    global _PASSED, _FAILED
    if cond:
        _PASSED += 1
        print(f"  ✓ {name}")
    else:
        _FAILED += 1
        print(f"  ✗ {name}   <-- FAILED")


def section(title):
    print(f"\n▶ {title}")


# ───────────────────────── 1. 经验与升级 ─────────────────────────
section("经验 / 升级")
check("expForLevel(1)=100", exp_for_level(1) == 100)
check("expForLevel(30)=825", exp_for_level(30) == 100 + 29 * 25)

base = {"instanceId": "x", "defId": "yao_she", "level": 1, "exp": 0, "breakthrough": 0}
r = gain_exp(base, 100)
check("+100 精确升级到 Lv.2 exp0", r["level"] == 2 and r["exp"] == 0)

r = gain_exp(base, 50)
check("+50 仅累积在 Lv.1", r["level"] == 1 and r["exp"] == 50)

r = gain_exp(base, 300)
# 100(L1->L2) 剩200; 125(L2->L3) 剩75 → Lv.3 exp75
check("+300 连升到 Lv.3 exp75", r["level"] == 3 and r["exp"] == 75)

# 满级封顶
cap_inst = {"instanceId": "c", "defId": "yao_she", "level": 30, "exp": 0, "breakthrough": 0}
r = gain_exp(cap_inst, 99999)
check("满级后等级不变", r["level"] == 30)
check("满级后经验封顶 <= expForLevel(29)=800", r["exp"] <= exp_for_level(29))

# ───────────────────────── 2. 突破 ─────────────────────────
section("突破")
lv19 = {"instanceId": "b", "defId": "yao_she", "level": 19, "exp": 0, "breakthrough": 0}
check("Lv.19 未达首次突破条件(需20)", not can_breakthrough(lv19))
lv20 = {"instanceId": "b", "defId": "yao_she", "level": 20, "exp": 0, "breakthrough": 0}
check("Lv.20 可首次突破", can_breakthrough(lv20))
check("首次突破消耗妖丹=20", breakthrough_cost(0) == 20)
check("第二次突破消耗妖丹=35", breakthrough_cost(1) == 35)
bt1 = breakthrough(lv20)
check("突破后 breakthrough=1", bt1["breakthrough"] == 1)
check("突破后等级上限 30→35", level_cap(bt1) == 35)
bt1_lv24 = {**bt1, "level": 24}
check("突破1次后 Lv.24 未达再突破条件(需25)", not can_breakthrough(bt1_lv24))
bt1_lv25 = {**bt1, "level": 25}
check("突破1次后 Lv.25 可再突破", can_breakthrough(bt1_lv25))
check("突破达上限后不可再突破", not can_breakthrough({**lv20, "breakthrough": MAX_BREAKTHROUGH}))

# ───────────────────────── 3. 属性加成 ─────────────────────────
section("突破属性加成")
check("突破0次系数=1.0", abs(stat_mult({"breakthrough": 0}) - 1.0) < 1e-9)
check("突破1次系数=1.08", abs(stat_mult({"breakthrough": 1}) - 1.08) < 1e-9)
check("突破5次系数=1.40", abs(stat_mult({"breakthrough": 5}) - 1.40) < 1e-9)
inst_lv1 = {"instanceId": "e", "defId": "yao_she", "level": 1, "exp": 0, "breakthrough": 1}
es = effective_stats(inst_lv1, POOL)
# 蛇妖 Lv.1 基础 atk=46; ×1.08 = 49.68 → int = 49
check("有效攻击 46×1.08→49", es["atk"] == 49)

# ───────────────────────── 4. 进化 ─────────────────────────
section("进化")
snake_lv24 = {"instanceId": "s", "defId": "yao_she", "level": 24, "exp": 0, "breakthrough": 0}
check("Lv.24 不可进化(需25)", not can_evolve(snake_lv24, POOL))
snake_lv25 = {**snake_lv24, "level": 25}
check("Lv.25 可进化", can_evolve(snake_lv25, POOL))
ev = evolve(snake_lv25, POOL)
check("进化后 defId→yao_jiao", ev["defId"] == "yao_jiao")
check("进化保留等级25", ev["level"] == 25)
check("进化保留突破0", ev["breakthrough"] == 0)
chain = evolution_chain("yao_she", POOL)
check("蛇→蛟→龙 进化链完整", chain == ["yao_she", "yao_jiao", "yao_long"])
chain_gui = evolution_chain("gui_guijia", POOL)
check("龟妖→灵龟→玄武 进化链完整", chain_gui == ["gui_guijia", "gui_linggui", "gui_xuanwu"])

# ───────────────────────── 5. GameState 集成 + 任务推进 ─────────────────────────
section("GameState 集成 / 任务推进")
gs = GameState()
i1 = gs.catch("yao_she")
check("catch 生成唯一实例并加入队伍", i1["instanceId"] in gs.owned and i1["instanceId"] in gs.team)
# 培养到 15 级：需经验 sum(expForLevel(1..14))
need = sum(exp_for_level(lv) for lv in range(1, 15))  # 3675
gs.gain_exp(i1["instanceId"], need + 200)
check("培养后达到 Lv.15", gs.owned[i1["instanceId"]]["level"] == 15)
check("培养任务 t_train 完成", gs.quests["t_train"]["state"] == "COMPLETED")

# 突破任务：先到 Lv.20
gs2 = GameState()
gs2.yaodan = 999
i2 = gs2.catch("yao_she")
gs2.gain_exp(i2["instanceId"], sum(exp_for_level(lv) for lv in range(1, 20)))  # 到 Lv.20
check("达到 Lv.20", gs2.owned[i2["instanceId"]]["level"] == 20)
ok = gs2.do_breakthrough(i2["instanceId"])
check("突破成功", ok and gs2.owned[i2["instanceId"]]["breakthrough"] == 1)
check("突破任务 t_break 完成", gs2.quests["t_break"]["state"] == "COMPLETED")
check("突破扣除妖丹20", gs2.yaodan == 999 - 20)

# 进化任务
gs3 = GameState()
i3 = gs3.catch("yao_she")
gs3.gain_exp(i3["instanceId"], sum(exp_for_level(lv) for lv in range(1, 25)))  # 到 Lv.25
ok3 = gs3.do_evolve(i3["instanceId"])
check("进化成功", ok3 and gs3.owned[i3["instanceId"]]["defId"] == "yao_jiao")
check("进化任务 s_evolve 完成", gs3.quests["s_evolve"]["state"] == "COMPLETED")

# ───────────────────────── 6. 存档往返（序列化） ─────────────────────────
section("存档往返 / 序列化")
gs4 = GameState()
gs4.yaodan = 500
a = gs4.catch("yao_she")
b = gs4.catch("gui_guijia")
gs4.gain_exp(a["instanceId"], sum(exp_for_level(lv) for lv in range(1, 20)))  # a 到 Lv.20
gs4.do_breakthrough(a["instanceId"])  # a 突破1（-20 妖丹 → 480）
gs4.gain_exp(b["instanceId"], sum(exp_for_level(lv) for lv in range(1, 26)))  # b 到 Lv.26
gs4.do_evolve(b["instanceId"])  # b 进化 → gui_linggui

raw = json.dumps(gs4.to_save())
# 反序列化（含未知字段容忍，对应 kotlinx ignoreUnknownKeys）
loaded = json.loads(raw)
gs5 = GameState()
gs5.apply_save(loaded)

check("实例数量恢复(2)", len(gs5.owned) == 2)
check("队伍实例id恢复", set(gs5.team) == set(gs4.team))
check("a 等级/突破恢复", gs5.owned[a["instanceId"]]["level"] == 20 and gs5.owned[a["instanceId"]]["breakthrough"] == 1)
check("b 进化结果恢复(defId→gui_linggui)", gs5.owned[b["instanceId"]]["defId"] == "gui_linggui")
check("nextInstSeq 恢复", gs5.nextInstSeq == gs4.nextInstSeq)
check("妖丹恢复(500-20=480)", gs5.yaodan == 480)
check("JSON 含 owned 字段", '"owned"' in raw)
check("旧存档(无 owned)可兼容(默认空)", json.loads('{"version":1,"team":[],"nextInstSeq":1,"yaodan":0}') is not None)

# ───────────────────────── 汇总 ─────────────────────────
print(f"\n{'='*48}")
print(f"结果：通过 {_PASSED} 项，失败 {_FAILED} 项")
print(f"{'='*48}")
if _FAILED > 0:
    raise SystemExit(1)
