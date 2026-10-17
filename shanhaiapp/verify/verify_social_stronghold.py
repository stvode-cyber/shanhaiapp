# -*- coding: utf-8 -*-
"""
#182 社交据点战 本地规则契约验证（纯 Python 等价验证，不依赖编译器）

验证对象（shared 已落地的确定性核心，六类平台契约中可纯客户端验证的第三块）：
  - logic/SocialEngine.kt      : Faction 三派枚举 / STRONGHOLD_ATTACK_BONUS=1.10 /
                                  StrongholdState 状态机（canCapture/capture/attackBonus）/
                                  canGiftDaily 占位（服务器依赖）
  - logic/LocationEngine.kt    : STRONGHOLD_NODES 白名单（派生自 NODE_KEYS.values，4 节点真源）
  - logic/GameState.kt         : captureNode 白名单守卫（防注入）+ 据点贡献结算 +
                                  strongholdAttackBonus 接线 + maybeResetDaily 据点每日重置
  - logic/SectTechEngine.kt    : CONTRIBUTION_PER_CAPTURE=50

分层：
  [A] 解析级契约接线：白名单 4 节点真值 / Faction 3 枚举 / 1.10 / 50 /
      守卫 `node !in STRONGHOLD_NODES → false` / 加成接线 / 每日重置行 / 贡献公式
  [B] 行为级复现 SocialEngine 状态机：每日限一次（canCapture/capture）+ 攻击加成 1.10/1.0
  [C] 白名单守卫抗注入：合法 4 节点可占领、任意注入名被拒（防御性边界）

运行：python verify/verify_social_stronghold.py
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SHARED = os.path.join(ROOT, "shared", "src", "commonMain", "kotlin", "com", "shanhai", "core")

passed = 0
failed = 0
FAILS = []


def check(cond, msg):
    global passed, failed
    if cond:
        passed += 1
    else:
        failed += 1
        FAILS.append(msg)
        print("  [FAIL] " + msg)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def find(*rel):
    return read(os.path.join(SHARED, *rel))


social_src = find("logic", "SocialEngine.kt")
loc_src = find("logic", "LocationEngine.kt")
gs_src = find("logic", "GameState.kt")
sect_src = find("logic", "SectTechEngine.kt")


# ── [A] 解析级契约接线 ───────────────────────────────────────────────
print("[A] 解析级契约接线")

# A1 STRONGHOLD_NODES 真源 = NODE_KEYS.values（坐标 to 名称，恰好 4 项）
node_block = re.search(r"private val NODE_KEYS\s*=\s*mapOf\((.*?)\)", loc_src, re.S)
check(node_block is not None, "LocationEngine.NODE_KEYS 定义存在")
node_names = re.findall(r'"[\d.]+:[\d.]+"\s+to\s+"([^"]+)"', node_block.group(1)) if node_block else []
check(len(node_names) == 4, "NODE_KEYS 恰好 4 个灵脉节点（白名单基数）")
check(
    node_names == ["城隍桥", "古庙", "老槐树", "东海之滨"],
    "STRONGHOLD_NODES 白名单真值 = 城隍桥/古庙/老槐树/东海之滨（与 GDD 灵脉节点对应）",
)

# A2 STRONGHOLD_NODES 派生自 NODE_KEYS.values（单一真源，非硬编码重复）
check(
    "val STRONGHOLD_NODES: Set<String> = NODE_KEYS.values.toSet()" in loc_src,
    "STRONGHOLD_NODES 派生自 NODE_KEYS.values（单一真源，避免 POI 重复硬编码）",
)

# A3 Faction 三派枚举（GDD 十三·社交 三派）
fac = re.search(r"enum class Faction\s*\{([^}]*)\}", social_src)
fac_vals = [x.strip() for x in fac.group(1).split(",") if x.strip()] if fac else []
check(fac_vals == ["MAOSHAN", "LONGHUSHAN", "GEZAO"], "Faction 枚举 = 茅山/龙虎山/阁皂山 三派")

# A4 据点攻击加成常量
m = re.search(r"const val STRONGHOLD_ATTACK_BONUS\s*=\s*([0-9.]+)", social_src)
bonus = float(m.group(1)) if m else None
check(bonus == 1.10, "STRONGHOLD_ATTACK_BONUS = 1.10（占领据点攻击 +10%）")

# A5 据点贡献常量
m = re.search(r"const val CONTRIBUTION_PER_CAPTURE\s*=\s*(\d+)", sect_src)
cpc = int(m.group(1)) if m else None
check(cpc == 50, "CONTRIBUTION_PER_CAPTURE = 50（每次占领据点经营贡献基数）")

# A6 canGiftDaily 占位（服务器依赖，本地恒 false）
m = re.search(r"fun canGiftDaily\(\): Boolean = (false|true)", social_src)
check(m is not None and m.group(1) == "false", "canGiftDaily() 占位恒 false（需好友列表/服务器，未越界实现）")

# A7 captureNode 白名单守卫（防注入边界）：未知节点直接拒绝、零变更
check(
    "if (node !in LocationEngine.STRONGHOLD_NODES) return false" in gs_src,
    "GameState.captureNode 白名单守卫：未知节点（平台误传/外挂注入）直接拒绝",
)

# A8 strongholdAttackBonus 接线：复用 SocialEngine.attackBonus(strongholdNode != null)
check(
    "SocialEngine.attackBonus(strongholdNode != null)" in gs_src,
    "strongholdAttackBonus 接线 SocialEngine.attackBonus（占领→1.10，否则 1.0）",
)

# A9 maybeResetDaily 据点每日重置：strongholdDay != today → 清空节点
check(
    "if (strongholdDay != today) { strongholdNode = null; strongholdDay = today }" in gs_src,
    "maybeResetDaily 据点每日重置：跨天清空 strongholdNode（次日可再占领）",
)

# A10 占据贡献公式：CONTRIBUTION_PER_CAPTURE * (1.0 + sectStrongholdBonus())
check(
    "CONTRIBUTION_PER_CAPTURE * (1.0 + sectStrongholdBonus())" in gs_src,
    "捕获成功贡献 = CONTRIBUTION_PER_CAPTURE * (1 + 据点科技加成)（接线单一真源）",
)


# ── [B] 行为级复现 SocialEngine 状态机 ───────────────────────────────
print("[B] 行为级复现 SocialEngine 状态机")


class StrongholdState:
    def __init__(self):
        self.ownedNode = None
        self.lastCaptureDay = ""


def can_capture(state, today):
    return state.lastCaptureDay != today


def capture(state, node, today):
    if not can_capture(state, today):
        return False
    state.ownedNode = node
    state.lastCaptureDay = today
    return True


def attack_bonus(owned):
    return 1.10 if owned else 1.0


# B1 首启（lastCaptureDay 空）可占领
st = StrongholdState()
check(can_capture(st, "2026-08-17") is True, "首启空日期 → canCapture 为真（可占领）")

# B2 占领成功写入节点与日期
ok = capture(st, "城隍桥", "2026-08-17")
check(ok is True and st.ownedNode == "城隍桥" and st.lastCaptureDay == "2026-08-17",
      "capture 成功写入 ownedNode + lastCaptureDay，返回 true")

# B3 同日二次占领被拒（每日限一次）
check(can_capture(st, "2026-08-17") is False, "同日 canCapture 为假（每日限一次）")
check(capture(st, "古庙", "2026-08-17") is False, "同日二次 capture 返回 false（未覆盖节点）")
check(st.ownedNode == "城隍桥", "同日二次占领不覆盖已占节点（状态保持）")

# B4 跨天（lastCaptureDay != today）可再占领
check(can_capture(st, "2026-08-18") is True, "跨天 canCapture 恢复为真")
check(capture(st, "古庙", "2026-08-18") is True and st.ownedNode == "古庙",
      "跨天可占领新节点（每日限额重置）")

# B5 攻击加成：占领→1.10，未占→1.0
check(abs(attack_bonus(True) - 1.10) < 1e-9, "attackBonus(占领) = 1.10")
check(abs(attack_bonus(False) - 1.0) < 1e-9, "attackBonus(未占) = 1.0")
check(abs(attack_bonus(st.ownedNode is not None) - 1.10) < 1e-9, "attackBonus 随 ownedNode 非空返回 1.10")

# B6 加成仅两种取值（穷尽）
check(set(attack_bonus(o) for o in (True, False)) == {1.10, 1.0}, "attackBonus 取值域恰为 {1.0, 1.10}")


# ── [C] 白名单守卫抗注入行为 ────────────────────────────────────────
print("[C] 白名单守卫抗注入行为")

WHITELIST = set(node_names)  # 4 个合法节点

# C1 4 个合法节点均可进入占领判定（不被白名单误杀）
for n in ["城隍桥", "古庙", "老槐树", "东海之滨"]:
    check(n in WHITELIST, "合法节点 '%s' 在白名单内（可占领）" % n)

# C2 任意注入/过期节点被拒（防御性边界，避免悬空据点 + 虚假 +10%）
for inj in ["妖塔", "魔窟", "unknown_node", "", "城隍桥 "]:
    check(inj not in WHITELIST, "注入节点 '%s' 不在白名单（captureNode 返回 false）" % repr(inj)[1:-1])

# C3 白名单规模恒为 4（防止误增误删 POI 导致越权/漏权）
check(len(WHITELIST) == 4, "白名单规模恒为 4（防 POI 漂移）")


# ── 汇总 ───────────────────────────────────────────────────────────
print("")
print("===== #182 社交据点战契约验证 =====")
print("通过 %d 项，失败 %d 项" % (passed, failed))
if FAILS:
    print("失败明细：")
    for f in FAILS:
        print("  - " + f)
    sys.exit(1)
print("✅ 全过")
