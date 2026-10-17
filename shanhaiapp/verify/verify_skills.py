# -*- coding: utf-8 -*-
"""
《山海妖志》技能池完整性校验（本机无 JDK，解析源码代替编译验证）
- 解析 shared/.../SkillLibrary.kt 与 iosApp/.../Core.swift 的技能定义
- 校验：总数=73 / id 唯一 / SkillType·Element 枚举合法 / 威力∈[0,200]
        / 暴击∈[0,0.5] / 伤害技能威力>0 且 非伤害威力=0 / 6 大类均有覆盖
- 交叉比对：Kotlin ↔ Swift 技能 id 集合完全一致（无重复/无单向差异）
- 实战接线：统计妖池中有多少妖引用了「新增技能」（四神兽已装配签名技）
用法：python verify/verify_skills.py
"""
import re
import sys

KOTLIN = "shared/src/commonMain/kotlin/com/shanhai/core/data/SkillLibrary.kt"
SWIFT = "iosApp/iosApp/Core.swift"
MONSTER_POOL = "shared/src/commonMain/kotlin/com/shanhai/core/data/MonsterPool.kt"

SKILL_TYPES = {"ATTACK", "DEFENSE", "SPELL", "SUPPORT", "ELEMENT", "PASSIVE"}
ELEMENTS = {"METAL", "WOOD", "WATER", "FIRE", "EARTH", "THUNDER", "WIND"}

# 既有 24 式（扩池前），用于区分「原有 / 新增」
ORIGINAL_IDS = {
    "s_liekong", "s_jingtao", "s_fentian", "s_wanjian", "s_jiuxiao", "s_qingmu",
    "s_luoshi", "s_gangfeng", "s_jinzhong", "s_xuanbing", "s_fanzhen", "s_kunyao",
    "s_dingshen", "s_ganlin", "s_xuming", "s_sanmei", "s_hantan", "s_wulei",
    "s_qingmu2", "s_houtu", "s_ruijin", "s_jifeng", "s_xueshi", "s_qinhe",
}

passed = 0
failed = 0


def check(cond, msg):
    global passed, failed
    if cond:
        passed += 1
    else:
        failed += 1
        print("  ✗", msg)


def parse_kotlin(path):
    text = open(path, encoding="utf-8").read()
    pat = re.compile(
        r'Skill\(\s*"([^"]+)"\s*,\s*"[^"]*"\s*,\s*SkillType\.(\w+)\s*,\s*'
        r'Element\.(\w+)\s*,\s*(\d+)\s*,\s*(true|false)(?:\s*,\s*([\d.]+))?'
    )
    out = []
    for m in pat.finditer(text):
        sid, st, el, pw, dmg, crit = m.groups()
        out.append({
            "id": sid, "type": st.upper(), "elem": el.upper(), "power": int(pw),
            "is_dmg": dmg == "true", "crit": float(crit) if crit else 0.0,
        })
    return out


def parse_swift(path):
    text = open(path, encoding="utf-8").read()
    # 仅取 SK 数组内的 S(...) 定义
    start = text.index("let SK = [")
    end = text.index("].reduce(into:")
    seg = text[start:end]
    pat = re.compile(
        r'S\(\s*"([^"]+)"\s*,\s*"[^"]*"\s*,\s*\.(\w+)\s*,\s*\.(\w+)\s*,\s*'
        r'(\d+)\s*,\s*(true|false)(?:\s*,\s*([\d.]+))?'
    )
    out = []
    for m in pat.finditer(seg):
        sid, st, el, pw, dmg, crit = m.groups()
        out.append({
            "id": sid, "type": st.upper(), "elem": el.upper(), "power": int(pw),
            "is_dmg": dmg == "true", "crit": float(crit) if crit else 0.0,
        })
    return out


def validate(label, skills):
    ids = [s["id"] for s in skills]
    check(len(skills) == 73, f"[{label}] 技能总数应为 73（实际 {len(skills)}）")
    check(len(ids) == len(set(ids)), f"[{label}] id 唯一（重复：{sorted(set(x for x in ids if ids.count(x) > 1))}）")
    types_seen, elems_seen = set(), set()
    for s in skills:
        check(s["type"] in SKILL_TYPES, f"[{label}] {s['id']} SkillType 合法（{s['type']}）")
        check(s["elem"] in ELEMENTS, f"[{label}] {s['id']} Element 合法（{s['elem']}）")
        check(0 <= s["power"] <= 200, f"[{label}] {s['id']} 威力∈[0,200]（{s['power']}）")
        check(0.0 <= s["crit"] <= 0.5, f"[{label}] {s['id']} 暴击∈[0,0.5]（{s['crit']}）")
        if s["is_dmg"]:
            check(s["power"] > 0, f"[{label}] {s['id']} 伤害技能威力>0（{s['power']}）")
        else:
            check(s["power"] == 0, f"[{label}] {s['id']} 非伤害技能威力=0（{s['power']}）")
        types_seen.add(s["type"])
        elems_seen.add(s["elem"])
    check(types_seen == SKILL_TYPES, f"[{label}] 6 大类全覆盖（缺：{SKILL_TYPES - types_seen}）")
    return set(ids)


def count_monsters_using_new(path, new_ids):
    text = open(path, encoding="utf-8").read()
    # 仅匹配技能列表 listOf("s_...")，排除容器 listOf(m(...))
    blocks = re.findall(r'listOf\(\s*("s_[^)]*)\)', text)
    used = 0
    for b in blocks:
        refs = set(re.findall(r'"([^"]+)"', b))
        if refs & new_ids:
            used += 1
    return used, len(blocks)


def main():
    k = parse_kotlin(KOTLIN)
    s = parse_swift(SWIFT)
    k_ids = validate("Kotlin", k)
    s_ids = validate("Swift", s)

    # 交叉比对
    check(k_ids == s_ids, "Kotlin↔Swift 技能 id 集合完全一致")
    if k_ids != s_ids:
        print("    Kotlin 独有：", sorted(k_ids - s_ids))
        print("    Swift 独有：", sorted(s_ids - k_ids))

    # 新增技能实战接线（四神兽已装配签名技）
    new_ids = k_ids - ORIGINAL_IDS
    check(len(new_ids) == 49, f"新增技能应为 49 式（实际 {len(new_ids)}）")
    used, total = count_monsters_using_new(MONSTER_POOL, new_ids)
    check(used >= 4, f"≥4 只妖已装配新增技能（实际 {used}/{total}）")
    print(f"  信息：妖池 {total} 只中 {used} 只引用了新增技能（四神兽签名技已接线）")

    print(f"\n技能池校验：通过 {passed} 项，失败 {failed} 项")
    if failed == 0:
        print("✓ 全部断言通过")
        return 0
    print("✗ 存在失败断言")
    return 1


if __name__ == "__main__":
    sys.exit(main())
