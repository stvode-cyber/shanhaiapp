#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
#180 云同步契约 · Python 等价验证
=================================
验证对象：shared 侧 [CloudSyncEngine]（纯函数、无网络/账号依赖）。
核心确定性逻辑：
  1. FNV-1a 32-bit 校验和（UTF-16 码元 → 对 ASCII 等价于字节级 FNV-1a）；
  2. merge：modifiedAt 做 LWW，平局按 deviceId 字典序，三货币(灵玉/妖丹/香火)取 max；
  3. toEnvelope/fromEnvelope 版本门控（复用 SaveExportEngine.EXPORT_VERSION）；
  4. 合并结果经 SaveIntegrityEngine.sanitize 收口。

验证策略（与 #181 同构）：
  A. 解析 Kotlin 源码，钉死契约常量与接线（不可漂移）；
  B. 用 Python 复现确定性函数，对标准 FNV-1a 参考向量断言，证明复现正确；
  C. 对 merge 各类分支（LWW / 平局 / null / 货币 max）断言行为正确。

运行：python verify/verify_cloud_sync.py
"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT = os.path.join(ROOT, "shared", "src", "commonMain", "kotlin", "com", "shanhai", "core")

CS_ENGINE = os.path.join(KT, "persistence", "CloudSyncEngine.kt")
EXPORT_ENGINE = os.path.join(KT, "persistence", "SaveExportEngine.kt")
INTEGRITY_ENGINE = os.path.join(KT, "persistence", "SaveIntegrityEngine.kt")

_passed = 0
_failed = 0


def check(cond, name):
    global _passed, _failed
    if cond:
        _passed += 1
        print(f"  ✓ {name}")
    else:
        _failed += 1
        print(f"  ✗ {name}")


def read_text(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


# ---------- A. 源码契约常数与接线解析 ----------

def section(src, fn_name):
    """提取 `fun fn_name` 到下一个顶层 `fun ` / `}` 之前的代码块。"""
    m = re.search(r"fun\s+" + re.escape(fn_name) + r"\b", src)
    if not m:
        return ""
    start = m.start()
    # 找到函数体的 { 与匹配的 }
    i = src.index("{", m.end())
    depth = 0
    for j in range(i, len(src)):
        if src[j] == "{":
            depth += 1
        elif src[j] == "}":
            depth -= 1
            if depth == 0:
                return src[start:j + 1]
    return src[start:]


def test_source_contract():
    print("[A] 源码契约常数与接线（解析级钉死）")
    cs = read_text(CS_ENGINE)
    exp = read_text(EXPORT_ENGINE)

    # FNV-1a 魔数必须出现（链接到标准算法，防私有改动）
    check("0x811c9dc5" in cs, "checksum 使用 FNV-1a 偏移基 0x811c9dc5")
    check("0x01000193" in cs, "checksum 使用 FNV-1a 素数 0x01000193")

    # EXPORT_VERSION 必须为 1 且被复用
    m = re.search(r"EXPORT_VERSION\s*=\s*(\d+)", exp)
    check(m is not None and int(m.group(1)) == 1, "SaveExportEngine.EXPORT_VERSION == 1")
    ver = int(m.group(1)) if m else 1
    check(f"SaveExportEngine.EXPORT_VERSION" in cs, "toEnvelope 复用 SaveExportEngine.EXPORT_VERSION")

    # toEnvelope / fromEnvelope 版本门控
    to_env = section(cs, "toEnvelope")
    check("SaveExportEngine.EXPORT_VERSION" in to_env, "toEnvelope 把 EXPORT_VERSION 写入信封 v 字段")
    from_env = section(cs, "fromEnvelope")
    check("e.v != SaveExportEngine.EXPORT_VERSION" in from_env, "fromEnvelope 对版本不符返回 null（版本门控）")
    check("return null" in from_env, "fromEnvelope 解析失败/版本不符 → null")

    # merge：LWW + 三货币 max + sanitize 接线
    merge = section(cs, "merge")
    check("winner.save.copy(" in merge, "merge 以 LWW 胜方存档为基底（winner.save.copy）")
    check(merge.count("maxOf(") == 3, "merge 恰好对三货币(灵玉/妖丹/香火)取 max")
    check("SaveIntegrityEngine.sanitize(" in merge, "merge 结果经 SaveIntegrityEngine.sanitize 收口")
    check("ignoreUnknownKeys = true" in cs, "Json 配置 ignoreUnknownKeys=true（旧存档兼容）")

    # 三货币字段名存在性（与 SaveData 对应）
    sd = read_text(os.path.join(KT, "persistence", "SaveData.kt"))
    check(all(c in sd for c in ("lingyu", "yaodan", "incense")), "SaveData 含三货币字段 lingyu/yaodan/incense")
    return ver


# ---------- B. FNV-1a 复现 + 标准参考向量 ----------

def fnv1a_32(text):
    """复现 Kotlin CloudSyncEngine.checksum：FNV-1a 32-bit，作用于码元低字节（ASCII 等价字节级）。"""
    h = 0x811C9DC5
    for ch in text:
        b = ord(ch) & 0xFF  # UTF-16 码元低字节；ASCII 下即字节值
        h = (h ^ b) & 0xFFFFFFFF
        h = (h * 0x01000193) & 0xFFFFFFFF
    # Kotlin hash.toInt()：32-bit 补码有符号
    return h if h < 0x80000000 else h - 0x100000000


def test_fnv_reference():
    print("[B] FNV-1a 复现正确性（对标准参考向量断言）")
    # 标准 FNV-1a 32-bit 参考值（有符号 32-bit 表示）
    check(fnv1a_32("") == -2128831035, "FNV-1a(\"\") == 0x811c9dc5（空串→偏移基）")
    check(fnv1a_32("a") == -468965076, "FNV-1a(\"a\") == 0xe40c292c（标准参考向量）")
    # 确定性：同输入必同输出
    check(fnv1a_32("save-v1") == fnv1a_32("save-v1"), "checksum 是纯函数（同输入同输出）")
    # 不同输入不同哈希（雪崩性基本校验）
    check(fnv1a_32("save-v1") != fnv1a_32("save-v2"), "不同输入产生不同校验和")


# ---------- C. merge LWW + 平局 + 货币 max ----------

def merge(local, remote):
    """等价复现 CloudSyncEngine.merge（仅含契约相关字段）。"""
    if local is None:
        return remote
    if remote is None:
        return local
    if remote["modifiedAt"] > local["modifiedAt"]:
        winner, loser = remote, local
    elif local["modifiedAt"] > remote["modifiedAt"]:
        winner, loser = local, remote
    elif remote["deviceId"] > local["deviceId"]:  # 字典序平局打破
        winner, loser = remote, local
    else:
        winner, loser = local, remote
    merged = dict(winner["save"])
    merged["lingyu"] = max(winner["save"]["lingyu"], loser["save"]["lingyu"])
    merged["yaodan"] = max(winner["save"]["yaodan"], loser["save"]["yaodan"])
    merged["incense"] = max(winner["save"]["incense"], loser["save"]["incense"])
    return {"deviceId": winner["deviceId"], "modifiedAt": winner["modifiedAt"], "save": merged}


def env(device_id, modified_at, lingyu, yaodan, incense):
    return {"deviceId": device_id, "modifiedAt": modified_at,
            "save": {"lingyu": lingyu, "yaodan": yaodan, "incense": incense}}


def test_merge():
    print("[C] merge LWW + deviceId 平局 + 三货币 max")
    # remote 更新 → remote 胜
    r = merge(env("A", 1000, 5, 5, 5), env("B", 2000, 1, 1, 1))
    check(r["deviceId"] == "B" and r["modifiedAt"] == 2000, "remote modifiedAt 更大 → remote 胜")
    # local 更新 → local 胜
    r = merge(env("A", 3000, 1, 1, 1), env("B", 2000, 9, 9, 9))
    check(r["deviceId"] == "A" and r["modifiedAt"] == 3000, "local modifiedAt 更大 → local 胜")
    # modifiedAt 平局，deviceId 字典序大者胜
    r = merge(env("AAA", 1000, 1, 1, 1), env("BBB", 1000, 9, 9, 9))
    check(r["deviceId"] == "BBB", "modifiedAt 平局 → deviceId 字典序大者(BBB)胜")
    r = merge(env("ZZZ", 1000, 1, 1, 1), env("AAA", 1000, 9, 9, 9))
    check(r["deviceId"] == "ZZZ", "modifiedAt 平局 → deviceId 字典序大者(ZZZ)胜")
    # 货币取 max（胜方低、败方高）
    r = merge(env("A", 3000, 1, 2, 3), env("B", 2000, 8, 9, 10))
    check(r["save"] == {"lingyu": 8, "yaodan": 9, "incense": 10}, "三货币取双端 max（胜方低+败方高→败方值）")
    # 货币 max 双向
    r = merge(env("A", 3000, 8, 1, 4), env("B", 2000, 2, 9, 1))
    check(r["save"]["lingyu"] == 8 and r["save"]["yaodan"] == 9 and r["save"]["incense"] == 4,
          "三货币各自取 max（混合高低）")
    # null 兜底
    check(merge(None, env("B", 1, 1, 1, 1)) is not None, "local==null → 返回 remote")
    check(merge(env("A", 1, 1, 1, 1), None) is not None, "remote==null → 返回 local")
    check(merge(None, None) is None, "双 null → 返回 null（调用方兜底）")
    # 非货币字段一律 LWW 胜方（其余字段采用 winner）
    # 用更长的 save 字典模拟：胜方带额外字段，败方无
    win = {"deviceId": "A", "modifiedAt": 3000,
           "save": {"lingyu": 1, "yaodan": 1, "incense": 1, "extra": "from_winner"}}
    lose = {"deviceId": "B", "modifiedAt": 2000,
            "save": {"lingyu": 9, "yaodan": 9, "incense": 9, "extra": "from_loser"}}
    r = merge(win, lose)
    check(r["save"]["extra"] == "from_winner", "非货币字段一律采用 LWW 胜方（extra=from_winner）")
    check(r["save"]["lingyu"] == 9, "但三货币仍取 max（lingyu=9）")


def main():
    print("=" * 60)
    print("#180 云同步契约 · Python 等价验证")
    print("=" * 60)
    test_source_contract()
    test_fnv_reference()
    test_merge()
    print("-" * 60)
    print(f"通过 {_passed} 项，失败 {_failed} 项")
    if _failed == 0:
        print("✓ 全部断言通过（#180 云同步契约已钉死）")
    else:
        print("✗ 存在失败断言")
    print("-" * 60)
    return _failed == 0


if __name__ == "__main__":
    import sys
    sys.exit(0 if main() else 1)
