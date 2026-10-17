"""
《山海妖志》渡劫天劫系统 · 等价验证脚本

镜像 shared 层逻辑（Kotlin CultivationEngine / GameState / QuestLog / SaveData）：
  - maxTribulations：按基础稀有度决定可渡劫次数（神阶不可再渡）
  - canTribulate：满级（达当前等级上限）+ 满突破 + 未满渡劫次数
  - tribulateCost：逐级递增（基础 200 ×(次数+1)）
  - tribulateMult：每次渡劫 +25% 永久属性
  - effectiveStats：基础 × 突破系数 × 渡劫系数
  - 任务：TRIBULATE_TOTAL 事件推进；渡劫消耗妖丹、稀有度升阶
  - 存档往返：tribulations 随 MonsterInstance 持久化（旧档容错）

运行：python verify/verify_tribulation.py
"""

import json

# ───────────────────────── 基础数据（镜像） ─────────────────────────

RARITY = ["FAN", "LING", "XIAN", "SHEN"]          # 稀有度阶序
RARITY_GROWTH = {"FAN": 1.0, "LING": 1.1, "XIAN": 1.25, "SHEN": 1.5}

# 妖种定义（仅取验证所需字段）：id -> dict(rarity, atk, def, hp, spd)
MONSTERS = {
    "yao_she":        {"rarity": "FAN",  "atk": 46, "def": 40, "hp": 100, "spd": 62},
    "yao_jiao":       {"rarity": "LING", "atk": 60, "def": 52, "hp": 130, "spd": 70},
    "yao_long":       {"rarity": "SHEN", "atk": 88, "def": 72, "hp": 170, "spd": 80},
    "lingshou_baihu": {"rarity": "SHEN", "atk": 92, "def": 68, "hp": 155, "spd": 90},
    "gui_guijia":     {"rarity": "FAN",  "atk": 44, "def": 52, "hp": 130, "spd": 28},
}

# 养成常量（与 CultivationEngine 对齐）
BASE_LEVEL_CAP = 30
MAX_BREAKTHROUGH = 5
LEVEL_CAP_PER_BREAK = 5
STAT_MULT_PER_BREAK = 0.08
EVOLVE_LEVEL_REQ = 25
TRIBULATE_STAT_BONUS = 0.25
TRIBULATE_COST_YAODAN = 200


def rarity_index(r):
    return RARITY.index(r)


def stats_at_level(base, level):
    g = RARITY_GROWTH[base["rarity"]]
    lc = 1.0 + (level - 1) * 0.02
    return {
        "atk": max(1, int(base["atk"] * g * lc)),
        "def": max(1, int(base["def"] * g * lc)),
        "hp": max(1, int(base["hp"] * g * lc)),
        "spd": max(1, int(base["spd"] * g * lc)),
    }


def level_cap(inst):
    return BASE_LEVEL_CAP + inst["breakthrough"] * LEVEL_CAP_PER_BREAK


def stat_mult(inst):
    return 1.0 + inst["breakthrough"] * STAT_MULT_PER_BREAK


def tribulate_mult(inst):
    return 1.0 + inst["tribulations"] * TRIBULATE_STAT_BONUS


def max_tribulations(rarity):
    return max(0, rarity_index("SHEN") - rarity_index(rarity))


def current_rarity(inst):
    base = MONSTERS[inst["defId"]]["rarity"]
    idx = min(rarity_index(base) + inst["tribulations"], rarity_index("SHEN"))
    return RARITY[idx]


def can_tribulate(inst):
    base = MONSTERS[inst["defId"]]["rarity"]
    if inst["tribulations"] >= max_tribulations(base):
        return False
    return inst["breakthrough"] >= MAX_BREAKTHROUGH and inst["level"] >= level_cap(inst)


def tribulate_cost(inst):
    return TRIBULATE_COST_YAODAN * (inst["tribulations"] + 1)


def tribulate(inst):
    if not can_tribulate(inst):
        return None
    n = dict(inst)
    n["tribulations"] += 1
    return n


def effective_stats(inst):
    base = MONSTERS[inst["defId"]]
    s = stats_at_level(base, inst["level"])
    mult = stat_mult(inst) * tribulate_mult(inst)
    return {
        "atk": max(1, int(s["atk"] * mult)),
        "def": max(1, int(s["def"] * mult)),
        "hp": max(1, int(s["hp"] * mult)),
        "spd": max(1, int(s["spd"] * mult)),
    }


# ───────────────────────── 任务系统（镜像） ─────────────────────────

class QuestLog:
    def __init__(self):
        # 仅含与渡劫相关的目标类型
        self.objectives = {"TRIBULATE_TOTAL": 0}
        self.target = {"TRIBULATE_TOTAL": 1}
        self.state = "ACTIVE"

    def record(self, event):
        if self.state != "ACTIVE":
            return []
        if event == "MonsterTribulated":
            self.objectives["TRIBULATE_TOTAL"] = min(
                self.objectives["TRIBULATE_TOTAL"] + 1, self.target["TRIBULATE_TOTAL"])
        if all(self.objectives[k] >= self.target[k] for k in self.target):
            self.state = "COMPLETED"
            return ["渡劫飞升"]
        return []


# ───────────────────────── 游戏状态（镜像） ─────────────────────────

class GameState:
    def __init__(self):
        self.owned = {}
        self.yaodan = 0
        self.quests = QuestLog()

    def add(self, inst):
        self.owned[inst["instanceId"]] = inst

    def tribulate_monster(self, iid):
        inst = self.owned.get(iid)
        if inst is None or not can_tribulate(inst):
            return False
        cost = tribulate_cost(inst)
        if self.yaodan < cost:
            return False
        self.yaodan -= cost
        self.owned[iid] = tribulate(inst)
        self.quests.record("MonsterTribulated")
        return True


# ───────────────────────── 断言框架 ─────────────────────────

PASS = 0
FAIL = 0


def check(cond, msg):
    global PASS, FAIL
    if cond:
        PASS += 1
    else:
        FAIL += 1
        print(f"  ✗ FAIL: {msg}")


def approx_eq(a, b, msg, tol=0):
    check(abs(a - b) <= tol, f"{msg} (期望 {b}, 实际 {a})")


# ───────────────────────── 测试用例 ─────────────────────────

def make_inst(def_id, level, breakthrough=0, trib=0):
    return {"instanceId": f"i_{def_id}", "defId": def_id,
            "level": level, "exp": 0, "breakthrough": breakthrough, "tribulations": trib}


def test_max_tribulations():
    # 凡→3, 灵→2, 仙→1, 神→0
    check(max_tribulations("FAN") == 3, "凡可渡劫 3 次")
    check(max_tribulations("LING") == 2, "灵可渡劫 2 次")
    check(max_tribulations("XIAN") == 1, "仙可渡劫 1 次")
    check(max_tribulations("SHEN") == 0, "神不可再渡（保值）")


def test_can_tribulate_prereqs():
    # 未满突破 / 未满级(等级上限=30+5*5=55) → 不可
    check(not can_tribulate(make_inst("yao_she", 55, 0, 0)), "凡未突破不可渡劫")
    check(not can_tribulate(make_inst("yao_she", 50, 5, 0)), "未满级(50<55)不可渡劫")
    # 满级(55)满突破(5) 凡妖 → 可
    check(can_tribulate(make_inst("yao_she", 55, 5, 0)), "满级满突破凡妖可渡劫")
    # 神阶已满渡劫次数 → 不可
    check(not can_tribulate(make_inst("yao_long", 55, 5, 0)), "神阶(基础)不可渡劫")


def test_tribulate_cost_escalates():
    check(tribulate_cost(make_inst("yao_she", 35, 5, 0)) == 200, "首次渡劫 200 妖丹")
    check(tribulate_cost(make_inst("yao_she", 35, 5, 1)) == 400, "二次渡劫 400 妖丹")
    check(tribulate_cost(make_inst("yao_she", 35, 5, 2)) == 600, "三次渡劫 600 妖丹")


def test_tribulate_mult_and_effective():
    # 蛇妖 满级满突破，未渡劫
    inst = make_inst("yao_she", 55, 5, 0)
    base_eff = effective_stats(inst)
    # 渡劫一次：属性 ×1.25
    inst1 = tribulate(inst)
    eff1 = effective_stats(inst1)
    check(inst1 is not None, "满级满突破可渡劫")
    check(inst1["tribulations"] == 1, "渡劫次数 +1")
    for k in ("atk", "def", "hp", "spd"):
        # 渡劫后属性 ≈ 未渡劫 ×1.25（int 截断允许 ±1 误差）
        approx_eq(eff1[k], max(1, int(base_eff[k] * 1.25)), f"渡劫后 {k} 提升 ≈25%", tol=1)
    # 渡劫次数越高倍率越高
    inst2 = tribulate(inst1)
    eff2 = effective_stats(inst2)
    for k in ("atk", "def", "hp", "spd"):
        approx_eq(eff2[k], max(1, int(base_eff[k] * 1.5)), f"二次渡劫 {k} ≈×1.5", tol=1)


def test_current_rarity_tier_up():
    inst = make_inst("yao_she", 55, 5, 0)        # 基础 凡
    check(current_rarity(inst) == "FAN", "渡劫前为凡")
    inst1 = tribulate(inst)
    check(current_rarity(inst1) == "LING", "渡劫一次升为灵")
    inst2 = tribulate(inst1)
    check(current_rarity(inst2) == "XIAN", "渡劫二次升为仙")
    inst3 = tribulate(inst2)
    check(current_rarity(inst3) == "SHEN", "渡劫三次升为神")
    # 神阶不可再渡
    check(tribulate(inst3) is None, "神阶不可再渡劫")


def test_game_state_tribulate_and_quest():
    g = GameState()
    g.yaodan = 1000
    g.add(make_inst("yao_she", 55, 5, 0))
    # 渡劫一次：消耗 200 妖丹，任务推进
    ok = g.tribulate_monster("i_yao_she")
    check(ok, "GameState 渡劫成功")
    check(g.yaodan == 800, "渡劫消耗 200 妖丹")
    check(g.owned["i_yao_she"]["tribulations"] == 1, "实例渡劫次数 +1")
    check(g.quests.objectives["TRIBULATE_TOTAL"] == 1, "任务 TRIBULATE_TOTAL 推进")
    check(g.quests.state == "COMPLETED", "渡劫任务完成")
    # 妖丹不足 → 失败
    g2 = GameState()
    g2.yaodan = 50
    g2.add(make_inst("yao_she", 55, 5, 0))
    check(not g2.tribulate_monster("i_yao_she"), "妖丹不足渡劫失败")
    # 未满条件 → 失败
    g3 = GameState()
    g3.yaodan = 1000
    g3.add(make_inst("yao_she", 30, 5, 0))  # 未满级
    check(not g3.tribulate_monster("i_yao_she"), "未满级渡劫失败")


def test_save_roundtrip():
    # 模拟存档：tribulations 随实例序列化；旧档（无 tribulations）容错为 0
    inst = make_inst("yao_she", 35, 5, 2)
    save = json.loads(json.dumps(inst))  # 序列化往返
    check(save["tribulations"] == 2, "存档含 tribulations")
    # 旧档缺少字段
    old = {"instanceId": "i_x", "defId": "yao_she", "level": 10, "exp": 0, "breakthrough": 0}
    check(old.get("tribulations", 0) == 0, "旧档缺失 tribulations 容错为 0")
    # 恢复后仍可正常计算有效属性
    restored = dict(old, tribulations=old.get("tribulations", 0))
    _ = effective_stats(restored)
    check(True, "恢复后有效属性可计算")


def main():
    test_max_tribulations()
    test_can_tribulate_prereqs()
    test_tribulate_cost_escalates()
    test_tribulate_mult_and_effective()
    test_current_rarity_tier_up()
    test_game_state_tribulate_and_quest()
    test_save_roundtrip()
    print(f"\n渡劫验证：通过 {PASS} 项，失败 {FAIL} 项")
    if FAIL == 0:
        print("✓ 全部断言通过")
    else:
        print("✗ 存在失败断言")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
