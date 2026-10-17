#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
双端镜像一致性审计（Kotlin shared ↔ iOS Core.swift）
====================================================
《山海妖志》核心铁律：数值/判定全部收敛到 shared，iOS Core.swift 须与 shared API 一一对应、双端不漂移。

本脚本做两件独立的事：
  [1] 类型级审计：shared 顶层类型 ↔ Core.swift 类型差集（Kotlin 有、Swift 无 = 真实漂移）。
  [3] 成员级审计：对「两端都存在且同名」的类型，逐一比对成员名集合。
       归一化处理：蛇形↔camel（去下划线+小写）、Swift 同行多声明(let a=1,b=2)逗号拆分、
       函数名取 '(' 前 / 属性名取 ':' 或 '=' 前、实例方法与静态方法合并（object↔struct static）。
       只报告「Kotlin 有但 Swift 缺」的成员（潜在漂移），Swift 多出的辅助成员忽略。

花括号深度感知：只收集类型直接成员（depth==1），剔除函数体内局部变量，降低误报。

用法：
    python verify_mirror.py
输出：类型漂移 / 成员级潜在漂移（供 triage，确认无误后可将关键项固化进主验证 verify_lbs_gacha_dungeon.py）。
注意：常量被内联进 Swift 函数体属良性差异（行为已被主验证覆盖），本脚本仅作复核清单，不直接 fail。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMMON = os.path.join(ROOT, "shared", "src", "commonMain", "kotlin", "com", "shanhai", "core")
CORE_SWIFT = os.path.join(ROOT, "iosApp", "iosApp", "Core.swift")

SCAN_DIRS = ["logic", "model", "data", "persistence"]

# ── 类型声明（仅 depth==0 顶层）──
KT_TYPE_RE = re.compile(r'^(?:object|class|enum class|data class|interface)\s+(\w+)')
SW_TYPE_RE = re.compile(r'^(?:struct|enum|class|protocol)\s+(\w+)')

# ── 成员声明（depth==1 直接成员）── 捕获关键字 + 余部，后续按关键字解析
KT_MOD_RE = re.compile(
    r'^\s+(?:(?:public|internal|override|suspend|inline|tailrec|'
    r'operator|const|final|open|actual|expect)\s+)*(fun|val|var|class|object|enum class|interface)\b\s*(.*)$'
)
SW_MOD_RE = re.compile(
    r'^\s+(?:(?:static|mutating|internal|public|open|final|class|'
    r'convenience|required|override|lazy)\s+)*(?:@\w+\s+)*(func|let|var|init)\b\s*(.*)$'
)


def normalize(name):
    """蛇形↔camel 归一：去下划线 + 小写 + 去 Swift 反引号转义（`catch`→catch）。"""
    return name.strip().lower().replace('_', '').replace('`', '')


def kotlin_member_names(keyword, rest):
    rest = rest.strip()
    if keyword == 'fun':
        name = rest.split('(')[0].split('<')[0].strip()
        return [normalize(name)] if name and re.match(r'^\w+$', name) else []
    # val / var：Kotlin 不支持 `val a=1,b=2` 同行多声明，取首个标识符
    name = re.split(r'[:=]', rest)[0].split('<')[0].strip()
    if name and re.match(r'^\w+$', name):
        return [normalize(name)]
    return []


def swift_member_names(keyword, rest):
    rest = rest.strip()
    if keyword == 'func' or keyword == 'init':
        name = rest.split('(')[0].split('<')[0].strip()
        if name and re.match(r'^\w+$', name):
            return [normalize(name)]
        return []
    # let / var：支持同行多声明 `a = 1, b = 2, c = 3`
    names = []
    for piece in rest.split(','):
        piece = piece.strip()
        nm = re.split(r'[:=]', piece)[0].replace('?', '').strip()
        if nm and re.match(r'^\w+$', nm):
            names.append(normalize(nm))
    return names


def _brace_collect(path, type_re, mod_re, parser):
    """返回 (types, methods, kind)。methods[type] = {'fun': set, 'prop': set}（归一化名）。

    花括号深度感知：只收集类型直接成员（brace_depth==1）。
    括号深度感知（#198 修复）：Kotlin 常把 data class 嵌套在 object 内，其主构造参数
    （val id / val name ...）写在 `(...)` 里、且 data class 可能无类体 `{}`。若不跟踪
    括号深度，这些嵌套类型的构造参数会被误算为外层类型的属性 → 伪造 [3b] 缺口。
    因此只在 brace_depth==1 且 paren_depth==0 时收集成员。
    """
    types = set()
    methods = {}
    kind = {}
    depth = 0          # 花括号深度
    paren = 0          # 圆括号深度（构造参数 / 调用参数）
    top_type = None
    with open(path, encoding="utf-8") as f:
        for ln in f:
            m = type_re.match(ln)
            if m and depth == 0:
                name = m.group(1)
                kd = ln.strip().split()[0]
                types.add(name)
                methods[name] = {'fun': set(), 'prop': set()}
                kind[name] = kd
                top_type = name
            if top_type is not None and depth == 1 and paren == 0:
                fm = mod_re.match(ln)
                if fm:
                    kw = fm.group(1)
                    rest = fm.group(2)
                    cat = 'fun' if kw in ('fun', 'func', 'init') else 'prop'
                    for nm in parser(kw, rest):
                        methods[top_type][cat].add(nm)
            depth += ln.count("{") - ln.count("}")
            paren += ln.count("(") - ln.count(")")
            if depth == 0:
                top_type = None
                paren = 0
    return types, methods, kind


def collect_kotlin():
    types, methods, kind = set(), {}, {}
    for d in SCAN_DIRS:
        base = os.path.join(COMMON, d)
        if not os.path.isdir(base):
            continue
        for fn in sorted(os.listdir(base)):
            if not fn.endswith(".kt"):
                continue
            t, m, k = _brace_collect(os.path.join(base, fn), KT_TYPE_RE, KT_MOD_RE, kotlin_member_names)
            types |= t
            methods.update(m)
            kind.update(k)
    return types, methods, kind


def collect_swift():
    return _brace_collect(CORE_SWIFT, SW_TYPE_RE, SW_MOD_RE, swift_member_names)


# 平台/运行时专有、无需 Swift 镜像（由平台层接入）
PLATFORM_ONLY = {
    "CloudSyncBackend", "SocialService", "MapSdkAdapter",
    "DailyResetScheduler", "ArCaptureBackend",
}
# 表示差异：Kotlin 有对应 Swift 实现但形态不同（非逻辑漂移）
REPRESENTATION_DIFF = {
    "SkillLibrary": "Swift 内联为 SKILLS 数组",
    "CatchOutcome": "Swift 返回 [String] 简化",
    "SaveCodec": "Swift 原生 Codable 等价实现",
}

# 成员级「已确认表示/命名差异」（同名类型内，Kotlin 成员 ↔ Swift 等价实现，非逻辑漂移）：
# 多为「Kotlin 计算函数 ↔ Swift 属性/内联」或「命名差异」。审计时从缺口中豁免，仅作备查。
# 格式：("Kotlin类型", "归一化成员名") -> 说明
# 注：byName / nodeIds / layersOf / milestones / newlyUnlocked 已于 #198 落地为真实 1:1 镜像，
#     不再是表示差异，已从本清单移除（审计 [3a] 现自然零缺口）。
REPRESENTATION_VARIANCE = {
    ("Codex", "regiontotal"): "Swift 用 `all.count` 内联（all 属性等价，无独立函数）",
    ("Codex", "regioncaught"): "Kotlin 为 fun regionCaught()，Swift 为计算属性 var regionCaught，行为等价",
    ("Codex", "catch"): "Swift 为反引号转义 `catch`(id:secret:)，逻辑等价",
    ("ComboEngine", "damagemultiplier"): "Swift 在战斗中按 ComboEffect.damageMul 内联应用",
    ("ComboEngine", "healpercent"): "Swift 在战斗中按 ComboEffect.healPercent 内联应用",
}


def main():
    kt_types, kt_methods, kt_kind = collect_kotlin()
    sw_types, sw_methods, _ = collect_swift()

    print("=" * 72)
    print("双端镜像一致性审计")
    print("shared 顶层类型: %d  |  Core.swift 类型: %d" % (len(kt_types), len(sw_types)))
    print("=" * 72)

    # [1] 类型级缺失
    missing_types = [n for n in sorted(kt_types)
                     if n not in sw_types and n not in PLATFORM_ONLY and n not in REPRESENTATION_DIFF]
    print("\n[1] Kotlin 有但 Swift 漏镜像（真实类型漂移）: %d" % len(missing_types))
    for n in missing_types:
        print("    - %s" % n)
    print("\n[1b] 表示差异（已豁免，非漂移）: %d" % len(REPRESENTATION_DIFF))
    for n in sorted(REPRESENTATION_DIFF):
        print("    - %-16s %s" % (n, REPRESENTATION_DIFF[n]))
    extra_swift = sorted(s for s in sw_types if s not in kt_types)
    print("\n[1c] Swift 专有类型（iOS 辅助，备查）: %d" % len(extra_swift))
    for s in extra_swift:
        print("    - %s" % s)

    # [3] 成员级比对（同名类型），分函数级（高信号=逻辑漂移）/ 属性级（低信号=表示差异）
    print("\n[3a] 函数级潜在漂移（同名类型，Kotlin fun / Swift 缺 → 真实逻辑 drift 信号）:")
    fun_gaps = []
    for name in sorted(t for t in kt_types if t in sw_types
                       and t not in PLATFORM_ONLY and t not in REPRESENTATION_DIFF):
        kfun = kt_methods.get(name, {}).get('fun', set())
        sfun = sw_methods.get(name, {}).get('fun', set())
        miss = sorted(kfun - sfun)
        miss = [m for m in miss if (name, m) not in REPRESENTATION_VARIANCE]  # 剔除已确认差异
        if miss:
            fun_gaps.append((name, miss))
            print("    %-22s 缺: %s" % (name, ", ".join(miss)))
    if not fun_gaps:
        print("    （无函数级漂移）")
    print("\n[3a-已确认] 表示/命名差异（豁免，非漂移）: %d" % len(REPRESENTATION_VARIANCE))
    for k, v in sorted(REPRESENTATION_VARIANCE.items()):
        print("    - %-22s %s" % ("%s.%s" % k, v))

    print("\n[3b] 属性级差异（同名类型，Kotlin 属性 / Swift 缺 → 多为内联/表示差异，备查）:")
    prop_gaps = 0
    for name in sorted(t for t in kt_types if t in sw_types
                       and t not in PLATFORM_ONLY and t not in REPRESENTATION_DIFF):
        kprop = kt_methods.get(name, {}).get('prop', set())
        sprop = sw_methods.get(name, {}).get('prop', set())
        miss = sorted(kprop - sprop)
        if miss:
            prop_gaps += len(miss)
            print("    %-22s 缺(%d): %s" % (name, len(miss), ", ".join(miss)))
    if prop_gaps == 0:
        print("    （无属性级差异）")

    print("\n" + "=" * 72)
    print("汇总: 类型漂移 %d  /  函数级漂移(类型数) %d  /  属性级差异(项数) %d  /  表示差异豁免 %d"
          % (len(missing_types), len(fun_gaps), prop_gaps, len(REPRESENTATION_DIFF)))
    print("=" * 72)
    return 0


if __name__ == "__main__":
    sys.exit(main())
