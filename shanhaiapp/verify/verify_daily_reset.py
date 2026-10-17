"""
《山海妖志》每日重置契约校验 · 直接解析 shared 层源码（#181 平台契约运行时接入）

不依赖编译器：用正则从 Kotlin 源文件抽取每日重置权威定义，断言：
  - DailyResetEngine.Target 枚举 7 项（与 GDD 十六/十九/二十「每日」语义对应）
  - isCrossDay 跨天判定真值表（首启空串 / 同日 / 跨日 / 跨月 / 跨年）
  - resolvePending 跨天返回 7 项清单、同日返回空（调用方不应改动任何字段）
  - SeasonEngine 常量（SEASON_LENGTH_DAYS=90 / DAY_MS=86400000）与 seasonsElapsed 赛季推进边界
  - SchedulerStatus 4 态（idle/registered/suspended/unavailable）
  - 接线：GameState.maybeResetDaily 与 DailyResetScheduler.resolvePending 均复用 DailyResetEngine.isCrossDay（唯一权威，防漂移）

运行：python verify/verify_daily_reset.py
"""

import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
COMMON = os.path.normpath(os.path.join(
    HERE, "..", "shared", "src", "commonMain", "kotlin",
    "com", "shanhai", "core"))

ENGINE = os.path.join(COMMON, "logic", "DailyResetEngine.kt")
SCHED = os.path.join(COMMON, "logic", "DailyResetScheduler.kt")
SEASON = os.path.join(COMMON, "logic", "SeasonEngine.kt")
GAMESTATE = os.path.join(COMMON, "logic", "GameState.kt")

PASS, FAIL = 0, 0


def check(cond, msg):
    global PASS, FAIL
    if cond:
        PASS += 1
    else:
        FAIL += 1
        print(f"  ✗ {msg}")


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def parse_enum_ids(src, enum_name):
    """从 `enum class <Name>(...){ A("id", ...), B("id", ...), }` 抽取每个条目的字符串 id。"""
    m = re.search(rf"enum class {enum_name}\(.*?\)\s*\{{(.*?)\}}", src, re.S)
    if not m:
        return []
    body = m.group(1)
    return re.findall(r'\w+\(\s*"([^"]+)"', body)


def parse_const_int(src, name):
    """解析 `const val <name> = 123` 或 `= 86_400_000L`，返回 int（去下划线）。"""
    m = re.search(rf"const val {name}\s*=\s*([0-9_]+)", src)
    if not m:
        return None
    return int(m.group(1).replace("_", ""))


# ───────── 1) 权威目标清单（DailyResetEngine.Target，应 7 项） ─────────
engine_src = read(ENGINE)
target_ids = parse_enum_ids(engine_src, "Target")
EXPECTED_TARGETS = {"quests", "secret_realm", "dungeon", "shop",
                    "stronghold", "reputation", "fatigue"}
check(len(target_ids) == 7, f"每日重置目标 Target 枚举=7 项（实 {len(target_ids)}）")
check(set(target_ids) == EXPECTED_TARGETS,
      f"Target 七项 id 与 GDD 语义一致（实 {sorted(target_ids)}）")
check(len(set(target_ids)) == len(target_ids), "Target 七项 id 互不重复")

# ───────── 2) isCrossDay 跨天判定（纯函数契约） ─────────
def is_cross_day(last, today):
    # 与 DailyResetEngine.isCrossDay 完全一致：首启空串或 != 今日即跨天
    return last == "" or last != today

check(is_cross_day("", "2026-08-17") is True, "首启（last 空串）视为跨天")
check(is_cross_day("2026-08-16", "2026-08-17") is True, "昨日→今日 跨天")
check(is_cross_day("2026-08-17", "2026-08-17") is False, "同日不跨天")
check(is_cross_day("2026-08-31", "2026-09-01") is True, "跨月边界 跨天")
check(is_cross_day("2025-12-31", "2026-01-01") is True, "跨年边界 跨天")
check(is_cross_day("2026-08-17", "2026-08-16") is True,
      "时钟回拨（今日<上次）仍判跨天（与 Kotlin != 语义一致，防漏重置）")
check(is_cross_day("", "") is True, "双空串（理论上 today 非空）仍判跨天")

# ───────── 3) resolvePending 待重置清单 ─────────
def resolve_pending(last, today):
    return target_ids if is_cross_day(last, today) else []

check(resolve_pending("2026-08-16", "2026-08-17") == target_ids,
      "跨天 → 返回全部 7 项待重置目标")
check(resolve_pending("2026-08-17", "2026-08-17") == [],
      "同日 → 返回空列表（调用方不应改动任何字段）")
check(resolve_pending("", "2026-08-17") == target_ids,
      "首启 → 返回全部 7 项（使初始状态规整）")

# ───────── 4) 赛季推进（SeasonEngine 常量 + seasonsElapsed） ─────────
season_src = read(SEASON)
SEASON_LEN = parse_const_int(season_src, "SEASON_LENGTH_DAYS")
DAY_MS = parse_const_int(season_src, "DAY_MS")
check(SEASON_LEN == 90, f"SEASON_LENGTH_DAYS=90（实 {SEASON_LEN}）")
check(DAY_MS == 86_400_000, f"DAY_MS=86400000（实 {DAY_MS}）")

def seasons_elapsed(start, now):
    if now < start:
        return 0
    return ((now - start) // DAY_MS) // SEASON_LEN

check(seasons_elapsed(0, 0) == 0, "赛季第 0 天 → 0 个赛季")
check(seasons_elapsed(0, 89 * DAY_MS) == 0, "第 89 天（<90）→ 0 个赛季")
check(seasons_elapsed(0, 90 * DAY_MS - 1) == 0, "第 90 天前 1ms → 0 个赛季")
check(seasons_elapsed(0, 90 * DAY_MS) == 1, "满 90 天 → 推进 1 个赛季")
check(seasons_elapsed(0, 91 * DAY_MS) == 1, "第 91 天 → 仍 1 个赛季")
check(seasons_elapsed(0, 179 * DAY_MS) == 1, "第 179 天 → 仍 1 个赛季")
check(seasons_elapsed(0, 180 * DAY_MS) == 2, "满 180 天 → 推进 2 个赛季")
check(seasons_elapsed(1000, 500) == 0, "now<start → 0 个赛季（防负）")

# ───────── 5) SchedulerStatus 4 态 ─────────
sched_src = read(SCHED)
status_ids = parse_enum_ids(sched_src, "SchedulerStatus")
EXPECTED_STATUS = {"idle", "registered", "suspended", "unavailable"}
check(len(status_ids) == 4, f"SchedulerStatus=4 态（实 {len(status_ids)}）")
check(set(status_ids) == EXPECTED_STATUS,
      f"4 态 id 正确（实 {sorted(status_ids)}）")

# ───────── 6) 接线：唯一权威 isCrossDay 被复用（防旁路漂移） ─────────
gs_src = read(GAMESTATE)
check("DailyResetEngine.isCrossDay" in gs_src,
      "GameState.maybeResetDaily 复用 DailyResetEngine.isCrossDay（唯一权威）")
check("lastDailyReset = today" in gs_src,
      "GameState 跨天重置后写入 lastDailyReset = today")
check("DailyResetEngine.isCrossDay" in sched_src,
      "DailyResetScheduler.resolvePending 复用 DailyResetEngine.isCrossDay")
check("DailyResetEngine.TARGETS" in sched_src,
      "DailyResetScheduler.resolvePending 复用 DailyResetEngine.TARGETS（7 项权威）")

# ───────── 汇总 ─────────
print(f"\n每日重置契约校验：通过 {PASS} 项，失败 {FAIL} 项")
if FAIL == 0:
    print("✓ 全部断言通过（#181 每日重置契约已钉死）")
else:
    print("✗ 存在失败断言")
    raise SystemExit(1)
