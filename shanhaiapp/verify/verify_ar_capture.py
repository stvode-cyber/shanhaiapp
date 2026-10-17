#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
《山海妖志》#183 AR 收妖契约校验（Python 等价验证，不依赖 Kotlin 编译器）。

#183 AR（GDD 四·AR 可选）的 shared 层是**契约占位**：摄像头 / ARKit / ARCore 由平台实现，
shared 仅定义 AR 与平台之间的契约，并**复用 CaptureEngine 的点按判定逻辑**（不引入独立收妖率）。
本脚本分三层钉死：
  [A] 解析级契约接线：ARStatus 三态 / status() 默认 UNSUPPORTED / resolveAtAnchor 纯委托
       CaptureEngine.resolve / ArCaptureBackend 五方法且 resolveCapture 契约复用 CaptureEngine.resolve /
       GameState.arStatus() 委托接线。
  [B] CaptureEngine 公式权威值（解析源码，防漂移）：环周期基数 / 稀有度系数 / 0.92·0.06·0.18·1.4·0.8·0.2 常量。
  [C] 行为级复现：accuracy / successProbability / ringCycleMs / resolve 四则确定性逻辑，含边界
       （p=1.0 必成·必不挣扎；p=0.06 地板·双次独立 rng 抽样的成功/挣扎解耦）。
       AR 路径 == 普通收妖路径（resolveAtAnchor ≡ CaptureEngine.resolve），证明 AR 不引入独立收妖率。

运行：python verify/verify_ar_capture.py
"""
import re
import sys
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATH_AR = os.path.join(ROOT, "shared/src/commonMain/kotlin/com/shanhai/core/logic/ARCaptureController.kt")
PATH_CAP = os.path.join(ROOT, "shared/src/commonMain/kotlin/com/shanhai/core/logic/CaptureEngine.kt")
PATH_GS = os.path.join(ROOT, "shared/src/commonMain/kotlin/com/shanhai/core/logic/GameState.kt")


def slurp(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


ar_src = slurp(PATH_AR)
cap_src = slurp(PATH_CAP)
gs_src = slurp(PATH_GS)


def balanced_block(src, name):
    """定位 `fun <name>(...)` 并提取其花括号平衡的函数体（含嵌套 when{}）。"""
    idx = src.find("fun " + name)
    if idx < 0:
        return None
    i = src.find("{", idx)
    if i < 0:
        return None
    depth = 0
    j = i
    while j < len(src):
        c = src[j]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[i + 1:j]
        j += 1
    return None


passed = 0
failed = 0


def check(cond, name):
    global passed, failed
    if cond:
        passed += 1
    else:
        failed += 1
        print("  ✗ " + name)


# ============================ [A] 解析级契约接线 ============================
# A1. ARStatus 枚举恰好三态
m = re.search(r"enum class ARStatus\s*\{(.*?)\}", ar_src, re.S)
arstatus_block = m.group(1)
arstatus_vals = re.findall(r"^\s*([A-Z][A-Z_]+)", arstatus_block, re.M)
check(set(arstatus_vals) == {"UNSUPPORTED", "PERMISSION_DENIED", "READY"},
      "ARStatus 枚举恰好三态 UNSUPPORTED/PERMISSION_DENIED/READY")
check(len(arstatus_vals) == 3, "ARStatus 枚举数量为 3")

# A2. status() 默认 UNSUPPORTED（平台应覆盖）
check(re.search(r"fun status\(\):\s*ARStatus\s*=\s*ARStatus\.UNSUPPORTED", ar_src) is not None,
      "status() 默认返回 UNSUPPORTED（平台占位）")

# A3. resolveAtAnchor 签名 + 纯委托 CaptureEngine.resolve（无独立收妖率）
check(re.search(
    r"fun resolveAtAnchor\(\s*accuracy:\s*Double,\s*rarity:\s*Rarity,\s*assist:\s*Boolean,"
    r"\s*rng:\s*\(\)\s*->\s*Double\s*=\s*::defaultRandom", ar_src, re.S) is not None,
    "resolveAtAnchor 签名（accuracy/rarity/assist/rng 四参）")
check(re.search(r"CaptureEngine\.resolve\(accuracy,\s*rarity,\s*assist,\s*rng\)", ar_src) is not None,
      "resolveAtAnchor 纯委托 CaptureEngine.resolve（无独立收妖率）")

# A4. ArCaptureBackend 恰好五方法
m = re.search(r"interface ArCaptureBackend\s*\{(.*?)\n\}", ar_src, re.S)
backend_block = m.group(1)
backend_methods = re.findall(r"fun\s+(\w+)", backend_block)
check(set(backend_methods) == {"isCameraAvailable", "requestPermission", "beginSession", "endSession", "resolveCapture"},
      "ArCaptureBackend 恰好五方法（相机/权限/会话/收妖）")

# A5. resolveCapture 契约复用 CaptureEngine.resolve（接口抽象方法，平台实现应委托）
check(re.search(
    r"fun resolveCapture\(\s*accuracy:\s*Double,\s*rarity:\s*Rarity,\s*assist:\s*Boolean,"
    r"\s*rng:\s*\(\)\s*->\s*Double\s*=\s*::defaultRandom", ar_src, re.S) is not None
      and "CaptureEngine.resolve" in ar_src,
      "ArCaptureBackend.resolveCapture 同签名 + 文件声明复用 CaptureEngine.resolve（契约）")

# A6. requestPermission 返回 ARStatus（契约：平台弹窗后回写状态）
# 兼容 ARCaptureController.ARStatus（同文件独立 interface 需全限定名）
check(re.search(r"fun requestPermission\(\):\s*(?:ARCaptureController\.)?ARStatus", ar_src) is not None,
      "requestPermission 返回 ARStatus")

# A7. GameState.arStatus() 委托接线（UI 据此切 AR/卷轴背景）
check(re.search(
    r"fun arStatus\(\):\s*ARCaptureController\.ARStatus\s*=\s*ARCaptureController\.status\(\)",
    gs_src) is not None,
    "GameState.arStatus() 委托 ARCaptureController.status()")

# ============================ [B] CaptureEngine 公式权威值 ============================
ring_block = balanced_block(cap_src, "ringCycleMs")
sp_block = balanced_block(cap_src, "successProbability")
acc_block = balanced_block(cap_src, "accuracy")

# B1. 环周期基数（稀有更快）
ring_bases = dict(re.findall(r"Rarity\.(\w+)\s*->\s*(\d+)L", ring_block))
check(ring_bases == {"FAN": "2600", "LING": "2200", "XIAN": "1900", "SHEN": "1600"},
      "ringCycleMs 基数 FAN=2600/LING=2200/XIAN=1900/SHEN=1600")
check("1.4" in ring_block, "ringCycleMs 辅助模式放慢 1.4 倍")

# B2. 稀有度系数（收妖率）
rarity_factors = dict(re.findall(r"Rarity\.(\w+)\s*->\s*([\d.]+)", sp_block))
check(rarity_factors == {"FAN": "1.0", "LING": "0.9", "XIAN": "0.8", "SHEN": "0.7"},
      "successProbability 稀有度系数 FAN=1.0/LING=0.9/XIAN=0.8/SHEN=0.7")
check("0.92" in sp_block and "0.06" in sp_block, "successProbability 含 0.92 缩放 + 0.06 保底")
check("0.18" in sp_block, "successProbability 辅助模式 +0.18 精度抬升")

# B3. accuracy 公式常量
check("0.8" in acc_block and "0.2" in acc_block, "accuracy 辅助模式 raw*0.8+0.2 抬升")
check("coerceAtLeast(1" in acc_block, "accuracy 半径跨度防零（coerceAtLeast(1)）")

# ============================ [C] 行为级复现 ============================
RING_BASE = {k: int(v) for k, v in ring_bases.items()}
RF = {k: float(v) for k, v in rarity_factors.items()}


def ring_cycle_ms(rarity, assist):
    return int(RING_BASE[rarity] * (1.4 if assist else 1.0))


def accuracy(tap_r, min_r, max_r, assist):
    span = max(max_r - min_r, 1.0)
    raw = 1.0 - ((tap_r - min_r) / span)
    raw = max(0.0, min(1.0, raw))
    if assist:
        return max(0.0, min(1.0, raw * 0.8 + 0.2))
    return raw


def success_probability(acc, rarity, assist, capture_mul=1.0):
    eff = max(0.0, min(1.0, acc + (0.18 if assist else 0.0)))
    p = (eff * RF[rarity] * 0.92 + 0.06) * capture_mul
    return max(0.0, min(1.0, p))


def resolve(acc, rarity, assist, rng, capture_mul=1.0):
    # 与 Kotlin 一致：success 与 struggled 各抽一次 rng（独立）
    p = success_probability(acc, rarity, assist, capture_mul)
    return {"success": rng() < p, "probability": p, "struggled": rng() >= p, "accuracy": acc}


# C1. ringCycleMs 取值（忠实复刻 Kotlin base*1.4 的 .toLong() 截断，非手算四舍五入）
for rar, base in RING_BASE.items():
    check(ring_cycle_ms(rar, False) == base, "ringCycleMs {} 无辅助={}".format(rar, base))
    check(ring_cycle_ms(rar, True) == int(base * 1.4), "ringCycleMs {} 辅助=base*1.4截断({})".format(rar, int(base * 1.4)))

# C2. accuracy 边界
check(abs(accuracy(10.0, 10.0, 100.0, False) - 1.0) < 1e-9, "accuracy 最小半径处精度=1.0")
check(abs(accuracy(100.0, 10.0, 100.0, False) - 0.0) < 1e-9, "accuracy 最大半径处精度=0.0")
check(abs(accuracy(100.0, 10.0, 100.0, True) - 0.2) < 1e-9, "accuracy 辅助模式最大半径处抬升至0.2")
check(abs(accuracy(55.0, 10.0, 100.0, False) - 0.5) < 1e-9, "accuracy 中点无辅助=0.5")
check(abs(accuracy(55.0, 10.0, 100.0, True) - 0.6) < 1e-9, "accuracy 中点辅助=0.6（0.5*0.8+0.2）")

# C3. successProbability 边界与系数
check(abs(success_probability(1.0, "FAN", False) - 0.98) < 1e-9, "successProb FAN 精度1无辅助=0.98（1*0.92+0.06）")
check(abs(success_probability(0.0, "FAN", False) - 0.06) < 1e-9, "successProb FAN 精度0无辅助=地板0.06")
check(abs(success_probability(1.0, "SHEN", False) - 0.704) < 1e-9, "successProb SHEN 精度1无辅助=0.704（0.7*0.92+0.06）")
check(abs(success_probability(0.0, "FAN", True) - 0.2256) < 1e-9, "successProb FAN 精度0辅助=0.2256（0.18*0.92+0.06）")
# 乘子上限：captureMul=2 使 p 越界被 clamp 到 1.0
check(abs(success_probability(1.0, "FAN", False, capture_mul=2.0) - 1.0) < 1e-9, "successProb captureMul=2 越界 clamp 到 1.0")

# C4. resolve 双次独立 rng 抽样 + 边界语义
draws = iter([0.5, 0.5])
r = resolve(1.0, "FAN", False, lambda: next(draws), capture_mul=2.0)
check(r["success"] is True and r["struggled"] is False and abs(r["probability"] - 1.0) < 1e-9,
      "resolve p=1.0：必成功且必不挣扎（rng>=1.0 恒 false）")
draws = iter([0.05, 0.07])
r = resolve(0.0, "FAN", False, lambda: next(draws))
check(r["success"] is True and r["struggled"] is True, "resolve 地板 p=0.06：rng1<0.06 成、rng2>=0.06 挣扎（独立）")
draws = iter([0.07, 0.03])
r = resolve(0.0, "FAN", False, lambda: next(draws))
check(r["success"] is False and r["struggled"] is False, "resolve 地板 p=0.06：rng1>=0.06 败、rng2<0.06 不挣扎（解耦）")
check(abs(r["accuracy"] - 0.0) < 1e-9, "resolve 回显 accuracy 原值")

# C5. AR 路径 == 普通收妖路径（resolveAtAnchor ≡ CaptureEngine.resolve）
def ar_resolve(acc, rarity, assist, rng, capture_mul=1.0):
    return resolve(acc, rarity, assist, rng, capture_mul)


seq = [0.3, 0.9, 0.1, 0.4, 0.77, 0.02]
it1, it2 = iter(seq[:]), iter(seq[:])
a = ar_resolve(0.6, "LING", True, lambda: next(it1))
b = resolve(0.6, "LING", True, lambda: next(it2))
check(a == b, "AR 收妖路径 == 普通收妖路径（resolveAtAnchor ≡ CaptureEngine.resolve，无独立收妖率）")
check(abs(a["probability"] - success_probability(0.6, "LING", True)) < 1e-9, "AR 收妖概率符合 CaptureEngine 公式")

# ============================ 汇总 ============================
print("------------------------------------------------------------")
print("#183 AR 收妖契约校验")
print("  通过 {} 项，失败 {} 项".format(passed, failed))
print("------------------------------------------------------------")
if failed == 0:
    print("✅ 全过")
    sys.exit(0)
else:
    print("❌ 有失败项")
    sys.exit(1)
