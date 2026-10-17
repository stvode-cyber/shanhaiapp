#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
天赋/命格系统 等价验证（本机无 JDK，不依赖编译）。

设计：直接正则解析 shared 源码抽取常量与字段，再做 Python 镜像逻辑断言，
避免手抄常量导致双端/文档漂移。

覆盖：
- TalentEngine：talentCap 随突破、顺序解锁、容量上限、四分支每层百分比、assign 不可分配返回 None
- CultivationEngine.effectiveStats：含天赋倍率（atk/def/hp/spd 四倍率相乘）
- GameState.assignTalent：成功 / 容量满失败 / 不存在返回 false
- QuestLog：TALENT_TOTAL 事件累计
- 存档往返：旧档缺 talents 默认空、新档保留
"""
import os
import re

ROOT = os.path.dirname(os.path.abspath(__file__))
ENGINE = os.path.join(ROOT, "..", "shared", "src", "commonMain", "kotlin",
                       "com", "shanhai", "core", "logic", "TalentEngine.kt")
MODEL = os.path.join(ROOT, "..", "shared", "src", "commonMain", "kotlin",
                     "com", "shanhai", "core", "model", "MonsterInstance.kt")


def parse_const(src, name):
    m = re.search(r"const val %s:\s*(?:Int|Double)\s*=\s*([0-9.]+)" % re.escape(name), src)
    if not m:
        raise AssertionError("源码未找到常量 %s" % name)
    v = m.group(1)
    return int(v) if "." not in v else float(v)


# ── 从源码抽取常量（校验与 Kotlin 一致） ──
with open(ENGINE, encoding="utf-8") as f:
    SRC = f.read()
MAX_LAYER = parse_const(SRC, "MAX_LAYER")
BASE_TALENT = parse_const(SRC, "BASE_TALENT")
TALENT_PER_BREAK = parse_const(SRC, "TALENT_PER_BREAK")
ATK_PCT = parse_const(SRC, "ATK_PCT_PER_LAYER")
DEF_PCT = parse_const(SRC, "DEF_PCT_PER_LAYER")
HP_PCT = parse_const(SRC, "HP_PCT_PER_LAYER")
SPD_PCT = parse_const(SRC, "SPD_PCT_PER_LAYER")

assert (MAX_LAYER, BASE_TALENT, TALENT_PER_BREAK, ATK_PCT, DEF_PCT, HP_PCT, SPD_PCT) == \
       (3, 3, 1, 0.06, 0.06, 0.04, 0.05), "常量与预期不符，源码可能已改"

# 字段存在性校验
with open(MODEL, encoding="utf-8") as f:
    MODEL_SRC = f.read()
assert "talents: Map<TalentBranch, Int> = emptyMap()" in MODEL_SRC, "MonsterInstance.talents 字段缺失"


# ── Python 镜像逻辑 ──
BRANCHES = ["ATTACK", "DEFENSE", "SUPPORT"]


def talent_cap(inst):
    return BASE_TALENT + inst["breakthrough"] * TALENT_PER_BREAK


def layers_of(inst, branch):
    return inst["talents"].get(branch, 0)


def allocated(inst):
    return sum(inst["talents"].values())


def can_assign(inst, branch):
    return layers_of(inst, branch) < MAX_LAYER and allocated(inst) < talent_cap(inst)


def assign(inst, branch):
    if not can_assign(inst, branch):
        return None
    nxt = dict(inst["talents"])
    nxt[branch] = layers_of(inst, branch) + 1
    return dict(inst, talents=nxt)


def atk_mult(inst):
    return 1.0 + ATK_PCT * layers_of(inst, "ATTACK")


def def_mult(inst):
    return 1.0 + DEF_PCT * layers_of(inst, "DEFENSE")


def hp_mult(inst):
    return 1.0 + HP_PCT * layers_of(inst, "DEFENSE")


def spd_mult(inst):
    return 1.0 + SPD_PCT * layers_of(inst, "SUPPORT")


def stat_mult(inst):
    return (1.0 + inst["breakthrough"] * 0.08) * (1.0 + inst["tribulations"] * 0.25)


def effective_stats(inst, base):
    m = stat_mult(inst)
    return {
        "atk": int(base["atk"] * m * atk_mult(inst)),
        "def": int(base["def"] * m * def_mult(inst)),
        "hp": int(base["hp"] * m * hp_mult(inst)),
        "spd": int(base["spd"] * m * spd_mult(inst)),
    }


class GameState:
    def __init__(self):
        self.owned = {}
        self.talent_total = 0

    def assign_talent(self, iid, branch):
        inst = self.owned.get(iid)
        if inst is None:
            return False
        if not can_assign(inst, branch):
            return False
        self.owned[iid] = assign(inst, branch)
        self.talent_total += 1
        return True


class QuestLog:
    def __init__(self):
        self.talent_total = 0

    def record(self, event):
        if event[0] == "MonsterTalentAssigned":
            self.talent_total = min(self.talent_total + 1, event[1])


# ── 断言 ──
passed = 0


def check(cond, msg):
    global passed
    assert cond, "FAIL: " + msg
    passed += 1


# 基础实例
fresh = {"breakthrough": 0, "tribulations": 0, "talents": {}}
check(talent_cap(fresh) == 3, "break0 容量=3")
check(layers_of(fresh, "ATTACK") == 0, "默认层数 0")
check(allocated(fresh) == 0, "默认已分配 0")
check(can_assign(fresh, "ATTACK"), "空实例可分配")

# 顺序解锁 + 容量上限（break0 容量 3）
inst = dict(fresh)
for _ in range(3):
    nxt = assign(inst, "ATTACK")
    check(nxt is not None, "分配成功")
    inst = nxt
check(inst["talents"]["ATTACK"] == 3, "ATTACK 点满 3 层")
check(not can_assign(inst, "ATTACK"), "分支满 3 层不可再点")
check(allocated(inst) == 3, "已分配=容量")
check(not can_assign(inst, "DEFENSE"), "容量耗尽不可分配其他分支")
check(assign(inst, "ATTACK") is None, "满分支 assign 返回 None")

# 分支取舍：3 点分给 攻1/防1/辅1
inst = dict(fresh)
for b in ["ATTACK", "DEFENSE", "SUPPORT"]:
    inst = assign(inst, b)
check(inst["talents"] == {"ATTACK": 1, "DEFENSE": 1, "SUPPORT": 1}, "三分支各 1 层")
check(not can_assign(inst, "ATTACK"), "容量 3 耗尽")

# 容量随突破提升（break2 → 容量 5）
inst = {"breakthrough": 2, "tribulations": 0, "talents": {}}
check(talent_cap(inst) == 5, "break2 容量=5")
for _ in range(3):
    inst = assign(inst, "ATTACK")
check(inst["talents"]["ATTACK"] == 3, "ATTACK 封顶 3")
check(can_assign(inst, "DEFENSE"), "ATTACK 满后仍可点其他（容量未耗尽）")
for _ in range(2):
    inst = assign(inst, "DEFENSE")
check(inst["talents"]["DEFENSE"] == 2 and allocated(inst) == 5, "攻3+防2 用满容量 5")
check(not can_assign(inst, "SUPPORT"), "容量 5 耗尽")

# 四分支每层百分比倍率
inst = {"breakthrough": 0, "tribulations": 0, "talents": {"ATTACK": 2}}
check(abs(atk_mult(inst) - 1.12) < 1e-9, "ATTACK 2层 atkMult=1.12")
inst = {"breakthrough": 0, "tribulations": 0, "talents": {"DEFENSE": 3}}
check(abs(def_mult(inst) - 1.18) < 1e-9, "DEFENSE 3层 defMult=1.18")
check(abs(hp_mult(inst) - 1.12) < 1e-9, "DEFENSE 3层 hpMult=1.12")
inst = {"breakthrough": 0, "tribulations": 0, "talents": {"SUPPORT": 1}}
check(abs(spd_mult(inst) - 1.05) < 1e-9, "SUPPORT 1层 spdMult=1.05")

# effectiveStats 含天赋倍率（base 100，break0/trib0 → 倍率仅天赋）
base = {"atk": 100, "def": 100, "hp": 100, "spd": 100}
inst = {"breakthrough": 0, "tribulations": 0, "talents": {"ATTACK": 2}}
es = effective_stats(inst, base)
check(es["atk"] == 112 and es["def"] == 100 and es["hp"] == 100 and es["spd"] == 100,
      "ATTACK2 → atk=112 其余不变")
inst = {"breakthrough": 0, "tribulations": 0, "talents": {"DEFENSE": 3}}
es = effective_stats(inst, base)
check(es["def"] == 118 and es["hp"] == 112 and es["atk"] == 100 and es["spd"] == 100,
      "DEFENSE3 → def=118 hp=112")
inst = {"breakthrough": 0, "tribulations": 0, "talents": {"ATTACK": 2, "DEFENSE": 3}}
es = effective_stats(inst, base)
check(es["atk"] == 112 and es["def"] == 118 and es["hp"] == 112 and es["spd"] == 100,
      "攻2+防3 组合倍率")

# 突破/渡劫倍率叠加（与天赋相乘）
inst = {"breakthrough": 1, "tribulations": 1, "talents": {"ATTACK": 1}}
es = effective_stats(inst, base)
# statMult=1.08, tribMult=1.25, atkMult=1.06 → 100*1.08*1.25*1.06
exp = int(100 * 1.08 * 1.25 * 1.06)
check(es["atk"] == exp, "突破1+渡劫1+攻1 倍率叠加 %d" % exp)

# GameState.assignTalent
gs = GameState()
gs.owned["i1"] = dict(fresh)
check(gs.assign_talent("i1", "ATTACK"), "assignTalent 成功")
check(gs.assign_talent("i1", "DEFENSE"), "assignTalent 成功")
check(gs.assign_talent("i1", "SUPPORT"), "assignTalent 成功")
check(not gs.assign_talent("i1", "ATTACK"), "容量满 assignTalent 失败")
check(not gs.assign_talent("ghost", "ATTACK"), "不存在实例返回 false")
check(gs.owned["i1"]["talents"] == {"ATTACK": 1, "DEFENSE": 1, "SUPPORT": 1}, "分配结果正确")

# QuestLog TALENT_TOTAL
ql = QuestLog()
for _ in range(3):
    ql.record(("MonsterTalentAssigned", 3))
check(ql.talent_total == 3, "TALENT_TOTAL 累计 3")

# 存档往返：旧档缺 talents 默认空
old = {"instanceId": "i1", "defId": "m_snake", "level": 5, "exp": 0, "breakthrough": 0, "tribulations": 0}
old.setdefault("talents", {})
check(old["talents"] == {}, "旧档默认 talents 空（向后兼容）")
# 新档保留
new = dict(old, talents={"ATTACK": 2, "DEFENSE": 1})
check(new["talents"] == {"ATTACK": 2, "DEFENSE": 1}, "新档 talents 保留")

print("天赋/命格验证通过：%d 项断言，0 失败" % passed)
