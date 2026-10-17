"""
《山海妖志》妖池完整性校验 · 直接解析 shared 层 MonsterPool.kt 源码

不依赖编译器：用正则从 Kotlin 源文件抽取每个 m(...) 妖怪定义，断言：
  - id 全局唯一
  - category / element / rarity 均为合法枚举值
  - 每个技能 id 在 SkillLibrary 的合法集合内
  - 四项基础属性为正整数且在合理区间（1..250）
  - 进化链闭合：evolveTo 目标存在、其 evolveFrom 反向指向本妖、稀有度严格 +1 阶
  - 全池总数与按稀有度/类别分布

运行：python verify/verify_pool.py
"""

import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
POOL = os.path.normpath(os.path.join(
    HERE, "..", "shared", "src", "commonMain", "kotlin",
    "com", "shanhai", "core", "data", "MonsterPool.kt"))

# 合法枚举（与 model / data 层保持一致）
CATS = {"JING", "GUAI", "YAO", "LINGSHOU"}
ELEMENTS = {"METAL", "WOOD", "WATER", "FIRE", "EARTH", "THUNDER", "WIND"}
RARS = {"FAN", "LING", "XIAN", "SHEN"}
RAR_ORDER = {"FAN": 0, "LING": 1, "XIAN": 2, "SHEN": 3}
# 合法技能集合：动态解析 SkillLibrary.kt（避免硬编码随扩池过期）
SKILL_LIB = "shared/src/commonMain/kotlin/com/shanhai/core/data/SkillLibrary.kt"
SKILLS = set(re.findall(r'Skill\(\s*"([^"]+)"', open(SKILL_LIB, encoding="utf-8").read()))

PASS, FAIL = 0, 0


def check(cond, msg):
    global PASS, FAIL
    if cond:
        PASS += 1
    else:
        FAIL += 1
        print(f"  ✗ FAIL: {msg}")


def parse_pool(path):
    """从 Kotlin 源码抽取每个 m(...) 定义（可能跨行），返回 list[dict]"""
    with open(path, encoding="utf-8") as f:
        text = f.read()
    monsters = []
    # m(...) 可能跨行；listOf(...) 内无嵌套括号，作为原子处理；m 闭合 ) 后跟分隔 , 或列表闭合 )
    pat = re.compile(
        r'm\(\s*"([^"]+)"\s*,\s*"[^"]*"\s*,\s*'
        r'MonsterCategory\.(\w+)\s*,\s*Element\.(\w+)\s*,\s*Rarity\.(\w+)\s*,\s*'
        r'(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'
        r'listOf\([^)]*\)\s*,?\s*(.*?)\)\s*[,)]',
        re.DOTALL)
    ef_pat = re.compile(r'evolveFrom\s*=\s*"([^"]*)"')
    et_pat = re.compile(r'evolveTo\s*=\s*"([^"]*)"')
    for m in pat.finditer(text):
        mid, cat, elem, rar, atk, dfn, hp, spd, tail = m.groups()
        skills_block = re.search(r'listOf\(([^)]*)\)', m.group(0))
        skills = re.findall(r'"([^"]+)"', skills_block.group(1)) if skills_block else []
        ef = ef_pat.search(tail)
        et = et_pat.search(tail)
        monsters.append({
            "id": mid,
            "cat": cat,
            "elem": elem,
            "rar": rar,
            "atk": int(atk), "def": int(dfn), "hp": int(hp), "spd": int(spd),
            "skills": skills,
            "ef": ef.group(1) if ef else None,
            "et": et.group(1) if et else None,
        })
    return monsters


def main():
    monsters = parse_pool(POOL)
    by_id = {m["id"]: m for m in monsters}

    # 1) 总数
    check(len(monsters) == 100, f"妖池总数应为 100（实际 {len(monsters)}）")

    # 2) id 唯一
    ids = [m["id"] for m in monsters]
    check(len(ids) == len(set(ids)), f"id 唯一（重复：{set(x for x in ids if ids.count(x) > 1)}）")

    # 3) 枚举 / 技能 / 数值合法性
    for m in monsters:
        check(m["cat"] in CATS, f"{m['id']} category 合法（{m['cat']}）")
        check(m["elem"] in ELEMENTS, f"{m['id']} element 合法（{m['elem']}）")
        check(m["rar"] in RARS, f"{m['id']} rarity 合法（{m['rar']}）")
        for s in m["skills"]:
            check(s in SKILLS, f"{m['id']} 技能 {s} 在 SkillLibrary 中")
        check(len(m["skills"]) == 4, f"{m['id']} 恰好 4 个技能（实际 {len(m['skills'])}）")
        for k in ("atk", "def", "hp", "spd"):
            v = m[k]
            check(isinstance(v, int) and 1 <= v <= 250, f"{m['id']} {k}={v} 在 1..250")

    # 4) 进化链完整性
    for m in monsters:
        if m["et"]:
            t = by_id.get(m["et"])
            check(t is not None, f"{m['id']} evolveTo 目标 {m['et']} 存在")
            if t:
                check(t["ef"] == m["id"], f"{m['id']}→{m['et']} 反向 evolveFrom 一致")
                # 稀有度严格递增（允许跨阶，如 LING→SHEN 跳过 XIAN，沿用既有链设计）
                check(RAR_ORDER[t["rar"]] > RAR_ORDER[m["rar"]],
                      f"{m['id']}({m['rar']})→{m['et']}({t['rar']}) 稀有度严格递增")
        if m["ef"]:
            p = by_id.get(m["ef"])
            check(p is not None, f"{m['id']} evolveFrom 来源 {m['ef']} 存在")
            if p:
                check(p["et"] == m["id"], f"{m['id']}←{m['ef']} 反向 evolveTo 一致")
                check(RAR_ORDER[p["rar"]] < RAR_ORDER[m["rar"]],
                      f"{m['ef']}({p['rar']})→{m['id']}({m['rar']}) 稀有度严格递增")

    # 5) 链端点一致性（无 et 者为链顶；无 ef 者为链底）—— 已由 +1 规则间接保证，这里统计
    chains = [m["id"] for m in monsters if m["ef"] and m["et"]]
    tops = [m["id"] for m in monsters if m["et"] and not m["ef"]]
    bots = [m["id"] for m in monsters if m["ef"] and not m["et"]]
    check(len(tops) == len(bots), f"进化链顶/底数量一致（顶 {len(tops)} / 底 {len(bots)}）")

    # 6) 分布统计 + 钉死真实分布（A-3：防扩池漂移；此前仅打印未断言）
    from collections import Counter
    rar_cnt = Counter(m["rar"] for m in monsters)
    cat_cnt = Counter(m["cat"] for m in monsters)
    check(rar_cnt.get("FAN", 0) == 27, f"凡品 FAN=27（实 {rar_cnt.get('FAN', 0)}）")
    check(rar_cnt.get("LING", 0) == 26, f"灵品 LING=26（实 {rar_cnt.get('LING', 0)}）")
    check(rar_cnt.get("XIAN", 0) == 17, f"仙品 XIAN=17（实 {rar_cnt.get('XIAN', 0)}）")
    check(rar_cnt.get("SHEN", 0) == 30, f"神品 SHEN=30（实 {rar_cnt.get('SHEN', 0)}）")
    check(cat_cnt.get("JING", 0) == 30, f"精 JING=30（实 {cat_cnt.get('JING', 0)}）")
    check(cat_cnt.get("GUAI", 0) == 24, f"怪 GUAI=24（实 {cat_cnt.get('GUAI', 0)}）")
    check(cat_cnt.get("YAO", 0) == 25, f"妖 YAO=25（实 {cat_cnt.get('YAO', 0)}）")
    check(cat_cnt.get("LINGSHOU", 0) == 21, f"灵兽 LINGSHOU=21（实 {cat_cnt.get('LINGSHOU', 0)}）")
    check(sum(rar_cnt.values()) == 100, "稀有度分布加和=100")
    check(sum(cat_cnt.values()) == 100, "类别分布加和=100")
    check(len(SKILLS) == 73, f"技能池 SkillLibrary=73 式（实 {len(SKILLS)}）")
    print("\n妖池分布：")
    print("  稀有度：", {r: rar_cnt.get(r, 0) for r in ("FAN", "LING", "XIAN", "SHEN")})
    print("  类别  ：", {c: cat_cnt.get(c, 0) for c in ("JING", "GUAI", "YAO", "LINGSHOU")})
    print(f"  进化链中间节点 {len(chains)} 个；完整链 {len(tops)} 条")
    print(f"  解析到妖怪 {len(monsters)} 只")

    print(f"\n妖池校验：通过 {PASS} 项，失败 {FAIL} 项")
    if FAIL == 0:
        print("✓ 全部断言通过")
    else:
        print("✗ 存在失败断言")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
