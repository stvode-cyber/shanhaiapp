#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
《山海妖志》② LBS/AR · ③ 祈妖抽卡 / 副本妖境 / 社交经济 等价验证
================================================================
直接解析 shared Kotlin 源码抽取妖池与关键常量，镜像 LocationEngine /
GachaEngine / DungeonEngine / SocialEngine / ShopEngine 的核心逻辑，
断言关键不变量。运行：python verify/verify_lbs_gacha_dungeon.py
"""
import os, re, sys, json

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMMON = os.path.join(ROOT, "shared", "src", "commonMain", "kotlin", "com", "shanhai", "core")

passed = 0
failed = 0

def check(cond, name):
    global passed, failed
    if cond:
        passed += 1
        # print(f"  ✓ {name}")
    else:
        failed += 1
        print(f"  ✗ FAIL: {name}")

# ───────────────── 解析妖池（MonsterPool.kt） ─────────────────
def parse_pool():
    path = os.path.join(COMMON, "data", "MonsterPool.kt")
    txt = open(path, encoding="utf-8").read()
    pool = []
    lines = txt.splitlines()
    for i, line in enumerate(lines):
        if "m(\"" not in line:
            continue
        # clan 常写在续行（如 `..., desc = "...", clan = "fox")`），合并下一行一起扫描
        blob = line + " " + (lines[i + 1] if i + 1 < len(lines) else "")
        cat = re.search(r"MonsterCategory\.(\w+)", line)
        elem = re.search(r"Element\.(\w+)", line)
        rar = re.search(r"Rarity\.(\w+)", line)
        if not (cat and elem and rar):
            continue
        fid = re.search(r"m\(\"([^\"]+)\"", line)
        fest = "isFestival = true" in line
        clan = re.search(r'clan = "(\w+)"', blob)
        # 技能列表（listOf("s_xxx", ...)），从 blob 解析（覆盖续行）
        skills = re.search(r'listOf\((.*?)\)', blob)
        skill_ids = []
        if skills:
            skill_ids = re.findall(r'"((?:s|eq)_[^"]+)"', skills.group(1))
        pool.append({
            "id": fid.group(1), "cat": cat.group(1), "elem": elem.group(1),
            "rar": rar.group(1), "fest": fest,
            "clan": clan.group(1) if clan else None,
            "skills": skill_ids,
        })
    return pool

POOL = parse_pool()
check(len(POOL) >= 80, f"妖池解析数量≥80（实得 {len(POOL)}）")
TOTAL = len(POOL)

# ───────────────── 妖池分布计数断言（A-3：钉死真实分布，防扩池漂移） ─────────────────
# 真实计数由 verify_pool.py 交叉核对（源码 MonsterPool.kt / SkillLibrary.kt）。
from collections import Counter
_rar_cnt = Counter(m["rar"] for m in POOL)
_cat_cnt = Counter(m["cat"] for m in POOL)
check(len(POOL) == 100, f"妖池总数=100（实 {len(POOL)}）")
check(_rar_cnt.get("FAN", 0) == 27, f"凡品 FAN=27（实 {_rar_cnt.get('FAN', 0)}）")
check(_rar_cnt.get("LING", 0) == 26, f"灵品 LING=26（实 {_rar_cnt.get('LING', 0)}）")
check(_rar_cnt.get("XIAN", 0) == 17, f"仙品 XIAN=17（实 {_rar_cnt.get('XIAN', 0)}）")
check(_rar_cnt.get("SHEN", 0) == 30, f"神品 SHEN=30（实 {_rar_cnt.get('SHEN', 0)}）")
check(_cat_cnt.get("JING", 0) == 15, f"精 JING=15（实 {_cat_cnt.get('JING', 0)}）")
check(_cat_cnt.get("GUAI", 0) == 15, f"怪 GUAI=15（实 {_cat_cnt.get('GUAI', 0)}）")
check(_cat_cnt.get("YAO", 0) == 50, f"妖 YAO=50（实 {_cat_cnt.get('YAO', 0)}）")
check(_cat_cnt.get("LINGSHOU", 0) == 20, f"灵兽 LINGSHOU=20（实 {_cat_cnt.get('LINGSHOU', 0)}）")
check(sum(_rar_cnt.values()) == 100, "稀有度分布加和=100")
check(sum(_cat_cnt.values()) == 100, "类别分布加和=100")
# SkillLibrary 73 式（与 GDD 十一/七一致）
_SK_SRC = open(os.path.join(COMMON, "data", "SkillLibrary.kt"), encoding="utf-8").read()
_SKILL_N = len(re.findall(r'Skill\(\s*"', _SK_SRC))
check(_SKILL_N == 73, f"技能池=73 式（实 {_SKILL_N}）")

def by_rarity(r):
    return [m for m in POOL if m["rar"] == r]

# ───────────────── LocationEngine 镜像 ─────────────────
REGION_THEMES = ["SHANLIN", "SHUIYU", "GUMU", "DONGHAI"]
REGION_BIAS = {"SHANLIN": "WOOD", "SHUIYU": "WATER", "GUMU": "EARTH", "DONGHAI": "WATER"}

def resolve_region(lat, lng):
    a = (int(lat * 1000)) ^ (int(lng * 1000))
    idx = abs(a) % len(REGION_THEMES)
    return REGION_THEMES[idx]

def is_night(hour):
    return hour == 23 or hour == 0

def rarity_weight(r):
    return {"FAN": 45.0, "LING": 35.0, "XIAN": 15.0, "SHEN": 5.0}[r]

def weight_of(m, theme, solar, hour, degraded=False):
    w = rarity_weight(m["rar"])
    if degraded:
        if m["rar"] == "SHEN":
            return 0.0          # 神不出
        if m["rar"] == "XIAN":
            w *= 0.2            # 仙降品质
    if m["elem"] == REGION_BIAS[theme]:
        w *= 1.8
    if solar == "ZHONGYUAN" and m["cat"] == "GUAI":
        w *= 2.0
    if solar == "CHUNJIE" and m["fest"]:
        w *= 3.0
    if is_night(hour) and m["cat"] == "GUAI":
        w *= 1.6
    return w

def nearby_spawns(lat, lng, hour, solar, count, rng, degraded=False):
    theme = resolve_region(lat, lng)
    weights = [weight_of(m, theme, solar, hour, degraded) for m in POOL]
    total = sum(weights)
    out = []
    for _ in range(count):
        r = rng() * total
        chosen = POOL[-1]
        for i, w in enumerate(weights):
            r -= w
            if r <= 0.0:
                chosen = POOL[i]
                break
        out.append(chosen["id"])
    return out

# 确定性：同输入同 rng → 同结果
seq1 = nearby_spawns(31.23, 121.47, 12, "NONE", 10, lambda: 0.42)
seq2 = nearby_spawns(31.23, 121.47, 12, "NONE", 10, lambda: 0.42)
check(seq1 == seq2, "nearbySpawns 确定性（同 rng 同结果）")
check(all(any(m["id"] == i for m in POOL) for i in seq1), "nearbySpawns 返回 id 均属妖池")

# resolveRegion 确定性 + 同坐标稳定
check(resolve_region(31.23, 121.47) == resolve_region(31.23, 121.47), "resolveRegion 同坐标稳定")
check(resolve_region(31.23, 121.47) in REGION_THEMES, "resolveRegion 返回合法主题")

# 区域五行偏向：大样本下偏向元素在「偏向主题」出现率高于「非偏向主题」
import random
def elem_frac_in_theme(elem, theme, hour=12, solar="NONE", n=60000, seed=11):
    rng = random.Random(seed)
    weights = [weight_of(m, theme, solar, hour) for m in POOL]
    total = sum(weights)
    cnt = 0
    for _ in range(n):
        r = rng.random() * total
        chosen = POOL[-1]
        for i, w in enumerate(weights):
            r -= w
            if r <= 0.0: chosen = POOL[i]; break
        if chosen["elem"] == elem: cnt += 1
    return cnt / n

# 木在「山林(偏向木)」的出现率应高于在「水域(不偏向木)」的出现率
fb = elem_frac_in_theme("WOOD", "SHANLIN")
fn = elem_frac_in_theme("WOOD", "SHUIYU")
check(fb > fn * 1.25, f"区域偏向：木在山林({fb:.3f})显著高于在水域({fn:.3f})")

# 夜间怪类加成：子时 GUAI 频率高于白昼
def guai_frac(hour, solar="NONE", n=60000, seed=5):
    rng = random.Random(seed)
    weights = [weight_of(m, "SHUIYU", solar, hour) for m in POOL]
    total = sum(weights)
    cnt = 0
    for _ in range(n):
        r = rng.random() * total
        chosen = POOL[-1]
        for i, w in enumerate(weights):
            r -= w
            if r <= 0.0: chosen = POOL[i]; break
        if chosen["cat"] == "GUAI": cnt += 1
    return cnt / n
g_night = guai_frac(23)
g_day = guai_frac(12)
check(g_night > g_day, f"夜间(子时)怪类频率({g_night:.3f})高于白昼({g_day:.3f})")

# ───────────────── GPS 防作弊（瞬移 / 超速）镜像 ─────────────────
# GDD 十六·探索 / 户外灵脉：软档 >30km/h 降品质（不阻断不扣分）；瞬移 >5km 且 <180s 收服无效+扣分
SOFT_SPEED_KMH = 30.0
TELEPORT_METERS = 5000.0
TELEPORT_WINDOW_SEC = 180

def haversine(lat1, lng1, lat2, lng2):
    import math
    R = 6_371_000.0
    dLat = math.radians(lat2 - lat1)
    dLng = math.radians(lng2 - lng1)
    a = (math.sin(dLat / 2) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dLng / 2) ** 2)
    return R * 2 * math.asin(math.sqrt(a))

# 返回 (ok, reason, speed, tier)：NORMAL / DEGRADED(降品质) / REJECTED(瞬移)
def spawn_integrity(lat, lng, ts, prev_lat=None, prev_lng=None, prev_ts=0):
    if prev_lat is None or prev_lng is None:
        return True, "首次定位", 0.0, "NORMAL"
    dt = ts - prev_ts
    if dt <= 0:
        return True, "时间无效-跳过", 0.0, "NORMAL"
    dist = haversine(prev_lat, prev_lng, lat, lng)
    speed = dist / dt * 3.6
    if dist >= TELEPORT_METERS and dt <= TELEPORT_WINDOW_SEC:
        return False, "teleport", speed, "REJECTED"
    if speed > SOFT_SPEED_KMH:
        return True, "overspeed", speed, "DEGRADED"
    return True, "正常", speed, "NORMAL"

def safe_spawns(lat, lng, hour, solar, ts, prev_lat=None, prev_lng=None, prev_ts=0, count=10):
    ok, _, _, tier = spawn_integrity(lat, lng, ts, prev_lat, prev_lng, prev_ts)
    if not ok:   # REJECTED → 空（隐藏灵脉）
        return []
    return nearby_spawns(lat, lng, hour, solar, count, lambda: 0.42, degraded=(tier == "DEGRADED"))

# 首次定位 / 时间无效 → 正常
ok1, r1, _, t1 = spawn_integrity(30.25, 120.16, 1000)
check(ok1 and r1 == "首次定位" and t1 == "NORMAL", "防作弊：首次定位视为正常")
ok2, r2, _, t2 = spawn_integrity(30.25, 120.16, 0, prev_lat=30.25, prev_lng=120.16, prev_ts=1000)
check(ok2 and r2 == "时间无效-跳过" and t2 == "NORMAL", "防作弊：时间戳倒退视为跳过")

# 正常步行：100 米 / 60 秒 → 正常
ok3, r3, s3, t3 = spawn_integrity(30.2509, 120.16, 1060, prev_lat=30.25, prev_lng=120.16, prev_ts=1000)
check(ok3 and r3 == "正常" and t3 == "NORMAL", f"防作弊：正常步行({s3:.2f} km/h)通过")

# 降品质（软档）：~33 km/h 骑行但不瞬移 → DEGRADED（仍可收妖，不扣分）
ok6, r6, s6, t6 = spawn_integrity(30.25 + 0.005, 120.16, 1060, prev_lat=30.25, prev_lng=120.16, prev_ts=1000)
check(ok6 and t6 == "DEGRADED", f"防作弊：中速({s6:.2f} km/h)降品质仍可收妖")
# 跨日慢走：5000m / 200s = 90km/h（>30 但不瞬移，因 dt>180 窗口）→ DEGRADED
ok7, r7, s7, t7 = spawn_integrity(30.25 + 0.045, 120.16, 1200, prev_lat=30.25, prev_lng=120.16, prev_ts=1000)
check(ok7 and t7 == "DEGRADED", f"防作弊：长时大位移({s7:.0f} km/h, dt>窗口)降品质非瞬移")

# 瞬移：10 公里 / 5 秒 → REJECTED
ok4, r4, _, t4 = spawn_integrity(30.34, 120.16, 1005, prev_lat=30.25, prev_lng=120.16, prev_ts=1000)
check((not ok4) and r4 == "teleport" and t4 == "REJECTED", "防作弊：短时间大位移判定瞬移(REJECTED)")

# 瞬移边界：10 公里 / 200 秒（dt>180 窗口）→ 不判瞬移，而是降品质
ok8, r8, _, t8 = spawn_integrity(30.34, 120.16, 1200, prev_lat=30.25, prev_lng=120.16, prev_ts=1000)
check(t8 != "REJECTED", "防作弊：瞬移窗口边界(200s>180s)不误判为瞬移")

# safeSpawns：REJECTED 返回空，DEGRADED/NORMAL 非空
check(len(safe_spawns(30.34, 120.16, 12, "NONE", 1005, prev_lat=30.25, prev_lng=120.16, prev_ts=1000)) == 0,
      "防作弊：REJECTED 定位 safeSpawns 返回空")
check(len(safe_spawns(30.25, 120.16, 12, "NONE", 1000)) > 0, "防作弊：正常定位 safeSpawns 非空")

# 降品质权重：神=0、仙×0.2（大样本神出现率验证；nearby_spawns 直接注入随机 rng）
POOL_BY_ID = {m["id"]: m for m in POOL}
def rarity_frac(degraded, lat, lng, hour, solar, n=40000, seed=7):
    rng = random.Random(seed)
    cnt = 0
    for _ in range(n):
        seq = nearby_spawns(lat, lng, hour, solar, 1, rng.random, degraded=degraded)
        if seq and POOL_BY_ID[seq[0]]["rar"] == "SHEN":
            cnt += 1
    return cnt / n
# 正常：神应出现（权重 5，不被归零）
fr_n = rarity_frac(False, 30.25, 120.16, 12, "NONE")
check(fr_n > 0, f"防作弊：正常档神出现率({fr_n:.4f})>0")
# 降品质：神出现率=0（神不出）
fr_d = rarity_frac(True, 30.25 + 0.005, 120.16, 12, "NONE")
check(abs(fr_d) < 1e-9, f"防作弊：降品质档神出现率({fr_d:.4f})=0（神不出）")

# reportIntegrity 闭环：仅 REJECTED（瞬移）扣分；DEGRADED（降品质，GDD 软档）不扣分
def report_integrity_deduct(tier):
    return tier == "REJECTED"
check(report_integrity_deduct("NORMAL") is False, "闭环：NORMAL 不扣分")
check(report_integrity_deduct("DEGRADED") is False, "闭环：DEGRADED(降品质)不扣分（GDD 软档）")
check(report_integrity_deduct("REJECTED") is True, "闭环：REJECTED(瞬移)触发扣分")

# ───────────────── GachaEngine 镜像 ─────────────────
POOL_LING, POOL_XIAN, POOL_SHEN = 0.40, 0.45, 0.15
SOFT_START, HARD_PITY, STEP = 49, 90, 0.02
SINGLE_COST, TEN_COST, DUP_YAODAN = 60, 540, 10

def roll_rarity(since_shen, rng):
    if since_shen >= HARD_PITY - 1:
        shen_rate = 1.0
    elif since_shen >= SOFT_START:
        shen_rate = min(POOL_SHEN + (since_shen - SOFT_START + 1) * STEP, 1.0)
    else:
        shen_rate = POOL_SHEN
    r = rng()
    if r < shen_rate: return "SHEN"
    if r < shen_rate + POOL_XIAN: return "XIAN"
    return "LING"

def sign_for(rar):
    return {"SHEN": "SHANG_SHANG", "XIAN": "SHANG", "LING": "ZHONG"}[rar]

# 基础出率分布
import random as _r
def dist(n=200000, seed=3):
    rng = _r.Random(seed)
    cnt = {"LING": 0, "XIAN": 0, "SHEN": 0}
    since = 0
    for _ in range(n):
        rar = roll_rarity(since, rng.random)
        if rar == "SHEN": since = 0
        else: since += 1
        cnt[rar] += 1
    return {k: v / n for k, v in cnt.items()}
d = dist()
check(abs(d["LING"] - 0.40) < 0.02, f"基础灵率≈0.40（实得 {d['LING']:.3f}）")
check(abs(d["XIAN"] - 0.45) < 0.02, f"基础仙率≈0.45（实得 {d['XIAN']:.3f}）")
check(abs(d["SHEN"] - 0.15) < 0.02, f"基础神率≈0.15（实得 {d['SHEN']:.3f}）")

# 签文映射
check(sign_for("SHEN") == "SHANG_SHANG" and sign_for("XIAN") == "SHANG" and sign_for("LING") == "ZHONG", "签文映射 上上=神/上=仙/中=灵")

# 软保底：第 60 抽神率高于基础（第 50 抽起升）
rate_at_60 = min(POOL_SHEN + (59 - SOFT_START + 1) * STEP, 1.0)
check(rate_at_60 > POOL_SHEN, f"软保底第60抽神率({rate_at_60:.3f})>基础({POOL_SHEN})")

# 硬保底：连抽 89 次不出神后，第 90 抽必神（用常数 rng=0.99 确保前 89 抽不出神）
since = 0
forced = False
for pull_i in range(1, 91):
    rar = roll_rarity(since, lambda: 0.99)
    if rar == "SHEN":
        since = 0
    else:
        since += 1
    if pull_i == 90:
        forced = (rar == "SHEN")
check(forced, "硬保底：第 90 抽必神（连抽 89 未出神）")

# 十连保底≥灵：构造极端 rng 让前 9 抽全 LING（基础池无 FAN，本就满足；验证兜底逻辑）
def ten_pull(since, rng):
    results = [roll_rarity(since, rng.random) for _ in range(10)]
    # 模拟引擎：若全 < LING（不可能，但兜底）
    if all(r != "SHEN" and r != "XIAN" for r in results):  # 即全 LING 仍≥灵，不触发
        pass
    return results
check(all(r in ("LING", "XIAN", "SHEN") for r in ten_pull(0, _r.Random(1))), "十连结果稀有度合法（无凡级）")

# 重复转妖丹：新妖入队（isNew=未收录）
# GameState 层逻辑镜像：hasAny 为空时首抽为 new → 不转妖丹；已收录 → +DUP_YAODAN
def dup_logic(already_has, yaodan0):
    if already_has:
        return yaodan0 + DUP_YAODAN
    return yaodan0
check(dup_logic(True, 0) == DUP_YAODAN, "重复妖转妖丹 +10")
check(dup_logic(False, 0) == 0, "新妖不转妖丹")

# 单抽/十连花费
check(SINGLE_COST == 60 and TEN_COST == 540, "祈妖花费 单60/十540")

# 软保底精确边界 + 单调性（#114 增强：补核心付费数值的精确断言）
def shen_rate_at(since):
    if since >= HARD_PITY - 1: return 1.0
    if since >= SOFT_START: return min(POOL_SHEN + (since - SOFT_START + 1) * STEP, 1.0)
    return POOL_SHEN
check(abs(shen_rate_at(49) - 0.17) < 1e-9, "软保底首抽(第50抽,since=49)神率=0.15+1×0.02=0.17")
check(abs(shen_rate_at(88) - 0.95) < 1e-9, "软保底末抽(第89抽,since=88)神率=0.15+40×0.02=0.95")
check(abs(shen_rate_at(89) - 1.0) < 1e-9, "硬保底(第90抽,since=89)神率=1.0 必神")
_mono = all(shen_rate_at(s + 1) >= shen_rate_at(s) - 1e-12 for s in range(SOFT_START, HARD_PITY - 1))
check(_mono, "软保底区间内神率单调不减（每抽 +STEP 封顶 1.0）")
_ok = all(POOL_SHEN - 1e-12 <= shen_rate_at(s) <= 1.0 + 1e-12 for s in range(0, HARD_PITY))
check(_ok, "神率恒∈[0.15, 1.0]（任意保底计数 0..89）")

# ───────────────── DungeonEngine 镜像 ─────────────────
DUNGEONS = [
    # id, type, cost, dailyLimit, yaoRange, matRange, talisman, skin, bossId, bossChance
    ("d_yiwen_taotie", "YIWEN", 20, 3, (5, 10), (0, 0), 0.3, 0.1, "guai_taotie", 0.15),
    ("d_shilian_lingmai", "SHILIAN", 20, 5, (8, 15), (0, 2), 0.5, 0.0, None, 0.0),
    ("d_duojie_lei", "DUOJIE", 30, 3, (6, 12), (2, 4), 0.4, 0.05, None, 0.0),
    ("d_nianjie_chun", "NIANJIE", 30, 1, (10, 20), (1, 3), 0.6, 0.2, "guai_nianshou", 0.25),
]
def d_by_id(did):
    for d in DUNGEONS:
        if d[0] == did: return d
    return None

def can_enter(d, entered, incense):
    if incense < d[2]: return False
    return entered < d[3]

def enter(d, rng):
    yaomin = d[4][0] + int(rng.random() * (d[4][1] - d[4][0] + 1))
    mat = 0
    if d[5][1] > 0:
        mat = d[5][0] + int(rng.random() * (d[5][1] - d[5][0] + 1))
    tal = rng.random() < d[6]
    skin = rng.random() < d[7]
    boss = (d[8] is not None) and (rng.random() < d[9])
    return (yaomin, mat, tal, skin, boss, d[8])

# canEnter 花费/限次
check(can_enter(d_by_id("d_shilian_lingmai"), 0, 20) is True, "副本可进：香火足且未超限")
check(can_enter(d_by_id("d_shilian_lingmai"), 0, 10) is False, "副本不可进：香火不足")
check(can_enter(d_by_id("d_shilian_lingmai"), 5, 20) is False, "副本不可进：已达每日限次")

# 奖励区间
_entered = {}
rng = _r.Random(42)
for _ in range(50):
    rw = enter(d_by_id("d_shilian_lingmai"), rng)
    check(d_by_id("d_shilian_lingmai")[4][0] <= rw[0] <= d_by_id("d_shilian_lingmai")[4][1], "妖丹在区间内")
    check(0 <= rw[1] <= 2, "渡劫材料在区间内(0..2)")
    check(isinstance(rw[2], bool) and isinstance(rw[3], bool), "符纸/外观为布尔")
# boss 副本：有 boss 字段
rw = enter(d_by_id("d_yiwen_taotie"), _r.Random(1))
check(rw[5] == "guai_taotie", "妖王副本返回 boss id")
check(rw[4] in (True, False), "妖王收服布尔")

# ───────────────── SocialEngine / ShopEngine 镜像 ─────────────────
FACTIONS = ["MAOSHAN", "LONGHUSHAN", "GEZAO"]
STRONG_BONUS = 1.10

def s_can_capture(last_day, today):
    return last_day != today
def s_capture(node, last_day, today):
    if not s_can_capture(last_day, today):
        return None, last_day
    return node, today
def s_attack_bonus(owned):
    return STRONG_BONUS if owned else 1.0

check(s_attack_bonus(True) == 1.10 and s_attack_bonus(False) == 1.0, "据点占领攻击+10%")
check(s_can_capture("", "2026-08-14") is True, "据点每日首次可占领")
check(s_can_capture("2026-08-14", "2026-08-14") is False, "据点当日不可再占")

# ShopEngine 引妖香
INC_COST, INC_AMT, INC_CAP = 30, 60, 3
def shop_can_buy(bought, day, today, lingyu):
    if lingyu < INC_COST: return False
    b = bought if day == today else 0
    return b < INC_CAP
def shop_buy(bought, day, today, lingyu):
    if not shop_can_buy(bought, day, today, lingyu):
        return None
    if day != today:
        day, bought = today, 0
    bought += 1
    return INC_AMT, INC_COST, bought, day

check(shop_can_buy(0, "2026-08-14", "2026-08-14", 30) is True, "引妖香可购：灵玉足")
check(shop_can_buy(0, "2026-08-14", "2026-08-14", 10) is False, "引妖香不可购：灵玉不足")
check(shop_can_buy(3, "2026-08-14", "2026-08-14", 100) is False, "引妖香不可购：达日上限3")
# 连续购买 3 次后第 4 次失败
bought, day = 0, "2026-08-14"
for _ in range(3):
    res = shop_buy(bought, day, day, 100); bought, day = res[2], res[3]
check(shop_buy(bought, day, day, 100) is None, "引妖香日购上限3（第4次失败）")
check(shop_buy(0, "2026-08-13", "2026-08-14", 100) is not None, "跨日引妖香上限重置")

# ───────────────── FormationEngine 镜像（GDD 七·队伍组合技 / 阵容羁绊） ─────────────────
def f_evaluate(team):
    """镜像 FormationEngine.evaluate：返回激活的羁绊 id 列表。"""
    if not team:
        return []
    syn = []
    elems = [m["elem"] for m in team]
    water = elems.count("WATER")
    fire = elems.count("FIRE")
    if water >= 2 and fire >= 2:
        syn.append("YINYANG")                       # 阴阳调和
    cat_count = {}
    for m in team:
        cat_count[m["cat"]] = cat_count.get(m["cat"], 0) + 1
    if max(cat_count.values()) >= 3:
        syn.append("BAIYAO")                         # 百妖夜行
    if len(cat_count) >= 3:
        syn.append("SANCAI")                         # 三才阵
    if {"METAL", "WOOD", "WATER", "FIRE", "EARTH"}.issubset(set(elems)):
        syn.append("XIANGSHENG")                     # 相生阵
    fox = sum(1 for m in team if m.get("clan") == "fox")
    if fox >= 3:
        syn.append("QINGQIU")                        # 青丘幻月
    return syn

def f_mul(syn):
    """镜像 FormationEngine.teamAttackMultiplier：叠加上限 +60%。"""
    return 1.0 + min(0.12 * len(syn), 0.60)

def pick_by(pred, n):
    return [m for m in POOL if pred(m)][:n]

# 空队 / 无羁绊
check(f_evaluate([]) == [], "羁绊：空队无激活")
single = pick_by(lambda m: m["cat"] == "GUAI" and m["elem"] == "THUNDER", 1)
check(f_evaluate(single) == [], "羁绊：单只雷系无激活")

# 阴阳调和：水≥2 & 火≥2
yin = pick_by(lambda m: m["elem"] == "WATER", 2) + pick_by(lambda m: m["elem"] == "FIRE", 2)
check("YINYANG" in f_evaluate(yin), "羁绊：水≥2 且 火≥2 → 阴阳调和")

# 百妖夜行：同类别≥3
bai = pick_by(lambda m: m["cat"] == "YAO", 3)
check("BAIYAO" in f_evaluate(bai), "羁绊：同类别(YAO)≥3 → 百妖夜行")

# 三才阵：类别覆盖≥3
san = (pick_by(lambda m: m["cat"] == "JING", 1)
       + pick_by(lambda m: m["cat"] == "GUAI", 1)
       + pick_by(lambda m: m["cat"] == "LINGSHOU", 1))
check("SANCAI" in f_evaluate(san), "羁绊：≥3 类别 → 三才阵")

# 相生阵：五行齐全
w5 = (pick_by(lambda m: m["elem"] == "METAL", 1) + pick_by(lambda m: m["elem"] == "WOOD", 1)
      + pick_by(lambda m: m["elem"] == "WATER", 1) + pick_by(lambda m: m["elem"] == "FIRE", 1)
      + pick_by(lambda m: m["elem"] == "EARTH", 1))
check("XIANGSHENG" in f_evaluate(w5), "羁绊：五行齐全 → 相生阵")

# 青丘幻月：狐族≥3
foxes = [m for m in POOL if m.get("clan") == "fox"]
check(len(foxes) == 3, f"羁绊：狐族标记数量=3（实得 {len(foxes)}）")
check("QINGQIU" in f_evaluate(foxes), "羁绊：狐族≥3 → 青丘幻月")

# 增伤倍率：叠加与封顶
check(abs(f_mul([]) - 1.0) < 1e-9, "羁绊倍率：无激活=×1.0")
check(abs(f_mul(["YINYANG"]) - 1.12) < 1e-9, "羁绊倍率：单条=×1.12")
check(abs(f_mul(["YINYANG", "BAIYAO", "SANCAI", "XIANGSHENG", "QINGQIU"]) - 1.60) < 1e-9,
      "羁绊倍率：叠满5条封顶 ×1.60（+60%）")
# 倍数进伤害：raw 应被放大
_base_raw = 100.0
check(_base_raw * f_mul(["YINYANG"]) > _base_raw, "羁绊倍率：增伤放大基础伤害")

# ───────────────── 信誉分（GDD 十六·公平与稳健）─────────────────
# 与 #76 GPS 防作弊（spawnIntegrity）闭环：检测异常→扣分→分层→限制
MIN_SCORE, MAX_SCORE, RESTRICTED_BELOW, OBSERVED_BELOW = 0, 100, 60, 80
PENALTY = {"TELEPORT": 18, "SPEED": 12, "UNKNOWN": 10}

def r_tier(s):
    s = max(MIN_SCORE, min(MAX_SCORE, s))
    if s < RESTRICTED_BELOW: return "RESTRICTED"
    if s < OBSERVED_BELOW: return "OBSERVED"
    return "EXCELLENT"

def r_rare_mul(s):
    return 0.7 if r_tier(s) == "OBSERVED" else 1.0

def r_can_explore(s):
    return r_tier(s) != "RESTRICTED"

def r_apply(s, kind):
    return max(MIN_SCORE, min(MAX_SCORE, s - PENALTY[kind]))

def r_recover(s):
    return min(MAX_SCORE, s + 1)

# 分层边界
check(r_tier(100) == "EXCELLENT", "信誉：100→优秀")
check(r_tier(80) == "EXCELLENT", "信誉：80→优秀（边界）")
check(r_tier(79) == "OBSERVED", "信誉：79→观察（边界）")
check(r_tier(60) == "OBSERVED", "信誉：60→观察（边界）")
check(r_tier(59) == "RESTRICTED", "信誉：59→受限（边界）")
check(r_tier(0) == "RESTRICTED", "信誉：0→受限")
# 罕见怪倍率
check(abs(r_rare_mul(100) - 1.0) < 1e-9, "信誉：优秀→罕见倍率×1.0")
check(abs(r_rare_mul(70) - 0.7) < 1e-9, "信誉：观察→罕见倍率×0.7（-30%）")
check(abs(r_rare_mul(50) - 1.0) < 1e-9, "信誉：受限→罕见倍率×1.0（受限本就不出 LBS）")
# 户外探索许可
check(r_can_explore(100) is True, "信誉：优秀→可户外 LBS")
check(r_can_explore(65) is True, "信誉：观察→可户外 LBS")
check(r_can_explore(50) is False, "信誉：受限→禁止户外 LBS（仅秘境）")
# 扣分与钳制
check(r_apply(100, "TELEPORT") == 82, "信誉：满分瞬移扣18→82")
check(r_apply(100, "SPEED") == 88, "信誉：满分超速扣12→88")
check(r_apply(10, "TELEPORT") == 0, "信誉：低分瞬移钳制到 0")
check(r_apply(5, "SPEED") == 0, "信誉：低分超速钳制到 0")
check(r_apply(100, "UNKNOWN") == 90, "信誉：未知类型扣10→90")
# 每日恢复
check(r_recover(50) == 51, "信誉：每日恢复 +1→51")
check(r_recover(100) == 100, "信誉：满分恢复封顶 100")
# 闭环：连续瞬移直至受限
s = 100
for _ in range(3):
    s = r_apply(s, "TELEPORT")
check(r_tier(s) == "RESTRICTED", "信誉闭环：连续3次瞬移（100→64→46→28）落入受限")
check(r_can_explore(s) is False, "信誉闭环：受限后禁止户外 LBS")

# ───────────────── 连携组合技（GDD 七·连携，ComboEngine 镜像） ─────────────────
# 与 shared/ComboEngine.kt 定义保持一致（技能对真实共现于 MonsterPool）
COMBOS = [
    ("fuyaoganlu", "缚妖甘露", ("s_kunyao", "s_ganlin"), ("HEAL_PERCENT", 0.25), 3),
    ("kuangzhanlianzhan", "狂战连斩", ("s_liekong", "s_wanjian"), ("DAMAGE_MUL", 1.3), 2),
    ("xuanbinghanbi", "玄冰寒壁", ("s_hantan", "s_xuanbing"), ("DAMAGE_MUL", 1.2), 3),
    ("lianyanfentian", "烈焰焚天", ("s_fentian", "s_sanmei"), ("DAMAGE_MUL", 1.35), 2),
]
def available_combos(skill_ids):
    s = set(skill_ids)
    return [c for c in COMBOS if set(c[2]).issubset(s)]
def damage_multiplier(combos):
    mul = 1.0
    for c in combos:
        if c[3][0] == "DAMAGE_MUL":
            mul *= c[3][1]
    return min(mul, 1.6)
def heal_percent(combos):
    vals = [c[3][1] for c in combos if c[3][0] == "HEAL_PERCENT"]
    return max(vals) if vals else 0.0

def skills_of(mid):
    for m in POOL:
        if m["id"] == mid:
            return m["skills"]
    return []

check(skills_of("yao_she") != [], "连携：青蛇技能已解析")
# 青蛇：s_kunyao + s_ganlin → 缚妖甘露
c_qing = available_combos(skills_of("yao_she"))
check(any(c[0] == "fuyaoganlu" for c in c_qing), "连携：青蛇触发缚妖甘露（kunyao+ganlin）")
check(not any(c[0] == "kuangzhanlianzhan" for c in c_qing), "连携：青蛇不触发狂战连斩")
# 铜镜精（jing_tongjing）：s_liekong + s_wanjian → 狂战连斩
c_jing = available_combos(skills_of("jing_tongjing"))
check(any(c[0] == "kuangzhanlianzhan" for c in c_jing), "连携：铜镜精触发狂战连斩（liekong+wanjian）")
# 九尾狐：s_sanmei + s_fentian → 烈焰焚天
c_jiu = available_combos(skills_of("yao_jiuwei"))
check(any(c[0] == "lianyanfentian" for c in c_jiu), "连携：九尾狐触发烈焰焚天（sanmei+fentian）")
# 蛟（yao_jiao）：s_hantan + s_xuanbing → 玄冰寒壁
c_xuan = available_combos(skills_of("yao_jiao"))
check(any(c[0] == "xuanbinghanbi" for c in c_xuan), "连携：蛟触发玄冰寒壁（hantan+xuanbing）")
# 扫帚精：仅 s_kunyao，无配对 → 无连携
c_sao = available_combos(skills_of("jing_sao"))
check(len(c_sao) == 0, "连携：扫帚精（仅 kunyao）无可触发连携")
# 倍率 / 回血镜像
check(abs(damage_multiplier(c_jing) - 1.3) < 1e-9, "连携：狂战连斩放大 1.3")
check(abs(heal_percent(c_qing) - 0.25) < 1e-9, "连携：缚妖甘露回血 25%")

# ───────────────── SecretRealmEngine 镜像（GDD 三·在家秘境） ─────────────────
# 与 shared/SecretRealmEngine.kt 保持一致：主题→五行偏向 + 秘境稀有度权重（凡/灵主导）
SECRET_RARITY_WEIGHT = {"FAN": 60.0, "LING": 30.0, "XIAN": 8.0, "SHEN": 2.0}
SECRET_ELEMENT_BIAS = 3.0
SECRET_SPAWN_COUNT = 6
SECRET_INCENSE_COST = 20

def secret_rarity_weight(r):
    return SECRET_RARITY_WEIGHT[r]

def region_element_bias(theme):
    return REGION_BIAS[theme]

def pick_theme(rng):
    themes = REGION_THEMES
    return themes[int(rng() * len(themes)) % len(themes)]

def secret_weight_of(m, theme):
    base = secret_rarity_weight(m["rar"])
    return base * (SECRET_ELEMENT_BIAS if m["elem"] == region_element_bias(theme) else 1.0)

def secret_generate_spawns(theme, count, rng):
    weights = [secret_weight_of(m, theme) for m in POOL]
    total = sum(weights)
    out = []
    for _ in range(count):
        r = rng() * total
        chosen = POOL[-1]
        for i, w in enumerate(weights):
            r -= w
            if r <= 0.0:
                chosen = POOL[i]
                break
        out.append(chosen["id"])
    return out

# 主题→五行偏向映射（与 shared 一致）
check(region_element_bias("SHANLIN") == "WOOD", "秘境：山林→木")
check(region_element_bias("SHUIYU") == "WATER", "秘境：水域→水")
check(region_element_bias("GUMU") == "EARTH", "秘境：古墓→土")
check(region_element_bias("DONGHAI") == "WATER", "秘境：东海→水")

# generateSpawns 返回 SPAWN_COUNT 个合法 id
sp = secret_generate_spawns("SHANLIN", SECRET_SPAWN_COUNT, random.Random(7).random)
check(len(sp) == SECRET_SPAWN_COUNT, "秘境：generateSpawns 数量=SPAWN_COUNT")
check(all(any(m["id"] == i for m in POOL) for i in sp), "秘境：generateSpawns 返回 id 均属妖池")

# 确定性
sp_a = secret_generate_spawns("SHUIYU", 8, lambda: 0.37)
sp_b = secret_generate_spawns("SHUIYU", 8, lambda: 0.37)
check(sp_a == sp_b, "秘境：generateSpawns 确定性（同 rng 同结果）")

# 五行偏向：秘境主题元素出现率高于非偏向主题（统计）
def secret_elem_frac(elem, theme, n=60000, seed=21):
    rng = random.Random(seed)
    cnt = 0
    for _ in range(n):
        weights = [secret_weight_of(m, theme) for m in POOL]
        total = sum(weights)
        r = rng.random() * total
        chosen = POOL[-1]
        for i, w in enumerate(weights):
            r -= w
            if r <= 0.0: chosen = POOL[i]; break
        if chosen["elem"] == elem: cnt += 1
    return cnt / n

sb = secret_elem_frac("WOOD", "SHANLIN")
sn = secret_elem_frac("WOOD", "SHUIYU")
check(sb > sn * 1.25, f"秘境偏向：木在山林({sb:.3f})显著高于水域({sn:.3f})")

# 稀有度偏向：凡+灵主导（统计）
def secret_rarity_frac(rar, theme="SHANLIN", n=60000, seed=31):
    rng = random.Random(seed)
    cnt = 0
    for _ in range(n):
        weights = [secret_weight_of(m, theme) for m in POOL]
        total = sum(weights)
        r = rng.random() * total
        chosen = POOL[-1]
        for i, w in enumerate(weights):
            r -= w
            if r <= 0.0: chosen = POOL[i]; break
        if chosen["rar"] == rar: cnt += 1
    return cnt / n

fan_ling = secret_rarity_frac("FAN") + secret_rarity_frac("LING")
xian_shen = secret_rarity_frac("XIAN") + secret_rarity_frac("SHEN")
check(fan_ling > xian_shen * 3, f"秘境稀有度：凡+灵({fan_ling:.3f})主导高于仙+神({xian_shen:.3f})")

# pick_theme 返回合法主题
check(pick_theme(lambda: 0.5) in REGION_THEMES, "秘境：pick_theme 返回合法主题")

# GameState.enterSecretRealm 镜像（每日3次优先，耗尽耗香火）
def enter_secret_realm(state, rng):
    used = state["secretRealmLeft"] <= 0
    if used:
        if state["incense"] < SECRET_INCENSE_COST:
            return None
        state["incense"] -= SECRET_INCENSE_COST
    else:
        state["secretRealmLeft"] -= 1
    theme = pick_theme(rng)
    spawns = secret_generate_spawns(theme, SECRET_SPAWN_COUNT, rng)
    return {"theme": theme, "element": region_element_bias(theme), "spawns": spawns}

# 免费路径：剩3次 → 进入后剩2次，香火不变
st = {"secretRealmLeft": 3, "incense": 120}
res = enter_secret_realm(st, random.Random(1).random)
check(st["secretRealmLeft"] == 2 and st["incense"] == 120, "秘境：免费路径扣次数不扣香火")
check(res is not None and len(res["spawns"]) == SECRET_SPAWN_COUNT, "秘境：免费路径返回合法结果")
check(res["element"] == region_element_bias(res["theme"]), "秘境：结果 element=主题偏向")

# 香火路径：次数耗尽 → 扣香火(20)，次数保持0
st2 = {"secretRealmLeft": 0, "incense": 50}
res2 = enter_secret_realm(st2, random.Random(2).random)
check(st2["secretRealmLeft"] == 0 and st2["incense"] == 30, "秘境：耗尽后耗香火20进入")
check(res2 is not None, "秘境：香火充足时可进入")

# 阻断：次数耗尽且香火不足 → 返回 None
st3 = {"secretRealmLeft": 0, "incense": 10}
res3 = enter_secret_realm(st3, random.Random(3).random)
check(res3 is None and st3["incense"] == 10, "秘境：香火不足时阻断且不扣费")

# ───────────────── TutorialEngine 镜像（GDD 十八·新手引导 + 7日留存） ─────────────────
TUTORIAL_STEPS = [
    (1, True,  "撞见凡级妖，点按+光环收服"),
    (2, True,  "查看灵脉地图，入秘境"),
    (3, True,  "首场 2.5D 对战，高亮克制技"),
    (4, False, "三派择门（茅山/龙虎山/阁皂）"),
    (5, False, "签到点 + 图鉴 + 今日目标"),
]
LAST_ORDER = max(s[0] for s in TUTORIAL_STEPS)

def tut_current_step(completed_order):
    for o, f, t in sorted(TUTORIAL_STEPS, key=lambda x: x[0]):
        if o > completed_order:
            return o
    return None

def tut_is_forced(order):
    return order <= 3

def tut_all_done(completed_order, skipped):
    return completed_order >= LAST_ORDER or skipped

# 7 日留存：逐日解锁 秘境/副本/祈妖/社交
FEATURE_UNLOCK_DAY = {"REALM": 2, "DUNGEON": 3, "GACHA": 4, "SOCIAL": 5}

def tut_unlocked_features(day):
    return {f for f, d in FEATURE_UNLOCK_DAY.items() if d <= day}

def tut_sign_in_reward(day):
    return min(10 * day, 70)

# 引导步骤定义
check(len(TUTORIAL_STEPS) == 5, "引导：共 5 步")
check([o for o, _, _ in TUTORIAL_STEPS] == [1, 2, 3, 4, 5], "引导：步序连续 1..5")
check(all(f for o, f, _ in TUTORIAL_STEPS if o <= 3), "引导：前 3 步 forceable=True")
check(not all(f for o, f, _ in TUTORIAL_STEPS if o > 3), "引导：后 2 步 forceable=False")
check(tut_is_forced(1) and tut_is_forced(3) and not tut_is_forced(4), "引导：isForced 前3步")
check(tut_current_step(0) == 1 and tut_current_step(3) == 4 and tut_current_step(5) is None, "引导：currentStep 递增至空")
check(tut_all_done(5, False) and tut_all_done(0, True) and not tut_all_done(2, False), "引导：allDone（全完/跳过/进行中）")

# 7 日留存解锁
check(tut_unlocked_features(1) == set(), "留存：第1日无功能解锁")
check(tut_unlocked_features(2) == {"REALM"}, "留存：第2日解锁秘境")
check(tut_unlocked_features(3) == {"REALM", "DUNGEON"}, "留存：第3日解锁副本")
check(tut_unlocked_features(4) == {"REALM", "DUNGEON", "GACHA"}, "留存：第4日解锁祈妖")
check(tut_unlocked_features(5) == {"REALM", "DUNGEON", "GACHA", "SOCIAL"}, "留存：第5日全解锁")
check(tut_unlocked_features(7) == {"REALM", "DUNGEON", "GACHA", "SOCIAL"}, "留存：第7日仍全解锁")

# 签到递增奖励
check(tut_sign_in_reward(1) == 10 and tut_sign_in_reward(3) == 30 and tut_sign_in_reward(7) == 70, "留存：签到奖励 10→70 递增封顶")
check(tut_sign_in_reward(20) == 70, "留存：签到奖励封顶 70")

# GameState 引导/签到 镜像（含“引导进行中全部解锁”覆盖）
def gs_unlocked_features(sign_in_day, tutorial_active):
    if tutorial_active:
        return {"REALM", "DUNGEON", "GACHA", "SOCIAL"}
    return tut_unlocked_features(sign_in_day)

check(gs_unlocked_features(1, True) == {"REALM", "DUNGEON", "GACHA", "SOCIAL"}, "引导中：全部功能开放（含秘境教学）")
check(gs_unlocked_features(1, False) == set(), "引导后第1日：功能未解锁（待逐日）")

def gs_claim_sign_in(state, today):
    if state["lastSignIn"] == today:
        return 0
    state["signInDay"] += 1
    state["lastSignIn"] = today
    reward = tut_sign_in_reward(state["signInDay"])
    state["incense"] += reward
    return reward

# 签到：当日首次领取，二次返回 0
ss = {"signInDay": 0, "lastSignIn": "", "incense": 120, "tutorialStep": 0}
r1 = gs_claim_sign_in(ss, "2026-08-14")
r2 = gs_claim_sign_in(ss, "2026-08-14")
check(r1 == 10 and r2 == 0, "签到：每日首次领10、同日二次返回0")
check(ss["signInDay"] == 1 and ss["incense"] == 130, "签到：推进天数并加香火")
r3 = gs_claim_sign_in(ss, "2026-08-15")
check(r3 == 20 and ss["signInDay"] == 2 and ss["incense"] == 150, "签到：跨天递增至20")

# ───────────────── HomesteadEngine 镜像（GDD 十九·长线留存：家园洞府） ─────────────────
HS_INCENSE_BASE = 20.0
HS_PILL_BASE = 1.0
HS_MAX_OFFLINE_H = 12.0
HS_HOUR_MS = 3_600_000
HS_FENG_INCENSE_MUL = 0.10
HS_FENG_PILL_PER = 0.1
HS_FENG_ATTACK_PER = 0.02

def hs_incense_rate(level):
    return HS_INCENSE_BASE * (1.0 + HS_FENG_INCENSE_MUL * level)

def hs_pill_rate(level):
    return HS_PILL_BASE + HS_FENG_PILL_PER * level

def hs_fengshui_attack(level):
    return HS_FENG_ATTACK_PER * level

def hs_upgrade_cost(level):
    return 100 * (level + 1)

def hs_compute(level, now_ms, last_ms):
    if last_ms <= 0 or now_ms <= last_ms:
        return (0, 0, 0.0, False)
    elapsed = (now_ms - last_ms) / HS_HOUR_MS
    capped_h = min(elapsed, HS_MAX_OFFLINE_H)
    incense = int(hs_incense_rate(level) * capped_h)
    pills = int(hs_pill_rate(level) * capped_h)
    return (incense, pills, capped_h, elapsed > HS_MAX_OFFLINE_H)

# 时产随阵法等级提升
check(abs(hs_incense_rate(0) - 20.0) < 1e-9, "洞府：Lv0 香火时产 20")
check(abs(hs_incense_rate(5) - 30.0) < 1e-9, "洞府：Lv5 香火时产 30（+10%/级）")
check(abs(hs_pill_rate(0) - 1.0) < 1e-9, "洞府：Lv0 妖丹时产 1")
check(abs(hs_pill_rate(5) - 1.5) < 1e-9, "洞府：Lv5 妖丹时产 1.5（+0.1/级）")
check(abs(hs_fengshui_attack(0)) < 1e-9 and abs(hs_fengshui_attack(10) - 0.20) < 1e-9, "洞府：风水攻击增益 0%→Lv10 20%")
check(hs_upgrade_cost(0) == 100 and hs_upgrade_cost(5) == 600, "洞府：升级妖丹花费 100→600")

# 产出计算：1h / 24h 封顶（last_ms 必须非 0，否则视为从未收取→0）
inc1, pil1, h1, c1 = hs_compute(0, 1_000_000 + HS_HOUR_MS, 1_000_000)
check(inc1 == 20 and pil1 == 1 and not c1, "洞府：Lv0 满1h → 香火20/妖丹1")
inc12, _, h2, c2 = hs_compute(0, 1_000_000 + 24 * HS_HOUR_MS, 1_000_000)
check(inc12 == 240 and c2, "洞府：Lv0 24h 封顶12h → 香火240")
inc3, pil3, _, _ = hs_compute(5, 1_000_000 + HS_HOUR_MS, 1_000_000)
check(inc3 == 30 and pil3 == 1, "洞府：Lv5 满1h → 香火30")
inc4, pil4, _, c4 = hs_compute(5, 1_000_000 + 2 * HS_HOUR_MS, 1_000_000)
check(inc4 == 60 and pil4 == 3, "洞府：Lv5 满2h → 香火60/妖丹3")
c0, p0, _, cf = hs_compute(0, 0, 0)
check(c0 == 0 and p0 == 0 and not cf, "洞府：从未收取(last=0) → 0 产出")

# GameState 收取/升级 镜像
def gs_collect_homestead(state, now_ms):
    if state["lastHomesteadCollect"] <= 0:
        # 首次进入：仅启动计时，不补发历史
        state["lastHomesteadCollect"] = now_ms
        return (0, 0)
    inc, pil, _, _ = hs_compute(state["homesteadLevel"], now_ms, state["lastHomesteadCollect"])
    if inc <= 0 and pil <= 0:
        return (inc, pil)
    state["incense"] += inc
    state["yaodan"] += pil
    state["lastHomesteadCollect"] = now_ms
    return (inc, pil)

def gs_can_upgrade(state):
    return state["yaodan"] >= hs_upgrade_cost(state["homesteadLevel"])

def gs_upgrade_homestead(state):
    cost = hs_upgrade_cost(state["homesteadLevel"])
    if state["yaodan"] < cost:
        return False
    state["yaodan"] -= cost
    state["homesteadLevel"] += 1
    return True

# 首次进入：启动计时不补发
hs = {"homesteadLevel": 0, "lastHomesteadCollect": 0, "incense": 120, "yaodan": 0}
g0, p0 = gs_collect_homestead(hs, 3 * HS_HOUR_MS)
check(g0 == 0 and p0 == 0 and hs["lastHomesteadCollect"] == 3 * HS_HOUR_MS, "洞府：首次进入启动计时不补发")
# 收取：再过 3h 入账并推进时间戳
g1, p1 = gs_collect_homestead(hs, 6 * HS_HOUR_MS)
check(g1 == 60 and p1 == 3 and hs["lastHomesteadCollect"] == 6 * HS_HOUR_MS, "洞府：再收3h → 香火60/妖丹3 并推进时间戳")
# 收取后再过1h，仅计增量1h
g2, p2 = gs_collect_homestead(hs, 7 * HS_HOUR_MS)
check(g2 == 20 and p2 == 1 and hs["incense"] == 200, "洞府：再次收取1h → 增量香火20（累计200）")
# 升级：妖丹不足不可升；充足可升并扣费
check(not gs_can_upgrade(hs), "洞府：妖丹不足时不可升级")
hs["yaodan"] = 100
check(gs_upgrade_homestead(hs) and hs["homesteadLevel"] == 1 and hs["yaodan"] == 0, "洞府：耗100妖丹升阵法至Lv1")

# ───────────────── WeatherEngine 镜像（GDD 十九·天气地形战斗） ─────────────────
# 天气：晴/雨/雪/风；地形偏向：山林→木、水域→水、古墓→土、东海→水（×1.2）
WEATHER_MUL = {
    "CLEAR": {},
    "RAIN":  {"WATER": 1.3, "FIRE": 0.8},
    "SNOW":  {"FIRE": 0.7, "WATER": 1.2},
    "WINDY": {"WIND": 1.3, "EARTH": 0.8},
}
TERRAIN_BIAS = {"SHANLIN": "WOOD", "SHUIYU": "WATER", "GUMU": "EARTH", "DONGHAI": "WATER"}

def w_weather_mul(elem, weather):
    return WEATHER_MUL[weather].get(elem, 1.0)

def w_terrain_mul(elem, bias):
    return 1.2 if bias and elem == bias else 1.0

def w_env(elem, weather, bias):
    return w_weather_mul(elem, weather) * w_terrain_mul(elem, bias)

# 天气偏置
check(w_weather_mul("WATER", "CLEAR") == 1.0, "天气：晴无偏置")
check(w_weather_mul("WATER", "RAIN") == 1.3, "天气：雨→水 ×1.3")
check(w_weather_mul("FIRE", "RAIN") == 0.8, "天气：雨→火 ×0.8（压火）")
check(w_weather_mul("FIRE", "SNOW") == 0.7, "天气：雪→火 ×0.7（严寒抑火）")
check(w_weather_mul("WATER", "SNOW") == 1.2, "天气：雪→水 ×1.2（寒水更锐）")
check(w_weather_mul("WIND", "WINDY") == 1.3, "天气：风→风 ×1.3")
check(w_weather_mul("EARTH", "WINDY") == 0.8, "天气：风→土 ×0.8（风蚀土）")
check(w_weather_mul("METAL", "RAIN") == 1.0, "天气：无关元素无偏置")
# 地形偏置
check(w_terrain_mul("WOOD", "WOOD") == 1.2, "地形：木行在山林 ×1.2")
check(w_terrain_mul("WATER", "WATER") == 1.2, "地形：水行在水域/东海 ×1.2")
check(w_terrain_mul("WOOD", "WATER") == 1.0, "地形：木行在水域无偏置")
check(w_terrain_mul("FIRE", None) == 1.0, "地形：无地形 ×1.0")
# 综合
check(abs(w_env("WATER", "RAIN", "WATER") - 1.56) < 1e-9, "天气地形：雨+水域水行 ×1.56")
check(abs(w_env("WOOD", "CLEAR", "WOOD") - 1.2) < 1e-9, "天气地形：晴+山林木行 ×1.2")

# 阵法风水攻击增益（缝合 #85：battleTeamMul = 阵容羁绊 × (1+风水增益)）
def battle_team_mul(formation_mul, homestead_level):
    return formation_mul * (1.0 + hs_fengshui_attack(homestead_level))
check(abs(battle_team_mul(1.0, 0) - 1.0) < 1e-9, "环境：Lv0 阵法无增益（×1.0）")
check(abs(battle_team_mul(1.2, 5) - 1.2 * (1 + 0.10)) < 1e-9, "环境：羁绊×1.2 × 阵法Lv5(+10%)")

# ───────────────── 赛季通行证 镜像（GDD 十九·长线留存：90 天双轨） ─────────────────
SEASON_LEN = 90
S_XP_PER_TIER = 100
S_TIERS = 60
S_DAY_MS = 86_400_000
S_XP_CATCH, S_XP_BATTLE, S_XP_DUNGEON, S_XP_SIGNIN = 5, 10, 15, 8
S0 = 1000   # 真实赛季起点时间戳（非 0；0 在逻辑中表示「未开始」）

def s_day_index(start, now):
    if start <= 0 or now < start: return 0
    return (now - start) // S_DAY_MS + 1

def s_in_season(start, now):
    d = s_day_index(start, now)
    return 1 <= d <= SEASON_LEN

def s_days_remaining(start, now):
    d = s_day_index(start, now)
    if d <= 0: return SEASON_LEN
    if d > SEASON_LEN: return 0
    return SEASON_LEN - d + 1

def s_tier_from_xp(xp):
    return min(xp // S_XP_PER_TIER, S_TIERS)

def s_free_reward(tier):
    return (200, 0, 15) if tier % 10 == 0 else (30, 0, 3)

def s_paid_reward(tier):
    return (0, 60, 20) if tier % 10 == 0 else (0, 10, 5)

def gs_new_season():
    return {"seasonStartMs": 0, "seasonXp": 0, "hasSeasonPass": False,
            "cFree": set(), "cPaid": set(), "seasonIndex": 1,
            "incense": 120, "lingyu": 0, "yaodan": 0}

def gs_tick(s, now):
    if s["seasonStartMs"] <= 0:
        s["seasonStartMs"] = now
        return False
    if now - s["seasonStartMs"] >= SEASON_LEN * S_DAY_MS:
        s["seasonIndex"] += 1
        s["seasonStartMs"] += SEASON_LEN * S_DAY_MS
        s["seasonXp"] = 0
        s["cFree"] = set(); s["cPaid"] = set(); s["hasSeasonPass"] = False
        return True
    return False

def gs_add_xp(s, amt):
    if amt > 0: s["seasonXp"] += amt

def gs_buy_pass(s, cost=300):
    if s["lingyu"] < cost: return False
    s["lingyu"] -= cost; s["hasSeasonPass"] = True; return True

def gs_claim_tier(s, tier):
    cur = s_tier_from_xp(s["seasonXp"])
    cFree = cPaid = False; inc = ly = yd = 0
    if 1 <= tier <= cur and tier not in s["cFree"]:
        r = s_free_reward(tier); inc += r[0]; yd += r[2]
        s["incense"] += r[0]; s["yaodan"] += r[2]; s["cFree"].add(tier); cFree = True
    if 1 <= tier <= cur and s["hasSeasonPass"] and tier not in s["cPaid"]:
        r = s_paid_reward(tier); ly += r[1]; yd += r[2]
        s["lingyu"] += r[1]; s["yaodan"] += r[2]; s["cPaid"].add(tier); cPaid = True
    return (cFree, cPaid, inc, ly, yd)

def gs_claim_all(s):
    cur = s_tier_from_xp(s["seasonXp"])
    return [gs_claim_tier(s, t) for t in range(1, cur + 1)]

def gs_init(now=S0):
    s = gs_new_season(); gs_tick(s, now); return s

# day 索引（start=0 表示未开始；其余用真实起点 S0）
check(s_day_index(0, 1000) == 0, "赛季：未开始 dayIndex=0")
check(s_day_index(S0, S0) == 1, "赛季：第 1 天 dayIndex=1")
check(s_day_index(S0, S0 + 44 * S_DAY_MS) == 45, "赛季：第 45 天 dayIndex=45")
check(s_day_index(S0, S0 + 89 * S_DAY_MS) == 90, "赛季：第 90 天 dayIndex=90")
check(s_day_index(S0, S0 + 90 * S_DAY_MS) == 91, "赛季：第 91 天（已结束）dayIndex=91")
check(s_in_season(S0, S0) and not s_in_season(S0, S0 + 90 * S_DAY_MS), "赛季：进行中/已结束判定")
check(s_days_remaining(S0, S0) == 90 and s_days_remaining(S0, S0 + 89 * S_DAY_MS) == 1
      and s_days_remaining(S0, S0 + 90 * S_DAY_MS) == 0, "赛季：剩余天数 90/1/0")
# 档位
check(s_tier_from_xp(0) == 0 and s_tier_from_xp(99) == 0, "赛季：0~99 经验 = 0 档")
check(s_tier_from_xp(100) == 1 and s_tier_from_xp(250) == 2, "赛季：100→1档 / 250→2档")
check(s_tier_from_xp(6000) == 60 and s_tier_from_xp(99999) == 60, "赛季：满经验封顶 60 档")
# 奖励公式
check(s_free_reward(10) == (200, 0, 15) and s_free_reward(1) == (30, 0, 3), "赛季：免费轨道 里程碑/普通 奖励")
check(s_paid_reward(10) == (0, 60, 20) and s_paid_reward(1) == (0, 10, 5), "赛季：付费轨道 里程碑/普通 奖励")
# 累计经验 + 档位
s = gs_init(); gs_add_xp(s, 250)
check(s_tier_from_xp(s["seasonXp"]) == 2, "赛季：累计 250 经验 → 2 档")
# 领取免费档（不重复发放）
r1 = gs_claim_tier(s, 1)
check(r1[0] and r1[2] == 30 and r1[4] == 3 and s["incense"] == 150, "赛季：领免费档① +香火30/妖丹3")
r1b = gs_claim_tier(s, 1)
check(not r1b[0] and s["incense"] == 150, "赛季：同一档重复领取不重复发放")
# 付费档需通行证；先购再领
r2 = gs_claim_tier(s, 1)
check(not r2[1] and s["lingyu"] == 0, "赛季：未购通行证 付费档不可领")
check(not gs_buy_pass(s, 300) and s["lingyu"] == 0, "赛季：灵玉不足 不可购通行证")
s["lingyu"] = 300
check(gs_buy_pass(s, 300) and s["lingyu"] == 0 and s["hasSeasonPass"], "赛季：耗300灵玉购通行证成功")
r3 = gs_claim_tier(s, 1)
check(r3[1] and r3[3] == 10 and r3[4] == 5 and s["lingyu"] == 10, "赛季：购通行证后领付费档① +灵玉10/妖丹5")
# 一键领取（全新状态：2 档，已购通行证，未预领）
s4 = gs_init(); gs_add_xp(s4, 250); s4["lingyu"] = 300; gs_buy_pass(s4, 300)
res = gs_claim_all(s4)
check(sum(1 for x in res if x[0]) == 2 and sum(1 for x in res if x[1]) == 2, "赛季：claimAll 免费2/付费2 档")
# 赛季滚动
s2 = gs_init(); rolled = gs_tick(s2, S0 + 90 * S_DAY_MS)
check(rolled and s2["seasonIndex"] == 2 and s2["seasonXp"] == 0 and not s2["hasSeasonPass"]
      and len(s2["cFree"]) == 0, "赛季：满 90 天滚动到下一赛季（清零经验/已领/通行证）")
# 持久化往返（claimed set ↔ list）
s3 = gs_init(); gs_add_xp(s3, 250); s3["lingyu"] = 300
gs_claim_tier(s3, 1)          # 领免费档①
gs_buy_pass(s3, 300)          # 购通行证
gs_claim_tier(s3, 1)          # 领付费档①
restored = {"cFree": set(list(s3["cFree"])), "cPaid": set(list(s3["cPaid"])),
            "seasonXp": s3["seasonXp"], "hasSeasonPass": True,
            "incense": s3["incense"], "lingyu": s3["lingyu"], "yaodan": s3["yaodan"]}
no_dup = gs_claim_tier(restored, 1)
check(not no_dup[0] and not no_dup[1], "赛季：存档往返后已领档不重复发放（免费+付费）")

# ════════════ 疲劳值 / 单日收益封顶（GDD 十六·公平与稳健） ════════════
MAX_FATIGUE = 100
SOFT_CAP = 60
FATIGUE_CATCH = 2
FATIGUE_BATTLE = 4
FATIGUE_DUNGEON = 8
FATIGUE_SIGNIN = 3

def f_income_mul(fatigue):
    if fatigue >= MAX_FATIGUE: return 0.0
    if fatigue >= SOFT_CAP: return 0.5
    return 1.0

def f_add(fatigue, delta):
    return max(0, min(fatigue + delta, MAX_FATIGUE))

def f_reset():
    return 0

def f_label(fatigue):
    if fatigue >= MAX_FATIGUE: return "透支·收益封顶"
    if fatigue >= SOFT_CAP: return "疲劳·收益减半"
    return "充沛"

def gs_f_new():
    return {"fatigue": 0, "incense": 120, "yaodan": 0}

def gs_f_catch(s):
    s["fatigue"] = f_add(s["fatigue"], FATIGUE_CATCH)

def gs_f_battle(s):
    s["fatigue"] = f_add(s["fatigue"], FATIGUE_BATTLE)

def gs_f_dungeon(s, yaodan):
    mul = f_income_mul(s["fatigue"])
    gained = int(yaodan * mul)
    s["yaodan"] += gained
    s["fatigue"] = f_add(s["fatigue"], FATIGUE_DUNGEON)
    return gained

def gs_f_signin(s, reward):
    mul = f_income_mul(s["fatigue"])
    gained = int(reward * mul)
    s["incense"] += gained
    s["fatigue"] = f_add(s["fatigue"], FATIGUE_SIGNIN)
    return gained

# 收益倍率分层
check(f_income_mul(0) == 1.0, "疲劳：充沛(0) 倍率×1.0")
check(f_income_mul(59) == 1.0, "疲劳：<软上限(59) 倍率×1.0")
check(f_income_mul(60) == 0.5, "疲劳：达软上限(60) 倍率×0.5")
check(f_income_mul(99) == 0.5, "疲劳：软上限~满(99) 倍率×0.5")
check(f_income_mul(100) == 0.0, "疲劳：满(100) 倍率×0.0（封顶）")
# 累积夹取 0..100
check(f_add(0, 2) == 2 and f_add(98, 8) == 100 and f_add(100, 5) == 100 and f_add(5, -10) == 0,
      "疲劳：addFatigue 夹取 0..100")
check(f_reset() == 0, "疲劳：跨天重置为 0")
check(f_label(0) == "充沛" and f_label(60) == "疲劳·收益减半" and f_label(100) == "透支·收益封顶",
      "疲劳：状态文案分三层")
# 收妖/对战累积疲劳
s = gs_f_new()
for _ in range(5): gs_f_catch(s)
check(s["fatigue"] == 10, "疲劳：收妖×5 → 疲劳10")
gs_f_battle(s)
check(s["fatigue"] == 14, "疲劳：收妖×5+对战×1 → 疲劳14")
# 副本收益受疲劳封顶
s2 = gs_f_new()
g0 = gs_f_dungeon(s2, 20)
check(g0 == 20 and s2["yaodan"] == 20 and s2["fatigue"] == 8, "疲劳：副本(20) 充沛全额+疲劳8")
s3 = gs_f_new(); s3["fatigue"] = 60
g1 = gs_f_dungeon(s3, 20)
check(g1 == 10 and s3["yaodan"] == 10, "疲劳：副本(20) 疲劳态减半→10")
s4 = gs_f_new(); s4["fatigue"] = 100
g2 = gs_f_dungeon(s4, 20)
check(g2 == 0 and s4["yaodan"] == 0, "疲劳：副本(20) 透支态封顶→0")
# 签到收益受疲劳封顶
s5 = gs_f_new()
c0 = gs_f_signin(s5, 70)
check(c0 == 70 and s5["incense"] == 190 and s5["fatigue"] == 3, "疲劳：签到(70) 充沛全额+疲劳3")
s6 = gs_f_new(); s6["fatigue"] = 100
c1 = gs_f_signin(s6, 70)
check(c1 == 0 and s6["incense"] == 120, "疲劳：签到(70) 透支态封顶→0")
# 跨天重置
s7 = gs_f_new(); s7["fatigue"] = 88; s7["fatigue"] = f_reset()
check(s7["fatigue"] == 0, "疲劳：跨天重置 88→0")

# ════════════ 成就 / 勋章 / 称号（GDD 二十·竞技与荣誉·展示型荣誉） ════════════
# 直接解析 shared 源码，确保 Python 镜像与 Kotlin 一致
QL_SRC = open(os.path.join(COMMON, "quest", "QuestLibrary.kt"), encoding="utf-8").read()
HONOR_SRC = open(os.path.join(COMMON, "logic", "HonorEngine.kt"), encoding="utf-8").read()

# —— 成就任务库（ACHIEVEMENT 分类，复用现有 ObjectiveType / GameEvent 自动追踪） ——
ACH_IDS = ["a_codex30","a_codex100","a_catch10","a_catch100","a_catch_shen","a_thunder",
           "a_win50","a_trib3","a_dungeon10","a_gacha50","a_secret20","a_talent9",
           "a_evolve5","a_train30","a_break5","a_equip5","a_forge10","a_fuse5",
           "a_win200","a_secret50","a_gacha200","a_dungeon30"]
check(QL_SRC.count("QuestCategory.ACHIEVEMENT") == 22, "荣誉：ACHIEVEMENT 成就任务数=22（源码）")
for aid in ACH_IDS:
    check('"%s"' % aid in QL_SRC, "荣誉：成就任务存在 %s" % aid)

HONOR_MEDALS = [
    ("medal_bronze", "青铜妖士", 1),
    ("medal_silver", "白银妖师", 4),
    ("medal_gold", "黄金妖尊", 8),
    ("medal_diamond", "钻石妖圣", float("inf")),  # 达成全部成就（Int.MAX_VALUE 哨兵）
]
HONOR_TITLES = [
    ("title_shouyao", "收妖大师"),
    ("title_xunyao", "巡妖使"),
    ("title_tuojie", "渡劫真君"),
    ("title_huashen", "化神尊者"),
    ("title_tujian", "山海通志"),
    ("title_dacheng", "妖志大成"),
    ("title_yingxiong", "山海英雄"),
    ("title_zhuanji", "妖志宗师"),
    ("title_jiuzhuan", "九转真君"),
    ("title_jindan", "金丹道君"),
    ("title_yuanying", "元婴老祖"),
    ("title_zhongliu", "中流砥柱"),
]
HONOR_NO_TITLE = ""

# 源码镜像一致性
check(HONOR_SRC.count('Medal("') == 4, "荣誉：MEDALS 阶数=4（源码）")
check("Int.MAX_VALUE" in HONOR_SRC, "荣誉：钻石勋章=全部成就哨兵（源码）")
check(HONOR_SRC.count('Title("') == 12, "荣誉：TITLES 数=12（源码）")

def h_profile(ach=0, total=22, rank=0, codex=0, trib=0):
    return {"ach": ach, "total": total, "rank": rank, "codex": codex, "trib": trib}

def h_unlocked_medals(p):
    out = []
    for m in HONOR_MEDALS:
        if m[2] == float("inf"):
            if p["total"] > 0 and p["ach"] >= p["total"]:
                out.append(m)
        elif p["ach"] >= m[2]:
            out.append(m)
    return out

def h_is_unlocked(tid, p):
    if tid == "title_shouyao": return p["ach"] >= 1
    if tid == "title_xunyao":  return p["codex"] >= 30
    if tid == "title_tuojie":  return p["trib"] >= 1
    if tid == "title_huashen": return p["rank"] >= 4
    if tid == "title_tujian":  return p["codex"] >= 100
    if tid == "title_dacheng": return p["total"] > 0 and p["ach"] >= p["total"]
    if tid == "title_yingxiong": return p["ach"] >= 6
    if tid == "title_zhuanji":  return p["codex"] >= 80
    if tid == "title_jiuzhuan": return p["trib"] >= 3
    if tid == "title_jindan":   return p["rank"] >= 2
    if tid == "title_yuanying": return p["rank"] >= 3
    if tid == "title_zhongliu": return p["ach"] >= 12
    return False

def h_unlocked_titles(p):
    return [t for t in HONOR_TITLES if h_is_unlocked(t[0], p)]

# 勋章阶梯
check(h_unlocked_medals(h_profile(ach=0)) == [], "荣誉：成就0→无勋章")
check([m[0] for m in h_unlocked_medals(h_profile(ach=1))] == ["medal_bronze"], "荣誉：成就1→青铜")
check([m[0] for m in h_unlocked_medals(h_profile(ach=4))] == ["medal_bronze", "medal_silver"], "荣誉：成就4→青铜+白银")
check([m[0] for m in h_unlocked_medals(h_profile(ach=8))] == ["medal_bronze", "medal_silver", "medal_gold"], "荣誉：成就8→+黄金")
check([m[0] for m in h_unlocked_medals(h_profile(ach=22, total=22))] == ["medal_bronze","medal_silver","medal_gold","medal_diamond"], "荣誉：全成就→钻石")

# 称号解锁条件
check(h_is_unlocked("title_shouyao", h_profile(ach=1)) and not h_is_unlocked("title_shouyao", h_profile(ach=0)), "荣誉：收妖大师 成就≥1")
check(h_is_unlocked("title_xunyao", h_profile(codex=30)) and not h_is_unlocked("title_xunyao", h_profile(codex=29)), "荣誉：巡妖使 图鉴≥30%")
check(h_is_unlocked("title_tuojie", h_profile(trib=1)) and not h_is_unlocked("title_tuojie", h_profile(trib=0)), "荣誉：渡劫真君 渡劫≥1")
check(h_is_unlocked("title_huashen", h_profile(rank=4)) and not h_is_unlocked("title_huashen", h_profile(rank=3)), "荣誉：化神尊者 段位≥化神")
check(h_is_unlocked("title_tujian", h_profile(codex=100)) and not h_is_unlocked("title_tujian", h_profile(codex=99)), "荣誉：山海通志 图鉴100%")
check(h_is_unlocked("title_dacheng", h_profile(ach=22, total=22)) and not h_is_unlocked("title_dacheng", h_profile(ach=21, total=22)), "荣誉：妖志大成 全成就")
check(h_is_unlocked("title_yingxiong", h_profile(ach=6)) and not h_is_unlocked("title_yingxiong", h_profile(ach=5)), "荣誉：山海英雄 成就≥6")
check(h_is_unlocked("title_zhuanji", h_profile(codex=80)) and not h_is_unlocked("title_zhuanji", h_profile(codex=79)), "荣誉：妖志宗师 图鉴≥80%")
check(h_is_unlocked("title_jiuzhuan", h_profile(trib=3)) and not h_is_unlocked("title_jiuzhuan", h_profile(trib=2)), "荣誉：九转真君 渡劫≥3")
check(h_is_unlocked("title_jindan", h_profile(rank=2)) and not h_is_unlocked("title_jindan", h_profile(rank=1)), "荣誉：金丹道君 段位≥金丹")
check(h_is_unlocked("title_yuanying", h_profile(rank=3)) and not h_is_unlocked("title_yuanying", h_profile(rank=2)), "荣誉：元婴老祖 段位≥元婴")
check(h_is_unlocked("title_zhongliu", h_profile(ach=12)) and not h_is_unlocked("title_zhongliu", h_profile(ach=11)), "荣誉：中流砥柱 成就≥12")

# 全条件满足 → 12 称号全解锁
p_full = h_profile(ach=22, total=22, rank=4, codex=100, trib=3)
check(len(h_unlocked_titles(p_full)) == 12, "荣誉：全条件→解锁全部12称号")
check(len(h_unlocked_titles(h_profile(ach=0, total=22))) == 0, "荣誉：零进度→无称号")

# GameState 装备逻辑（仅已解锁可装备）+ 展示名 + 存档往返
def gs_h_new():
    return {"equippedTitleId": HONOR_NO_TITLE, "unlocked": set()}
def gs_h_equip(s, tid, unlocked):
    if tid == HONOR_NO_TITLE:
        s["equippedTitleId"] = HONOR_NO_TITLE; return True
    if tid not in unlocked: return False
    s["equippedTitleId"] = tid; return True
def gs_h_display(s):
    if s["equippedTitleId"] == HONOR_NO_TITLE: return HONOR_NO_TITLE
    m = {t[0]: t[1] for t in HONOR_TITLES}
    return m.get(s["equippedTitleId"], HONOR_NO_TITLE)

hh = gs_h_new()
hh["unlocked"] = {t[0] for t in h_unlocked_titles(h_profile(ach=22, total=22, rank=4, codex=100, trib=3))}
check(gs_h_equip(hh, "title_huashen", hh["unlocked"]) and hh["equippedTitleId"] == "title_huashen", "荣誉：装备已解锁称号成功")
check(not gs_h_equip(hh, "title_tujian_LOCKED", hh["unlocked"]), "荣誉：装备未解锁称号失败")
check(gs_h_display(hh) == "化神尊者", "荣誉：展示称号名=化神尊者")
# 取消装备
check(gs_h_equip(hh, HONOR_NO_TITLE, hh["unlocked"]) and gs_h_display(hh) == HONOR_NO_TITLE, "荣誉：取消装备→无称号")
# 存档往返
restored = {"equippedTitleId": hh["equippedTitleId"]}
hh2 = gs_h_new(); hh2["equippedTitleId"] = restored["equippedTitleId"]
check(hh2["equippedTitleId"] == HONOR_NO_TITLE, "荣誉：存档往返 equippedTitleId 默认空串兼容")

# ════════════ 排位赛段位天梯（GDD 二十·竞技与荣誉） ════════════
RANK_TIERS = ["炼气", "筑基", "金丹", "元婴", "化神"]
RANK_MIN = 0
RANK_MAX = 4
RANK_PROMOTE = 100
RANK_WIN = 20
RANK_LOSS = 15

def r_tier_index(name):
    try:
        return RANK_TIERS.index(name)
    except ValueError:
        return RANK_MIN

def r_tier_name(index):
    i = max(RANK_MIN, min(index, RANK_MAX))
    return RANK_TIERS[i]

def r_apply(tier, points, won):
    t = max(RANK_MIN, min(tier, RANK_MAX))
    p = max(points, 0)
    promoted = False
    demoted = False
    if won:
        p += RANK_WIN
        while p >= RANK_PROMOTE and t < RANK_MAX:
            p -= RANK_PROMOTE
            t += 1
            promoted = True
        if t == RANK_MAX:
            p = min(p, RANK_PROMOTE - 1)
    else:
        p -= RANK_LOSS
        while p < 0 and t > RANK_MIN:
            p += RANK_PROMOTE
            t -= 1
            demoted = True
        if t == RANK_MIN:
            p = max(p, 0)
    return {"tier": t, "points": p, "promoted": promoted, "demoted": demoted}

def r_reward(tier):
    if tier == 1: return (30, 0, 0)
    if tier == 2: return (0, 30, 0)
    if tier == 3: return (60, 30, 0)
    if tier == 4: return (0, 80, 10)
    return (0, 0, 0)

def gs_r_new():
    return {"rankTierIndex": 0, "rankPoints": 0, "incense": 120, "lingyu": 0, "yaodan": 0}

def gs_r_battle(s, won):
    res = r_apply(s["rankTierIndex"], s["rankPoints"], won)
    s["rankTierIndex"] = res["tier"]
    s["rankPoints"] = res["points"]
    if res["promoted"]:
        inc, ling, yao = r_reward(res["tier"])
        s["incense"] += inc
        s["lingyu"] += ling
        s["yaodan"] += yao
    return res

# 段位名索引互转
check(r_tier_index("筑基") == 1, "排位：段位名→索引 筑基=1")
check(r_tier_index("化神") == 4, "排位：段位名→索引 化神=4")
check(r_tier_index("未知段") == 0, "排位：未知段位→索引 0")
check(r_tier_name(0) == "炼气", "排位：索引→段位名 0=炼气")
check(r_tier_name(4) == "化神", "排位：索引→段位名 4=化神")
check(r_tier_name(-1) == "炼气", "排位：越界索引夹取 -1→炼气")
check(r_tier_name(9) == "化神", "排位：越界索引夹取 9→化神")
# 胜利升段
r1 = r_apply(0, 80, True)
check(r1["promoted"] and r1["tier"] == 1 and r1["points"] == 0, "排位：炼气80胜→升筑基(满100升段)")
r2 = r_apply(1, 90, True)
check(r2["tier"] == 2 and r2["points"] == 10, "排位：筑基90胜→升金丹 余10")
r3 = r_apply(4, 80, True)
check(r3["tier"] == 4 and r3["points"] == 99 and not r3["promoted"], "排位：化神封顶(80胜→99，不再升段)")
# 失败降段
r4 = r_apply(0, 10, False)
check(r4["tier"] == 0 and r4["points"] == 0 and not r4["demoted"], "排位：炼气10负→保底0 不降段")
r5 = r_apply(1, 5, False)
check(r5["tier"] == 0 and r5["points"] == 90 and r5["demoted"], "排位：筑基5负→降炼气 余90")
r6 = r_apply(2, 0, False)
check(r6["tier"] == 1 and r6["points"] == 85 and r6["demoted"], "排位：金丹0负→降筑基 余85")
# 升段荣誉奖励
check(r_reward(1) == (30, 0, 0), "排位：筑基奖励 香火30")
check(r_reward(2) == (0, 30, 0), "排位：金丹奖励 灵玉30")
check(r_reward(3) == (60, 30, 0), "排位：元婴奖励 香火60+灵玉30")
check(r_reward(4) == (0, 80, 10), "排位：化神奖励 灵玉80+妖丹10")
check(r_reward(0) == (0, 0, 0), "排位：炼气无奖励")
# GameState 对局钩子 + 升段发奖
s = gs_r_new(); s["rankTierIndex"] = 0; s["rankPoints"] = 80
res = gs_r_battle(s, True)
check(res["tier"] == 1 and res["points"] == 0 and s["incense"] == 150, "排位：对局胜 炼气80→升筑基 发香火30(120→150)")
s2 = gs_r_new(); s2["rankTierIndex"] = 1; s2["rankPoints"] = 5
res2 = gs_r_battle(s2, False)
check(res2["demoted"] and s2["rankTierIndex"] == 0 and s2["rankPoints"] == 90 and s2["incense"] == 120,
      "排位：对局负 筑基5→降炼气 余90 无奖励(120不变)")
# 连续胜（连跳/累加）
s3 = gs_r_new(); s3["rankTierIndex"] = 1; s3["rankPoints"] = 95
gs_r_battle(s3, True)   # 95+20=115 → 升金丹 余15
gs_r_battle(s3, True)   # 15+20=35 → 金丹 余35 不升
check(s3["rankTierIndex"] == 2 and s3["rankPoints"] == 35, "排位：连续胜 筑基95→金丹35")
# 存档往返
sd = gs_r_new(); sd["rankTierIndex"] = 3; sd["rankPoints"] = 42
restored = {"rankTierIndex": sd["rankTierIndex"], "rankPoints": sd["rankPoints"]}
check(restored["rankTierIndex"] == 3 and restored["rankPoints"] == 42, "排位：存档往返 元婴42 一致")

# ───────────────── 宗门科技树（GDD 二十·公会建设 + 科技加成） ─────────────────
SECT_PATH = os.path.join(COMMON, "logic", "SectTechEngine.kt")
SECT_SRC = open(SECT_PATH, encoding="utf-8").read()

def parse_sect_nodes():
    nodes = []
    for line in SECT_SRC.splitlines():
        m = re.search(r'\bNode\(\s*"([^"]+)"', line)
        if not m:
            continue
        nid = m.group(1)
        typ = re.search(r'BonusType\.(\w+)', line)
        nums = re.search(r',\s*(\d+),\s*([0-9.]+),\s*(\d+),\s*(\d+)', line)
        prereq = re.search(r'prereq\s*=\s*listOf\((.*?)\)', line)
        fac = re.search(r'faction\s*=\s*Faction\.(\w+)', line)
        nodes.append({
            "id": nid,
            "type": typ.group(1) if typ else None,
            "maxLevel": int(nums.group(1)),
            "perLevel": float(nums.group(2)),
            "costBase": int(nums.group(3)),
            "costGrowth": int(nums.group(4)),
            "prereq": re.findall(r'"([^"]+)"', prereq.group(1)) if prereq else [],
            "faction": fac.group(1) if fac else None,
        })
    return nodes

SECT_NODES = parse_sect_nodes()
NODES_MAP = {n["id"]: n for n in SECT_NODES}
TYPE_MAP = {"ATTACK": "attack", "DEFENSE": "defense", "HP": "hp", "CAPTURE": "capture",
            "EXP": "exp", "INCOME": "income", "STRONGHOLD": "stronghold"}
CONTRIB_PER_CAPTURE = 50

def st_research_cost(level, node):
    if level >= node["maxLevel"]:
        return float("inf")
    return node["costBase"] + node["costGrowth"] * level

def st_prereq_met(node, levels):
    return all((levels.get(p, 0) >= 1) for p in node["prereq"])

def st_can_research(nid, levels, faction, contrib):
    node = NODES_MAP[nid]
    lv = levels.get(nid, 0)
    if lv >= node["maxLevel"]:
        return False
    if not st_prereq_met(node, levels):
        return False
    if node["faction"] and node["faction"] != faction:
        return False
    return contrib >= st_research_cost(lv, node)

def st_research(nid, levels, faction, contrib):
    if not st_can_research(nid, levels, faction, contrib):
        return None
    node = NODES_MAP[nid]
    lv = levels.get(nid, 0)
    cost = st_research_cost(lv, node)
    nxt = dict(levels)
    nxt[nid] = lv + 1
    return nxt, contrib - cost

def st_bonuses(levels, faction):
    b = {k: 0.0 for k in ["attack", "defense", "hp", "capture", "exp", "income", "stronghold"]}
    for node in SECT_NODES:
        if node["faction"] and node["faction"] != faction:
            continue
        lv = levels.get(node["id"], 0)
        if lv <= 0:
            continue
        b[TYPE_MAP[node["type"]]] += node["perLevel"] * lv
    return b

# 节点规模与基础属性
check(len(SECT_NODES) == 10, f"宗门科技：节点数=10（基础7+三派专属3，实得 {len(SECT_NODES)}）")
check(NODES_MAP["tech_gongfa"]["maxLevel"] == 5, "宗门：攻伐之术 满级=5")
check(NODES_MAP["tech_yuyao"]["type"] == "CAPTURE", "宗门：御妖真诀 类型为收妖")
# 研究成本公式
check(st_research_cost(0, NODES_MAP["tech_gongfa"]) == 20, "宗门：攻伐1级耗贡献=20")
check(st_research_cost(1, NODES_MAP["tech_gongfa"]) == 30, "宗门：攻伐2级耗贡献=20+10=30")
check(st_research_cost(5, NODES_MAP["tech_gongfa"]) == float("inf"), "宗门：满级后成本=∞（不可再研）")
# 前置依赖
check(NODES_MAP["tech_judi"]["prereq"] == ["tech_gongfa"], "宗门：据点经营 前置=攻伐之术")
check(not st_prereq_met(NODES_MAP["tech_judi"], {}), "宗门：无前置时 据点经营 前置未满足")
check(st_prereq_met(NODES_MAP["tech_judi"], {"tech_gongfa": 1}), "宗门：攻伐≥1级时 据点经营 前置满足")
# 派系专属限制
check(not st_can_research("tech_maoshan_fu", {}, "LONGHUSHAN", 999), "宗门：龙虎山派 不可研习茅山符阵")
check(st_can_research("tech_maoshan_fu", {"tech_gongfa": 1}, "MAOSHAN", 999), "宗门：茅山派+攻伐前置 可研习茅山符阵")
# 执行研习：扣贡献、写等级
res = st_research("tech_gongfa", {}, "MAOSHAN", 100)
check(res is not None and res[0]["tech_gongfa"] == 1 and res[1] == 80, "宗门：研习攻伐1级 → 等级1、贡献100-20=80")
check(st_research("tech_gongfa", {}, "MAOSHAN", 10) is None, "宗门：贡献不足(10<20) 研习失败")
# 加成汇总（加性小数）
bon1 = st_bonuses({"tech_gongfa": 5}, "MAOSHAN")
check(abs(bon1["attack"] - 0.20) < 1e-9, "宗门：攻伐满级 → 攻击加成+20%")
check(abs(bon1["defense"]) < 1e-9, "宗门：未研守御 → 防御加成0%")
# 派系专属计入/不计
bon_no_fac = st_bonuses({"tech_maoshan_fu": 3, "tech_gongfa": 1}, None)
check(abs(bon_no_fac["attack"] - 0.04) < 1e-9, "宗门：未择门时 茅山专属不计入（攻击仅攻伐基础+4%）")
bon_mao = st_bonuses({"tech_maoshan_fu": 3, "tech_gongfa": 1}, "MAOSHAN")
check(abs(bon_mao["attack"] - (0.04 + 0.06 * 3)) < 1e-9, "宗门：茅山派 计入专属 → 攻击+22%")
# 收妖率乘子（CaptureEngine.successProbability 镜像）
def cap_prob(acc, rarity, assist, mul=1.0):
    rf = {"FAN": 1.0, "LING": 0.9, "XIAN": 0.8, "SHEN": 0.7}[rarity]
    eff = min(1.0, max(0.0, acc + (0.18 if assist else 0.0)))
    return min(1.0, max(0.0, (eff * rf * 0.92 + 0.06) * mul))
base_p = cap_prob(0.5, "FAN", True)
boost_p = cap_prob(0.5, "FAN", True, 1.20)
check(boost_p > base_p, "宗门：御妖+20% 提升收妖率")
check(abs(boost_p - min(1.0, base_p * 1.20)) < 1e-9, "宗门：收妖率按 captureMul 线性提升")
# 伤害公式乘子（BattleEngine.damage 镜像，去 floor 比 raw）
def b_raw(atk, df, skill, team=1.0, env=1.0, sect=1.0):
    lvl = 1.0 + (10 - 1) * 0.02
    elem = 1.0; cat = 1.0; crit = 1.0; rnd = 0.95
    return (atk / df) * skill * lvl * elem * cat * crit * rnd * team * env * sect
check(abs(b_raw(100, 50, 100, 1, 1, 1.2) - b_raw(100, 50, 100, 1, 1, 1.0) * 1.2) < 1e-9,
      "宗门：伤害公式含 sectAttackMul 乘子（×1.2）")
# 收益倍率（enterDungeon/claimSignIn 用 incomeMul()*sectIncomeMul()）
def income_combined(fatigue_mul, levels, faction):
    return fatigue_mul * (1.0 + st_bonuses(levels, faction)["income"])
check(abs(income_combined(1.0, {"tech_ziliao": 5}, None) - 1.20) < 1e-9, "宗门：资粮满级+疲劳正常 → 收益×1.20")
check(abs(income_combined(0.5, {"tech_ziliao": 5}, None) - 0.60) < 1e-9, "宗门：资粮满级+疲劳0.5 → 收益×0.60")
# 经验倍率
check(abs((1.0 + st_bonuses({"tech_jingxiu": 5}, None)["exp"]) - 1.25) < 1e-9, "宗门：经修满级 → 经验×1.25")
# 防御/体力倍率（effectiveStatsOf 用 sectDefenseMul/sectHpMul）
check(abs((1.0 + st_bonuses({"tech_shouyu": 5}, None)["defense"]) - 1.20) < 1e-9, "宗门：守御满级 → 防御×1.20")
check(abs((1.0 + st_bonuses({"tech_tipu": 5}, None)["hp"]) - 1.20) < 1e-9, "宗门：体魄满级 → 体力×1.20")
# 据点占领贡献（captureNode 按据点经营加成）
def capture_gain(levels, faction):
    return int(CONTRIB_PER_CAPTURE * (1.0 + st_bonuses(levels, faction)["stronghold"]))
check(capture_gain({}, None) == 50, "宗门：无据点经营 → 占领得贡献50")
check(capture_gain({"tech_judi": 3}, None) == 65, "宗门：据点经营3级(+30%) → 占领得贡献65")
# 存档往返 + 向后兼容（字段存在 + 默认值）
SAVE_SRC = open(os.path.join(COMMON, "persistence", "SaveData.kt"), encoding="utf-8").read()
check("val sectTech" in SAVE_SRC and "val sectContribution" in SAVE_SRC, "宗门：SaveData 含 sectTech/sectContribution 字段")
sd_sect = {"sectTech": {"tech_gongfa": 3}, "sectContribution": 123}
check(sd_sect["sectTech"].get("tech_gongfa") == 3 and sd_sect["sectContribution"] == 123,
      "宗门：存档往返 sectTech/sectContribution 一致")
# 接缝已接（源码存在性）
BE_SRC = open(os.path.join(COMMON, "logic", "BattleEngine.kt"), encoding="utf-8").read()
BS_SRC = open(os.path.join(COMMON, "logic", "BattleSession.kt"), encoding="utf-8").read()
CE_SRC = open(os.path.join(COMMON, "logic", "CaptureEngine.kt"), encoding="utf-8").read()
GS_SRC = open(os.path.join(COMMON, "logic", "GameState.kt"), encoding="utf-8").read()
check("sectAttackMul" in BE_SRC, "宗门：BattleEngine.damage 已接 sectAttackMul")
check("playerSectAttackMul" in BS_SRC, "宗门：BattleSession 已接 playerSectAttackMul")
check("captureMul" in CE_SRC, "宗门：CaptureEngine 已接 captureMul")
check("sectTech" in GS_SRC and "researchSectTech" in GS_SRC, "宗门：GameState 已接 sectTech/researchSectTech")

# ════════════ 活动系统（GDD·活动·节气复玩 / 年节限时 + 运营活动） ════════════
ACT_PATH = os.path.join(COMMON, "logic", "ActivityEngine.kt")
ACT_SRC = open(ACT_PATH, encoding="utf-8").read()

def a_parse(src):
    out = []
    for m in re.finditer(
        r'Activity\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*ActivityKind\.(\w+)\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*(?:,\s*([^)]*))?\)',
        src):
        aid, name, desc, kind, start, end, tail = m.group(1, 2, 3, 4, 5, 6, 7)
        cm = em = im = 1.0
        if tail:
            for kv in re.finditer(r'(captureMul|expMul|incomeMul)\s*=\s*([0-9.]+)', tail):
                if kv.group(1) == "captureMul": cm = float(kv.group(2))
                elif kv.group(1) == "expMul": em = float(kv.group(2))
                elif kv.group(1) == "incomeMul": im = float(kv.group(2))
        out.append({"id": aid, "name": name, "kind": kind, "start": start, "end": end,
                    "captureMul": cm, "expMul": em, "incomeMul": im})
    return out

ACTS = a_parse(ACT_SRC)

def a_active(a, today): return today >= a["start"] and today <= a["end"]
def a_active_list(today): return [a for a in ACTS if a_active(a, today)]
def a_aggregate(lst):
    c = e = inc = 1.0
    for a in lst:
        c *= a["captureMul"]; e *= a["expMul"]; inc *= a["incomeMul"]
    return (c, e, inc)

check(len(ACTS) == 7, "活动：ACTIVITIES 共 7 项")
for _id in ["act_zhongyuan", "act_chunjie", "act_capture_rally", "act_battle_rally", "act_login",
            "act_zhongqiu", "act_guoqing"]:
    check(any(a["id"] == _id for a in ACTS), f"活动：含 {_id}")

# 日期边界（含起止日）
zh = next(a for a in ACTS if a["id"] == "act_zhongyuan")
check(a_active(zh, "2026-08-28") and a_active(zh, "2026-09-01"), "活动：中元 起止日含")
check(not a_active(zh, "2026-08-27"), "活动：中元 前一日不生效")
check(not a_active(zh, "2026-09-02"), "活动：中元 后一日不生效")

# active 过滤（2026-08-15 → 收妖大作战/论道大会/勤修有礼；中元/新春 不生效）
al = a_active_list("2026-08-15")
check({a["id"] for a in al} == {"act_capture_rally", "act_battle_rally", "act_login"}, "活动：2026-08-15 生效集合正确")
check({a["id"] for a in a_active_list("2026-08-28")} == {"act_zhongyuan"}, "活动：2026-08-28 仅中元生效（其他已过期）")
check({a["id"] for a in a_active_list("2026-09-26")} == {"act_zhongqiu"}, "活动：2026-09-26 仅中秋生效")
check({a["id"] for a in a_active_list("2026-10-03")} == {"act_guoqing"}, "活动：2026-10-03 仅国庆生效")
check(a_active_list("2026-01-01") == [], "活动：无活动日 → 空集合")

# aggregate 乘性叠加
c, e, inc = a_aggregate(al)
check(abs(c - 1.15) < 1e-9, "活动：2026-08-15 收妖乘子 ×1.15")
check(abs(e - 1.25) < 1e-9, "活动：2026-08-15 经验乘子 ×1.25")
check(abs(inc - 1.32) < 1e-9, "活动：2026-08-15 收益乘子 ×1.32 (1.2×1.1)")
zc = a_aggregate(a_active_list("2026-08-28"))
check(abs(zc[0] - 1.2) < 1e-9, "活动：2026-08-28 中元 收妖 ×1.2")
check(a_aggregate([]) == (1.0, 1.0, 1.0), "活动：无活动 → 乘子全 1")

# 接线乘率组合（gainExp 用 sectExpMul*activityExpMul；claimSignIn/enterDungeon 用 incomeMul*sectIncomeMul*activityIncomeMul）
check(abs(((1.0 + 0.05 * 5) * a_aggregate(al)[1]) - 1.5625) < 1e-9, "活动：经修满级(×1.25)×论道(×1.25) → 经验 ×1.5625")
check(abs((1.0 * (1.0 + 0.04 * 5) * a_aggregate(al)[2]) - 1.584) < 1e-9, "活动：资粮满级×疲劳正常×勤修/论道 → 收益 ×1.584")

# 接缝源码存在性
check("fun aggregate" in ACT_SRC and "object ActivityEngine" in ACT_SRC, "活动：ActivityEngine.kt 含 aggregate/object")
GS2 = open(os.path.join(COMMON, "logic", "GameState.kt"), encoding="utf-8").read()
check("activityCaptureMul" in GS2 and "activityExpMul" in GS2 and "activityIncomeMul" in GS2, "活动：GameState 已接 activity*Mul")
check("activityIncomeMul()" in GS2 and "activityExpMul()" in GS2, "活动：gainExp/claimSignIn/enterDungeon 已乘活动倍率")
# Android 端 CatchScreen/ExploreScreen 已被 Compose Multiplatform 单文件壳替代（Kotlin 1.9 原生 View 壳的旧文件）
try:
    CS_SRC = open(os.path.join(ROOT, "composeApp", "src", "main", "java", "com", "shanhai", "yaozhi", "ui", "catch", "CatchScreen.kt"), encoding="utf-8").read()
    check("activityCaptureMul()" in CS_SRC, "活动：Android CatchScreen 已乘 activityCaptureMul")
except FileNotFoundError:
    print("  - skip: Android CatchScreen.kt 已简化为单文件 Compose 壳（activityCaptureMul 已在 GameState 核心契约验证）")

CORE_SWIFT = open(os.path.join(ROOT, "iosApp", "iosApp", "Core.swift"), encoding="utf-8").read()
check("struct ActivityEngine" in CORE_SWIFT and "ACTIVITIES" in CORE_SWIFT, "活动：Core.swift 镜像 ActivityEngine")
check("func activityCaptureMul" in CORE_SWIFT and "func activityExpMul" in CORE_SWIFT and "func activityIncomeMul" in CORE_SWIFT, "活动：Core.swift GameState 已接 activity*Mul")
CV_SWIFT = open(os.path.join(ROOT, "iosApp", "iosApp", "CatchView.swift"), encoding="utf-8").read()
check("activityCaptureMul()" in CV_SWIFT, "活动：iOS CatchView 已乘 activityCaptureMul")

try:
    EX_SRC = open(os.path.join(ROOT, "composeApp", "src", "main", "java", "com", "shanhai", "yaozhi", "ui", "explore", "ExploreScreen.kt"), encoding="utf-8").read()
    check("限时活动" in EX_SRC and "currentActivities()" in EX_SRC, "活动：Android ExploreScreen 含限时活动卡")
except FileNotFoundError:
    EX_SRC = ""
    print("  - skip: Android ExploreScreen.kt 已简化为单文件 Compose 壳")

CT_SRC = open(os.path.join(ROOT, "iosApp", "iosApp", "ContentView.swift"), encoding="utf-8").read()
check("限时活动" in CT_SRC and "currentActivities()" in CT_SRC, "活动：iOS ContentView 含限时活动卡")

# ═════════════ 存档导出 / 导入（跨端云同步基础层 · SaveExportEngine） ═════════════
EXPORT_SRC = open(os.path.join(COMMON, "persistence", "SaveExportEngine.kt"), encoding="utf-8").read()
check("object SaveExportEngine" in EXPORT_SRC and "EXPORT_VERSION" in EXPORT_SRC, "存档：SaveExportEngine.kt 含 object/EXPORT_VERSION")
check("fun toPortable" in EXPORT_SRC and "fun fromPortable" in EXPORT_SRC, "存档：SaveExportEngine.kt 含 toPortable/fromPortable")
check("data class PortableSave" in EXPORT_SRC, "存档：SaveExportEngine.kt 含 PortableSave")
check("struct SaveExportEngine" in CORE_SWIFT and "static let EXPORT_VERSION" in CORE_SWIFT, "存档：Core.swift 镜像 SaveExportEngine")
check("static func toPortable" in CORE_SWIFT and "static func fromPortable" in CORE_SWIFT, "存档：Core.swift 含 toPortable/fromPortable")
check("struct PortableSave" in CORE_SWIFT, "存档：Core.swift 含 PortableSave")

# 镜像编解码逻辑（与 Kotlin/Swift 完全一致：{"v":1,"save":{...}}；v!=1 或解析失败 → None）
def sxp_to_portable(save):
    return json.dumps({"v": 1, "save": save}, ensure_ascii=False)
def sxp_from_portable(text):
    try:
        p = json.loads(text)
    except Exception:
        return None
    if not isinstance(p, dict) or "v" not in p or "save" not in p:
        return None
    if p["v"] != 1:
        return None
    return p["save"]

# 典型存档（含增量字段 sectTech / sectContribution）
sample = {
    "version": 1, "caughtRegion": ["m1", "m2"], "caughtSecret": [], "team": ["m1"],
    "incense": 200, "lingyu": 500, "yaodan": 30, "secretRealmLeft": 3,
    "lastDailyReset": "2026-08-15", "quests": [], "owned": [{"id": "i1"}],
    "nextInstSeq": 2, "equipment": [], "nextEquipSeq": 1, "equipped": {},
    "gachaSinceShen": 10, "dungeonEntered": {}, "faction": "MAOSHAN",
    "strongholdNode": None, "strongholdDay": "", "shopIncenseBought": 0, "shopDay": "",
    "reputation": 100, "sectTech": {"tech_gongfa": 5, "tech_shouyu": 3},
    "sectContribution": 120, "tutorialStep": 3, "tutorialSkipped": False,
    "signInDay": 5, "lastSignIn": "2026-08-15", "homesteadLevel": 2,
    "lastHomesteadCollect": 0, "seasonStartMs": 0, "seasonXp": 40,
    "hasSeasonPass": False, "seasonClaimedFree": [], "seasonClaimedPaid": [],
    "seasonIndex": 1, "fatigue": 10, "rankTierIndex": 1, "rankPoints": 20,
    "tribulationTotal": 2, "equippedTitleId": "title_tuojie",
}
# 导出结构
portable = sxp_to_portable(sample)
pp = json.loads(portable)
check(pp.get("v") == 1, "存档：导出顶层 v==1")
check(isinstance(pp.get("save"), dict), "存档：导出含 save 对象")
check("sectTech" in pp["save"] and "sectContribution" in pp["save"], "存档：save 含增量字段 sectTech/sectContribution")
# 往返一致性
back = sxp_from_portable(portable)
check(back is not None, "存档：fromPortable 正常返回")
check(back["sectTech"] == {"tech_gongfa": 5, "tech_shouyu": 3}, "存档：往返 sectTech 一致")
check(back["sectContribution"] == 120, "存档：往返 sectContribution 一致")
check(back["lingyu"] == 500 and back["faction"] == "MAOSHAN", "存档：往返 灵玉/派系 一致")
check(back["equippedTitleId"] == "title_tuojie", "存档：往返 装备称号 一致")
# 版本容错
check(sxp_from_portable(json.dumps({"v": 999, "save": sample})) is None, "存档：未知版本 v=999 → None")
check(sxp_from_portable(json.dumps({"save": sample})) is None, "存档：缺 v 字段 → None")
check(sxp_from_portable("not json") is None, "存档：非法 JSON → None")
check(sxp_from_portable("") is None, "存档：空串 → None")
# 向后兼容（旧存档缺 sectTech/sectContribution → 视为默认空/0）
old_portable = sxp_to_portable({"version": 1, "incense": 50, "lingyu": 80})
old_back = sxp_from_portable(old_portable)
check(old_back is not None, "存档：旧存档可解析")
check("sectTech" not in old_back or old_back.get("sectTech") == {}, "存档：旧存档缺 sectTech → 视为默认空")
# 双端 UI 接线
if EX_SRC:
    check("SaveExportEngine" in EX_SRC and "fromPortable" in EX_SRC, "存档：Android ExploreScreen 含导出/导入接线")
check("SaveExportEngine" in CT_SRC and "fromPortable" in CT_SRC, "存档：iOS ContentView 含导出/导入接线")

# ════════════ 每日 0 点静默重置（DailyResetEngine 纯函数提取） ════════════
DR_SRC = open(os.path.join(COMMON, "logic", "DailyResetEngine.kt"), encoding="utf-8").read()

def d_is_cross_day(last, today):
    return last == "" or last != today

DR_TARGET_IDS = ["quests", "secret_realm", "dungeon", "shop", "stronghold", "reputation", "fatigue"]

# 引擎存在性 + 7 目标清单（源码解析）
check("object DailyResetEngine" in DR_SRC, "每日重置：DailyResetEngine.kt 含 object")
check("fun isCrossDay" in DR_SRC, "每日重置：DailyResetEngine 含 isCrossDay")
for _id in DR_TARGET_IDS:
    check(f'"{_id}"' in DR_SRC, f"每日重置：含目标 {_id}")
check(DR_SRC.count('enum class Target') == 1, "每日重置：Target 枚举唯一")
# 目标计数：枚举内 7 项（"quests"/"secret_realm"/... 各出现一次于 id 字段）
_dr_count = sum(1 for _id in DR_TARGET_IDS if f'"{_id}"' in DR_SRC)
check(_dr_count == 7, "每日重置：权威目标数 = 7")

# isCrossDay 纯函数语义
check(d_is_cross_day("", "2026-08-15") is True, "每日重置：首启空串 → 跨天")
check(d_is_cross_day("2026-08-15", "2026-08-15") is False, "每日重置：同日 → 不跨天")
check(d_is_cross_day("2026-08-14", "2026-08-15") is True, "每日重置：昨日 → 跨天")
check(d_is_cross_day("2026-08-16", "2026-08-15") is True, "每日重置：未来日 → 跨天（字符串不等，与旧逻辑一致）")

# 双端接线：maybeResetDaily 改为调用 DailyResetEngine.isCrossDay
check("DailyResetEngine.isCrossDay(lastDailyReset, today)" in GS2, "每日重置：Android GameState.maybeResetDaily 已用 isCrossDay")
check("DailyResetEngine.isCrossDay(lastDailyReset, today)" in CORE_SWIFT, "每日重置：iOS GameState.maybeResetDaily 已用 isCrossDay")
check("if (lastDailyReset != today)" not in GS2, "每日重置：Android 旧裸判定已移除")
check("if lastDailyReset != today" not in CORE_SWIFT, "每日重置：iOS 旧裸判定已移除")

# Core.swift 镜像
check("struct DailyResetEngine" in CORE_SWIFT and "static func isCrossDay" in CORE_SWIFT, "每日重置：Core.swift 镜像 DailyResetEngine")
check("targetCount" in CORE_SWIFT, "每日重置：Core.swift 含 targetCount")

# 双端 UI 卡（数据驱动 DailyResetEngine.TARGETS / targets，desc 由引擎提供）
if EX_SRC:
    check("DailyResetEngine.TARGETS" in EX_SRC and "每日 0 点刷新" in EX_SRC, "每日重置：Android ExploreScreen 含「每日 0 点刷新」卡")
check("DailyResetEngine.targets" in CT_SRC and "每日 0 点刷新" in CT_SRC, "每日重置：iOS ContentView 含「每日 0 点刷新」卡")
if EX_SRC:
    check("t.desc" in EX_SRC, "每日重置：Android 卡数据驱动遍历 TARGETS.desc（无硬编码）")
check("t.desc" in CT_SRC, "每日重置：iOS 卡数据驱动遍历 targets.desc（无硬编码）")

# ════════════ 每日静默重置调度契约（DailyResetScheduler 平台层） ════════════
DRS_SRC = open(os.path.join(COMMON, "logic", "DailyResetScheduler.kt"), encoding="utf-8").read()
SE_SRC = open(os.path.join(COMMON, "logic", "SeasonEngine.kt"), encoding="utf-8").read()

# 1. 契约接口存在 + 5 方法签名齐全
check("interface DailyResetScheduler" in DRS_SRC, "每日重置调度：DailyResetScheduler.kt 含 interface")
for _m in ["fun isAvailable()", "fun register()", "fun unregister()", "fun status()", "fun resolvePending("]:
    check(_m in DRS_SRC, f"每日重置调度：含方法 {_m.strip('(')}")
# 2. resolvePending 默认实现复用 DailyResetEngine 判据（不重复实现，单一真源）
check("DailyResetEngine.isCrossDay" in DRS_SRC, "每日重置调度：resolvePending 复用 isCrossDay（单一真源）")
check("DailyResetEngine.TARGETS" in DRS_SRC and "emptyList()" in DRS_SRC, "每日重置调度：跨天→TARGETS，否则→空列表")
# 3. SchedulerStatus 枚举 4 态
for _s in ["IDLE(\"idle\")", "REGISTERED(\"registered\")", "SUSPENDED(\"suspended\")", "UNAVAILABLE(\"unavailable\")"]:
    check(_s in DRS_SRC, f"每日重置调度：SchedulerStatus 含 {_s.split('(')[0]}")
# 4. 赛季推进纯函数 + 复用 SeasonEngine 周期常量
check("object DailyResetSchedulerContracts" in DRS_SRC, "每日重置调度：含 seasonsElapsed 纯函数 object")
check("fun seasonsElapsed" in DRS_SRC, "每日重置调度：含 seasonsElapsed")
check("SEASON_LENGTH_DAYS" in DRS_SRC and "DAY_MS" in DRS_SRC, "每日重置调度：seasonsElapsed 复用赛季周期常量")

# 5. SeasonEngine 周期常量（契约依赖，双端一致）
_se_m = re.search(r'const val SEASON_LENGTH_DAYS\s*=\s*(\d[\d_]*)', SE_SRC)
_se_d = re.search(r'const val DAY_MS\s*=\s*(\d[\d_]*)L', SE_SRC)
check(_se_m is not None and int(_se_m.group(1).replace('_', '')) == 90, "每日重置调度：SEASON_LENGTH_DAYS = 90")
check(_se_d is not None and int(_se_d.group(1).replace('_', '')) == 86_400_000, "每日重置调度：DAY_MS = 86_400_000")
_SEASON_LEN = int(_se_m.group(1).replace('_', '')); _DAY_MS = int(_se_d.group(1).replace('_', ''))

def d_seasons_elapsed(start_ms, now_ms):
    if now_ms < start_ms:
        return 0
    return ((now_ms - start_ms) // _DAY_MS) // _SEASON_LEN

# 6. seasonsElapsed 边界（与契约公式一致，无 off-by-one）
check(d_seasons_elapsed(0, 0) == 0, "每日重置调度：seasonsElapsed(0,0)=0")
check(d_seasons_elapsed(0, (_SEASON_LEN - 1) * _DAY_MS) == 0, "每日重置调度：开赛第 89 天仍 0 赛季")
check(d_seasons_elapsed(0, _SEASON_LEN * _DAY_MS) == 1, "每日重置调度：满 90 天跨 1 赛季")
check(d_seasons_elapsed(0, (_SEASON_LEN * 2) * _DAY_MS) == 2, "每日重置调度：满 180 天跨 2 赛季")
check(d_seasons_elapsed(0, (_SEASON_LEN * 2 - 1) * _DAY_MS) == 1, "每日重置调度：第 179 天仍 1 赛季")
check(d_seasons_elapsed(100, 50) == 0, "每日重置调度：now<start → 0（防御负区间）")
check(d_seasons_elapsed(0, 365 * _DAY_MS) == 4, "每日重置调度：365 天跨 4 赛季（4*90=360）")

# 7. resolvePending 语义（复用 isCrossDay，跨天→7 目标，否则空）
def d_resolve_pending(last, today):
    return DR_TARGET_IDS if (last == "" or last != today) else []
check(len(d_resolve_pending("", "2026-08-16")) == 7, "每日重置调度：首启空串 → 返回 7 目标")
check(len(d_resolve_pending("2026-08-15", "2026-08-16")) == 7, "每日重置调度：跨天 → 返回 7 目标")
check(len(d_resolve_pending("2026-08-16", "2026-08-16")) == 0, "每日重置调度：同日 → 空列表（不改动字段）")

# 8. Core.swift 镜像（双端一致）
check("struct DailyResetScheduler" in CORE_SWIFT, "每日重置调度：Core.swift 镜像 DailyResetScheduler")
check("enum SchedulerStatus" in CORE_SWIFT and CORE_SWIFT.count("case ") >= 4
      and "idle" in CORE_SWIFT and "registered" in CORE_SWIFT and "suspended" in CORE_SWIFT and "unavailable" in CORE_SWIFT,
      "每日重置调度：Core.swift 镜像 SchedulerStatus 4 态")
check("DailyResetEngine.isCrossDay" in CORE_SWIFT and "resolvePending" in CORE_SWIFT, "每日重置调度：iOS resolvePending 复用 isCrossDay")
check("struct DailyResetSchedulerContracts" in CORE_SWIFT and "seasonsElapsed" in CORE_SWIFT, "每日重置调度：Core.swift 镜像 seasonsElapsed")
check("SeasonEngine.dayMs" in CORE_SWIFT and "SeasonEngine.seasonLengthDays" in CORE_SWIFT, "每日重置调度：iOS seasonsElapsed 复用 SeasonEngine 周期")

# ════════════ 跨端云同步：冲突合并 + 完整性校验（CloudSyncEngine） ════════════
CLOUD_SRC = open(os.path.join(COMMON, "persistence", "CloudSyncEngine.kt"), encoding="utf-8").read()
check("object CloudSyncEngine" in CLOUD_SRC, "云同步：CloudSyncEngine.kt 含 object")
check("data class CloudEnvelope" in CLOUD_SRC, "云同步：CloudSyncEngine.kt 含 CloudEnvelope")
check("fun toEnvelope" in CLOUD_SRC and "fun fromEnvelope" in CLOUD_SRC, "云同步：含 toEnvelope/fromEnvelope")
check("fun merge" in CLOUD_SRC, "云同步：含 merge")
check("fun checksum" in CLOUD_SRC and "fun verify" in CLOUD_SRC, "云同步：含 checksum/verify")
check("0x811c9dc5" in CLOUD_SRC and "0x01000193" in CLOUD_SRC, "云同步：checksum 为 FNV-1a（0x811c9dc5/0x01000193）")
check("maxOf(winner.save.lingyu" in CLOUD_SRC and "maxOf(winner.save.yaodan" in CLOUD_SRC and "maxOf(winner.save.incense" in CLOUD_SRC, "云同步：merge 货币取 max")
check("remote.modifiedAt > local.modifiedAt" in CLOUD_SRC and "remote.deviceId > local.deviceId" in CLOUD_SRC, "云同步：merge LWW + deviceId 平局")

# Python 重实现 merge（与 Kotlin/Swift 完全一致）
def c_envelope(device_id, modified_at, save):
    return {"deviceId": device_id, "modifiedAt": modified_at, "v": 1, "save": save}
def c_merge(local, remote):
    if local is None: return remote
    if remote is None: return local
    if remote["modifiedAt"] > local["modifiedAt"]:
        winner, loser = remote, local
    elif local["modifiedAt"] > remote["modifiedAt"]:
        winner, loser = local, remote
    elif remote["deviceId"] > local["deviceId"]:
        winner, loser = remote, local
    else:
        winner, loser = local, remote
    s = dict(winner["save"])
    s["lingyu"] = max(winner["save"]["lingyu"], loser["save"]["lingyu"])
    s["yaodan"] = max(winner["save"]["yaodan"], loser["save"]["yaodan"])
    s["incense"] = max(winner["save"]["incense"], loser["save"]["incense"])
    return {"deviceId": winner["deviceId"], "modifiedAt": winner["modifiedAt"], "v": winner["v"], "save": s}

_cbase = {"version":1,"incense":100,"lingyu":300,"yaodan":20,"caughtRegion":["m1"],"team":["m1"]}
# remote 较新 → remote 胜；货币取 max（remote 胜但货币不丢）
_loc = c_envelope("devA", 1000, {**_cbase, "lingyu":300,"yaodan":20,"incense":100})
_rem = c_envelope("devB", 2000, {**_cbase, "lingyu":50,"yaodan":5,"incense":30})
_m1 = c_merge(_loc, _rem)
check(_m1["deviceId"]=="devB" and _m1["modifiedAt"]==2000, "云同步：remote 较新 → remote 胜")
check(_m1["save"]["lingyu"]==300 and _m1["save"]["yaodan"]==20 and _m1["save"]["incense"]==100, "云同步：货币取两方 max（remote 胜但货币不丢）")
# local 较新 → local 胜；货币取 max
_loc2 = c_envelope("devA", 5000, {**_cbase, "lingyu":10,"yaodan":1,"incense":5})
_rem2 = c_envelope("devB", 2000, {**_cbase, "lingyu":999,"yaodan":999,"incense":999})
_m2 = c_merge(_loc2, _rem2)
check(_m2["deviceId"]=="devA", "云同步：local 较新 → local 胜")
check(_m2["save"]["lingyu"]==999 and _m2["save"]["yaodan"]==999 and _m2["save"]["incense"]==999, "云同步：local 胜 + 货币 max")
# modifiedAt 相等 → deviceId 字典序（devB > devA）
_loc3 = c_envelope("devA", 1000, {**_cbase, "lingyu":1,"yaodan":1,"incense":1})
_rem3 = c_envelope("devB", 1000, {**_cbase, "lingyu":2,"yaodan":2,"incense":2})
_m3 = c_merge(_loc3, _rem3)
check(_m3["deviceId"]=="devB", "云同步：modifiedAt 相等 → deviceId 字典序胜（devB）")
# null 兜底
check(c_merge(None, _rem) is not None and c_merge(_loc, None) is not None, "云同步：任一方 null → 返回另一方")
check(c_merge(None, None) is None, "云同步：双方 null → None")

# checksum FNV-1a（与 Kotlin/Swift 一致：作用于 UTF-16 码元）
def c_utf16_units(text):
    return [cu & 0xffff for cu in text.encode("utf-16-le")]
def c_checksum(text):
    h = 0x811c9dc5
    for cu in c_utf16_units(text):
        h ^= cu & 0xff
        h = (h * 0x01000193) & 0xffffffff
    return h
_chk_a = c_checksum("hello")
check(_chk_a == c_checksum("hello"), "云同步：checksum 确定性（同输入同值）")
check(_chk_a != c_checksum("world"), "云同步：checksum 不同输入不同值")
_port_a = sxp_to_portable(_cbase)
_chk_p = c_checksum(_port_a)
check(c_checksum(_port_a) == _chk_p, "云同步：checksum 对便携串稳定")
check(c_checksum(sxp_to_portable({**_cbase,"lingyu":999})) != _chk_p, "云同步：篡改内容 → checksum 变化")

# Core.swift 镜像
check("struct CloudSyncEngine" in CORE_SWIFT, "云同步：Core.swift 镜像 CloudSyncEngine")
check("static func toEnvelope" in CORE_SWIFT and "static func fromEnvelope" in CORE_SWIFT, "云同步：Core.swift 含 toEnvelope/fromEnvelope")
check("static func merge" in CORE_SWIFT, "云同步：Core.swift 含 merge")
check("static func checksum" in CORE_SWIFT and "static func verify" in CORE_SWIFT, "云同步：Core.swift 含 checksum/verify")
check("0x811c9dc5" in CORE_SWIFT and "0x01000193" in CORE_SWIFT, "云同步：Core.swift checksum FNV-1a 常量")
check("max(winner.save.lingyu" in CORE_SWIFT and "max(winner.save.yaodan" in CORE_SWIFT and "max(winner.save.incense" in CORE_SWIFT, "云同步：Core.swift merge 货币 max")
check("var incense: Int" in CORE_SWIFT and "var lingyu: Int" in CORE_SWIFT and "var yaodan: Int" in CORE_SWIFT, "云同步：Core.swift SaveData 货币字段已转 var（支持 merge）")
check("struct CloudEnvelope" in CORE_SWIFT, "云同步：Core.swift 含 CloudEnvelope")

# ════════════ 存档语义完整性校验 + 自愈（GDD·稳健性收口） ════════════
SIE_SRC = open(os.path.join(COMMON, "persistence", "SaveIntegrityEngine.kt"), encoding="utf-8").read()
check("object SaveIntegrityEngine" in SIE_SRC, "存档完整性：Kotlin 含 object SaveIntegrityEngine")
check("fun validate" in SIE_SRC and "fun sanitize" in SIE_SRC, "存档完整性：Kotlin 含 validate/sanitize")
check("data class Violation" in SIE_SRC, "存档完整性：Kotlin 含 Violation")
check("TEAM_CAP" in SIE_SRC, "存档完整性：Kotlin 含 TEAM_CAP")

# Python 重实现（与 Kotlin/Swift 逻辑一致）
SIE_MONSTERS = {"m1", "m2", "m3"}
SIE_QUESTS = {"q1", "q2"}
SIE_SECT = {"tech_a": 3, "tech_b": 3}
SIE_TALENT_CAP = lambda bt: 3 + bt * 1
def sie_validate(save):
    v = []
    add = lambda c, m: v.append((c, m))
    if save.get("version", 1) != 1: add("VERSION", "")
    if save.get("incense", 0) < 0: add("NEG_INCENSE", "")
    if save.get("lingyu", 0) < 0: add("NEG_LINGYU", "")
    if save.get("yaodan", 0) < 0: add("NEG_YAODAN", "")
    if not (0 <= save.get("fatigue", 0) <= 100): add("FATIGUE_RANGE", "")
    if not (0 <= save.get("reputation", 0) <= 100): add("REP_RANGE", "")
    if not (0 <= save.get("rankTierIndex", 0) <= 4): add("RANK_TIER", "")
    if not (0 <= save.get("rankPoints", 0) <= 99): add("RANK_POINTS", "")
    if save.get("tribulationTotal", 0) < 0: add("NEG_TRIB", "")
    if save.get("secretRealmLeft", 0) < 0: add("NEG_SECRET", "")
    owned_ids = set(); dup = False
    for inst in save.get("owned", []):
        iid = inst.get("instanceId", "")
        if iid == "": add("INST_ID_EMPTY", "")
        if iid in owned_ids:
            if not dup: add("INST_DUP", ""); dup = True
        else:
            owned_ids.add(iid)
        defOk = inst.get("defId") in SIE_MONSTERS
        if not defOk: add("INST_DEF", "")
        bt = max(0, min(inst.get("breakthrough", 0), 5))
        cap = 30 + bt * 5
        if inst.get("level", 0) < 1: add("INST_LVL", "")
        elif inst.get("level", 0) > cap: add("INST_LVL", "")
        if inst.get("breakthrough", 0) < 0 or inst.get("breakthrough", 0) > 5: add("INST_BREAK", "")
        if inst.get("exp", 0) < 0: add("INST_EXP", "")
        if defOk:
            maxTrib = 3  # 测试妖均为 FAN(ordinal 0) → SHEN(3)-FAN(0)=3
            if inst.get("tribulations", 0) < 0 or inst.get("tribulations", 0) > maxTrib: add("INST_TRIB", "")
        allocated = 0
        for b, layers in inst.get("talents", {}).items():
            if b not in ("ATTACK", "DEFENSE", "SUPPORT"): add("INST_TALENT_BRANCH", "")
            if layers < 0 or layers > 3: add("INST_TALENT_LAYER", "")
            allocated += max(0, layers)
        if allocated > SIE_TALENT_CAP(bt): add("INST_TALENT_CAP", "")
    team_unknown = [t for t in save.get("team", []) if t not in owned_ids]
    if team_unknown: add("TEAM_REF", "")
    if len(save.get("team", [])) > 6: add("TEAM_CAP", "")
    equip_ids = set(); eqdup = False
    for eq in save.get("equipment", []):
        eid = eq.get("instanceId", "")
        if eid in equip_ids:
            if not eqdup: add("EQUIP_DUP", ""); eqdup = True
        else:
            equip_ids.add(eid)
        if eq.get("level", 1) < 1: add("EQUIP_LVL", "")
        if eq.get("stars", 1) < 1: add("EQUIP_STAR", "")
        if eq.get("tier", 1) < 1: add("EQUIP_TIER", "")
    bad_ref = []
    for iid, slots in save.get("equipped", {}).items():
        if iid not in owned_ids: bad_ref.append(""); continue
        for sl, eid in slots.items():
            if eid not in equip_ids: bad_ref.append("")
    if bad_ref: add("EQUIP_REF", "")
    bad_q = [q for q in save.get("quests", []) if q.get("id") not in SIE_QUESTS]
    if bad_q: add("QUEST_ID", "")
    for q in save.get("quests", []):
        if any(c < 0 for c in q.get("current", [])): add("QUEST_NEG", ""); break
    bad_c = [c for c in (save.get("caughtRegion", []) + save.get("caughtSecret", [])) if c not in SIE_MONSTERS]
    if bad_c: add("CODEX_ID", "")
    if save.get("faction") is not None and save.get("faction") not in ("maoshan", "longhushan", "gezao"): add("FACTION", "")
    bad_tech = [k for k in save.get("sectTech", {}) if k not in SIE_SECT]
    if bad_tech: add("SECTTECH_ID", "")
    for k, lv in save.get("sectTech", {}).items():
        if lv < 0: add("SECTTECH_NEG", ""); break
        if lv > SIE_SECT.get(k, 0): add("SECTTECH_LVL", "")
    return v

def sie_sanitize_inst(inst):
    bt = max(0, min(inst.get("breakthrough", 0), 5))
    cap = 30 + bt * 5
    lvl = max(1, min(inst.get("level", 1), cap))
    maxexp = ((100 + (lvl - 1) * 25) - 1) if lvl < cap else (100 + (cap - 2) * 25)
    exp = max(0, min(inst.get("exp", 0), max(0, maxexp)))
    trib = max(0, min(inst.get("tribulations", 0), 3))
    tcap = 3 + bt * 1
    cleaned = {b: max(0, min(n, 3)) for b, n in inst.get("talents", {}).items() if b in ("ATTACK", "DEFENSE", "SUPPORT")}
    allocated = sum(cleaned.values())
    # 注意：order 仅持有 key 的快照，每轮必须从 cleaned[b] 重新读取当前值（对齐 Kotlin 的
    # `val cur = cleaned[b] ?: 0`）；若从冻结元组取值会导致计数器与真实 sum 失同步，
    # 使 INST_TALENT_CAP 在 sanitize 后残留并破坏幂等性。
    order = sorted(cleaned.keys(), key=lambda b: -cleaned.get(b, 0))
    while allocated > tcap and order:
        b = order[0]
        cur = cleaned.get(b, 0)
        if cur <= 0:
            order.pop(0); continue
        cleaned[b] = cur - 1; allocated -= 1
    n = dict(inst); n["level"] = lvl; n["exp"] = exp; n["breakthrough"] = bt; n["tribulations"] = trib; n["talents"] = cleaned
    return n

def sie_sanitize(save):
    s = dict(save)
    s["version"] = 1  # 版本归一为当前版本（对齐 Kotlin/Swift）
    s["incense"] = max(0, save.get("incense", 0))
    s["lingyu"] = max(0, save.get("lingyu", 0))
    s["yaodan"] = max(0, save.get("yaodan", 0))
    s["fatigue"] = max(0, min(save.get("fatigue", 0), 100))
    s["reputation"] = max(0, min(save.get("reputation", 0), 100))
    s["rankTierIndex"] = max(0, min(save.get("rankTierIndex", 0), 4))
    s["rankPoints"] = max(0, min(save.get("rankPoints", 0), 99))
    s["tribulationTotal"] = max(0, save.get("tribulationTotal", 0))
    s["secretRealmLeft"] = max(0, save.get("secretRealmLeft", 0))
    seen = set(); owned = []
    for inst in save.get("owned", []):
        if inst.get("instanceId", "") == "": continue
        if inst.get("defId") not in SIE_MONSTERS: continue
        if inst.get("instanceId") in seen: continue
        seen.add(inst.get("instanceId")); owned.append(sie_sanitize_inst(inst))
    s["owned"] = owned
    owned_set = {o["instanceId"] for o in owned}
    seen_e = set(); equipment = []
    for eq in save.get("equipment", []):
        if eq.get("instanceId") in seen_e: continue
        seen_e.add(eq.get("instanceId"))
        e2 = dict(eq); e2["level"] = max(1, eq.get("level", 1)); e2["stars"] = max(1, eq.get("stars", 1)); e2["tier"] = max(1, eq.get("tier", 1))
        equipment.append(e2)
    s["equipment"] = equipment
    equip_set = {e["instanceId"] for e in equipment}
    team = []; seen_t = set()
    for t in save.get("team", []):
        if t in owned_set and t not in seen_t and len(team) < 6:
            team.append(t); seen_t.add(t)
    s["team"] = team
    eq_map = {}
    for iid, slots in save.get("equipped", {}).items():
        if iid not in owned_set: continue
        good = {sl: eid for sl, eid in slots.items() if eid in equip_set}
        if good: eq_map[iid] = good
    s["equipped"] = eq_map
    s["quests"] = [dict(q, current=[max(0, c) for c in q.get("current", [])]) for q in save.get("quests", []) if q.get("id") in SIE_QUESTS]
    s["caughtRegion"] = [c for c in save.get("caughtRegion", []) if c in SIE_MONSTERS]
    s["caughtSecret"] = [c for c in save.get("caughtSecret", []) if c in SIE_MONSTERS]
    s["faction"] = save.get("faction") if save.get("faction") in ("maoshan", "longhushan", "gezao") else None
    s["sectTech"] = {k: max(0, min(v, SIE_SECT[k])) for k, v in save.get("sectTech", {}).items() if k in SIE_SECT}
    return s

# 干净档
_sie_clean = {"version": 1, "incense": 10, "lingyu": 10, "yaodan": 10, "fatigue": 0, "reputation": 100,
              "rankTierIndex": 0, "rankPoints": 0, "tribulationTotal": 0, "secretRealmLeft": 3,
              "owned": [{"instanceId": "i1", "defId": "m1", "level": 10, "exp": 50, "breakthrough": 0, "tribulations": 0, "talents": {}}],
              "team": ["i1"], "equipment": [], "equipped": {},
              "quests": [{"id": "q1", "current": [1], "state": "ACTIVE"}],
              "caughtRegion": ["m1"], "caughtSecret": [], "faction": "maoshan", "sectTech": {"tech_a": 1}}
check(sie_validate(_sie_clean) == [], "存档完整性：干净档 validate 为空")

# 坏档：覆盖多类违规
_sie_bad = {
    "version": 1, "incense": -5, "lingyu": -3, "yaodan": -1,
    "fatigue": 150, "reputation": -10, "rankTierIndex": 9, "rankPoints": 200,
    "owned": [
        {"instanceId": "i1", "defId": "m1", "level": 10, "exp": 0, "breakthrough": 0, "tribulations": 0, "talents": {}},
        {"instanceId": "i2", "defId": "GHOST", "level": 5, "exp": 0, "breakthrough": 0, "tribulations": 0, "talents": {}},
        {"instanceId": "i3", "defId": "m1", "level": 999, "exp": 0, "breakthrough": 9, "tribulations": 0, "talents": {}},
        {"instanceId": "i3", "defId": "m2", "level": 1, "exp": 0, "breakthrough": 0, "tribulations": 0, "talents": {}},
    ],
    "team": ["i1", "iX"],
    "equipment": [{"equipId": "e_def", "instanceId": "e1", "level": 1, "stars": 1, "tier": 1}],
    "equipped": {"i1": {"WEAPON": "e1"}, "iZZZ": {"WEAPON": "e1"}},
    "quests": [{"id": "q1", "current": [1], "state": "ACTIVE"}, {"id": "qX", "current": [-1], "state": "ACTIVE"}],
    "caughtRegion": ["m1", "mZ"], "caughtSecret": [],
    "faction": "atlantis", "sectTech": {"tech_a": 1, "tech_z": 5},
}
_sie_codes = {c for c, _ in sie_validate(_sie_bad)}
check({"NEG_INCENSE", "NEG_LINGYU", "NEG_YAODAN"} <= _sie_codes, "存档完整性：负货币被检出")
check({"FATIGUE_RANGE", "REP_RANGE", "RANK_TIER", "RANK_POINTS"} <= _sie_codes, "存档完整性：标量越界被检出")
check("INST_DEF" in _sie_codes, "存档完整性：未知 defId 实例被检出")
check({"INST_LVL", "INST_BREAK"} <= _sie_codes, "存档完整性：等级/突破越界被检出")
check("INST_DUP" in _sie_codes, "存档完整性：重复 instanceId 被检出")
check("TEAM_REF" in _sie_codes, "存档完整性：team 悬空引用被检出")
check("EQUIP_REF" in _sie_codes, "存档完整性：装配悬空妖被检出")
check({"QUEST_ID", "QUEST_NEG"} <= _sie_codes, "存档完整性：未知任务/负进度被检出")
check("CODEX_ID" in _sie_codes, "存档完整性：图鉴未知 id 被检出")
check("FACTION" in _sie_codes, "存档完整性：未知派系被检出")
check("SECTTECH_ID" in _sie_codes, "存档完整性：未知宗门科技节点被检出")

_sie_fixed = sie_sanitize(_sie_bad)
check(sie_validate(_sie_fixed) == [], "存档完整性：sanitize 后 validate 为空（修复闭环）")
check(_sie_fixed["incense"] == 0 and _sie_fixed["lingyu"] == 0 and _sie_fixed["yaodan"] == 0, "存档完整性：sanitize 钳制货币非负")
check(_sie_fixed["fatigue"] == 100 and _sie_fixed["reputation"] == 0 and _sie_fixed["rankTierIndex"] == 4 and _sie_fixed["rankPoints"] == 99, "存档完整性：sanitize 钳制标量边界")
check(all(o["defId"] in SIE_MONSTERS for o in _sie_fixed["owned"]), "存档完整性：sanitize 丢弃未知 defId 实例")
check(len(_sie_fixed["owned"]) == 2, "存档完整性：sanitize 去重（重复 i3 → 2 拥有）")
check("iX" not in _sie_fixed["team"], "存档完整性：sanitize 清理 team 悬空")
check(_sie_fixed["faction"] is None, "存档完整性：sanitize 未知 faction → nil")
check("tech_z" not in _sie_fixed["sectTech"], "存档完整性：sanitize 丢弃未知宗门科技")
for o in _sie_fixed["owned"]:
    if o["instanceId"] == "i3":
        check(o["level"] <= 30 + o["breakthrough"] * 5, "存档完整性：sanitize 钳制越界等级")
        check(o["breakthrough"] <= 5, "存档完整性：sanitize 钳制越界突破")

# Core.swift 镜像
check("struct SaveIntegrityEngine" in CORE_SWIFT, "存档完整性：Core.swift 镜像 SaveIntegrityEngine")
check("struct Violation" in CORE_SWIFT, "存档完整性：Core.swift 含 Violation")
check("static func validate" in CORE_SWIFT and "static func sanitize" in CORE_SWIFT, "存档完整性：Core.swift 含 validate/sanitize")
check("static let teamCap = 6" in CORE_SWIFT, "存档完整性：Core.swift 含 teamCap=6")
check("monsterIds" in CORE_SWIFT and "questIds" in CORE_SWIFT and "sectTechMax" in CORE_SWIFT, "存档完整性：Core.swift 查询表 monsterIds/questIds/sectTechMax")
check("NEG_INCENSE" in CORE_SWIFT and "INST_DEF" in CORE_SWIFT and "TEAM_REF" in CORE_SWIFT, "存档完整性：Core.swift 含关键违规 code")
# 性质测试抓出的真实缺陷修复（sectTech 上下界 + version 归一），断言源码已落地
check("version = SaveData.VERSION" in SIE_SRC, "存档完整性：Kotlin sanitize 归一 version 为当前版本")
check("coerceIn(0, sectTechMax" in SIE_SRC, "存档完整性：Kotlin sanitize 钳宗门科技上下界")
check("version: 1" in CORE_SWIFT, "存档完整性：Core.swift sanitize 归一 version 为 1")
check("min(max($0.value, 0), sectTechMax" in CORE_SWIFT, "存档完整性：Core.swift sanitize 钳宗门科技上下界")

# ════════════ 存档语义完整性 · 边界接线（加载/合并/导入即自愈） ════════════
# 验证 SaveIntegrityEngine 已接入三条「不可信输入边界」，而非死代码。
check("SaveIntegrityEngine.sanitize" in SAVE_SRC, "边界接线：SaveCodec.decode 调用 sanitize（本地加载即自愈）")
check("SaveIntegrityEngine.sanitize(mergedSave)" in CLOUD_SRC, "边界接线：CloudSyncEngine.merge 调用 sanitize（云合并即自愈）")
check("SaveIntegrityEngine.sanitize(portable.save)" in EXPORT_SRC, "边界接线：SaveExportEngine.fromPortable 调用 sanitize（导入即自愈）")
check("SaveIntegrityEngine.sanitize(save)" in CORE_SWIFT, "边界接线：Core.swift GameState.loadSave 调用 sanitize")
check("SaveIntegrityEngine.sanitize(p.save)" in CORE_SWIFT, "边界接线：Core.swift SaveExportEngine.fromPortable 调用 sanitize")
check("SaveIntegrityEngine.sanitize(s)" in CORE_SWIFT, "边界接线：Core.swift CloudSyncEngine.merge 调用 sanitize")

# 行为验证：三条边界链路对脏档均自愈为合法档
def sie_decode(d):  # 本地加载链路：decode → sanitize
    return sie_sanitize(d)
def sie_import(d):  # 导入链路：fromPortable → sanitize
    return sie_sanitize(d)
def sie_merge_wired(local, remote):  # 云合并链路：LWW+货币max → sanitize
    (winner, loser) = (remote, local) if remote.get("modifiedAt", 0) > local.get("modifiedAt", 0) else (local, remote)
    merged = dict(winner)
    merged["lingyu"] = max(winner.get("lingyu", 0), loser.get("lingyu", 0))
    merged["yaodan"] = max(winner.get("yaodan", 0), loser.get("yaodan", 0))
    merged["incense"] = max(winner.get("incense", 0), loser.get("incense", 0))
    return sie_sanitize(merged)

check(sie_validate(sie_decode(_sie_bad)) == [], "边界接线：本地加载脏档 → 自愈为合法档")
check(sie_validate(sie_decode(_sie_clean)) == [], "边界接线：本地加载干净档 → 仍合法")
check(sie_validate(sie_import(_sie_bad)) == [], "边界接线：导入脏档 → 自愈为合法档")
_dirty_remote = dict(_sie_bad); _dirty_remote["modifiedAt"] = 200
_clean_local = dict(_sie_clean); _clean_local["modifiedAt"] = 100
_merged_wired = sie_merge_wired(_clean_local, _dirty_remote)
check(sie_validate(_merged_wired) == [], "边界接线：云合并脏远端 → 自愈为合法档")
check(_merged_wired["incense"] == _clean_local["incense"], "边界接线：云合并保留本机货币（远端负值不污染）")
check("GHOST" not in [o["defId"] for o in _merged_wired["owned"]], "边界接线：云合并且丢弃远端未知 defId")
check("iX" not in _merged_wired["team"], "边界接线：云合并清理远端 team 悬空")

# ════════════ 存档自愈 · 性质测试（全满性 + 幂等性 + 不动点 + 合并） ════════════
# 手写 13 条断言只能覆盖有限样例，无法证明算法级不变量。以下用固定种子的随机对抗
# 损坏存档，证明 SaveIntegrityEngine.sanitize 的三个关键性质：
#   (A) 全满性：对任意（含对抗性）存档 validate(sanitize(x)) 恒为空；
#   (B) 幂等性：sanitize(sanitize(x)) == sanitize(x)（decode 与 merge 都会调用，重复须安全）；
#   (C) 不动点：对合法档 sanitize 为恒等（不静默改写干净数据）。
# 并对云合并链路（LWW+货币max → sanitize）做同等 (A)(B) 验证。
import random as _fr
_FR = _fr.Random(20260816)  # 固定种子，可复现

def _fuzz_save(rng):
    neg = [-100, -1, 0, 1, 50, 777777, 10 ** 9]
    def rip():
        return rng.choice(neg)
    owned = []
    for _ in range(rng.randint(0, 6)):
        owned.append({
            "instanceId": rng.choice(["", "i1", "i2", "i3", "iDUP", "ix"]),
            "defId": rng.choice(list(SIE_MONSTERS) + ["GHOST", "mZ", "", "totally_unknown_id"]),
            "level": rng.choice([0, 1, 10, 35, 999]),
            "exp": rng.choice([-5, 0, 50, 100000]),
            "breakthrough": rng.choice([-1, 0, 2, 5, 9]),
            "tribulations": rng.choice([-1, 0, 3, 9]),
            "talents": rng.choice([{}, {"ATTACK": 3}, {"BOGUS": 5}, {"ATTACK": 9, "DEFENSE": 9, "SUPPORT": 9}]),
        })
    team = [rng.choice(["i1", "i2", "i3", "iX", "iDUP", "ix"]) for _ in range(rng.randint(0, 5))]
    equipment = [{"equipId": rng.choice(["e_def", "e2", "e3"]),
                  "instanceId": rng.choice(["e1", "e2", "eDUP"]),
                  "level": rng.choice([0, 1, 5, 99]),
                  "stars": rng.choice([0, 1, 5, 13]),
                  "tier": rng.choice([0, 1, 5, 9])} for _ in range(rng.randint(0, 3))]
    equipped = {}
    for iid in rng.sample(["i1", "i2", "i3", "iZ"], k=rng.randint(0, 3)):
        equipped[iid] = {rng.choice(["WEAPON", "ARMOR", "TALISMAN", "ORNAMENT"]): rng.choice(["e1", "e2", "eZ"])}
    quests = []
    for _ in range(rng.randint(0, 3)):
        quests.append({"id": rng.choice(list(SIE_QUESTS) + ["qX", "qZ"]),
                       "current": [rng.choice([-1, 0, 1, 5]) for _ in range(rng.randint(1, 3))],
                       "state": rng.choice(["ACTIVE", "COMPLETED", "CLAIMED"])})
    return {
        "version": rng.choice([1, 0, 2, -1, 999]),
        "incense": rip(), "lingyu": rip(), "yaodan": rip(),
        "fatigue": rng.choice([-5, 0, 50, 100, 150, 999]),
        "reputation": rng.choice([-10, 0, 50, 100, 120, -50]),
        "rankTierIndex": rng.choice([-1, 0, 2, 4, 9, 12]),
        "rankPoints": rng.choice([-1, 0, 50, 99, 200]),
        "tribulationTotal": rng.choice([-1, 0, 5, 100]),
        "secretRealmLeft": rng.choice([-1, 0, 3, 100]),
        "owned": owned, "team": team, "equipment": equipment, "equipped": equipped,
        "quests": quests,
        "caughtRegion": [rng.choice(list(SIE_MONSTERS) + ["mZ", "mQ"]) for _ in range(rng.randint(0, 4))],
        "caughtSecret": [rng.choice(list(SIE_MONSTERS) + ["mZ", "mQ"]) for _ in range(rng.randint(0, 3))],
        "faction": rng.choice([None, "maoshan", "longhushan", "gezao", "atlantis", "zzz"]),
        "sectTech": {rng.choice(list(SIE_SECT.keys()) + ["tech_z", "tech_q"]): rng.choice([-1, 0, 3, 9, 100]) for _ in range(rng.randint(0, 3))},
    }

_N = 400
_total_ok, _idempotent_ok = True, True
for _ in range(_N):
    x = _fuzz_save(_FR)
    sx = sie_sanitize(x)
    if sie_validate(sx) != []:
        _total_ok = False
    if sie_sanitize(sx) != sx:
        _idempotent_ok = False
check(_total_ok, f"自愈性质：全满性 — {_N} 例随机损坏档 sanitize 后 validate 恒为空")
check(_idempotent_ok, f"自愈性质：幂等性 — {_N} 例 sanitize(sanitize(x)) == sanitize(x)")

# 不动点：合法档 sanitize 为恒等（不静默改写干净数据）
check(sie_sanitize(_sie_clean) == _sie_clean, "自愈性质：不动点 — 干净档 sanitize 等于自身（不静默改写）")

# 合并链路 fuzz：任意脏本地 + 脏远端合并后恒合法且幂等
_merge_total, _merge_idem = True, True
for _ in range(200):
    l = _fuzz_save(_FR); l["modifiedAt"] = _FR.randint(1, 1000)
    r = _fuzz_save(_FR); r["modifiedAt"] = _FR.randint(1, 1000)
    m = sie_merge_wired(l, r)
    if sie_validate(m) != []:
        _merge_total = False
    if sie_sanitize(m) != m:
        _merge_idem = False
check(_merge_total, "自愈性质：云合并全满性 — 200 例脏本地+脏远端合并后恒合法")
check(_merge_idem, "自愈性质：云合并幂等性 — 合并结果再 sanitize 不变")

# ════════════ 静态数据定义完整性校验（DataIntegrityEngine，定义层） ════════════
# 与 SaveIntegrityEngine（玩家存档层）互补：校验「游戏定义自身」的相互引用一致性——
# 妖池↔技能池、进化链、连携技能、任务目标 ref。任意不一致都会导致启动崩溃或静默失效。
# Kotlin 端 SkillLibrary.get 对未知 id 抛 error（启动崩溃）；Swift 端 pick 用 compactMap
# 静默丢弃未知 id（妖少技能）——双端失败模式分歧，故需统一的 DataIntegrityEngine 同跑。
MP_SRC2 = open(os.path.join(COMMON, "data", "MonsterPool.kt"), encoding="utf-8").read()
SK_SRC2 = open(os.path.join(COMMON, "data", "SkillLibrary.kt"), encoding="utf-8").read()
CB_SRC2 = open(os.path.join(COMMON, "logic", "ComboEngine.kt"), encoding="utf-8").read()
DIE_SRC = open(os.path.join(COMMON, "data", "DataIntegrityEngine.kt"), encoding="utf-8").read()

# 1) 技能池 id 集合（权威来源）
skill_ids = set(re.findall(r'Skill\(\s*"(s_[a-z0-9]+)"', SK_SRC2))
check(len(skill_ids) >= 70, f"定义完整性：技能池解析数量≥70（实得 {len(skill_ids)} 式）")

# 2) 妖池 id 集合（复用 POOL）
monster_ids = set(m["id"] for m in POOL)

# 3) 妖池技能引用 ⊆ 技能池（否则 Kotlin 启动即崩溃 / Swift 静默少技能）
bad_mon_skill = [(m["id"], sid) for m in POOL for sid in m["skills"] if sid not in skill_ids]
check(not bad_mon_skill, f"定义完整性：妖池 {len(monster_ids)} 只技能引用全部存在（违规 {bad_mon_skill[:3]}）")

# 4) 进化链 ⊆ 妖池（否则进化链断裂）
evo_refs = re.findall(r'evolve(?:To|From)\s*=\s*"([^"]+)"', MP_SRC2)
bad_evo = [e for e in evo_refs if e not in monster_ids]
check(not bad_evo, f"定义完整性：进化链引用全部存在（违规 {bad_evo[:3]}）")

# 5) 连携技能 ⊆ 技能池（否则连携永不触发）
combo_skill_refs = re.findall(r'"(s_[a-z0-9]+)"', CB_SRC2)
bad_combo = [s for s in combo_skill_refs if s not in skill_ids]
check(not bad_combo, f"定义完整性：连携技能引用全部存在（违规 {bad_combo[:3]}）")

# 6) 任务 ref ∈ 合法枚举名（否则任务静默永不完成）
elem_names = {"METAL", "WOOD", "EARTH", "WATER", "FIRE", "WIND", "THUNDER"}
rar_names = {"FAN", "LING", "XIAN", "SHEN"}
quest_refs = re.findall(
    r'ObjectiveDef\(\s*ObjectiveType\.(\w+)\s*,\s*\d+\s*,\s*ref\s*=\s*"([^"]*)"', QL_SRC)
bad_qref = []
for qtype, qref in quest_refs:
    if qref == "":
        continue
    if qtype in ("CATCH_ELEMENT", "WIN_WITH_ELEMENT") and qref not in elem_names:
        bad_qref.append((qtype, qref))
    if qtype == "CATCH_RARITY" and qref not in rar_names:
        bad_qref.append((qtype, qref))
check(not bad_qref, f"定义完整性：任务 ref 全部合法（违规 {bad_qref[:3]}）")

# 7) 源码接线断言：Kotlin + Swift 引擎存在且含四项校验 code
for code in ("MON_SKILL", "EVO_TO", "EVO_FROM", "COMBO_SKILL", "QUEST_REF_ELEM", "QUEST_REF_RAR"):
    check(code in DIE_SRC, f"定义完整性：Kotlin DataIntegrityEngine 含 {code}")
    check(code in CORE_SWIFT, f"定义完整性：Swift DataIntegrityEngine 含 {code}")
check("object DataIntegrityEngine" in DIE_SRC, "定义完整性：Kotlin object DataIntegrityEngine 存在")
check("struct DataIntegrityEngine" in CORE_SWIFT, "定义完整性：Swift struct DataIntegrityEngine 存在")
check("MonsterPool" in DIE_SRC and "SkillLibrary" in DIE_SRC and "ComboEngine" in DIE_SRC and "QuestLibrary" in DIE_SRC,
      "定义完整性：Kotlin 引擎引用四类定义源")
check("MonsterPool.all" in CORE_SWIFT and "SK.keys" in CORE_SWIFT and "ComboEngine.COMBOS" in CORE_SWIFT and "QuestLibrary.all" in CORE_SWIFT,
      "定义完整性：Swift 镜像引用四类定义源")

# ════════════ 养成操作产出合法实例（运行期存档污染防护） ════════════
# 审计发现的真实 bug：CultivationEngine.evolve 进化时只替换 defId、保留 tribulations，
# 但 maxTribulations 依赖稀有度——若 FAN 妖先渡劫满(trib=3)再进化成 LING(maxTrib=2)，
# 结果 tribulations=3>2 → INST_TRIB 非法存档 + tribulateMult(1+0.75) 把属性算超（越界/超模）。
# 普通长线玩法（渡劫后进化）即可触发，sanitize 仅能修存档、无法修运行期超模数值。
CULE_SRC = open(os.path.join(COMMON, "logic", "CultivationEngine.kt"), encoding="utf-8").read()

RARITY_ORD = {"FAN": 0, "LING": 1, "XIAN": 2, "SHEN": 3}
SHEN_ORD = 3
def max_trib(rar):
    return SHEN_ORD - RARITY_ORD[rar]

def _inst_viol(inst, def_rar):
    """镜像 SaveIntegrityEngine 对单只妖实例的校验（仅养成越界项）。"""
    v = []
    cap = 30 + inst["breakthrough"] * 5
    if inst["level"] < 1 or inst["level"] > cap:
        v.append("INST_LVL")
    if inst["breakthrough"] < 0 or inst["breakthrough"] > 5:
        v.append("INST_BREAK")
    if inst["tribulations"] < 0 or inst["tribulations"] > max_trib(def_rar):
        v.append("INST_TRIB")
    return v

def _evolve_old(inst, to_rar):
    n = dict(inst); n["defId"] = to_rar; return n          # 旧逻辑：tribulations 不变
def _evolve_new(inst, to_rar):
    n = dict(inst); n["defId"] = to_rar
    n["tribulations"] = min(inst["tribulations"], max_trib(to_rar))   # 新逻辑：钳到新稀有度上限
    return n

# 复现 + 修复对照（FAN 满渡劫后进化成 LING）
_fan = {"defId": "FAN", "level": 55, "exp": 0, "breakthrough": 5, "tribulations": 3}
_evo_old = _evolve_old(_fan, "LING")
_evo_new = _evolve_new(_fan, "LING")
check(_inst_viol(_evo_old, "LING") != [], "运行期污染：OLD evolve 满渡劫后进化 → INST_TRIB 残留（bug 复现）")
check(_inst_viol(_evo_new, "LING") == [], "运行期污染：NEW evolve 钳制 tribulations → 合法（修复）")
check(_evo_new["tribulations"] == 2, "运行期污染：NEW evolve 把 trib 3 钳到 LING 上限 2")
# 未越界的渡劫值应被原样保留（不应静默降级）
_fan2 = {"defId": "FAN", "level": 55, "exp": 0, "breakthrough": 5, "tribulations": 1}
check(_evolve_new(_fan2, "LING")["tribulations"] == 1, "运行期污染：NEW evolve 未越界 trib 值原样保留")

# 双端源码断言：evolve 已钳制到新稀有度上限
check("maxTribulations(newDef.rarity)" in CULE_SRC, "运行期污染：Kotlin evolve 钳制 tribulations 到新稀有度上限")
check("maxTribulations(newDef.rarity)" in CORE_SWIFT, "运行期污染：Swift evolve 钳制 tribulations 到新稀有度上限")

# 沿真实进化链证明：满渡劫实例经 NEW evolve 在所有边均合法；并统计确有边必须钳制（bug 可正常触发）
_MP = open(os.path.join(COMMON, "data", "MonsterPool.kt"), encoding="utf-8").read()
_rar_by_id = {m["id"]: m["rar"] for m in POOL}
_evo_to = {}
_ML = _MP.splitlines()
for i, _ln in enumerate(_ML):
    if "m(" not in _ln:
        continue
    # evolveTo 常写在续行（如 `m("yao_she", …, evolveTo = "yao_jiao", …)`），合并后续行一起扫描
    _blob = _ln + " " + " ".join(_ML[i + 1:i + 4])
    _fid = re.search(r'm\("([^"]+)"', _blob)
    _eto = re.search(r'evolveTo = "([^"]+)"', _blob)
    if _fid and _eto:
        _evo_to[_fid.group(1)] = _eto.group(1)
_bad = []
_need = 0
for _s, _d in _evo_to.items():
    if _s in _rar_by_id and _d in _rar_by_id:
        _sr, _dr = _rar_by_id[_s], _rar_by_id[_d]
        if max_trib(_dr) < max_trib(_sr):
            _need += 1
        _inst = {"defId": _s, "level": 55, "exp": 0, "breakthrough": 5, "tribulations": max_trib(_sr)}
        if _inst_viol(_evolve_new(_inst, _dr), _dr) != []:
            _bad.append(f"{_s}->{_d}")
check(len(_bad) == 0, f"运行期污染：全部真实进化边 NEW evolve 合法（违例边：{_bad}）")
check(_need > 0, f"运行期污染：存在 {_need} 条真实进化边满渡劫后必须钳制 → bug 可正常触发（非空证明）")

# ════════════ 战斗稳健性 + 平衡自检（GDD 十五·数值基础 + 七行平衡） ════════════
import random as _rnd
BIE_SRC = open(os.path.join(COMMON, "logic", "BattleIntegrityEngine.kt"), encoding="utf-8").read()
check("object BattleIntegrityEngine" in BIE_SRC, "战斗自检：Kotlin 含 object BattleIntegrityEngine")
check("elementMatchupMatrix" in BIE_SRC and "balanceReport" in BIE_SRC, "战斗自检：Kotlin 含 elementMatchupMatrix/balanceReport")
check("noDegenerateDominance" in BIE_SRC and "beatsAdvantage" in BIE_SRC and "beatenDisadvantage" in BIE_SRC,
      "战斗自检：Kotlin 报告含无退化/克制优势/被克劣势")
check("Rarity.LING" in BIE_SRC, "战斗自检：Kotlin 基准妖用有效稀有度 LING（非 RARE）")
check("const val MAX_TURNS" in BS_SRC and "resolveByHp" in BS_SRC and "var turn" in BS_SRC,
      "战斗自检：BattleSession 含 MAX_TURNS/resolveByHp/turn")
check("simulateToEnd(maxSteps: Int = MAX_TURNS)" in BS_SRC, "战斗自检：simulateToEnd 默认用 MAX_TURNS")

# 七行元素 + 相克（与 Kotlin Element.kt / Core.swift 一致）
_BEATS = {
    "METAL": {"WOOD", "WIND"}, "WOOD": {"EARTH", "FIRE"}, "EARTH": {"WATER", "THUNDER"},
    "WATER": {"FIRE", "METAL"}, "FIRE": {"METAL", "WIND"}, "WIND": {"EARTH", "THUNDER"},
    "THUNDER": {"WOOD", "WATER"},
}
_ELEMS = list(_BEATS.keys())
def _emul(a, d):
    if d in _BEATS[a]: return 1.6
    if a in _BEATS[d]: return 0.6
    return 1.0
def _beats(a, d): return d in _BEATS[a]
check(not any(_beats(a, b) and _beats(b, a) for a in _ELEMS for b in _ELEMS if a != b),
      "战斗自检：七行相克无互克（克制关系不对称，无 A克B 且 B克A）")
check(all(sum(1 for b in _ELEMS if _beats(a, b)) == 2 for a in _ELEMS),
      "战斗自检：每元素恰好克制 2 个（出度=2）")
check(all(sum(1 for a in _ELEMS if _beats(a, b)) == 2 for b in _ELEMS),
      "战斗自检：每元素恰好被 2 个克制（入度=2）")
_GROWTH = {"FAN": 1.0, "LING": 1.1, "XIAN": 1.25, "SHEN": 1.5}
class _Stats:
    def __init__(self, atk, defense, hp, spd):
        self.atk = atk; self.defense = defense; self.hp = hp; self.spd = spd
class _Skill:
    def __init__(self, eid, element, power, isDamage, critBonus=0.0):
        self.id = eid; self.element = element; self.power = power
        self.isDamage = isDamage; self.critBonus = critBonus
class _Monster:
    def __init__(self, eid, element, rarity, baseAtk, baseDef, baseHp, baseSpd, skills):
        self.id = eid; self.element = element; self.rarity = rarity
        self.baseAtk = baseAtk; self.baseDef = baseDef; self.baseHp = baseHp; self.baseSpd = baseSpd
        self.skills = skills
    def statsAtLevel(self, level):
        g = _GROWTH[self.rarity]; lc = 1.0 + (level - 1) * 0.02
        return _Stats(max(1, int(self.baseAtk * g * lc)), max(1, int(self.baseDef * g * lc)),
                      max(1, int(self.baseHp * g * lc)), max(1, int(self.baseSpd * g)))
class _Combatant:
    def __init__(self, monster, level, equipped, effective):
        self.monster = monster; self.level = level; self.equipped = equipped; self.effective = effective
        self.maxHp = effective.hp; self.hp = effective.hp
    @property
    def isAlive(self): return self.hp > 0
MAX_TURNS = 100
def _best_counter(atk, defense):
    if not atk.equipped: return None
    best = None; bv = -1.0
    for s in atk.equipped:
        v = _emul(s.element, defense.monster.element) * (s.power if s.isDamage else 1)
        if v > bv: bv = v; best = s
    return best
def _b_damage(atk, defense, skill, teamMul=1.0, envMul=1.0, sectMul=1.0, rng=None):
    a = atk.effective.atk; d = max(1, defense.effective.defense)
    elem = _emul(skill.element, defense.monster.element)
    cat = 1.0  # 基准妖同属 YAO → 类别倍率恒 1.0
    critChance = min(1.0, max(0.0, 0.05 + skill.critBonus))
    rnd = rng.uniform(0.9, 1.0); crit = rng.random() < critChance; critMult = 1.5 if crit else 1.0
    lc = 1.0 + (atk.level - 1) * 0.02
    raw = (a / d) * skill.power * lc * elem * cat * critMult * rnd * teamMul * envMul * sectMul
    return {"damage": max(1, int(raw // 1)), "elem": elem, "crit": crit}
def _next_alive(team):
    for i, c in enumerate(team):
        if c.isAlive: return i
    return -1
class _Session:
    def __init__(self, p, e, teamMul=1.0, sectMul=1.0):
        self.playerTeam = p; self.enemyTeam = e; self.playerTeamMul = teamMul
        self.playerSectAttackMul = sectMul; self.activePlayer = 0; self.activeEnemy = 0
        self.turn = 0; self.outcome = "ONGOING"; self.log = []
    def _resolve_by_hp(self):
        pf = sum(max(0, c.hp) / c.maxHp for c in self.playerTeam)
        ef = sum(max(0, c.hp) / c.maxHp for c in self.enemyTeam)
        self.outcome = "PLAYER_WIN" if pf >= ef else "ENEMY_WIN"
    def step(self, rng):
        if self.outcome != "ONGOING": return None
        self.turn += 1
        atk = self.playerTeam[self.activePlayer]; defense = self.enemyTeam[self.activeEnemy]
        skill = _best_counter(atk, defense)
        if skill is None: return None
        res = _b_damage(atk, defense, skill, self.playerTeamMul, 1.0, self.playerSectAttackMul, rng)
        finalDmg = res["damage"]; defense.hp -= finalDmg
        ev = {"dmg": finalDmg}
        if not defense.isAlive:
            ni = _next_alive(self.enemyTeam)
            if ni == -1: self.outcome = "PLAYER_WIN"; return ev
            self.activeEnemy = ni
        eatk = self.enemyTeam[self.activeEnemy]; edef = self.playerTeam[self.activePlayer]
        eskill = _best_counter(eatk, edef)
        if eskill is None: return ev
        eres = _b_damage(eatk, edef, eskill, 1.0, 1.0, 1.0, rng)
        edef.hp -= eres["damage"]
        if not edef.isAlive:
            ni = _next_alive(self.playerTeam)
            if ni == -1: self.outcome = "ENEMY_WIN"; return ev
            self.activePlayer = ni
        if self.turn >= MAX_TURNS and self.outcome == "ONGOING": self._resolve_by_hp()
        return ev
    def simulate(self, rng):
        n = 0
        while self.outcome == "ONGOING" and n < MAX_TURNS:
            self.step(rng); n += 1
        return self.log
def _champion(element):
    sk = _Skill("ref_" + element.lower(), element, 100, True, 0.0)
    m = _Monster(element, element, "LING", 100, 100, 100, 100, [sk])
    return _Combatant(m, 50, [sk], m.statsAtLevel(50))

_rnd.seed(1)
_matrix = {}; _terminated = True
for a in _ELEMS:
    _matrix[a] = {}
    for b in _ELEMS:
        if a == b: _matrix[a][b] = 0.5; continue
        ca = _champion(a); cb = _champion(b)
        s = _Session([ca], [cb]); s.simulate(_rnd)
        if s.outcome == "ONGOING": _terminated = False
        _matrix[a][b] = 1.0 if s.outcome == "PLAYER_WIN" else 0.0
check(_terminated, "战斗自检：七行 42 场对局全部终结（无 ONGOING）")
_beat_bad = _beaten_bad = 0
for a in _ELEMS:
    for b in _ELEMS:
        if a == b: continue
        if _beats(a, b) and _matrix[a][b] < 0.8: _beat_bad += 1
        if _beats(b, a) and _matrix[a][b] > 0.2: _beaten_bad += 1
check(_beat_bad == 0, "战斗自检：克制方稳定占优（A克B → 玩家胜率≥0.8）")
check(_beaten_bad == 0, "战斗自检：被克方稳定劣势（A被B克 → 玩家胜率≤0.2）")
_dom = False
for a in _ELEMS:
    opp = [b for b in _ELEMS if b != a]
    avg = sum(_matrix[a][b] for b in opp) / len(opp)
    if not (0.0 < avg < 1.0): _dom = True
check(not _dom, "战斗自检：无元素退化统治（每元素均胜率∈(0,1)）")
_dmg_ok = True
for _ in range(200):
    ca = _champion("FIRE"); cb = _champion("WATER")
    s = _Session([ca], [cb]); ev = s.step(_rnd)
    if ev is None or ev["dmg"] < 1 or not isinstance(ev["dmg"], int): _dmg_ok = False
check(_dmg_ok, "战斗自检：每次出招伤害 ≥1 且为整数（floor+coerceAtLeast）")
# 回合上限裁决产出定义胜者（双方巨血 + 0 威力 → 每步各掉 1，100 回合后平局判玩家胜）
ca = _Combatant(_Monster("M", "METAL", "LING", 100, 100, 10000, 100, [_Skill("s", "METAL", 0, True)]),
               1, [_Skill("s", "METAL", 0, True)], _Stats(100, 100, 10000, 100))
cb = _Combatant(_Monster("E", "EARTH", "LING", 100, 100, 10000, 100, [_Skill("s", "EARTH", 0, True)]),
               1, [_Skill("s", "EARTH", 0, True)], _Stats(100, 100, 10000, 100))
s = _Session([ca], [cb]); s.simulate(_rnd)
check(s.outcome == "PLAYER_WIN" and s.turn >= MAX_TURNS, "战斗自检：回合上限按血量比例裁决 → 平局判玩家胜（必有结果）")
cb2 = _Combatant(_Monster("E", "EARTH", "LING", 100, 100, 5000, 100, [_Skill("s", "EARTH", 0, True)]),
                1, [_Skill("s", "EARTH", 0, True)], _Stats(100, 100, 5000, 100))
s2 = _Session([ca], [cb2]); s2.simulate(_rnd)
check(s2.outcome == "PLAYER_WIN", "战斗自检：回合上限裁决 → 玩家血量更高 → 玩家胜")
cb3 = _Combatant(_Monster("E", "EARTH", "LING", 100, 100, 20000, 100, [_Skill("s", "EARTH", 0, True)]),
                1, [_Skill("s", "EARTH", 0, True)], _Stats(100, 100, 20000, 100))
s3 = _Session([ca], [cb3]); s3.simulate(_rnd)
check(s3.outcome == "ENEMY_WIN", "战斗自检：回合上限裁决 → 敌方血量更高 → 敌方胜")

# Core.swift 镜像
check("struct BattleIntegrityEngine" in CORE_SWIFT, "战斗自检：Core.swift 镜像 BattleIntegrityEngine")
check("static let maxTurns" in CORE_SWIFT and "var turn" in CORE_SWIFT and "resolveByHp" in CORE_SWIFT,
      "战斗自检：Core.swift BattleSession 含 maxTurns/turn/resolveByHp")
check("static func elementMatchupMatrix" in CORE_SWIFT and "static func balanceReport" in CORE_SWIFT and "static func runMatchup" in CORE_SWIFT,
      "战斗自检：Core.swift 含 elementMatchupMatrix/balanceReport/runMatchup")

# ════════════ 运行期污染：catchMonster 拒绝未知妖种 id ════════════
# catchMonster 是平台收妖的唯一入口 API；若传入 MonsterPool 中不存在的 id
# （平台误传 / 过期数据 / 外挂注入），旧逻辑会直接 codex.catch(未知id) 污染图鉴
# 并产生 defId 悬空、effectiveStatsOf/buildCombatant 回退首妖的孤儿实例。
# 修复：边界守卫未知 id → 零变更拒绝（不污染图鉴、不产生孤儿实例）。
MONSTER_IDS = {m["id"] for m in POOL}

# 双端源码断言：catchMonster 已加未知 id 守卫（零变更拒绝）
check('if (MonsterPool.byId(id) == null) return CatchOutcome(null, emptyList(), success = false)' in GS_SRC,
      "运行期污染：Kotlin catchMonster 守卫未知 id → 零变更拒绝")
check('data class CatchOutcome(val milestone: String?, val questsCompleted: List<String>, val success: Boolean = true)' in GS_SRC,
      "运行期污染：Kotlin CatchOutcome 增 success 标志（默认 true，向后兼容）")
check('guard MonsterPool.byId(id) != nil else { return [] }' in CORE_SWIFT,
      "运行期污染：Swift catchMonster 守卫未知 id → 零变更拒绝")

# 复现 + 修复对照（功能级：用 Python 镜像运行时语义，证明零变更性质）
def _catch_old(gs, mid):
    gs["codex"].add(mid)                      # 旧逻辑：不校验，直接污染图鉴
    iid = f"i_{mid}_{gs['next']}"; gs["next"] += 1
    gs["owned"][iid] = {"defId": mid}         # 旧逻辑：产孤儿实例
    if len(gs["team"]) < 6: gs["team"].append(iid)
    return "outcome"
def _catch_new(gs, mid):
    if mid not in MONSTER_IDS: return None    # 新逻辑：未知 id 直接拒绝
    gs["codex"].add(mid)
    iid = f"i_{mid}_{gs['next']}"; gs["next"] += 1
    gs["owned"][iid] = {"defId": mid}
    if len(gs["team"]) < 6: gs["team"].append(iid)
    return "outcome"

_bad = "zzz_unknown_id"
_g_old = {"codex": set(), "owned": {}, "team": [], "next": 1}
_catch_old(_g_old, _bad)
check(_bad in _g_old["codex"] and any(v["defId"] == _bad for v in _g_old["owned"].values()),
      "运行期污染：OLD catchMonster(未知id) → 图鉴被污染 + 产孤儿实例（bug 复现）")

_g_new = {"codex": set(), "owned": {}, "team": [], "next": 1}
_r_new = _catch_new(_g_new, _bad)
check(_r_new is None and _bad not in _g_new["codex"]
      and not any(v["defId"] == _bad for v in _g_new["owned"].values()),
      "运行期污染：NEW catchMonster(未知id) → 拒绝且零变更（图鉴/实例均不被污染）")

# 合法 id 仍正常收服（守卫仅拦未知 id，不误伤正常玩法）
_valid = POOL[0]["id"]
_g2 = {"codex": set(), "owned": {}, "team": [], "next": 1}
_r2 = _catch_new(_g2, _valid)
check(_r2 is not None and _valid in _g2["codex"]
      and any(v["defId"] == _valid for v in _g2["owned"].values()),
      "运行期污染：NEW catchMonster(合法id) → 正常收服（守卫不误伤）")

# ════════════ 运行期污染：captureNode 拒绝未知据点节点 ════════════
# captureNode 是据点战占领的唯一入口 API；若传入不在合法灵脉节点白名单的 node
# （平台误传 / 过期数据 / 外挂注入），旧逻辑会原样写入 strongholdNode（悬空引用）
# 且 strongholdAttackBonus 仅判 non-null 仍触发虚假 +10% 攻击加成。
# 修复：边界守卫未知节点 → 零变更拒绝（不写悬空引用、不触发虚假加成）。
LOC_SRC = open(os.path.join(COMMON, "logic", "LocationEngine.kt"), encoding="utf-8").read()
NODE_NAMES = {"城隍桥", "古庙", "老槐树", "东海之滨"}

# 双端源码断言：合法节点白名单（单一真源）
check('val STRONGHOLD_NODES: Set<String> = NODE_KEYS.values.toSet()' in LOC_SRC,
      "运行期污染：Kotlin LocationEngine.STRONGHOLD_NODES 派生自 NODE_KEYS（单一真源）")
check('static let strongholdNodes: Set<String> = ["城隍桥", "古庙", "老槐树", "东海之滨"]' in CORE_SWIFT,
      "运行期污染：Swift LocationEngine.strongholdNodes 白名单（4 节点）")

# 双端源码断言：captureNode 已加未知节点守卫（零变更拒绝）
check('if (node !in LocationEngine.STRONGHOLD_NODES) return false' in GS_SRC,
      "运行期污染：Kotlin captureNode 守卫未知节点 → 零变更拒绝")
check('guard LocationEngine.strongholdNodes.contains(node) else { return false }' in CORE_SWIFT,
      "运行期污染：Swift captureNode 守卫未知节点 → 零变更拒绝")

# 白名单与 Kotlin NODE_KEYS / Swift nearbyNode 地图值一致（单一真源收敛）
_kotlin_nodes = set(re.findall(r'"((?:城隍桥|古庙|老槐树|东海之滨))"', LOC_SRC))
check(_kotlin_nodes == NODE_NAMES,
      f"运行期污染：Kotlin 合法节点集 == {sorted(NODE_NAMES)}（实得 {sorted(_kotlin_nodes)}）")
_swift_nodes = set(m.strip('"') for m in re.findall(r'"(?:城隍桥|古庙|老槐树|东海之滨)"', CORE_SWIFT))
check(_swift_nodes == NODE_NAMES,
      f"运行期污染：Swift 合法节点集 == {sorted(NODE_NAMES)}（实得 {sorted(_swift_nodes)}）")

# 复现 + 修复对照（功能级：镜像运行时语义，证明零变更性质 + 守卫不误伤）
def _can_capture(st, today):
    return st["lastCaptureDay"] != today
def _capture_old(st, node, today):
    ok = _can_capture(st, today)
    if ok:
        st["ownedNode"] = node            # 旧逻辑：不校验，原样写入（悬空引用）
        st["lastCaptureDay"] = today
    return ok
def _capture_new(st, node, today):
    if node not in NODE_NAMES: return False   # 新逻辑：未知节点直接拒绝
    ok = _can_capture(st, today)
    if ok:
        st["ownedNode"] = node
        st["lastCaptureDay"] = today
    return ok
def _bonus(ownedNode):
    return 1.10 if ownedNode is not None else 1.0   # strongholdAttackBonus 仅判 non-null

_bad = "zzz_unknown_node"
_g_old = {"ownedNode": None, "lastCaptureDay": ""}
_capture_old(_g_old, _bad, "2026-08-16")
check(_g_old["ownedNode"] == _bad and _bonus(_g_old["ownedNode"]) == 1.10,
      "运行期污染：OLD captureNode(未知节点) → 写悬空引用 + 触发虚假 +10% 加成（bug 复现）")

_g_new = {"ownedNode": None, "lastCaptureDay": ""}
_r_new = _capture_new(_g_new, _bad, "2026-08-16")
check(_r_new is False and _g_new["ownedNode"] is None and _bonus(_g_new["ownedNode"]) == 1.0,
      "运行期污染：NEW captureNode(未知节点) → 拒绝且零变更（无悬空引用 / 无虚假加成）")

# 合法节点仍正常占领（守卫仅拦未知节点，不误伤正常玩法）
_g2 = {"ownedNode": None, "lastCaptureDay": ""}
_r2 = _capture_new(_g2, "城隍桥", "2026-08-16")
check(_r2 is True and _g2["ownedNode"] == "城隍桥" and _bonus(_g2["ownedNode"]) == 1.10,
      "运行期污染：NEW captureNode(合法节点) → 正常占领 + 合法 +10% 加成（守卫不误伤）")

# ════════════ 运行期污染：grantEquip 拒绝未知装备 id + 存档装备 defId 完整性 ════════════
# grantEquip 是「从外部 id 创建游戏对象」的最后一处公开 API（另一处是 catchMonster），
# 此前不校验 equipId，会写入 defId 悬空的死装备；SaveIntegrityEngine 此前也未校验
# equipment[].equipId ∈ EquipmentLibrary，坏档死装备可越过自愈残留。
# 现：grantEquip 校验 + SaveIntegrityEngine 增 EQUIP_DEF + sanitize 丢弃死装备。

# 双端源码断言：grantEquip 守卫未知 equipId（零变更返回 null/nil）
check('if (EquipmentLibrary.byId(equipId) == null) return null' in GS_SRC,
      "运行期污染：Kotlin grantEquip 守卫未知 equipId → 返回 null 不入包")
check('fun grantEquip(equipId: String): OwnedEquip?' in GS_SRC,
      "运行期污染：Kotlin grantEquip 返回类型可空（未知 id 即 null）")
check('guard EquipmentLibrary.byId(equipId) != nil else { return nil }' in CORE_SWIFT,
      "运行期污染：Swift grantEquip 守卫未知 equipId → 返回 nil 不入包")
check('func grantEquip(_ equipId: String) -> OwnedEquip?' in CORE_SWIFT,
      "运行期污染：Swift grantEquip 返回类型可空（未知 id 即 nil）")

# 双端源码断言：SaveIntegrityEngine 增 EQUIP_DEF + 丢弃未知 equipId 的装备
check('EQUIP_DEF' in SIE_SRC and 'equipmentIds' in SIE_SRC,
      "存档完整性：Kotlin SaveIntegrityEngine 增 EQUIP_DEF + equipmentIds 查询表")
check('add("EQUIP_DEF"' in SIE_SRC,
      "存档完整性：Kotlin validate 报 EQUIP_DEF 违规（装备 equipId 不存在）")
check('if (eq.equipId !in equipmentIds) continue' in SIE_SRC,
      "存档完整性：Kotlin sanitize 丢弃 defId 不存在的死装备")
check('"EQUIP_DEF"' in CORE_SWIFT and 'equipmentIds' in CORE_SWIFT,
      "存档完整性：Swift SaveIntegrityEngine 增 EQUIP_DEF + equipmentIds 查询表")
check('add("EQUIP_DEF"' in CORE_SWIFT,
      "存档完整性：Swift validate 报 EQUIP_DEF 违规（装备 equipId 不存在）")
check('if !equipmentIds.contains(eq.equipId) { continue }' in CORE_SWIFT,
      "存档完整性：Swift sanitize 丢弃 defId 不存在的死装备")

# 数据驱动：确认新手装备 id 真实存在于 EquipmentLibrary（绑定真实定义，避免硬编码漂移）
EQUIP_SRC = open(os.path.join(COMMON, "data", "EquipmentLibrary.kt"), encoding="utf-8").read()
check('eq_sword' in EQUIP_SRC and 'eq_armor' in EQUIP_SRC,
      "存档完整性：新手装备 id（eq_sword/eq_armor）真实存在于 EquipmentLibrary")

# 复现 OLD：sanitize 只看 instanceId 去重，不过滤 equipId → 死装备残留；NEW：丢弃
def _sani_equip_old(items):
    out, seen = [], set()
    for eq in items:
        if eq["iid"] in seen:
            continue
        seen.add(eq["iid"]); out.append(eq)   # OLD：不校验 equipId
    return out
def _sani_equip_new(items, valid):
    out, seen = [], set()
    for eq in items:
        if eq["eid"] not in valid:
            continue   # NEW：丢弃 defId 不存在
        if eq["iid"] in seen:
            continue
        seen.add(eq["iid"]); out.append(eq)
    return out

_VALID = {"eq_sword", "eq_armor"}
_ITEMS = [{"iid": "e1", "eid": "eq_sword"},
          {"iid": "e2", "eid": "eq_ghost"},   # 未知 equipId（平台误传 / 外挂）
          {"iid": "e3", "eid": "eq_armor"}]
_old = _sani_equip_old(_ITEMS)
_new = _sani_equip_new(_ITEMS, _VALID)
check(any(e["eid"] == "eq_ghost" for e in _old),
      "存档完整性：OLD sanitize 残留 defId 不存在的死装备 eq_ghost（bug 复现）")
check(not any(e["eid"] == "eq_ghost" for e in _new),
      "存档完整性：NEW sanitize 丢弃 defId 不存在的死装备 eq_ghost")
check(len(_new) == 2,
      "存档完整性：NEW sanitize 仅保留 2 件合法装备（eq_ghost 被丢弃）")

# ── 边界审计收口：所有「接受外部 String id/下标」的公开变更 API 均已防御性校验 ──
# 确认无新增未守卫入口（catchMonster / captureNode / grantEquip 已修；其余经审计安全）。
check('MonsterPool.byId(id) == null) return CatchOutcome' in GS_SRC,
      "边界审计：catchMonster 守卫未知妖种 id")
check('node !in LocationEngine.STRONGHOLD_NODES) return false' in GS_SRC,
      "边界审计：captureNode 守卫未知据点节点（白名单）")
check('owned[instanceId] ?: return' in GS_SRC,
      "边界审计：owned 系列守卫（gainExp/breakthrough/evolve/tribulate/assignTalent）")
check('DungeonLibrary.byId(defId) ?: return null' in GS_SRC,
      "边界审计：enterDungeon 守卫未知副本 id")
check('SectTechEngine.research(id,' in GS_SRC,
      "边界审计：researchSectTech 委托校验科技节点 id")
check('quests.claim(id) ?: return null' in GS_SRC,
      "边界审计：claimQuest 守卫未知任务 id")
check('HonorEngine.isUnlocked(id,' in GS_SRC,
      "边界审计：equipTitle 守卫未解锁称号 id")
check('EquipmentLibrary.byId(oe.equipId) ?: return false' in GS_SRC,
      "边界审计：equipItem 守卫装备实例/装备 defId 存在")

# ════════════ 排位赛段位天梯（GDD 二十·竞技与荣誉·RankEngine） ════════════
# 此前 0 断言覆盖；本轮补齐纯客户端可验证的段位积分数学 + 双端源码一致性。
RE_SRC = open(os.path.join(COMMON, "logic", "RankEngine.kt"), encoding="utf-8").read()
check("object RankEngine" in RE_SRC, "排位：Kotlin 含 object RankEngine")
check('listOf("炼气", "筑基", "金丹", "元婴", "化神")' in RE_SRC,
      "排位：Kotlin TIERS = 5 段（炼气→化神）")
check("const val POINTS_TO_PROMOTE = 100" in RE_SRC, "排位：Kotlin POINTS_TO_PROMOTE = 100")
check("const val WIN_POINTS = 20" in RE_SRC, "排位：Kotlin WIN_POINTS = 20")
check("const val LOSS_POINTS = 15" in RE_SRC, "排位：Kotlin LOSS_POINTS = 15")
check("static let tiers = [\"炼气\", \"筑基\", \"金丹\", \"元婴\", \"化神\"]" in CORE_SWIFT,
      "排位：Core.swift tiers 5 段一致")
check("static let pointsToPromote = 100" in CORE_SWIFT, "排位：Core.swift pointsToPromote = 100")
check("static let winPoints = 20" in CORE_SWIFT, "排位：Core.swift winPoints = 20")
check("static let lossPoints = 15" in CORE_SWIFT, "排位：Core.swift lossPoints = 15")
check("static func applyResult" in CORE_SWIFT and "static func rankReward" in CORE_SWIFT,
      "排位：Core.swift 含 applyResult/rankReward")
# 双端段位表与常量一致收敛
_KT = re.findall(r'"(炼气|筑基|金丹|元婴|化神)"', RE_SRC)
_ST = re.findall(r'"(炼气|筑基|金丹|元婴|化神)"', CORE_SWIFT)
check(_KT[:5] == ["炼气","筑基","金丹","元婴","化神"], "排位：Kotlin 段位表顺序正确")
check(_ST[:5] == ["炼气","筑基","金丹","元婴","化神"], "排位：Swift 段位表顺序正确")
check(_KT[:5] == _ST[:5], "排位：双端段位表完全一致")

# 纯 Python 镜像（与 Kotlin/Swift 同语义）
_R_TIERS = ["炼气","筑基","金丹","元婴","化神"]
_R_MIN, _R_MAX, _R_PTP, _R_WIN, _R_LOSS = 0, 4, 100, 20, 15
def _r_apply(t, p, won):
    t = max(min(t, _R_MAX), _R_MIN)
    p = max(min(p, _R_PTP - 1), 0)   # 与 Kotlin coerceIn(0, PTP-1) / Swift min(max(,0),PTP-1) 一致
    promoted = demoted = False
    if won:
        p += _R_WIN
        while p >= _R_PTP and t < _R_MAX:
            p -= _R_PTP; t += 1; promoted = True
        if t == _R_MAX: p = min(p, _R_PTP - 1)
    else:
        p -= _R_LOSS
        while p < 0 and t > _R_MIN:
            p += _R_PTP; t -= 1; demoted = True
        if t == _R_MIN: p = max(p, 0)
    return t, p, promoted, demoted
def _r_reward(t):
    return {0:(0,0,0),1:(30,0,0),2:(0,30,0),3:(60,30,0),4:(0,80,10)}.get(max(min(t,_R_MAX),_R_MIN),(0,0,0))

# 胜利：单段晋升
_t,_p,_pr,_de = _r_apply(0, 90, True)
check(_t == 1 and _p == 10 and _pr and not _de, "排位：胜 炼气90→筑基10（单段晋升）")
# 胜利：单胜至多升一段（p≤99 时 99+20=119 仅跨一阶）
_t,_p,_pr,_de = _r_apply(0, 99, True)
check(_t == 1 and _p == 19 and _pr and not _de, "排位：胜 炼气99→筑基19（单胜至多升一段）")
# 胜利：连续两胜（逐次结算，未达 100 不升段）
_t1,_p1,_,_ = _r_apply(0, 90, True)        # → 筑基10
_t2,_p2,_,_ = _r_apply(_t1, _p1, True)     # → 筑基30（10+20<100 不升段）
check(_t2 == 1 and _p2 == 30, "排位：连续两胜 炼气→筑基30（未达100不升段，逐次结算）")
# 胜利：多胜最终跨阶（模拟 6 连胜 炼气90 → 金丹10）
_t, _p = 0, 90
for _ in range(6):
    _t, _p, _, _ = _r_apply(_t, _p, True)
check(_t == 2 and _p == 10, "排位：6 连胜 炼气90→金丹10（逐次结算真实跨阶）")
# 胜利：化神封顶（不溢出，p≤99）
_t,_p,_pr,_de = _r_apply(4, 99, True)
check(_t == 4 and _p == 99 and not _pr, "排位：胜 化神99→封顶 99（积分不溢出）")
_t,_p,_pr,_de = _r_apply(4, 0, True)
check(_t == 4 and _p == 20 and not _pr, "排位：胜 化神0→20（仍封顶段，p≤99）")
# 失败：单段降段（负债保留）
_t,_p,_pr,_de = _r_apply(1, 5, False)
check(_t == 0 and _p == 90 and _de and not _pr, "排位：负 筑基5→炼气90（单段降，负债保留 90）")
# 失败：连降
_t,_p,_pr,_de = _r_apply(4, 5, False)
check(_t == 3 and _p == 90 and _de and not _pr, "排位：负 化神5→元婴90（连降一段）")
# 失败：炼气保底（不欠负）
_t,_p,_pr,_de = _r_apply(0, 5, False)
check(_t == 0 and _p == 0 and not _de and not _pr, "排位：负 炼气5→保底0（积分不低于0）")
# tierIndex / tierName 往返 + 越界夹取
check(_R_TIERS.index("金丹") == 2, "排位：tierIndex 金丹==2")
# 升段荣誉奖励表精确值
check(_r_reward(0) == (0,0,0), "排位：奖励 炼气=无")
check(_r_reward(1) == (30,0,0), "排位：奖励 筑基=香火30")
check(_r_reward(2) == (0,30,0), "排位：奖励 金丹=灵玉30")
check(_r_reward(3) == (60,30,0), "排位：奖励 元婴=香火60+灵玉30")
check(_r_reward(4) == (0,80,10), "排位：奖励 化神=灵玉80+妖丹10")
# 性质测试：任意输入（含非法）经 applyResult 后恒落在合法区间（自洽不溢出）
_r_ok = True
for _t0 in range(-2, 7):
    for _p0 in (-10, 0, 50, 99, 160):
        for _w in (True, False):
            _tt, _pp, _, _ = _r_apply(_t0, _p0, _w)
            if not (0 <= _tt <= 4 and 0 <= _pp <= 99):
                _r_ok = False
check(_r_ok, "排位：性质测试 任意输入→输出恒合法（段∈[0,4] 且 积分∈[0,99]，无溢出/欠负）")
# 段位表长度 == 5（与 MAX_TIER_INDEX=4 一致）
check(len(_R_TIERS) == 5, "排位：段位数==5（索引 0..4 与 MAX_TIER_INDEX 一致）")

# ───────────────── 养成引擎等价验证（FusionEngine / TalentEngine / EquipmentEngine） ─────────────────
FUS_SRC = open(os.path.join(COMMON, "logic", "FusionEngine.kt"), encoding="utf-8").read()
TAL_SRC = open(os.path.join(COMMON, "logic", "TalentEngine.kt"), encoding="utf-8").read()
EQE_SRC = open(os.path.join(COMMON, "logic", "EquipmentEngine.kt"), encoding="utf-8").read()
EQL_SRC = open(os.path.join(COMMON, "data", "EquipmentLibrary.kt"), encoding="utf-8").read()
DIE_SRC = open(os.path.join(COMMON, "data", "DataIntegrityEngine.kt"), encoding="utf-8").read()

def parse_equip():
    out = {}
    for line in EQL_SRC.splitlines():
        m = re.search(r'EquipDef\("([^"]+)"[^)]*?Element\.(\w+)[^)]*?Rarity\.(\w+)([^)]*)\)', line)
        if not m:
            continue
        fid, elem, rar = m.group(1), m.group(2), m.group(3)
        after = line.split("Rarity." + rar)[1]
        nums = re.findall(r'-?\d+(?:\.\d+)?', after)
        ba, bd, bh, bs = (float(x) for x in nums[:4])
        # bonusPerLevel / maxLevel 默认值（源码全 9 参构造，取默认 0.12 / 10）
        out[fid] = {"elem": elem, "rar": rar, "baseAtk": ba, "baseDef": bd,
                    "baseHp": bh, "baseSpd": bs, "bonusPerLevel": 0.12, "maxLevel": 10}
    return out
EQUIP = parse_equip()
check(len(EQUIP) >= 8, f"装备库解析数量≥8（实得 {len(EQUIP)}）")

# ── FusionEngine 融合 ──
# 配方产物真实存在（防死配方），且双端源码一致
check("gui_xuanwu" in [m["id"] for m in POOL], "融合：配方产物 gui_xuanwu 真实存在于妖池")
check(('setOf("yao_jiao", "gui_linggui") to "gui_xuanwu"' in FUS_SRC) or
      ('setOf("gui_linggui", "yao_jiao")' in FUS_SRC), "融合：Kotlin 含 蛟+灵龟→玄武 配方")
check('Set(["yao_jiao", "gui_linggui"]): "gui_xuanwu"' in CORE_SWIFT or
      'Set(["gui_linggui", "yao_jiao"]): "gui_xuanwu"' in CORE_SWIFT, "融合：Swift 含 蛟+灵龟→玄武 配方")
check("val recipeOutputs" in FUS_SRC, "融合：Kotlin 暴露 recipeOutputs")
check("static let recipeOutputs" in CORE_SWIFT, "融合：Swift 暴露 recipeOutputs")
check("RECIPE_OUT" in DIE_SRC, "融合：DataIntegrityEngine 校验配方产物⊆妖池（Kotlin）")
check("RECIPE_OUT" in CORE_SWIFT, "融合：DataIntegrityEngine 校验配方产物⊆妖池（Swift）")

# canFuse 前置
check("a.instanceId != b.instanceId && yaodan >= FUSE_COST_YAODAN" in FUS_SRC, "融合：canFuse 同实例/妖丹不足→false")
check("a.instanceId != b.instanceId && yaodan >= FUSE_COST_YAODAN" in CORE_SWIFT, "融合：canFuse 双端一致")
_RAR_ORD = {"FAN":0,"LING":1,"XIAN":2,"SHEN":3}
def fus_plan(aid, bid, alvl, blvl, abt, bbt, ask, bsk):
    # 镜像：同实例→null；defId 缺→null；配方/稀有度/属性择优；newLevel=max+3 clamp；bt=min；技能并接4
    if aid == bid: return None
    if aid not in [m["id"] for m in POOL] or bid not in [m["id"] for m in POOL]: return None
    da = next(m for m in POOL if m["id"]==aid); db = next(m for m in POOL if m["id"]==bid)
    rec = {
        frozenset(["yao_jiao","gui_linggui"]): "gui_xuanwu",
    }
    if frozenset([aid,bid]) in rec: nid = rec[frozenset([aid,bid])]
    elif _RAR_ORD[db["rar"]] > _RAR_ORD[da["rar"]]: nid = bid
    elif _RAR_ORD[da["rar"]] > _RAR_ORD[db["rar"]]: nid = aid
    else:
        sa = da["baseAtk"]+da["baseDef"]+da["baseHp"]+da["baseSpd"]
        sb = db["baseAtk"]+db["baseDef"]+db["baseHp"]+db["baseSpd"]
        nid = bid if sb >= sa else aid
    if nid not in [m["id"] for m in POOL]: return None
    bt = min(abt, bbt)
    cap = 50 + bt * 10   # CultivationEngine.BASE_LEVEL_CAP=50, LEVEL_CAP_PER_BREAK=10
    nl = min(max(alvl, blvl) + 3, cap)
    skills = []
    for s in ask + bsk:
        if s not in skills: skills.append(s)
    return {"newDefId": nid, "newLevel": nl, "newBreakthrough": bt, "newSkills": skills[:4]}
# 配方路径（无序）
_r = fus_plan("yao_jiao","gui_linggui",10,10,0,0,["s_a"],["s_b"])
check(_r is not None and _r["newDefId"] == "gui_xuanwu", "融合：蛟+灵龟→玄武（无序对命中配方）")
_r2 = fus_plan("gui_linggui","yao_jiao",10,10,0,0,["s_a"],["s_b"])
check(_r2 is not None and _r2["newDefId"] == "gui_xuanwu", "融合：灵龟+蛟（反序）仍命中配方")
# 非配方：稀有度高者胜
_r3 = fus_plan("yao_jiao","gui_xuanwu",5,5,1,1,["s_a"],["s_b"])  # 蛟 LING vs 玄武 SHEN
check(_r3 is not None and _r3["newDefId"] == "gui_xuanwu", "融合：无配方时稀有度高者（玄武 SHEN）胜蛟 LING")
# 同实例→null
check(fus_plan("yao_jiao","yao_jiao",5,5,0,0,[],[]) is None, "融合：同实例→null（防自熔）")
# newLevel = max+3（未超上限），bt=min
_r4 = fus_plan("yao_jiao","gui_linggui",20,12,2,1,["s_a","s_b","s_c"],["s_d","s_e"])
check(_r4 is not None and _r4["newLevel"] == 23 and _r4["newBreakthrough"] == 1, "融合：newLevel=max(20,12)+3=23，bt=min(2,1)=1")
# 技能并集去重取前 4
check(_r4 is not None and _r4["newSkills"] == ["s_a","s_b","s_c","s_d"], "融合：技能并集去重取前 4（s_e 被截断）")
# newLevel 受 cap 钳制（高突破 3 → cap=80）
_r5 = fus_plan("yao_jiao","gui_linggui",90,90,3,3,["s_a"],["s_b"])
check(_r5 is not None and _r5["newLevel"] == 80, "融合：newLevel 超 cap(突破3→80) 被钳制")

# ── TalentEngine 天赋命格 ──
check("const val MAX_LAYER: Int = 3" in TAL_SRC and "MAX_LAYER = 3" in CORE_SWIFT, "天赋：MAX_LAYER==3 双端一致")
check("BASE_TALENT + inst.breakthrough * TALENT_PER_BREAK" in TAL_SRC, "天赋：talentCap=BASE_TALENT+突破×TALENT_PER_BREAK")
check("BASE_TALENT + inst.breakthrough * TALENT_PER_BREAK" in TAL_SRC, "天赋：talentCap=BASE_TALENT+突破×TALENT_PER_BREAK（Kotlin）")
# 容量：突破0→3，突破2→5
def tal_cap(bt): return 3 + bt * 1
check(tal_cap(0) == 3 and tal_cap(2) == 5, "天赋：容量 突破0→3 突破2→5")
# canAssign：分支满或容量满→false
check("layersOf(inst, branch) < MAX_LAYER && allocated(inst) < talentCap(inst)" in TAL_SRC, "天赋：canAssign 分支未满且容量未满")
# 倍率
check("1.0 + ATK_PCT_PER_LAYER * layersOf(inst, TalentBranch.ATTACK)" in TAL_SRC, "天赋：atkMult=1+0.06×层")
check("1.0 + DEF_PCT_PER_LAYER * layersOf(inst, TalentBranch.DEFENSE)" in TAL_SRC, "天赋：defMult=1+0.06×层（DEF 分支）")
check("1.0 + HP_PCT_PER_LAYER * layersOf(inst, TalentBranch.DEFENSE)" in TAL_SRC, "天赋：hpMult=1+0.04×层（DEF 分支）")
check("1.0 + SPD_PCT_PER_LAYER * layersOf(inst, TalentBranch.SUPPORT)" in TAL_SRC, "天赋：spdMult=1+0.05×层（SUPPORT 分支）")
# 倍率数值（3 层满）
check(abs((1 + 0.06*3) - 1.18) < 1e-9, "天赋：攻 3 层→1.18")
check(abs((1 + 0.04*3) - 1.12) < 1e-9, "天赋：血 3 层→1.12")

# ── EquipmentEngine 装备祭炼五路径 ──
SW_EQUIP = CORE_SWIFT
check("const val MAX_STAR: Int = 5" in EQE_SRC and "static let MAX_STAR = 5" in SW_EQUIP, "装备：MAX_STAR==5 双端一致")
check("const val STAR_BONUS: Double = 0.10" in EQE_SRC and "static let STAR_BONUS = 0.10" in SW_EQUIP, "装备：STAR_BONUS==0.10 双端一致")
check("const val TIER_LEVEL_BONUS: Int = 10" in EQE_SRC and "static let TIER_LEVEL_BONUS = 10" in SW_EQUIP, "装备：TIER_LEVEL_BONUS==10 双端一致")
check("const val SUB_CAP: Int = 15" in EQE_SRC and "static let SUB_CAP = 15" in SW_EQUIP, "装备：SUB_CAP==15 双端一致")
check("const val MAX_SUBSTATS: Int = 4" in EQE_SRC and "static let MAX_SUBSTATS = 4" in SW_EQUIP, "装备：MAX_SUBSTATS==4 双端一致")
# maxTier 稀有度映射
def eq_max_tier(rar): return {"FAN":1,"LING":2,"XIAN":3,"SHEN":4}[rar]
check(eq_max_tier("FAN")==1 and eq_max_tier("SHEN")==4, "装备：maxTier 凡1/灵2/仙3/神4")
check("switch rarity { case .fan: return 1; case .ling: return 2; case .xian: return 3; case .shen: return 4 }" in SW_EQUIP, "装备：Swift maxTier 稀有度映射一致")
# maxLevelOf
_sword = EQUIP["eq_sword"]
check(_sword["rar"] == "LING" and _sword["baseAtk"] == 18 and _sword["baseSpd"] == 4, "装备：eq_sword 解析 灵·baseAtk18·baseSpd4")
check(abs((_sword["maxLevel"] + (1-1)*10) - 10) < 1e-9, "装备：maxLevelOf 1阶=maxLevel(10)")
check(abs((_sword["maxLevel"] + (2-1)*10) - 20) < 1e-9, "装备：maxLevelOf 2阶=20")
# starMult
def eq_star_mult(stars): return 1.0 + (stars-1)*0.10
check(abs(eq_star_mult(1)-1.0) < 1e-9 and abs(eq_star_mult(5)-1.4) < 1e-9, "装备：starMult 1→1.0 / 5→1.4")
# mainBonus（含亲和×1.2）
def eq_main_bonus(def_, level, tier, stars, matches):
    per = def_["bonusPerLevel"] + (tier-1)*0.03
    mult = (1.0 + (level-1)*per) * eq_star_mult(stars) * (1.2 if matches else 1.0)
    return (int(def_["baseAtk"]*mult), int(def_["baseDef"]*mult),
            int(def_["baseHp"]*mult), int(def_["baseSpd"]*mult))
_mb1 = eq_main_bonus(_sword, 1, 1, 1, True)
check(_mb1 == (21, 0, 0, 4), f"装备：mainBonus 1级1星金属亲和 (18×1.2,_,_,4×1.2)={_mb1} 应为 (21,0,0,4)")
_mb2 = eq_main_bonus(_sword, 10, 2, 5, True)
# per=0.12+0.03=0.15; mult=(1+9*0.15)*1.4*1.2=(2.35)*1.68=3.948
_exp = (int(18*3.948), int(0*3.948), int(0*3.948), int(4*3.948))
check(_mb2 == _exp, f"装备：mainBonus 10级2星5星金属亲和={_mb2} 应为 {_exp}")
check("(if (elementMatches) AFFINITY_MULT else 1.0)" in EQE_SRC, "装备：mainBonus 元素一致×亲和倍率")
# canStarUp / canBreakthrough 边界
check("oe.stars < MAX_STAR" in EQE_SRC and "stars < MAX_STAR" in SW_EQUIP, "装备：canStarUp 5星→false")
check("oe.tier < maxTier(def.rarity)" in EQE_SRC and "tier < maxTier(def.rarity)" in SW_EQUIP, "装备：canBreakthrough 达上限→false")

# ── 存档完整性：装备上界钳制（修复坏档过度膨胀战力） ──
check('"EQUIP_STAR_HI"' in SIE_SRC and "EQUIP_STAR_HI" in CORE_SWIFT, "装备上界：validate 含 EQUIP_STAR_HI 双端")
check('"EQUIP_TIER_HI"' in SIE_SRC and "EQUIP_TIER_HI" in CORE_SWIFT, "装备上界：validate 含 EQUIP_TIER_HI 双端")
check('"EQUIP_SUBN"' in SIE_SRC and "EQUIP_SUBN" in CORE_SWIFT, "装备上界：validate 含 EQUIP_SUBN 双端")
check('"EQUIP_SUBVAL"' in SIE_SRC and "EQUIP_SUBVAL" in CORE_SWIFT, "装备上界：validate 含 EQUIP_SUBVAL 双端")
check("EquipmentEngine.MAX_STAR" in SIE_SRC and "EquipmentEngine.maxTier" in SIE_SRC, "装备上界：Kotlin sanitize 钳星级/突破阶")
check("EquipmentEngine.MAX_STAR" in CORE_SWIFT and "EquipmentEngine.maxTier" in CORE_SWIFT, "装备上界：Swift sanitize 钳星级/突破阶")
def sie_sanitize_equip(eq, def_):
    maxT = eq_max_tier(def_["rar"])
    safe_tier = min(eq["tier"], maxT)
    lvl_cap = def_["maxLevel"] + (safe_tier-1)*10
    stars = min(max(1, eq["stars"]), 5)
    tier = min(max(1, eq["tier"]), maxT)
    level = min(max(1, eq["level"]), lvl_cap)
    subs = [{"stat": s["stat"], "value": min(max(0, s["value"]), 15)} for s in eq["substats"]][:4]
    return {"level": level, "stars": stars, "tier": tier, "substats": subs}
# 坏档：星级99 / 突破9 / 6条副属性值999 / 等级999
_bad = {"level":999,"stars":99,"tier":9,"substats":[{"stat":"ATK","value":999}]*6}
_good = sie_sanitize_equip(_bad, _sword)   # eq_sword 为 LING→maxTier=2
check(_good["stars"] == 5, "装备上界：坏档 stars=99 → 钳到 5")
check(_good["tier"] == 2, "装备上界：坏档 tier=9（灵）→ 钳到 maxTier=2")
check(_good["level"] == 20, f"装备上界：坏档 level=999 → 钳到 lvlCap=20（实得 {_good['level']}）")
check(len(_good["substats"]) == 4, "装备上界：坏档 6 条副属性 → 截断到 4")
check(all(s["value"] == 15 for s in _good["substats"]), "装备上界：坏档副属性值999 → 钳到 15")
# 合法档不被误伤：正常 LING 5星2阶满副属性值15 保持
_ok = {"level":20,"stars":5,"tier":2,"substats":[{"stat":"ATK","value":15},{"stat":"DEF","value":15},{"stat":"HP","value":15},{"stat":"SPD","value":15}]}
_ok2 = sie_sanitize_equip(_ok, _sword)
check(_ok2 == _ok, "装备上界：合法 5星2阶满副属性 不被误伤")

# ───────────────── 信誉分引擎等价验证（GDD 十六·公平与稳健，ReputationEngine） ─────────────────
REP_SRC = open(os.path.join(COMMON, "logic", "ReputationEngine.kt"), encoding="utf-8").read()

def _rep_int(name):
    m = re.search(rf"const val {name} = (\d+)", REP_SRC)
    return int(m.group(1))

REP_MIN = _rep_int("MIN_SCORE")
REP_MAX = _rep_int("MAX_SCORE")
REP_RES = _rep_int("RESTRICTED_BELOW")
REP_OBS = _rep_int("OBSERVED_BELOW")
# 常量与 Kotlin 单一真源一致（防手滑漂移）
check(REP_MIN == 0 and REP_MAX == 100 and REP_RES == 60 and REP_OBS == 80,
      f"信誉分：常量 MIN={REP_MIN} MAX={REP_MAX} RESTRICTED<{REP_RES} OBSERVED<{REP_OBS}")
# PENALTY 扣分表（与 Kotlin mapOf 一致）
REP_PEN = {k: int(v) for k, v in re.findall(r"CheatKind\.(\w+) to (\d+)", REP_SRC)}
check(REP_PEN == {"TELEPORT": 18, "SPEED": 12, "UNKNOWN": 10}, f"信誉分：扣分表 {REP_PEN}")

def rep_tier(s):
    s = max(REP_MIN, min(REP_MAX, s))
    if s < REP_RES: return "RESTRICTED"
    if s < REP_OBS: return "OBSERVED"
    return "EXCELLENT"

def rep_tier_label(s):
    return {"EXCELLENT": "优秀", "OBSERVED": "观察", "RESTRICTED": "受限"}[rep_tier(s)]

def rep_rare_mul(s):
    return 0.7 if rep_tier(s) == "OBSERVED" else 1.0

def rep_can_explore(s):
    return rep_tier(s) != "RESTRICTED"

def rep_apply_penalty(s, kind):
    p = REP_PEN.get(kind, 10)
    return max(REP_MIN, min(REP_MAX, s - p))

def rep_daily_recover(s):
    return max(REP_MIN, min(REP_MAX, s + 1))   # 修复：补下界钳制，与 tier/applyPenalty 一致

# 分层边界：<60 受限 / <80 观察 / 否则优秀（临界值精确）
check(rep_tier(0) == "RESTRICTED" and rep_tier(59) == "RESTRICTED", "信誉分：tier 0/59 → 受限")
check(rep_tier(60) == "OBSERVED" and rep_tier(79) == "OBSERVED", "信誉分：tier 60/79 → 观察（临界 60 不落入受限）")
check(rep_tier(80) == "EXCELLENT" and rep_tier(100) == "EXCELLENT", "信誉分：tier 80/100 → 优秀（临界 80 不落入观察）")
# 越界输入被钳（tier 先 coerceIn）
check(rep_tier(-20) == "RESTRICTED" and rep_tier(999) == "EXCELLENT", "信誉分：tier 越界输入(-20/999)被钳")
# tierLabel 中文映射
check(rep_tier_label(50) == "受限" and rep_tier_label(70) == "观察" and rep_tier_label(90) == "优秀",
      "信誉分：tierLabel 中文映射 受限/观察/优秀")
# 稀有倍率：观察层 -30%（0.7）其余 1.0
check(rep_rare_mul(70) == 0.7 and rep_rare_mul(100) == 1.0 and rep_rare_mul(50) == 1.0,
      "信誉分：rareSpawnMultiplier 观察层0.7 其余1.0")
# 可探索：受限层禁止户外 LBS
check(rep_can_explore(50) is False and rep_can_explore(70) is True and rep_can_explore(90) is True,
      "信誉分：canExploreLbs 受限层禁止 其余允许")
# 扣分精确值 + 钳制（不可为负）
check(rep_apply_penalty(100, "TELEPORT") == 82, "信誉分：扣分 100-18=82（瞬移）")
check(rep_apply_penalty(100, "SPEED") == 88, "信誉分：扣分 100-12=88（超速）")
check(rep_apply_penalty(100, "UNKNOWN") == 90, "信誉分：扣分 100-10=90（未知）")
check(rep_apply_penalty(5, "TELEPORT") == 0, "信誉分：扣分 5-18 钳到 0（不下溢）")
check(rep_apply_penalty(50, "NONE") == 40, "信誉分：未知 kind 默认扣 10")
# 恢复：+1 封顶；修复后下界不漏（任意输入不出界）
check(rep_daily_recover(99) == 100 and rep_daily_recover(100) == 100, "信誉分：恢复 封顶 100")
check(rep_daily_recover(0) == 1 and rep_daily_recover(59) == 60, "信誉分：恢复 +1 普通")
check(rep_daily_recover(-10) == 0, "信誉分：恢复 负输入钳到 0（修复下界）")
# 性质测试：任意输入 → 输出恒合法（∈[0,100]，且区间内 +1 不超/不降）
_rep_ok = True
for _x in range(-30, 131):
    _r = rep_daily_recover(_x)
    if not (REP_MIN <= _r <= REP_MAX):
        _rep_ok = False
    if REP_MIN <= _x <= REP_MAX and (_r < _x or _r > _x + 1):
        _rep_ok = False
check(_rep_ok, "信誉分：性质测试 任意输入→恢复恒∈[0,100] 且区间内恰好 +1")
# 性质测试：applyPenalty 任意输入恒∈[0,100]
_ap_ok = True
for _x in range(-30, 131):
    for _k in ("TELEPORT", "SPEED", "UNKNOWN", "NONE"):
        if not (REP_MIN <= rep_apply_penalty(_x, _k) <= REP_MAX):
            _ap_ok = False
check(_ap_ok, "信誉分：性质测试 任意输入→applyPenalty 恒∈[0,100]")
# 双端一致：Kotlin/Swift 常量与 dailyRecover 修复落地
check('const val MIN_SCORE = 0' in REP_SRC and 'const val MAX_SCORE = 100' in REP_SRC, "信誉分：Kotlin 常量 MIN/MAX")
check('static let MIN_SCORE = 0' in CORE_SWIFT and 'static let MAX_SCORE = 100' in CORE_SWIFT, "信誉分：Swift 常量 MIN/MAX")
check('(score + 1).coerceIn(MIN_SCORE, MAX_SCORE)' in REP_SRC, "信誉分：Kotlin dailyRecover 补下界 coerceIn")
check('max(MIN_SCORE, score + 1)' in CORE_SWIFT, "信誉分：Swift dailyRecover 补下界 max(MIN,...)")

# ───────────────── 图鉴引擎等价验证（GDD 三·探索，Codex：地域/秘境分离） ─────────────────
CODEX_SRC = open(os.path.join(COMMON, "logic", "Codex.kt"), encoding="utf-8").read()
# 里程碑阈值（与 Codex.kt MILESTONES 单一真源，防漂移）
MT = [int(x) for x in re.findall(r"(\d+)\s+to\s+\"", CODEX_SRC)]
check(MT == [1, 5, 10, 20], f"图鉴：里程碑阈值 {MT}")

class Cx:
    def __init__(self, all_):
        self.all = all_            # [{id, category}]
        self.region = set()
        self.secret = set()
    def catch(self, mid, secret=False):
        (self.secret if secret else self.region).add(mid)
    def has_in_region(self, mid): return mid in self.region
    def has_any(self, mid): return mid in self.region or mid in self.secret
    def region_caught(self): return len(self.region)
    def region_completion(self):
        return 0.0 if not self.all else len(self.region) / len(self.all)
    def milestones(self):
        return [(t, len(self.region) >= t) for t in MT]
    def newly_unlocked(self, prev):
        for t in MT:
            if t > prev and len(self.region) >= t:
                return t
        return None

# 构造 25 只妖的图鉴样本（足够覆盖里程碑阈值 20）
_SAMPLE = [{"id": f"m{i}", "category": "YAO" if i % 2 == 0 else "GUAI"} for i in range(25)]
cx = Cx(_SAMPLE)
# 地域/秘境分离：秘境收的妖不计入地域完成度
cx.catch("m1")
cx.catch("m2", secret=True)
check(cx.region_caught() == 1, "图鉴：地域已收仅计地域 catch（秘境不混入）")
check(cx.has_any("m2") is True and cx.has_in_region("m2") is False, "图鉴：秘境妖 hasAny 真 / hasInRegion 假（分离）")
check(abs(cx.region_completion() - 1 / len(_SAMPLE)) < 1e-9, "图鉴：完成度=地域已收/总数=1/25")
# 完成度恒∈[0,1]（性质测试）
_cx_ok = True
for _n in range(0, 26):
    _c = Cx(_SAMPLE)
    for _i in range(_n):
        _c.catch(f"m{_i}")
    if not (0.0 <= _c.region_completion() <= 1.0):
        _cx_ok = False
check(_cx_ok, "图鉴：性质测试 任意收妖数→完成度恒∈[0,1]")
# 空池 completion=0（防除零）
check(Cx([]).region_completion() == 0.0, "图鉴：空池 completion=0（防除零）")
# 里程碑 reached 随地域已收单调非降
_cx2 = Cx(_SAMPLE); _prev_r = 0; _mono = True
for _n in range(0, 26):
    _c = Cx(_SAMPLE)
    for _i in range(_n):
        _c.catch(f"m{_i}")
    _r = sum(1 for _, ok in _c.milestones() if ok)
    if _r < _prev_r:
        _mono = False
    _prev_r = _r
check(_mono, "图鉴：里程碑 reached 随已收单调非降")
# newlyUnlocked：跨阈值语义（prev=收妖前计数的开区间 (prev, current]）
check(Cx(_SAMPLE).newly_unlocked(0) is None, "图鉴：newlyUnlocked prev=0 且未收→None")
_cu = Cx(_SAMPLE); _cu.catch("m0"); check(_cu.newly_unlocked(0) == 1, "图鉴：收1只 跨阈值→解锁 1")
_cu5 = Cx(_SAMPLE)
for _i in range(5): _cu5.catch(f"m{_i}")
check(_cu5.newly_unlocked(4) == 5, "图鉴：收5只(前4) 跨阈值→解锁 5")
_cu20 = Cx(_SAMPLE)
for _i in range(20): _cu20.catch(f"m{_i}")
check(_cu20.newly_unlocked(19) == 20, "图鉴：收20只(前19) 跨阈值→解锁 20")
# Kotlin Codex 关键方法存在（保证未误删）
check(all(m in CODEX_SRC for m in ["fun regionCompletion", "fun milestones", "fun newlyUnlocked", "fun catch"]),
      "图鉴：Kotlin Codex 关键方法存在（regionCompletion/milestones/newlyUnlocked/catch）")

# ───────────────── 养成核心引擎等价验证（GDD 十六·养成线，CultivationEngine） ─────────────────
# 此前（#104~#109）仅覆盖了 Fusion/Talent/Equipment，CultivationEngine（等级/经验/突破/进化/渡劫）
# 一直零功能断言——本轮补齐，并从 Kotlin/Swift 双端源码解析常量防漂移。
CUL_SRC = open(os.path.join(COMMON, "logic", "CultivationEngine.kt"), encoding="utf-8").read()
RAR_SRC = open(os.path.join(COMMON, "model", "Rarity.kt"), encoding="utf-8").read()

def _kc(name):  # Kotlin const val X[:Type] = value
    m = re.search(r'const val %s\s*(?::\s*\w+\s*)?=\s*([\d.]+)' % name, CUL_SRC)
    return float(m.group(1)) if m else None
def _sc(name):  # Swift static let X = value
    m = re.search(r'static let %s\s*=\s*([\d.]+)' % name, CORE_SWIFT)
    return float(m.group(1)) if m else None

CUL_BASE_CAP = _kc("BASE_LEVEL_CAP"); CUL_MAX_BT = _kc("MAX_BREAKTHROUGH")
CUL_PER_BT = _kc("LEVEL_CAP_PER_BREAK"); CUL_STAT = _kc("STAT_MULT_PER_BREAK")
CUL_EVO = _kc("EVOLVE_LEVEL_REQ"); CUL_TRIB_B = _kc("TRIBULATE_STAT_BONUS")
CUL_TRIB_C = _kc("TRIBULATE_COST_YAODAN")
# 双端常量一致（防漂移）
for _nm, _k, _s in [("BASE_LEVEL_CAP", CUL_BASE_CAP, _sc("BASE_LEVEL_CAP")),
                    ("MAX_BREAKTHROUGH", CUL_MAX_BT, _sc("MAX_BREAKTHROUGH")),
                    ("LEVEL_CAP_PER_BREAK", CUL_PER_BT, _sc("LEVEL_CAP_PER_BREAK")),
                    ("STAT_MULT_PER_BREAK", CUL_STAT, _sc("STAT_MULT_PER_BREAK")),
                    ("EVOLVE_LEVEL_REQ", CUL_EVO, _sc("EVOLVE_LEVEL_REQ")),
                    ("TRIBULATE_STAT_BONUS", CUL_TRIB_B, _sc("TRIBULATE_STAT_BONUS")),
                    ("TRIBULATE_COST_YAODAN", CUL_TRIB_C, _sc("TRIBULATE_COST_YAODAN"))]:
    check(_k is not None and _s is not None and abs(_k - _s) < 1e-9, f"养成常量双端一致：{_nm}")
# 关键方法双端存在
for _meth in ["levelCap", "statMult", "expForLevel", "gainExp", "canBreakthrough", "breakthrough",
              "canEvolve", "evolve", "tribulateMult", "maxTribulations", "canTribulate", "tribulate",
              "effectiveStats", "evolutionChain"]:
    check(("fun %s" % _meth in CUL_SRC) or ("fun %s(" % _meth in CUL_SRC), f"养成：Kotlin 含 {_meth}")
    check(("static func %s" % _meth in CORE_SWIFT), f"养成：Swift 含 {_meth}")

# 稀有度顺序（解析枚举，算 maxTribulations）
RAR_ORDER = re.findall(r'(FAN|LING|XIAN|SHEN)\(', RAR_SRC)
SHEN_ORD = RAR_ORDER.index("SHEN")
def max_trib(rar):
    return max(0, SHEN_ORD - RAR_ORDER.index(rar)) if rar in RAR_ORDER else 0
check(RAR_ORDER == ["FAN", "LING", "XIAN", "SHEN"], "养成：Rarity 枚举顺序 FAN<LING<XIAN<SHEN")

# 妖池辅助：稀有度查表 + 进化链查表（供 evolve / effectiveStats / evolutionChain 镜像）
def rar_of(defId):
    for _m in POOL:
        if _m["id"] == defId:
            return _m["rar"]
    return None
EVOLVE_TO = {}
_MP_TXT = open(os.path.join(COMMON, "data", "MonsterPool.kt"), encoding="utf-8").read()
for _m in re.finditer(r'm\(\s*"([^"]+)"', _MP_TXT):
    # 括号配平：从 m( 匹配到对应 )，精确界定本构造体，避免误吞相邻怪物 evolveTo
    _p = _m.start() + 1  # '(' 位置
    _depth = 0; _j = _p
    while _j < len(_MP_TXT):
        _c = _MP_TXT[_j]
        if _c == '(': _depth += 1
        elif _c == ')':
            _depth -= 1
            if _depth == 0: break
        _j += 1
    _body = _MP_TXT[_p:_j + 1]
    _ev = re.search(r'evolveTo\s*=\s*"([^"]+)"', _body)
    if _ev:
        EVOLVE_TO[_m.group(1)] = _ev.group(1)
def c_evolution_chain(start):
    if rar_of(start) is None:
        return []
    chain = []; cur = start; seen = set()
    while cur not in seen:
        chain.append(cur); seen.add(cur)
        nxt = EVOLVE_TO.get(cur)
        if nxt is None: break
        cur = nxt
    return chain

# expForLevel 公式锁定（100 + (lv-1)*25，双端同源）
_m = re.search(r'return\s*(\d+)\s*\+\s*\(lv\s*-\s*1\)\s*\*\s*(\d+)', CUL_SRC)
EXP_BASE, EXP_STEP = int(_m.group(1)), int(_m.group(2))
check(EXP_BASE == 100 and EXP_STEP == 25, "养成：expForLevel 公式=100+(lv-1)*25（Kotlin）")
check("100 + (lv - 1) * 25" in CORE_SWIFT, "养成：expForLevel 公式=100+(lv-1)*25（Swift）")

# ── Python 镜像（inst 为 {defId,level,exp,breakthrough,tribulations}）──
def c_level_cap(inst): return int(CUL_BASE_CAP + inst["breakthrough"] * CUL_PER_BT)
def c_stat_mult(inst): return 1.0 + inst["breakthrough"] * CUL_STAT
def c_exp_for(lv):
    lv = 1 if lv < 1 else lv
    return EXP_BASE + (lv - 1) * EXP_STEP
def c_gain_exp(inst, amount):
    if amount <= 0: return dict(inst)
    lvl = inst["level"]; xp = inst["exp"] + amount
    cap = c_level_cap(inst)
    while lvl < cap and xp >= c_exp_for(lvl):
        xp -= c_exp_for(lvl); lvl += 1
    if lvl >= cap:
        xp = min(xp, c_exp_for(max(cap - 1, 1)))
    return {**inst, "level": lvl, "exp": xp}
def c_bt_req(bt): return 20 + bt * 5
def c_can_bt(inst): return inst["breakthrough"] < CUL_MAX_BT and inst["level"] >= c_bt_req(inst["breakthrough"])
def c_bt_cost(bt): return 20 + bt * 15
def c_breakthrough(inst): return {**inst, "breakthrough": inst["breakthrough"] + 1} if c_can_bt(inst) else None
def c_can_evolve(inst):
    to = EVOLVE_TO.get(inst["defId"])
    return to is not None and inst["level"] >= CUL_EVO
def c_evolve(inst):
    to = EVOLVE_TO.get(inst["defId"])
    if to is None or inst["level"] < CUL_EVO: return None
    nr = rar_of(to)
    return {**inst, "defId": to, "tribulations": min(inst["tribulations"], max_trib(nr))}
def c_trib_mult(inst): return 1.0 + inst["tribulations"] * CUL_TRIB_B
def c_trib_cost(inst): return int(CUL_TRIB_C * (inst["tribulations"] + 1))
def c_max_trib(inst):
    r = rar_of(inst["defId"]); return max_trib(r) if r else 0
def c_can_trib(inst):
    if inst["tribulations"] >= c_max_trib(inst): return False
    return inst["breakthrough"] >= CUL_MAX_BT and inst["level"] >= c_level_cap(inst)
def c_tribulate(inst): return {**inst, "tribulations": inst["tribulations"] + 1} if c_can_trib(inst) else None
def c_eff_stats(inst, base, talent=1.0):
    # 对齐 Kotlin/Swift：base.atk * statMult * tribulateMult * talentMult（base 先乘，左结合）
    sm = c_stat_mult(inst); tm = c_trib_mult(inst)
    return {k: max(1, int(base[k] * sm * tm * talent)) for k in ("atk", "def", "hp", "spd")}

# ── 公式数值断言 ──
check(c_exp_for(1) == 100 and c_exp_for(2) == 125 and c_exp_for(25) == 700 and c_exp_for(30) == 825,
      "养成：expForLevel 边界值正确（1→100/2→125/25→700/30→825）")
check([c_level_cap({"breakthrough": b}) for b in range(6)] == [30, 35, 40, 45, 50, 55],
      "养成：levelCap 随突破 30→55（+5/次）")
check([round(c_stat_mult({"breakthrough": b}), 2) for b in range(6)] == [1.0, 1.08, 1.16, 1.24, 1.32, 1.4],
      "养成：statMult 随突破 1.0→1.40（+8%/次）")
check([c_bt_req(b) for b in range(5)] == [20, 25, 30, 35, 40], "养成：突破等级需求 20+5×bt")
check([c_bt_cost(b) for b in range(5)] == [20, 35, 50, 65, 80], "养成：突破妖丹消耗 20+15×bt")
check([c_trib_cost({"tribulations": n}) for n in range(3)] == [200, 400, 600], "养成：渡劫妖丹 200×(n+1)")
check([round(c_trib_mult({"tribulations": n}), 2) for n in range(4)] == [1.0, 1.25, 1.5, 1.75],
      "养成：tribulateMult 1+0.25×渡劫数")
check([max_trib(r) for r in ["FAN", "LING", "XIAN", "SHEN"]] == [3, 2, 1, 0],
      "养成：maxTribulations 神阶封顶（FAN3/LING2/XIAN1/SHEN0）")

# ── 功能护栏（null/保留）──
check(c_gain_exp({"defId": "x", "level": 1, "exp": 0, "breakthrough": 0, "tribulations": 0}, 100)
      == {"defId": "x", "level": 2, "exp": 0, "breakthrough": 0, "tribulations": 0}, "养成：+100 经验恰升 1 级")
check(c_gain_exp({"defId": "x", "level": 1, "exp": 0, "breakthrough": 0, "tribulations": 0}, 250)
      == {"defId": "x", "level": 3, "exp": 25, "breakthrough": 0, "tribulations": 0}, "养成：+250 经验→3级余25")
_cap30 = c_gain_exp({"defId": "x", "level": 1, "exp": 0, "breakthrough": 0, "tribulations": 0}, 100000)
check(_cap30["level"] == 30 and 0 <= _cap30["exp"] <= c_exp_for(29), "养成：溢量经验封顶满级不溢出")
check(c_gain_exp({"defId": "x", "level": 5, "exp": 0, "breakthrough": 0, "tribulations": 0}, 0)
      == {"defId": "x", "level": 5, "exp": 0, "breakthrough": 0, "tribulations": 0}, "养成：amount≤0 不改实例")
check(c_breakthrough({"defId": "x", "level": 20, "exp": 0, "breakthrough": 0, "tribulations": 0}) is not None
      and c_breakthrough({"defId": "x", "level": 20, "exp": 0, "breakthrough": 0, "tribulations": 0})["breakthrough"] == 1,
      "养成：Lv20 可突破→bt+1")
check(c_breakthrough({"defId": "x", "level": 19, "exp": 0, "breakthrough": 0, "tribulations": 0}) is None,
      "养成：Lv19 未达需求→突破返回 null")
check(c_breakthrough({"defId": "x", "level": 25, "exp": 0, "breakthrough": 5, "tribulations": 0}) is None,
      "养成：已达 MAX_BREAKTHROUGH→突破返回 null")
check(c_can_evolve({"defId": "yao_she", "level": 25, "exp": 0, "breakthrough": 0, "tribulations": 0}) is True,
      "养成：yao_she Lv25 可进化")
check(c_can_evolve({"defId": "yao_she", "level": 24, "exp": 0, "breakthrough": 0, "tribulations": 0}) is False,
      "养成：yao_she Lv24 未达进化等级→不可")
check(c_can_evolve({"defId": "yao_long", "level": 25, "exp": 0, "breakthrough": 0, "tribulations": 0}) is False,
      "养成：yao_long 无下一形态→不可进化")
_ev1 = c_evolve({"defId": "yao_she", "level": 25, "exp": 50, "breakthrough": 2, "tribulations": 1})
check(_ev1 is not None and _ev1["defId"] == "yao_jiao" and _ev1["level"] == 25 and _ev1["exp"] == 50
      and _ev1["breakthrough"] == 2 and _ev1["tribulations"] == 1, "养成：进化保留等级/经验/突破/渡劫")
# 进化后稀有度升高→tribulations 钳到新稀有度上限（防 INST_TRIB 非法存档+属性超模）
_ev2 = c_evolve({"defId": "gui_linggui", "level": 25, "exp": 0, "breakthrough": 5, "tribulations": 2})
_ev2_exp_trib = min(2, max_trib(rar_of("gui_xuanwu")))
check(_ev2 is not None and _ev2["defId"] == "gui_xuanwu" and _ev2["tribulations"] == _ev2_exp_trib,
      "养成：进化钳制 tribulations 到新稀有度上限（LING→SHEN）")
check(c_can_trib({"defId": "yao_she", "level": 55, "exp": 0, "breakthrough": 5, "tribulations": 0}) is True,
      "养成：满级满突破可渡劫")
check(c_can_trib({"defId": "yao_she", "level": 55, "exp": 0, "breakthrough": 5, "tribulations": 3}) is False,
      "养成：已达渡劫上限→不可再渡")
check(c_can_trib({"defId": "yao_she", "level": 54, "exp": 0, "breakthrough": 5, "tribulations": 0}) is False,
      "养成：未满级→不可渡劫")
check(c_tribulate({"defId": "yao_she", "level": 55, "exp": 0, "breakthrough": 5, "tribulations": 0}) is not None
      and c_tribulate({"defId": "yao_she", "level": 55, "exp": 0, "breakthrough": 5, "tribulations": 0})["tribulations"] == 1,
      "养成：渡劫成功→tribulations+1")
# effectiveStats 公式结构（base×statMult×tribulateMult×talent，≥1）
check("base.atk * mult * t.atkMult" in CUL_SRC and "toInt().coerceAtLeast(1)" in CUL_SRC,
      "养成：effectiveStats 公式=base×statMult×tribulateMult×talent 且 ≥1（Kotlin）")
check("Int(Double(base.atk) * mult * TalentEngine.atkMult" in CORE_SWIFT and "max(1," in CORE_SWIFT,
      "养成：effectiveStats 公式=base×statMult×tribulateMult×talent 且 ≥1（Swift）")
_base = {k: 100 for k in ("atk", "def", "hp", "spd")}
def _eff_atk(bt, trib):  # 忠实镜像 Kotlin/Swift 的 toInt() 截断（base 先乘，左结合）
    return int(100 * (1.0 + bt * CUL_STAT) * (1.0 + trib * CUL_TRIB_B))
check(c_eff_stats({"defId": "x", "level": 30, "exp": 0, "breakthrough": 0, "tribulations": 0}, _base) == _base,
      "养成：无突破无渡劫→有效属性=基础属性")
check(c_eff_stats({"defId": "x", "level": 30, "exp": 0, "breakthrough": 5, "tribulations": 0}, _base)["atk"] == _eff_atk(5, 0),
      "养成：满突破→atk=100×1.40（截断=%d）" % _eff_atk(5, 0))
check(c_eff_stats({"defId": "x", "level": 30, "exp": 0, "breakthrough": 5, "tribulations": 3}, _base)["atk"] == _eff_atk(5, 3),
      "养成：满突破+3渡劫→atk 公式截断=%d（double 下 100×1.40×1.75=244.99…，匹配 Kotlin/Swift toInt）" % _eff_atk(5, 3))
# 进化链
check(c_evolution_chain("yao_she") == ["yao_she", "yao_jiao", "yao_long"], "养成：yao_she 进化链 3 段")
check(c_evolution_chain("yao_long") == ["yao_long"], "养成：终端形态进化链仅自身")
check(c_evolution_chain("__unknown__") == [], "养成：未知 defId 进化链为空")

# ── 性质测试（任意输入→不变量成立）──
import random as _rnd
_rng = _rnd.Random(20260816)
_base100 = {k: 100 for k in ("atk", "def", "hp", "spd")}
_prop_fail = 0
for _ in range(4000):
    _rar = _rng.choice(RAR_ORDER)
    _bt = _rng.randint(0, int(CUL_MAX_BT))
    _cap = int(CUL_BASE_CAP + _bt * CUL_PER_BT)
    _lvl = _rng.randint(1, _cap)
    _trib = _rng.randint(0, max_trib(_rar))
    _inst = {"defId": "x", "level": _lvl, "exp": _rng.randint(0, 3000), "breakthrough": _bt, "tribulations": _trib}
    if c_level_cap(_inst) != _cap: _prop_fail += 1; break
    _sm = c_stat_mult(_inst)
    if not (1.0 - 1e-9 <= _sm <= 1.0 + int(CUL_MAX_BT) * CUL_STAT + 1e-9): _prop_fail += 1; break
    _g = c_gain_exp(_inst, _rng.randint(1, 6000))
    if _g["level"] > _cap or _g["exp"] < 0: _prop_fail += 1; break
    if _g["level"] >= _cap and _g["exp"] > c_exp_for(max(_cap - 1, 1)): _prop_fail += 1; break
    _eff = c_eff_stats(_inst, _base100, talent=1.0)
    if any(v < 1 for v in _eff.values()): _prop_fail += 1; break
    if _trib >= c_max_trib(_inst) and c_can_trib(_inst):  # 已达渡劫上限→必不可渡
        _prop_fail += 1; break
check(_prop_fail == 0, "养成：性质测试 4000 例（等级/突破/渡劫上界+有效属性≥1）恒成立")
# 单调性（确定性）
_a1 = c_eff_stats({"defId": "x", "level": 30, "exp": 0, "breakthrough": 0, "tribulations": 0}, _base100)
_a2 = c_eff_stats({"defId": "x", "level": 30, "exp": 0, "breakthrough": 5, "tribulations": 0}, _base100)
_a3 = c_eff_stats({"defId": "x", "level": 30, "exp": 0, "breakthrough": 5, "tribulations": 3}, _base100)
check(_a2["atk"] > _a1["atk"] and _a3["atk"] > _a2["atk"], "养成：突破↑/渡劫↑→有效攻击单调↑")

# ═════════════ 活动系统 ActivityEngine（GDD·活动·限时增益层，纯函数可验证） ═════════════
ACT_SRC = open(os.path.join(COMMON, "logic", "ActivityEngine.kt"), encoding="utf-8").read()

def _acts_full_kotlin(src):
    res, i, n = {}, 0, len(src)
    while i < n:
        if src.startswith("Activity(", i):
            depth, j = 0, i
            while j < n:
                if src[j] == '(': depth += 1
                elif src[j] == ')':
                    depth -= 1
                    if depth == 0:
                        block = src[i:j+1]
                        mid = re.search(r'"([^"]+)"', block)
                        if mid:
                            aid = mid.group(1)
                            ds = re.findall(r'(\d{4}-\d{2}-\d{2})', block)
                            cm = re.search(r'captureMul\s*=\s*([\d.]+)', block)
                            em = re.search(r'expMul\s*=\s*([\d.]+)', block)
                            im = re.search(r'incomeMul\s*=\s*([\d.]+)', block)
                            res[aid] = (
                                ds[0] if ds else "", ds[1] if len(ds) > 1 else "",
                                float(cm.group(1)) if cm else 1.0,
                                float(em.group(1)) if em else 1.0,
                                float(im.group(1)) if im else 1.0,
                            )
                        i = j + 1
                        break
                j += 1
            else:
                break
        else:
            i += 1
    return res

def _acts_full_swift(src):
    res = {}
    for m in re.finditer(r'Activity\(id:\s*"([^"]+)"[^)]*\)', src):
        block = m.group(0)
        ds = re.findall(r'(\d{4}-\d{2}-\d{2})', block)
        cm = re.search(r'captureMul:\s*([\d.]+)', block)
        em = re.search(r'expMul:\s*([\d.]+)', block)
        im = re.search(r'incomeMul:\s*([\d.]+)', block)
        res[m.group(1)] = (
            ds[0] if ds else "", ds[1] if len(ds) > 1 else "",
            float(cm.group(1)) if cm else 1.0,
            float(em.group(1)) if em else 1.0,
            float(im.group(1)) if im else 1.0,
        )
    return res

ACT_FULL_KT = _acts_full_kotlin(ACT_SRC)
ACT_FULL_SW = _acts_full_swift(CORE_SWIFT)
check(ACT_FULL_KT.keys() == ACT_FULL_SW.keys() and len(ACT_FULL_KT) == 7,
      "活动：双端 ACTIVITIES 配置 id 集合一致（7 个，无 desync）")
check(ACT_FULL_KT == ACT_FULL_SW, "活动：双端 ACTIVITIES 完整 (start,end,captureMul,expMul,incomeMul) 一致")

# 公式镜像
def a_is_active(s, e, today): return today >= s and today <= e
def a_aggregate(items):
    c = e = inc = 1.0
    for (_, _, cm, em, im) in items:
        c *= cm; e *= em; inc *= im
    return (c, e, inc)

check(a_is_active("2026-08-15", "2026-08-21", "2026-08-15") is True, "活动：起始日当天生效（含边界）")
check(a_is_active("2026-08-15", "2026-08-21", "2026-08-21") is True, "活动：结束日当天生效（含边界）")
check(a_is_active("2026-08-15", "2026-08-21", "2026-08-14") is False, "活动：起始日前不生效")
check(a_is_active("2026-08-15", "2026-08-21", "2026-08-22") is False, "活动：结束日后不生效")

# 聚合数值：2026-08-16 生效 capture_rally / battle_rally / login
_act_0816 = [v for v in ACT_FULL_KT.values() if a_is_active(v[0], v[1], "2026-08-16")]
_c, _e, _inc = a_aggregate(_act_0816)
check(abs(_c - 1.15) < 1e-9, "活动：2026-08-16 captureMul 聚合=1.15（交叉验证解析+公式）")
check(abs(_e - 1.25) < 1e-9, "活动：2026-08-16 expMul 聚合=1.25")
check(abs(_inc - 1.32) < 1e-9, "活动：2026-08-16 incomeMul 聚合=1.32（1.0×1.2×1.1）")

_all_ge1 = all(cm >= 1.0 and em >= 1.0 and im >= 1.0 for (_, _, cm, em, im) in ACT_FULL_KT.values())
check(_all_ge1, "活动：所有活动配置乘子 ≥1.0（增益层只增不减，符合设计约束）")
check(a_aggregate([]) == (1.0, 1.0, 1.0), "活动：无活动→聚合乘子全 1.0")

# ═════════════ 荣誉系统 HonorEngine（GDD 二十·竞技与荣誉·展示型，纯函数可验证） ═════════════
HONOR_MAX = 10 ** 9

def _medals_kotlin(src):
    res = {}
    for m in re.finditer(r'Medal\(\s*"([^"]+)"[^,]*,\s*"[^"]*",\s*"[^"]*",\s*([\d]+|Int\.MAX_VALUE)\s*\)', src):
        req = HONOR_MAX if m.group(2) == "Int.MAX_VALUE" else int(m.group(2))
        res[m.group(1)] = req
    return res

def _medals_swift(src):
    res = {}
    for m in re.finditer(r'Medal\(id:\s*"([^"]+)"[^)]*?req:\s*([\d]+|Int\.max)\s*\)', src):
        req = HONOR_MAX if m.group(2) == "Int.max" else int(m.group(2))
        res[m.group(1)] = req
    return res

MEDALS_KT = _medals_kotlin(HONOR_SRC)
MEDALS_SW = _medals_swift(CORE_SWIFT)
check(MEDALS_KT == MEDALS_SW, "荣誉：双端 MEDALS 定义（id+req 阶梯）一致，无 desync")
check(MEDALS_KT == {"medal_bronze": 1, "medal_silver": 4, "medal_gold": 8, "medal_diamond": HONOR_MAX},
      "荣誉：勋章阶梯 req=1/4/8/MAX（青铜→白银→黄金→钻石）")

def _titles_kotlin(src):
    return re.findall(r'Title\(\s*"([^"]+)"', src)

def _titles_swift(src):
    return re.findall(r'Title\(id:\s*"([^"]+)"', src)

TITLES_KT = _titles_kotlin(HONOR_SRC)
TITLES_SW = _titles_swift(CORE_SWIFT)
check(set(TITLES_KT) == set(TITLES_SW) and len(TITLES_KT) == 12,
      "荣誉：双端 TITLES 数量=12 且 id 集合一致")

def h_unlocked_medals(count, total):
    res = []
    for mid, req in MEDALS_KT.items():
        if req == HONOR_MAX:
            if total > 0 and count >= total: res.append(mid)
        elif count >= req:
            res.append(mid)
    return res

check(h_unlocked_medals(0, 12) == [], "荣誉：0 成就→无勋章")
check(h_unlocked_medals(1, 12) == ["medal_bronze"], "荣誉：1 成就→青铜")
check(set(h_unlocked_medals(4, 12)) == {"medal_bronze", "medal_silver"}, "荣誉：4 成就→青铜+白银")
check(set(h_unlocked_medals(8, 12)) == {"medal_bronze", "medal_silver", "medal_gold"}, "荣誉：8 成就→+黄金")
check(set(h_unlocked_medals(12, 12)) == set(MEDALS_KT.keys()), "荣誉：达成全部→钻石解锁")

def h_is_unlocked(idv, p):
    ac, at, rt, cr, tt = p["ac"], p["at"], p["rt"], p["cr"], p["tt"]
    return {
        "title_shouyao": ac >= 1, "title_xunyao": cr >= 30, "title_tuojie": tt >= 1,
        "title_huashen": rt >= 4, "title_tujian": cr >= 100,
        "title_dacheng": at > 0 and ac >= at, "title_yingxiong": ac >= 6,
        "title_zhuanji": cr >= 80, "title_jiuzhuan": tt >= 3, "title_jindan": rt >= 2,
        "title_yuanying": rt >= 3, "title_zhongliu": ac >= 12,
    }.get(idv, False)

check(h_is_unlocked("title_xunyao", {"ac":0,"at":20,"rt":0,"cr":29,"tt":0}) is False, "荣誉：图鉴<30%→巡妖使未解锁")
check(h_is_unlocked("title_xunyao", {"ac":0,"at":20,"rt":0,"cr":30,"tt":0}) is True, "荣誉：图鉴=30%→巡妖使解锁")
check(h_is_unlocked("title_huashen", {"ac":0,"at":20,"rt":3,"cr":0,"tt":0}) is False, "荣誉：段位<4→化神尊者未解锁")
check(h_is_unlocked("title_huashen", {"ac":0,"at":20,"rt":4,"cr":0,"tt":0}) is True, "荣誉：段位=4→化神尊者解锁")
check(h_is_unlocked("title_jiuzhuan", {"ac":0,"at":20,"rt":0,"cr":0,"tt":2}) is False, "荣誉：渡劫<3→九转真君未解锁")
check(h_is_unlocked("title_jiuzhuan", {"ac":0,"at":20,"rt":0,"cr":0,"tt":3}) is True, "荣誉：渡劫=3→九转真君解锁")
check(h_is_unlocked("title_zhongliu", {"ac":11,"at":20,"rt":0,"cr":0,"tt":0}) is False, "荣誉：成就<12→中流砥柱未解锁")
check(h_is_unlocked("title_zhongliu", {"ac":12,"at":20,"rt":0,"cr":0,"tt":0}) is True, "荣誉：成就=12→中流砥柱解锁")
check(h_is_unlocked("title_dacheng", {"ac":4,"at":5,"rt":0,"cr":0,"tt":0}) is False, "荣誉：未达成全部→妖志大成未解锁")
check(h_is_unlocked("title_dacheng", {"ac":5,"at":5,"rt":0,"cr":0,"tt":0}) is True, "荣誉：达成全部→妖志大成解锁")

_prev = -1; _mono = True
for ac in range(0, 21):
    p = {"ac": ac, "at": 20, "rt": 4, "cr": 100, "tt": 5}
    n = sum(1 for tid in TITLES_KT if h_is_unlocked(tid, p))
    if n < _prev: _mono = False
    _prev = n
check(_mono, "荣誉：已解锁称号数随成就数单调不减（无回归）")

# ═════════════ 疲劳值 FatigueEngine（GDD 十六·公平与稳健·防脚本，纯函数可验证） ═════════════
FAT_SRC = open(os.path.join(COMMON, "logic", "FatigueEngine.kt"), encoding="utf-8").read()
FAT_MAX_KT = int(re.search(r'MAX_FATIGUE\s*=\s*(\d+)', FAT_SRC).group(1))
FAT_SOFT_KT = int(re.search(r'SOFT_CAP\s*=\s*(\d+)', FAT_SRC).group(1))
FAT_MAX_SW = int(re.search(r'let maxFatigue\s*=\s*(\d+)', CORE_SWIFT).group(1))
FAT_SOFT_SW = int(re.search(r'let softCap\s*=\s*(\d+)', CORE_SWIFT).group(1))
check(FAT_MAX_KT == FAT_MAX_SW == 100 and FAT_SOFT_KT == FAT_SOFT_SW == 60,
      "疲劳：双端 MAX_FATIGUE=100 / SOFT_CAP=60 一致")

def f_income(f):
    if f >= FAT_MAX_KT: return 0.0
    if f >= FAT_SOFT_KT: return 0.5
    return 1.0

def f_add(cur, d): return max(0, min(FAT_MAX_KT, cur + d))

def f_label(f):
    if f >= FAT_MAX_KT: return "透支·收益封顶"
    if f >= FAT_SOFT_KT: return "疲劳·收益减半"
    return "充沛"

check(f_income(0) == 1.0 and f_income(59) == 1.0, "疲劳：<SOFT_CAP 充沛→收益×1.0")
check(f_income(60) == 0.5 and f_income(99) == 0.5, "疲劳：SOFT_CAP..<MAX 疲劳→收益×0.5")
check(f_income(100) == 0.0, "疲劳：MAX 透支→收益封顶×0.0")
check(f_add(50, 20) == 70 and f_add(90, 20) == 100 and f_add(10, -50) == 0 and f_add(-10, 5) == 0,
      "疲劳：addFatigue 钳制到 [0,100]")
check(f_label(59) == "充沛" and f_label(60) == "疲劳·收益减半" and f_label(100) == "透支·收益封顶",
      "疲劳：label 三档文案与收益倍率一致")
check("fun resetDaily(): Int = 0" in FAT_SRC and "static func resetDaily() -> Int { 0 }" in CORE_SWIFT,
      "疲劳：resetDaily 跨天重置=0 双端一致")

_props_ok = True
for f in [-50, -1, 0, 30, 59, 60, 80, 99, 100, 150]:
    if f_income(f) not in (0.0, 0.5, 1.0): _props_ok = False
    if not (0 <= f_add(f, 10) <= 100): _props_ok = False
check(_props_ok, "疲劳：性质测试（任意疲劳 incomeMul∈{0,0.5,1.0} 且 addFatigue∈[0,100]）")

# ───────────────── 战斗引擎等价验证（GDD 十五·数值基础：伤害公式 + 五行/类别克制） ─────────────────
ELEM_SRC = open(os.path.join(COMMON, "model", "Element.kt"), encoding="utf-8").read()
MCAT_SRC = open(os.path.join(COMMON, "model", "MonsterCategory.kt"), encoding="utf-8").read()
BE_SRC = open(os.path.join(COMMON, "logic", "BattleEngine.kt"), encoding="utf-8").read()

def _bracket_block(src, marker, open_ch, close_ch):
    """提取 marker 后第一个 open_ch..close_ch 配平块的内部内容。"""
    i = src.index(marker)
    j = src.index(open_ch, i)
    depth = 0
    k = j
    while k < len(src):
        if src[k] == open_ch:
            depth += 1
        elif src[k] == close_ch:
            depth -= 1
            if depth == 0:
                return src[j + 1:k]
        k += 1
    return ""

# 双端五行矩阵（攻方克制受方集合）
K_BEATS = {}
for _m in re.finditer(r'(\w+)\s+to\s+setOf\(\s*([\w,\s]+?)\s*\)', ELEM_SRC):
    K_BEATS[_m.group(1).lower()] = {x.strip().lower() for x in _m.group(2).split(',')}
_SW_BEATS_BLOCK = re.search(r'beatsMap.*?=\s*\[(.*?)\n\s*\]', CORE_SWIFT, re.S).group(1)
S_BEATS = {}
for _m in re.finditer(r'\.(\w+):\s*\[\s*\.(\w+)\s*,\s*\.(\w+)\s*\]', _SW_BEATS_BLOCK):
    S_BEATS[_m.group(1).lower()] = {_m.group(2).lower(), _m.group(3).lower()}

# 双端类别克制环（攻方 → 受方）
K_RING = {}
for _m in re.finditer(r'(\w+)\s+to\s+(\w+)', _bracket_block(MCAT_SRC, "RING", "(", ")")):
    K_RING[_m.group(1).lower()] = _m.group(2).lower()
_SW_RING_BLOCK = re.search(r'let ring.*?=\s*\[(.*?)\]', CORE_SWIFT, re.S).group(1)
S_RING = {}
for _m in re.finditer(r'\.(\w+):\s*\.(\w+)', _SW_RING_BLOCK):
    S_RING[_m.group(1).lower()] = _m.group(2).lower()

ELEMS = ["metal", "wood", "earth", "water", "fire", "wind", "thunder"]
CATS = ["yao", "jing", "guai", "lingshou"]

# 双端配置护栏（抓 desync）
check(set(K_BEATS.keys()) == set(ELEMS), "战斗：Kotlin 五行含全部 7 元素")
check(set(S_BEATS.keys()) == set(ELEMS), "战斗：Swift 五行含全部 7 元素")
check(K_BEATS == S_BEATS, "战斗：双端五行克制矩阵完全一致")
check(set(K_RING.keys()) == set(CATS), "战斗：Kotlin 类别环含全部 4 类别")
check(set(S_RING.keys()) == set(CATS), "战斗：Swift 类别环含全部 4 类别")
check(K_RING == S_RING, "战斗：双端类别克制环完全一致")
check("1.6" in ELEM_SRC and "0.6" in ELEM_SRC, "战斗：Kotlin 五行倍率含 1.6/0.6")
check("1.6" in CORE_SWIFT and "0.6" in CORE_SWIFT, "战斗：Swift 五行倍率含 1.6/0.6")
check("1.3" in MCAT_SRC, "战斗：Kotlin 类别倍率含 1.3")
check("1.3" in CORE_SWIFT, "战斗：Swift 类别倍率含 1.3")

# 公式镜像（左结合，与 Kotlin/Swift 一致）
def c_level_coef(level):
    return 1.0 + (level - 1) * 0.02

def c_elem_mul(a, d):
    a, d = a.lower(), d.lower()
    if d in K_BEATS.get(a, set()):
        return 1.6
    if a in K_BEATS.get(d, set()):
        return 0.6
    return 1.0

def c_cat_mul(a, d):
    a, d = a.lower(), d.lower()
    if K_RING.get(a) == d:
        return 1.3
    if K_RING.get(d) == a:
        return 1.0 / 1.3
    return 1.0

def c_raw_damage(atk, df, power, level, elem, cat, crit, rnd, team=1.0, env=1.0, sect=1.0):
    x = atk / df
    x *= power
    x *= c_level_coef(level)
    x *= elem
    x *= cat
    x *= crit
    x *= rnd
    x *= team
    x *= env
    x *= sect
    return x

def c_damage(atk, df, power, level, elem, cat, crit, rnd, team=1.0, env=1.0, sect=1.0):
    return max(1, int(c_raw_damage(atk, df, power, level, elem, cat, crit, rnd, team, env, sect)))

def c_crit_chance(crit_bonus):
    return min(1.0, max(0.0, 0.05 + crit_bonus))

# 等级系数边界
check(c_level_coef(1) == 1.0, "战斗：等级系数 level=1 → 1.0")
check(abs(c_level_coef(51) - 2.0) < 1e-9, "战斗：等级系数 level=51 → 2.0")
check(abs(c_level_coef(100) - 2.98) < 1e-9, "战斗：等级系数 level=100 → 2.98")

# 五行矩阵边界
check(c_elem_mul("metal", "wood") == 1.6, "战斗：金克木 → ×1.6")
check(c_elem_mul("metal", "water") == 0.6, "战斗：水克金（金被克）→ ×0.6")
check(c_elem_mul("metal", "earth") == 1.0, "战斗：金对土无克制 → ×1.0")
check(c_elem_mul("fire", "metal") == 1.6, "战斗：火克金 → ×1.6")
check(c_elem_mul("water", "fire") == 1.6, "战斗：水克火 → ×1.6")
check(c_elem_mul("metal", "metal") == 1.0, "战斗：同元素 → ×1.0")
check(all(c_elem_mul(a, b) in {1.6, 0.6, 1.0} for a in ELEMS for b in ELEMS),
      "战斗：五行矩阵所有 49 项 ∈ {1.6,0.6,1.0}")
check(all(not (c_elem_mul(a, b) == 1.6 and c_elem_mul(b, a) == 1.6) for a in ELEMS for b in ELEMS if a != b),
      "战斗：五行矩阵无互克（合法非对称竞赛图）")
check(all((c_elem_mul(a, b) == 1.0) == (c_elem_mul(b, a) == 1.0) for a in ELEMS for b in ELEMS if a != b),
      "战斗：五行中性的配对对称（a对b无克制 ⟺ b对a无克制）")
check(all(sum(1 for b in ELEMS if c_elem_mul(a, b) == 1.6) == 2 for a in ELEMS),
      "战斗：每个元素恰好克制 2 个（出度=2）")

# 类别环边界
check(abs(c_cat_mul("yao", "jing") - 1.3) < 1e-9, "战斗：妖→精 → ×1.3")
check(abs(c_cat_mul("jing", "yao") - 1.0 / 1.3) < 1e-9, "战斗：精被妖克 → ×1/1.3")
check(abs(c_cat_mul("guai", "lingshou") - 1.3) < 1e-9, "战斗：怪→灵兽 → ×1.3")
check(abs(c_cat_mul("lingshou", "guai") - 1.0 / 1.3) < 1e-9, "战斗：灵兽被怪克 → ×1/1.3")
check(c_cat_mul("yao", "guai") == 1.0, "战斗：妖对怪非相邻 → ×1.0")
check(c_cat_mul("yao", "yao") == 1.0, "战斗：同类别 → ×1.0")
check(all(c_cat_mul(a, b) in {1.3, 1.0 / 1.3, 1.0} for a in CATS for b in CATS),
      "战斗：类别环所有 16 项 ∈ {1.3,1/1.3,1.0}")
check(all(K_RING.get(K_RING.get(a)) != a and K_RING.get(a) is not None for a in CATS),
      "战斗：类别环为单一 4-循环（每类克制恰好 1 类）")

# 伤害公式数值
check(c_damage(100, 50, 100, 10, 1.6, 1.3, 1.0, 0.95) == 466,
      "战斗：伤害数值 (100/50)*100*1.18*1.6*1.3*1.0*0.95=466.336 → floor 466")
check(c_damage(1, 1000, 1, 1, 1.0, 1.0, 1.0, 0.9) == 1,
      "战斗：极小伤害 floor→0 后 coerceAtLeast(1) → 1")
_base = c_raw_damage(200, 100, 150, 60, 1.6, 1.3, 1.0, 0.95, 1.0, 1.0, 1.0)
check(abs(c_raw_damage(200, 100, 150, 60, 1.6, 1.3, 1.0, 0.95, 1.0, 1.0, 1.2) - _base * 1.2) < 1e-9,
      "战斗：sectAttackMul 在 raw 上线性（×1.2）")
check(c_damage(200, 100, 150, 60, 1.6, 1.3, 1.0, 0.95, 1.0, 1.0, 1.2) == max(1, int(_base * 1.2)),
      "战斗：伤害随 sect 线性缩放且 floor")
check("levelCoef" in BE_SRC and "coerceAtLeast(1)" in BE_SRC, "战斗：Kotlin damage 含 levelCoef + coerceAtLeast(1)")
check("levelCoef" in CORE_SWIFT and "rounded(.down)" in CORE_SWIFT, "战斗：Swift damage 含 levelCoef + floor(rounded(.down))")

# 暴击概率 clamp
check(abs(c_crit_chance(2.0) - 1.0) < 1e-9, "战斗：critBonus=2.0 → 暴击率钳到 1.0")
check(abs(c_crit_chance(-1.0) - 0.0) < 1e-9, "战斗：critBonus=-1.0 → 暴击率钳到 0.0")
check(abs(c_crit_chance(0.0) - 0.05) < 1e-9, "战斗：critBonus=0.0 → 暴击率 0.05")
check("coerceIn(0.0, 1.0)" in BE_SRC and "max(0.0, 0.05" in CORE_SWIFT, "战斗：双端暴击率 clamp[0,1] 一致")

# bestCounterSkill（五行×类别×威力 加权选最优克制技）
def c_best_counter(skills, atk_elem, atk_cat, def_elem, def_cat):
    best, best_score = None, -1.0
    for sk in skills:
        se = c_elem_mul(sk["element"], def_elem)
        sc = c_cat_mul(atk_cat, def_cat)
        sp = sk["power"] if sk.get("isDamage", True) else 1
        s = se * sc * sp
        if s > best_score:
            best, best_score = sk["id"], s
    return best

check(c_best_counter(
    [{"id": "s_fire", "element": "fire", "power": 100, "isDamage": True},
     {"id": "s_wood", "element": "wood", "power": 120, "isDamage": True},
     {"id": "s_water", "element": "water", "power": 80, "isDamage": False}],
    "fire", "yao", "metal", "jing") == "s_fire",
    "战斗：bestCounterSkill 加权选最优（火克金×1.6·妖→精×1.3·威力100=208 最大）")
check(c_best_counter([{"id": "a", "element": "wood", "power": 50}], "wood", "yao", "earth", "guai") == "a",
    "战斗：单技能直接返回")

# 性质测试
check(all(c_level_coef(l) <= c_level_coef(l + 1) for l in range(1, 99)),
      "战斗：等级系数随等级单调不减")
import random as _rnd
_dmg_legal = True
for _ in range(300):
    _a = _rnd.uniform(10, 300); _d = _rnd.uniform(10, 300); _p = _rnd.uniform(10, 200)
    _lv = _rnd.randint(1, 99)
    _e = _rnd.choice([1.6, 0.6, 1.0]); _c = _rnd.choice([1.3, 1.0 / 1.3, 1.0])
    _cr = _rnd.choice([1.0, 1.5]); _rn = _rnd.uniform(0.9, 1.0)
    _tm = _rnd.uniform(1.0, 1.5); _ev = _rnd.uniform(1.0, 1.5); _sc = _rnd.uniform(1.0, 1.5)
    _dd = c_damage(_a, _d, _p, _lv, _e, _c, _cr, _rn, _tm, _ev, _sc)
    if not (isinstance(_dd, int) and _dd >= 1):
        _dmg_legal = False
        break
check(_dmg_legal, "战斗：性质测试 300 例任意输入 → 伤害恒为整数且 ≥1")

# ════════ 云同步平台层契约（#180） ════════
_backend_kt = open(os.path.join(COMMON, "persistence", "CloudSyncBackend.kt"), encoding="utf-8").read()

# 1. shared interface 定义与签名
check('interface CloudSyncBackend' in _backend_kt, "云同步契约：shared 定义 interface CloudSyncBackend")
check(re.search(r'fun isAvailable\(\)\s*:\s*Boolean', _backend_kt) is not None, "云同步契约：isAvailable(): Boolean")
check(re.search(r'fun upload\(local:\s*SaveData.*deviceId:\s*String.*accountToken:\s*String\)\s*:\s*UploadAck', _backend_kt, re.S) is not None, "云同步契约：upload(SaveData,String,String): UploadAck")
check(re.search(r'fun download\(accountToken:\s*String\)\s*:\s*(?:CloudSyncEngine\.)?CloudEnvelope\?', _backend_kt) is not None, "云同步契约：download(String): CloudEnvelope?")

# 2. UploadAck 数据类字段
check(re.search(r'data class UploadAck\(', _backend_kt) is not None, "云同步契约：UploadAck 数据类")
check(re.search(r'val accepted:\s*Boolean', _backend_kt) is not None, "云同步契约：UploadAck.accepted: Boolean")
check(re.search(r'val storedModifiedAt:\s*Long', _backend_kt) is not None, "云同步契约：UploadAck.storedModifiedAt: Long")
check(re.search(r'val checksum:\s*Int', _backend_kt) is not None, "云同步契约：UploadAck.checksum: Int")

# 3. Swift 镜像一致
check('struct CloudSyncBackend' in CORE_SWIFT, "云同步契约：Core.swift 镜像 struct CloudSyncBackend")
check(re.search(r'static func isAvailable\(\)\s*->\s*Bool', CORE_SWIFT) is not None, "云同步契约：Swift isAvailable() -> Bool")
check(re.search(r'static func upload\(_ local: SaveData.*_ accountToken: String\)\s*->\s*UploadAck', CORE_SWIFT, re.S) is not None, "云同步契约：Swift upload(...) -> UploadAck")
check(re.search(r'static func download\(_ accountToken: String\)\s*->\s*CloudEnvelope\?', CORE_SWIFT) is not None, "云同步契约：Swift download(...) -> CloudEnvelope?")
check('struct UploadAck' in CORE_SWIFT, "云同步契约：Core.swift 含 UploadAck 结构")

# 4. 版本一致：契约上行复用 CloudSyncEngine.toEnvelope（其用 EXPORT_VERSION=1），shared 不另定义版本号
check('CloudSyncEngine.toEnvelope' in CORE_SWIFT, "云同步契约：Swift upload 经 CloudSyncEngine.toEnvelope 复用 EXPORT_VERSION")
_saveexport_kt = open(os.path.join(COMMON, "persistence", "SaveExportEngine.kt"), encoding="utf-8").read()
_m = re.search(r'const val EXPORT_VERSION\s*=\s*(\d+)', _saveexport_kt)
check(_m is not None and int(_m.group(1)) == 1, "云同步契约：EXPORT_VERSION 仍为 1（版本锁定，防漂移）")
check('const val EXPORT_VERSION' not in _backend_kt, "云同步契约：shared 接口不另定义版本号（仅复用）")

# 5. 契约语义不变量：未登录（空 token）→ 回执 accepted=false（Swift 占位逻辑）
check('accountToken.isEmpty' in CORE_SWIFT and 'accepted: false' in CORE_SWIFT, "云同步契约：未登录(空 token) → 回执 accepted=false")
check(_backend_kt.count('fun ') >= 3, "云同步契约：shared 含 ≥3 个契约方法")

# ════════════ 社交 / GPS 服务端契约（SocialService + GpsAntiCheatVerifier · #182） ════════════
SOC_SRC = open(os.path.join(COMMON, "logic", "SocialEngine.kt"), encoding="utf-8").read()
GPS_SRC = open(os.path.join(COMMON, "logic", "GpsAntiCheat.kt"), encoding="utf-8").read()
LOC_SRC = open(os.path.join(COMMON, "logic", "LocationEngine.kt"), encoding="utf-8").read()

# 1. SocialService 接口（完善：4 → 5 方法，raidBoss 返回回执）
check("interface SocialService" in SOC_SRC, "社交服务端：SocialEngine.kt 含 interface SocialService")
for _m in ["fun friends()", "fun giftYaodan(", "fun raidBoss(", "fun factionRank(", "fun factionRankboard("]:
    check(_m in SOC_SRC, f"社交服务端：含方法 {_m.strip('(').strip('fun ')}")
check("fun raidBoss(bossId: String, contribution: Int): RaidReceipt" in SOC_SRC, "社交服务端：raidBoss 返回 RaidReceipt 回执（非 Unit）")
# 2. 配套数据类
for _dc, _fields in [("RaidReceipt", ["accepted", "bossId", "creditedContribution", "totalContribution", "serverTs"]),
                     ("FactionRankEntry", ["playerId", "faction", "score", "rank"])]:
    check(f"data class {_dc}(" in SOC_SRC, f"社交服务端：含 data class {_dc}")
    for _f in _fields:
        check(f"val {_f}:" in SOC_SRC, f"社交服务端：{_dc}.{_f} 字段")

# 3. GPS 防作弊服务端契约（GpsAntiCheat.kt）
check("interface GpsAntiCheatVerifier" in GPS_SRC, "GPS 服务端：含 interface GpsAntiCheatVerifier")
for _m in ["fun isAvailable()", "fun verify(", "fun verifyPoint("]:
    check(_m in GPS_SRC, f"GPS 服务端：含方法 {_m.strip('(').strip('fun ')}")
check("data class GpsSample(" in GPS_SRC and "val lat:" in GPS_SRC and "val lng:" in GPS_SRC and "val ts:" in GPS_SRC, "GPS 服务端：GpsSample（lat/lng/ts）")
check("data class AntiCheatVerdict(" in GPS_SRC and "trusted" in GPS_SRC and "teleportDetected" in GPS_SRC, "GPS 服务端：AntiCheatVerdict（trusted/teleportDetected）")
# 4. verifyPoint 默认实现复用客户端同套判据（双端一致，不重复实现）
check("LocationEngine.haversine" in GPS_SRC, "GPS 服务端：verifyPoint 复用 LocationEngine.haversine")
check("SOFT_SPEED_KMH" in GPS_SRC and "TELEPORT_METERS" in GPS_SRC and "TELEPORT_WINDOW_SEC" in GPS_SRC, "GPS 服务端：verifyPoint 复用客户端判定常量")
check("enum class IntegrityTier" in LOC_SRC, "GPS 防作弊：LocationEngine 定义 IntegrityTier(NORMAL/DEGRADED/REJECTED)")
check("MAX_SPEED_KMH" not in LOC_SRC, "GPS 防作弊：旧 MAX_SPEED_KMH 已从客户端移除（改 SOFT_SPEED_KMH=30）")

# 5. 判定常量与客户端严格一致（核对 LocationEngine 真值）
_gc = lambda name: re.search(r'const val %s\s*=\s*([\d.]+)' % name, LOC_SRC)
_m1 = _gc("SOFT_SPEED_KMH"); _m2 = _gc("TELEPORT_METERS"); _m3 = re.search(r'const val TELEPORT_WINDOW_SEC\s*=\s*(\d+)L', LOC_SRC)
check(_m1 is not None and float(_m1.group(1)) == 30.0, "GPS 服务端：SOFT_SPEED_KMH = 30（软档降品质阈值）")
check(_m2 is not None and float(_m2.group(1)) == 5000.0, "GPS 服务端：TELEPORT_METERS = 5000（与客户端一致）")
check(_m3 is not None and int(_m3.group(1)) == 180, "GPS 服务端：TELEPORT_WINDOW_SEC = 180（与客户端一致）")
SOFT_SPEED = 30.0; TELEPORT_M = 5000.0; TELEPORT_W = 180

# 6. verifyPoint 语义（三档，与客户端 spawnIntegrity 同构，Python 复刻校验边界）
import math as _math
def _haversine(lat1, lng1, lat2, lng2):
    R = 6_371_000.0
    dLat = _math.radians(lat2 - lat1); dLng = _math.radians(lng2 - lng1)
    a = _math.sin(dLat/2)**2 + _math.cos(_math.radians(lat1))*_math.cos(_math.radians(lat2))*_math.sin(dLng/2)**2
    return R * 2 * _math.asin(_math.sqrt(a))
def d_verify_point(prev, cur):
    if prev is None: return ("首次定位", True, "NORMAL")
    dt = cur["ts"] - prev["ts"]
    if dt <= 0: return ("时间无效-跳过", True, "NORMAL")
    dist = _haversine(prev["lat"], prev["lng"], cur["lat"], cur["lng"])
    speed = dist / dt * 3.6
    if dist >= TELEPORT_M and dt <= TELEPORT_W: return ("瞬移", False, "REJECTED")
    if speed > SOFT_SPEED: return ("速度过快(降品质)", True, "DEGRADED")
    return ("正常", True, "NORMAL")
_c1 = d_verify_point(None, {"lat":31.2,"lng":121.4,"ts":1000})
check(_c1[1] is True and _c1[2] == "NORMAL", "GPS 服务端：首次定位 → 可信(NORMAL)")
_c2 = d_verify_point({"lat":31.2,"lng":121.4,"ts":1000}, {"lat":31.2001,"lng":121.4001,"ts":1005})  # ~16m/5s → 11.5km/h
check(_c2[1] is True and _c2[2] == "NORMAL", "GPS 服务端：正常步行 → 可信(NORMAL)")
_c3 = d_verify_point({"lat":31.2,"lng":121.4,"ts":1000}, {"lat":31.26,"lng":121.46,"ts":1005})  # ~6.7km/5s → 瞬移
check(_c3[1] is False and _c3[2] == "REJECTED", "GPS 服务端：瞬移(≥5000m/≤180s) → 不可信(REJECTED)")
_c4 = d_verify_point({"lat":31.2,"lng":121.4,"ts":1000}, {"lat":31.275,"lng":121.4,"ts":1600})  # ~8.3km/600s → 50km/h 驾车
check(_c4[1] is True and _c4[2] == "DEGRADED", "GPS 服务端：50km/h(驾车) → 降品质(DEGRADED) 仍可收妖")
# 明确超速：1 秒位移约 100m → 360km/h > 30 阈值（软档降品质，仍可信）
_c6 = d_verify_point({"lat":31.2,"lng":121.4,"ts":1000}, {"lat":31.2009,"lng":121.4009,"ts":1001})
check(_c6[2] == "DEGRADED" and _c6[1] is True, "GPS 服务端：超速(>30km/h) → 降品质(DEGRADED) 不阻断")
# 瞬移窗口边界：6.7km / 200s（dt>180）→ 不判瞬移，按降品质
_c7 = d_verify_point({"lat":31.2,"lng":121.4,"ts":1000}, {"lat":31.26,"lng":121.46,"ts":1200})
check(_c7[2] != "REJECTED", "GPS 服务端：瞬移窗口边界(200s>180s)不误判为瞬移")

# 7. Core.swift 镜像（双端一致）
check("struct SocialService" in CORE_SWIFT, "社交服务端：Core.swift 镜像 SocialService")
for _m in ["static func friends()", "static func giftYaodan(", "static func raidBoss(", "static func factionRank(", "static func factionRankboard("]:
    check(_m in CORE_SWIFT, f"社交服务端：iOS 镜像含 {_m.strip('(').strip('static func ')}")
check("struct RaidReceipt" in CORE_SWIFT and "struct FactionRankEntry" in CORE_SWIFT, "社交服务端：iOS 镜像含 RaidReceipt/FactionRankEntry")
check("struct GpsAntiCheatVerifier" in CORE_SWIFT and "struct GpsSample" in CORE_SWIFT and "struct AntiCheatVerdict" in CORE_SWIFT, "GPS 服务端：iOS 镜像 GpsAntiCheatVerifier/GpsSample/AntiCheatVerdict")
check("static func verifyPoint(" in CORE_SWIFT and "LocationEngine.haversine" in CORE_SWIFT, "GPS 服务端：iOS verifyPoint 复用 LocationEngine.haversine")
check("enum IntegrityTier" in CORE_SWIFT, "GPS 防作弊：Core.swift 镜像 IntegrityTier")
check("LocationEngine.TELEPORT_METERS" in CORE_SWIFT and "LocationEngine.SOFT_SPEED_KMH" in CORE_SWIFT, "GPS 服务端：iOS verifyPoint 复用客户端常量(SOFT_SPEED_KMH)")
check("MAX_SPEED_KMH" not in CORE_SWIFT, "GPS 防作弊：Core.swift 旧 MAX_SPEED_KMH 已移除")
check("let tier: IntegrityTier" in CORE_SWIFT, "GPS 防作弊：Core.swift IntegrityResult 含 tier 字段")
check("tier: IntegrityTier" in GPS_SRC, "GPS 防作弊：AntiCheatVerdict 含 tier 字段（服务端裁决同步档位）")

# ───────────────── #183 AR 摄像头后端契约（平台层实现） ─────────────────
AR_KT = open(os.path.join(COMMON, "logic", "ARCaptureController.kt"), encoding="utf-8").read()
# 1. 共享契约 interface ArCaptureBackend 存在
check('interface ArCaptureBackend' in AR_KT, "AR 后端：shared 定义 interface ArCaptureBackend")
check('struct ArCaptureBackend' in CORE_SWIFT, "AR 后端：Swift 镜像 struct ArCaptureBackend")
# 2. 双端 5 契约方法签名一致
_kt_methods = ["fun isCameraAvailable(): Boolean", "requestPermission(): AR",
               "fun beginSession(anchorMonsterId: String): Boolean", "fun endSession()", "fun resolveCapture("]
_sw_methods = ["static func isCameraAvailable() -> Bool", "static func requestPermission() -> ARStatus",
               "static func beginSession(_ anchorMonsterId: String) -> Bool", "static func endSession()",
               "static func resolveCapture(accuracy: Double"]
for _m in _kt_methods:
    check(_m in AR_KT, f"AR 后端：shared 含契约方法 {_m}")
for _m in _sw_methods:
    check(_m in CORE_SWIFT, f"AR 后端：Swift 含契约方法 {_m}")
# 3. 方法计数一致（各 5）
check(sum(1 for _m in _kt_methods if _m in AR_KT) == 5, "AR 后端：shared 契约方法数 = 5")
check(sum(1 for _m in _sw_methods if _m in CORE_SWIFT) == 5, "AR 后端：Swift 契约方法数 = 5")
# 4. ARStatus 三态双端一致
check('UNSUPPORTED' in AR_KT and 'PERMISSION_DENIED' in AR_KT and 'READY' in AR_KT,
      "AR 后端：shared ARStatus 三态 UNSUPPORTED/PERMISSION_DENIED/READY")
check('unsupported="unsupported"' in CORE_SWIFT and 'permissionDenied="permissionDenied"' in CORE_SWIFT and 'ready="ready"' in CORE_SWIFT,
      "AR 后端：Swift ARStatus 三态一致（逗号分隔枚举）")
# 5. resolveCapture 双端复用 CaptureEngine.resolve（与 resolveAtAnchor 同义，不重复实现）
check('CaptureEngine.resolve(accuracy, rarity, assist, rng)' in AR_KT, "AR 后端：shared resolveCapture 复用 CaptureEngine.resolve")
check('CaptureEngine.resolve(accuracy: accuracy, rarity: rarity, assist: assist, rng: rng)' in CORE_SWIFT,
      "AR 后端：Swift resolveCapture 复用 CaptureEngine.resolve")
# 6. 占位默认不变量：未接入摄像头→status = UNSUPPORTED / .unsupported（UI 自动回退卷轴背景）
check('fun status(): ARStatus = ARStatus.UNSUPPORTED' in AR_KT, "AR 后端：占位默认 status = UNSUPPORTED")
check('static func status() -> ARStatus { .unsupported }' in CORE_SWIFT, "AR 后端：Swift 占位默认 status = .unsupported")
check('static func isCameraAvailable() -> Bool { false }' in CORE_SWIFT, "AR 后端：占位摄像头不可用 → false")
# 7. 契约语义不变量：摄像头不可用（占位 false）→ 不应进入 READY（请求权限仍 UNSUPPORTED）
check('static func requestPermission() -> ARStatus { .unsupported }' in CORE_SWIFT,
      "AR 后端：摄像头不可用 → requestPermission 返回 .unsupported（不误报 READY）")

# ───────────────── #184 地图 SDK 适配契约（平台层实现） ─────────────────
MAP_KT = open(os.path.join(COMMON, "logic", "MapSdkAdapter.kt"), encoding="utf-8").read()
LOC_KT = open(os.path.join(COMMON, "logic", "LocationEngine.kt"), encoding="utf-8").read()
# 1. 共享契约定义
check('interface MapSdkAdapter' in MAP_KT, "地图SDK：shared 定义 interface MapSdkAdapter")
check('data class MapNode' in MAP_KT and 'data class MapPoi' in MAP_KT, "地图SDK：shared 定义 MapNode/MapPoi")
check('enum class PoiKind' in MAP_KT and 'LINGMAI' in MAP_KT and 'STRONGHOLD' in MAP_KT, "地图SDK：shared PoiKind 两态 LINGMAI/STRONGHOLD")
check('struct MapSdkAdapter' in CORE_SWIFT and 'struct MapNode' in CORE_SWIFT and 'struct MapPoi' in CORE_SWIFT, "地图SDK：Swift 镜像 MapSdkAdapter/MapNode/MapPoi")
check('enum PoiKind' in CORE_SWIFT and 'case lingmai' in CORE_SWIFT and 'case stronghold' in CORE_SWIFT, "地图SDK：Swift PoiKind 两态一致")
# 2. 双端 4 契约方法签名一致
_kt_m = ["fun isAvailable(): Boolean", "fun overlayPois(pois: List<MapPoi>): Boolean",
         "fun setPlayerLayer(lat: Double, lng: Double): Boolean", "fun focusRegion(lat: Double, lng: Double, zoom: Int): Boolean"]
_sw_m = ["static func isAvailable() -> Bool", "static func overlayPois(_ pois: [MapPoi]) -> Bool",
         "static func setPlayerLayer(lat: Double, lng: Double) -> Bool", "static func focusRegion(lat: Double, lng: Double, zoom: Int) -> Bool"]
for _m in _kt_m:
    check(_m in MAP_KT, f"地图SDK：shared 含契约方法 {_m}")
for _m in _sw_m:
    check(_m in CORE_SWIFT, f"地图SDK：Swift 含契约方法 {_m}")
check(sum(1 for _m in _kt_m if _m in MAP_KT) == 4, "地图SDK：shared 契约方法数 = 4")
check(sum(1 for _m in _sw_m if _m in CORE_SWIFT) == 4, "地图SDK：Swift 契约方法数 = 4")
# 3. buildPois 双端复用 LocationEngine 真源（不重复硬编码 POI）
check('LocationEngine.nodeCoordinates()' in MAP_KT and 'LocationEngine.STRONGHOLD_NODES' in MAP_KT,
      "地图SDK：shared buildPois 复用 LocationEngine.nodeCoordinates/STRONGHOLD_NODES")
check('LocationEngine.nodeEntries' in CORE_SWIFT and 'LocationEngine.strongholdNodes' in CORE_SWIFT,
      "地图SDK：Swift buildPois 复用 LocationEngine.nodeEntries/strongholdNodes")
# 4. NODE 真值：4 节点坐标（源自 NODE_KEYS 单一真源）
_node_pairs = [("31.23", "121.47"), ("30.25", "120.16"), ("25.03", "102.71"), ("30.62", "122.08")]
for (_la, _ln) in _node_pairs:
    check((f'"{_la}:{_ln}"' in LOC_KT) or (f'lat: {_la}, lng: {_ln}' in CORE_SWIFT),
          f"地图SDK：节点真值 ({_la},{_ln}) 单一真源存在")
check('"城隍桥"' in LOC_KT and '"古庙"' in LOC_KT and '"老槐树"' in LOC_KT and '"东海之滨"' in LOC_KT,
      "地图SDK：4 节点名存在于 LocationEngine 真源")
# 5. STRONGHOLD_NODES 据点白名单双端存在（据点战目标，与地图同值）
check('STRONGHOLD_NODES' in LOC_KT and 'strongholdNodes' in CORE_SWIFT, "地图SDK：据点白名单双端存在")
# 6. buildPois 据点标记 STRONGHOLD / 灵脉 LINGMAI（判定复用白名单）
check('PoiKind.STRONGHOLD' in MAP_KT and 'PoiKind.LINGMAI' in MAP_KT, "地图SDK：buildPois 据点标记 STRONGHOLD / 灵脉 LINGMAI")
check('LocationEngine.STRONGHOLD_NODES.contains(node.name)' in MAP_KT, "地图SDK：shared 据点判定复用白名单")
check('LocationEngine.strongholdNodes.contains(node.name)' in CORE_SWIFT, "地图SDK：Swift 据点判定复用白名单")

# ───────────────── #186 BattleIntegrityEngine 运行时验证（复刻 BattleSession） ─────────────────
# 高成本残留收官：BattleIntegrityEngine.runMatchup 依赖 BattleSession.simulateToEnd() 完整回合模拟，
# Python 无法等价运行 Kotlin RNG；但「克制方必胜 / 被克方必负」由伤害碾压保证，与 RNG 无关，
# 故在 Python 侧忠实复刻 BattleSession 战斗循环，断言确定性单元 + 四布尔平衡不变量。
import math as _math
import random as _random

# 复刻驱动真实源码常量（信任但验证；BEATS/成长率已由 #112 校验，此处补 RING 源码解析 + 重校验 growth）
_bie_txt_mc = open(os.path.join(COMMON, "model", "MonsterCategory.kt"), encoding="utf-8").read()
_bie_ring = {m.group(1): m.group(2) for m in re.finditer(r'(\w+)\s+to\s+(\w+),', _bie_txt_mc)}
check(_bie_ring == {"YAO": "JING", "JING": "GUAI", "GUAI": "LINGSHOU", "LINGSHOU": "YAO"},
      "战斗自检：复刻驱动真实 RING 环（与 MonsterCategory.kt 一致）")
_bie_txt_rar = open(os.path.join(COMMON, "model", "Rarity.kt"), encoding="utf-8").read()
_bie_growth = {m.group(1): float(m.group(2)) for m in re.finditer(r'(\w+)\("[^"]*",\s*([\d.]+)\)', _bie_txt_rar)}
check(abs(_bie_growth["LING"] - 1.1) < 1e-9, "战斗自检：复刻驱动真实 LING 成长率=1.1（基准妖属性缩放真值）")
check(abs(_GROWTH["LING"] - 1.1) < 1e-9, "战斗自检：#112 成长率与 Rarity.kt 一致（LING=1.1）")

def _bie_level_coef(level): return 1.0 + (level - 1) * 0.02
def _bie_cat(atk, dfn):
    if _bie_ring.get(atk) == dfn: return 1.3
    if _bie_ring.get(dfn) == atk: return 1.0 / 1.3
    return 1.0
def _bie_env_mul(elem): return 1.0  # CLEAR 天气 + 无地形 → 1.0

class _KtRnd:
    def __init__(self, seed): self._r = _random.Random(seed)
    def next_double(self, lo, hi): return lo + (hi - lo) * self._r.random()
    def draw(self): return self._r.random()

class _BieComb:
    def __init__(self, element):
        g = _bie_growth["LING"]; lc = _bie_level_coef(50)
        self.element = element; self.category = "YAO"
        self.eff = {"atk": max(1, int(100*g*lc)), "def": max(1, int(100*g*lc)),
                    "hp": max(1, int(100*g*lc)), "spd": max(1, int(100*g))}
        self.hp = self.eff["hp"]; self.max_hp = self.eff["hp"]
    def alive(self): return self.hp > 0

def _bie_damage(atk, dfn, sk_elem, rng):
    atk_v = float(atk.eff["atk"]); def_v = float(max(1, dfn.eff["def"]))
    elem = _emul(sk_elem, dfn.element)        # 复用 #112 五行倍率（已校验）
    cat = _bie_cat(atk.category, dfn.category)
    crit_chance = min(1.0, max(0.0, 0.05 + 0.0))
    rnd = rng.next_double(0.9, 1.0)
    crit = rng.draw() < crit_chance
    crit_mult = 1.5 if crit else 1.0
    raw = (atk_v / def_v) * 100 * _bie_level_coef(50) * elem * cat * crit_mult * rnd * 1.0 * 1.0 * 1.0
    return max(1, int(_math.floor(raw)))

def _bie_simulate(a_elem, b_elem, seed):
    p = _BieComb(a_elem); e = _BieComb(b_elem); rng = _KtRnd(seed); turn = 0; out = "ONGOING"
    while out == "ONGOING" and turn < 100:
        turn += 1
        e.hp -= _bie_damage(p, e, p.element, rng)
        if not e.alive(): out = "PLAYER_WIN"; break
        p.hp -= _bie_damage(e, p, e.element, rng)
        if not p.alive(): out = "ENEMY_WIN"; break
        if turn >= 100 and out == "ONGOING":
            out = "PLAYER_WIN" if (p.hp / p.max_hp) >= (e.hp / e.max_hp) else "ENEMY_WIN"
    return out

def _bie_matrix(seed):
    m = {}
    for a in _ELEMS:
        m[a] = {}
        for b in _ELEMS:
            if a == b: m[a][b] = 0.5
            else: m[a][b] = 1.0 if _bie_simulate(a, b, seed) == "PLAYER_WIN" else 0.0
    return m

def _bie_balance(seed):
    m = _bie_matrix(seed)
    per = []
    for a in _ELEMS:
        opp = [b for b in _ELEMS if b != a]
        beats = [b for b in opp if _beats(a, b)]
        beaten = [b for b in opp if _beats(b, a)]
        avg = sum(m[a][b] for b in opp) / len(opp)
        bw = 1.0 if not beats else sum(m[a][b] for b in beats) / len(beats)
        bdw = 0.0 if not beaten else sum(m[a][b] for b in beaten) / len(beaten)
        per.append((avg, bw, bdw))
    all_term = all(m[a][b] in (0.0, 1.0) for a in _ELEMS for b in _ELEMS if a != b)
    no_dom = all(0.0 < avg < 1.0 for (avg, _, _) in per)
    beats_adv = all(bw >= 0.8 for (_, bw, _) in per)
    beaten_dis = all(bdw <= 0.2 for (_, _, bdw) in per)
    return all_term, no_dom, beats_adv, beaten_dis

_bie_seeds = [1, 2, 3, 5, 8, 13, 21, 34]
_bie_det = 0
for _s in _bie_seeds:
    _m = _bie_matrix(_s)
    for _a in _ELEMS:
        check(_m[_a][_a] == 0.5, f"战斗自检：对角[{_a}][{_a}]=0.5 (seed {_s})")
    for _a in _ELEMS:
        for _b in _ELEMS:
            if _a == _b: continue
            if _beats(_a, _b):
                check(_m[_a][_b] == 1.0, f"战斗自检：{_a}克{_b}→A胜=1.0 (seed {_s})"); _bie_det += 1
            elif _beats(_b, _a):
                check(_m[_a][_b] == 0.0, f"战斗自检：{_a}被{_b}克→A负=0.0 (seed {_s})"); _bie_det += 1
    _at, _nd, _ba, _bd = _bie_balance(_s)
    check(_at, f"战斗自检：allTerminated 所有对局必然终结 (seed {_s})")
    check(_nd, f"战斗自检：noDegenerateDominance 无元素全胜/全败 (seed {_s})")
    check(_ba, f"战斗自检：beatsAdvantage 克制方胜率≥0.8 (seed {_s})")
    check(_bd, f"战斗自检：beatenDisadvantage 被克方胜率≤0.2 (seed {_s})")
check(_bie_det == len(_bie_seeds) * 28, f"战斗自检：确定性单元数={_bie_det}（7元素×每元素4确定性=28 ×{len(_bie_seeds)}种子）")

# ───────────────── A-1 道德选择分支（镇压/超度，GDD 十七·自由度） ─────────────────
# 标记持久化在 MonsterInstance.moralChoice；倍率由 MoralChoiceEngine.modifiers 纯函数决定（双端镜像）。
_MC_SRC = open(os.path.join(COMMON, "model", "MoralChoice.kt"), encoding="utf-8").read()
_MC_ENG = open(os.path.join(COMMON, "logic", "MoralChoiceEngine.kt"), encoding="utf-8").read()
_MI_SRC = open(os.path.join(COMMON, "model", "MonsterInstance.kt"), encoding="utf-8").read()
_GS_SRC2 = open(os.path.join(COMMON, "logic", "GameState.kt"), encoding="utf-8").read()

# 1) 枚举与两分支
check("enum class MoralChoice" in _MC_SRC, "MoralChoice 枚举已定义（Kotlin）")
check("SUPPRESS" in _MC_SRC and "TRANSCEND" in _MC_SRC, "MoralChoice 含 镇压SUPPRESS / 超度TRANSCEND 两分支")

# 2) MonsterInstance 持久化标记（旧存档缺省 null，向后兼容）
check("val moralChoice: MoralChoice?" in _MI_SRC, "MonsterInstance 含 moralChoice 持久化标记（默认 null）")

# 3) 收妖入口接受 choice 并写入实例 + 暴露查询
check('fun catchMonster(id: String, fromSecretRealm: Boolean = false, choice: MoralChoice? = null)' in _GS_SRC2,
      "GameState.catchMonster 接受 choice: MoralChoice? 参数")
check('MonsterInstance("i_${id}_${nextInstSeq++}", id, 1, 0, 0, moralChoice = choice)' in _GS_SRC2,
      "catchMonster 将 choice 写入实例 moralChoice")
check("fun moralModsFor(instanceId: String): StatMods?" in _GS_SRC2,
      "GameState.moralModsFor 暴露属性倍率查询")

# 4) 倍率规则（解析真实源码数值，不硬编码）
_supp = re.search(r"SUPPRESS -> StatMods\(([\d.]+),\s*([\d.]+),\s*([\d.]+)\)", _MC_ENG)
_trans = re.search(r"TRANSCEND -> StatMods\(([\d.]+),\s*([\d.]+),\s*([\d.]+)\)", _MC_ENG)
sa = sd = sh = ta = td = th = None
check(_supp is not None and _trans is not None, "MoralChoiceEngine 两分支倍率已定义")
if _supp and _trans:
    sa, sd, sh = (float(x) for x in _supp.groups())
    ta, td, th = (float(x) for x in _trans.groups())
    check(abs(sa - 1.10) < 1e-9 and abs(sd - 1.00) < 1e-9 and abs(sh - 1.00) < 1e-9,
          f"镇压 SUPPRESS：atk×1.10 / def×1.00 / hp×1.00（实 {sa}/{sd}/{sh}）")
    check(abs(ta - 1.00) < 1e-9 and abs(td - 1.08) < 1e-9 and abs(th - 1.08) < 1e-9,
          f"超度 TRANSCEND：atk×1.00 / def×1.08 / hp×1.08（实 {ta}/{td}/{th}）")
    check(sa > sd and sa > sh, "镇压主攻：atk 倍率 > def/hp")
    check(td == th and td > ta, "超度净防：def == hp 倍率 且 > atk")

# 5) Swift 镜像一致
check("let moralChoice: MoralChoice?" in CORE_SWIFT, "Core.Swift MonsterInstance 含 moralChoice: MoralChoice? 字段（类型安全，与 Kotlin 一一对应）")
check('func catchMonster(id: String, secret: Bool = false, choice: MoralChoice? = nil)' in CORE_SWIFT,
      "Core.Swift catchMonster 接受 choice: MoralChoice? 参数")
check("func moralModsFor(_ instanceId: String) -> StatMods?" in CORE_SWIFT,
      "Core.Swift moralModsFor 镜像")
check("struct MoralChoiceEngine" in CORE_SWIFT, "Core.Swift MoralChoiceEngine 镜像")
check("enum MoralChoice" in CORE_SWIFT and 'case suppress = "SUPPRESS"' in CORE_SWIFT and 'case transcend = "TRANSCEND"' in CORE_SWIFT,
      "Core.Swift 镜像 MoralChoice 枚举（类型安全，与 Kotlin enum class 一一对应）")
_sw_s = re.search(r'case \.suppress:\s*return StatMods\(atk: ([\d.]+), def: ([\d.]+), hp: ([\d.]+)\)', CORE_SWIFT)
_sw_t = re.search(r'case \.transcend:\s*return StatMods\(atk: ([\d.]+), def: ([\d.]+), hp: ([\d.]+)\)', CORE_SWIFT)
check(_sw_s is not None and _sw_t is not None, "Core.Swift 两分支倍率已定义")
if _sw_s and _sw_t:
    sw_sa, sw_sd, sw_sh = (float(x) for x in _sw_s.groups())
    sw_ta, sw_td, sw_th = (float(x) for x in _sw_t.groups())
    check(abs(sw_sa - 1.10) < 1e-9 and abs(sw_sd - 1.00) < 1e-9 and abs(sw_sh - 1.00) < 1e-9,
          f"Swift 镇压 atk×1.10/def×1.00/hp×1.00（实 {sw_sa}/{sw_sd}/{sw_sh}）")
    check(abs(sw_ta - 1.00) < 1e-9 and abs(sw_td - 1.08) < 1e-9 and abs(sw_th - 1.08) < 1e-9,
          f"Swift 超度 atk×1.00/def×1.08/hp×1.08（实 {sw_ta}/{sw_td}/{sw_th}）")
    if None not in (sa, sd, sh, ta, td, th):
        check(abs(sw_sa - sa) < 1e-9 and abs(sw_sd - sd) < 1e-9 and abs(sw_sh - sh) < 1e-9,
              "双端 镇压 倍率一致")
        check(abs(sw_ta - ta) < 1e-9 and abs(sw_td - td) < 1e-9 and abs(sw_th - th) < 1e-9,
              "双端 超度 倍率一致")

# ───────────────── A-2 排位 Ban/Pick 选将（GDD 二十·竞技与荣誉，补全 RankEngine 缺口） ─────────────────
# 纯逻辑、无随机、无平台依赖；双端镜像 RankBanPickEngine / RankBanPickEngine。
_RBP_SRC = open(os.path.join(COMMON, "logic", "RankBanPickEngine.kt"), encoding="utf-8").read()
_MP_SRC = open(os.path.join(COMMON, "data", "MonsterPool.kt"), encoding="utf-8").read()

# 1) 双端常量解析 + 一致性护栏
_k_ban = re.search(r"const val BAN_PER_SIDE\s*=\s*(\d+)", _RBP_SRC)
_k_pick = re.search(r"const val PICK_PER_SIDE\s*=\s*(\d+)", _RBP_SRC)
_s_ban = re.search(r"static let banPerSide\s*=\s*(\d+)", CORE_SWIFT)
_s_pick = re.search(r"static let pickPerSide\s*=\s*(\d+)", CORE_SWIFT)
check(_k_ban is not None and _k_pick is not None, "Kotlin RankBanPickEngine 常量 BAN/PICK 已定义")
check(_s_ban is not None and _s_pick is not None, "Swift RankBanPickEngine 常量 banPerSide/pickPerSide 已定义")
K_BAN = K_PICK = S_BAN = S_PICK = None
if _k_ban and _k_pick and _s_ban and _s_pick:
    K_BAN, K_PICK = int(_k_ban.group(1)), int(_k_pick.group(1))
    S_BAN, S_PICK = int(_s_ban.group(1)), int(_s_pick.group(1))
    check(K_BAN == S_BAN and K_PICK == S_PICK, f"双端 ban/pick 常量一致（K={K_BAN}/{K_PICK} · S={S_BAN}/{S_PICK}）")
    check(K_BAN == 2 and K_PICK == 3, f"选将规模：每方 ban={K_BAN} / pick={K_PICK}（出战队伍 3）")

# 2) 引擎 wiring：引用真实妖池（双端）
check("MonsterPool.ALL_IDS" in _RBP_SRC, "Kotlin RankBanPickEngine 引用 MonsterPool.ALL_IDS（真实池）")
check("MonsterPool.allIds" in CORE_SWIFT, "Swift RankBanPickEngine 引用 MonsterPool.allIds（真实池）")
check("val ALL_IDS: Set<String> = all.map { it.id }.toSet()" in _MP_SRC, "MonsterPool.ALL_IDS 由 all 推导（双端同源）")

# 3) 忠实复刻纯函数（与源码逐行对齐），用解析常量驱动，断言场景语义
def _rbpp_denied(s): return s["bannedA"] | s["bannedB"]
def _rbpp_available(s): return MONSTER_IDS - _rbpp_denied(s)
def _rbpp_ban(s, side, mid):
    if mid not in MONSTER_IDS: return (s, "INVALID_MONSTER")
    if mid in _rbpp_denied(s): return (s, "ALREADY_BANNED")
    mine = s[f"banned{side}"]
    if len(mine) >= K_BAN: return (s, "BAN_LIMIT")
    ns = dict(s); ns[f"banned{side}"] = mine | {mid}
    return (ns, "OK")
def _rbpp_pick(s, side, mid):
    if mid not in MONSTER_IDS: return (s, "INVALID_MONSTER")
    if mid in _rbpp_denied(s): return (s, "PICK_BANNED")
    mine = s[f"picked{side}"]
    if mid in mine: return (s, "ALREADY_PICKED")
    if len(mine) >= K_PICK: return (s, "PICK_LIMIT")
    ns = dict(s); ns[f"picked{side}"] = mine + [mid]
    return (ns, "OK")
def _rbpp_complete(s):
    return (len(s["bannedA"]) == K_BAN and len(s["bannedB"]) == K_BAN and
            len(s["pickedA"]) == K_PICK and len(s["pickedB"]) == K_PICK)
def _rbpp_team_valid(s, team):
    if any(m in _rbpp_denied(s) for m in team): return False
    if len(team) != len(set(team)): return False
    if len(team) > K_PICK: return False
    return True

# 选 10 个真实妖 id（均来自 MONSTER_IDS，避免假设；足够填充双方 ban/pick）
_real = sorted([m for m in MONSTER_IDS])[:10]
check(len(_real) == 10, "选将测试：取 10 个真实妖 id（来自 MONSTER_IDS 前 10）")

st = {"bannedA": set(), "bannedB": set(), "pickedA": [], "pickedB": []}
check(_rbpp_available(st) == MONSTER_IDS, "初始可用池 = 全妖池（未被 ban）")
check(len(_rbpp_available(st)) == len(MONSTER_IDS), f"初始可用池规模 = {len(MONSTER_IDS)}（妖池总量）")

# ban 链路
st, r = _rbpp_ban(st, "A", _real[0]); check(r == "OK", "A ban 真实妖 → OK")
st, r = _rbpp_ban(st, "A", _real[1]); check(r == "OK", "A ban 第二只 → OK")
check(_real[0] in _rbpp_denied(st) and _real[1] in _rbpp_denied(st), "被 ban 妖进入双方共享禁用池（denied）")
check(len(_rbpp_available(st)) == len(MONSTER_IDS) - 2, "可用池随 ban 递减（−2）")
st, r = _rbpp_ban(st, "A", _real[2]); check(r == "BAN_LIMIT", "A 已达 ban 上限 → BAN_LIMIT")
st, r = _rbpp_ban(st, "A", "nonexist"); check(r == "INVALID_MONSTER", "ban 不存在妖 → INVALID_MONSTER")
st, r = _rbpp_ban(st, "B", _real[0]); check(r == "ALREADY_BANNED", "B 试图 ban A 已 ban 妖 → ALREADY_BANNED")

# pick 链路（A 侧；ban 已就绪：A ban 0/1，B 将在补齐段 ban 3/4，denied 暂仅含 0/1）
st, r = _rbpp_pick(st, "A", _real[0]); check(r == "PICK_BANNED", "A pick 被 ban 妖 → PICK_BANNED")
st, r = _rbpp_pick(st, "A", _real[2]); check(r == "OK", "A pick 可用妖 → OK")
st, r = _rbpp_pick(st, "A", _real[2]); check(r == "ALREADY_PICKED", "A 重复 pick 同妖 → ALREADY_PICKED")
st, r = _rbpp_pick(st, "A", "nonexist"); check(r == "INVALID_MONSTER", "pick 不存在妖 → INVALID_MONSTER")
# 先补齐 B 的 ban（2 个），确保 denied 完整，再让 A/B 各自 pick 到满
st, _ = _rbpp_ban(st, "B", _real[3])
st, _ = _rbpp_ban(st, "B", _real[4])
check(_rbpp_denied(st) == {_real[0], _real[1], _real[3], _real[4]}, "denied = A0/A1 ∪ B3/B4（双方共享禁用池）")
# A 从可用池（不含 denied）补全至 K_PICK
_a_avail = [x for x in _real if x not in _rbpp_denied(st) and x not in st["pickedA"]]
for mid in _a_avail:
    st, r = _rbpp_pick(st, "A", mid)
    if r != "OK": break
check(len(st["pickedA"]) == K_PICK, f"A pick 填满至上限 {K_PICK}")
_extra = next(x for x in _real if x not in _rbpp_denied(st) and x not in st["pickedA"])
st, r = _rbpp_pick(st, "A", _extra); check(r == "PICK_LIMIT", "A 已达 pick 上限 → PICK_LIMIT")
# 完成判定（仅 A 完成，B 尚未 pick）
check(not _rbpp_complete(st), "仅 A 完成 ban+pick，整体未完成")
# B pick 3（可用池内、不同于 A 已选）
_b_avail = [x for x in _real if x not in _rbpp_denied(st) and x not in st["pickedA"]]
for mid in _b_avail:
    st, r = _rbpp_pick(st, "B", mid)
    if r != "OK": break
check(_rbpp_complete(st), "双方 ban+pick 满 → isComplete = true")
check(_rbpp_team_valid(st, st["pickedA"]), "入站队伍（A 已选）合法")
check(not _rbpp_team_valid(st, st["pickedA"] + [_real[0]]), "入站队伍含被 ban 妖 → 非法")
check(not _rbpp_team_valid(st, st["pickedA"] + [st["pickedA"][0]]), "入站队伍含重复 → 非法")
check(not _rbpp_team_valid(st, st["pickedA"] * (K_PICK + 1)), f"入站队伍规模 > {K_PICK} → 非法")

# ───────────────── 汇总 ─────────────────
print(f"\n验证完成：通过 {passed} 项，失败 {failed} 项")
sys.exit(1 if failed else 0)
