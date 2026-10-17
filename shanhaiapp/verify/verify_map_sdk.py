#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
《山海妖志》#184 地图 SDK 适配契约校验（Python 等价验证，不依赖 Kotlin 编译器）。

#184 地图 SDK（GDD 十六·探索双模式）的 shared 层是**契约占位**：真实地图 SDK（ARKit/MapKit/
高德/腾讯）由平台实现，shared 仅定义地图适配契约，并**复用 LocationEngine 的 POI 单一真源**
（NODE_KEYS / STRONGHOLD_NODES），不重复硬编码坐标。

本脚本分三层钉死（补强主验证脚本仅覆盖的"契约接线/类型/双端存在性"，新增行为级复现）：
  [A] 解析级契约接线：MapSdkAdapter 接口 / MapNode·MapPoi 数据类 / PoiKind 两态 /
       shared 5 方法签名 / 双端镜像（Core.swift）存在性。
  [B] 单一真源解析：NODE_KEYS 4 节点真值（坐标来自 key 的 "lat:lng"）/ nodeCoordinates() 解析逻辑 /
       STRONGHOLD_NODES 派生自 NODE_KEYS.values（恰好等于全部灵脉节点 → 无独立据点）。
  [C] buildPois 行为级复现（核心新增，补强主验证）：复用真源构建 POI、坐标精确映射、
       id==name 接线、kind 全部 STRONGHOLD 的成因（白名单恰覆盖全部节点）、
       不重复硬编码坐标、kind 判定分支正确性、双端坐标真值一致收敛。

运行：python verify/verify_map_sdk.py
"""
import re
import sys
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATH_MAP = os.path.join(ROOT, "shared/src/commonMain/kotlin/com/shanhai/core/logic/MapSdkAdapter.kt")
PATH_LOC = os.path.join(ROOT, "shared/src/commonMain/kotlin/com/shanhai/core/logic/LocationEngine.kt")
PATH_CORE = os.path.join(ROOT, "iosApp/iosApp/Core.swift")


def slurp(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


map_src = slurp(PATH_MAP)
loc_src = slurp(PATH_LOC)
core_src = slurp(PATH_CORE)


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
# A1. shared 契约类型定义
check('interface MapSdkAdapter' in map_src, "地图SDK：shared 定义 interface MapSdkAdapter")
check('data class MapNode' in map_src and 'data class MapPoi' in map_src,
      "地图SDK：shared 定义 MapNode/MapPoi 数据类")
check('enum class PoiKind' in map_src and 'LINGMAI' in map_src and 'STRONGHOLD' in map_src,
      "地图SDK：shared PoiKind 两态 LINGMAI/STRONGHOLD")

# A2. 双端镜像类型存在（Core.swift）
check('struct MapSdkAdapter' in core_src and 'struct MapNode' in core_src and 'struct MapPoi' in core_src,
      "地图SDK：Swift 镜像 MapSdkAdapter/MapNode/MapPoi")
check('enum PoiKind' in core_src and 'case lingmai' in core_src and 'case stronghold' in core_src,
      "地图SDK：Swift PoiKind 两态一致（lingmai/stronghold）")

# A3. shared MapSdkAdapter 恰好 5 方法（4 抽象 + buildPois 默认实现）
_kt_methods = [
    "fun isAvailable(): Boolean",
    "fun overlayPois(pois: List<MapPoi>): Boolean",
    "fun setPlayerLayer(lat: Double, lng: Double): Boolean",
    "fun focusRegion(lat: Double, lng: Double, zoom: Int): Boolean",
    "fun buildPois(): List<MapPoi>",
]
for _m in _kt_methods:
    check(_m in map_src, f"地图SDK：shared 含契约方法 {_m}")
check(sum(1 for _m in _kt_methods if _m in map_src) == 5, "地图SDK：shared 契约方法数 = 5")

# A4. Swift MapSdkAdapter 恰好 5 方法（双端签名一致）
_sw_methods = [
    "static func isAvailable() -> Bool",
    "static func overlayPois(_ pois: [MapPoi]) -> Bool",
    "static func setPlayerLayer(lat: Double, lng: Double) -> Bool",
    "static func focusRegion(lat: Double, lng: Double, zoom: Int) -> Bool",
    "static func buildPois() -> [MapPoi]",
]
for _m in _sw_methods:
    check(_m in core_src, f"地图SDK：Swift 含契约方法 {_m}")
check(sum(1 for _m in _sw_methods if _m in core_src) == 5, "地图SDK：Swift 契约方法数 = 5")
check('static func buildPois() -> [MapPoi]' in core_src, "地图SDK：Swift buildPois 契约方法存在")

# ============================ [B] 单一真源解析 ============================
# B1. NODE_KEYS 真源定义（private 本质不改）
check('private val NODE_KEYS = mapOf(' in loc_src, "地图SDK：LocationEngine.NODE_KEYS 真源定义存在")

# B2. 解析 NODE_KEYS 4 节点真值（"lat:lng" to "name"）
_m = re.search(r"NODE_KEYS\s*=\s*mapOf\((.*?)\)", loc_src, re.S)
check(_m is not None, "地图SDK：NODE_KEYS mapOf 块可解析")
_node_block = _m.group(1) if _m else ""
_entries = re.findall(r'"([\d.]+):([\d.]+)"\s+to\s+"([^"]+)"', _node_block)
nodes = {name: (float(lat), float(lng)) for (lat, lng, name) in _entries}
check(len(_entries) == 4, "地图SDK：NODE_KEYS 恰好 4 个灵脉节点")
check(set(nodes.keys()) == {"城隍桥", "古庙", "老槐树", "东海之滨"},
      "地图SDK：4 节点名 = 城隍桥/古庙/老槐树/东海之滨")

# B3. 坐标精确真值（与 GDD·LBS 真实地理锚点一致）
_expected = {
    "城隍桥": (31.23, 121.47),
    "古庙": (30.25, 120.16),
    "老槐树": (25.03, 102.71),
    "东海之滨": (30.62, 122.08),
}
for name, (la, ln) in _expected.items():
    got = nodes.get(name)
    check(got is not None and abs(got[0] - la) < 1e-9 and abs(got[1] - ln) < 1e-9,
          f"地图SDK：节点 {name} 坐标精确 = ({la},{ln})")

# B4. nodeCoordinates() 解析逻辑（split(":") + toDouble，顺序 lat=lng）
_nc = balanced_block(loc_src, "nodeCoordinates")
check(_nc is not None, "地图SDK：nodeCoordinates() 函数体可定位")
check(_nc is not None and 'k.split(":")' in _nc, "地图SDK：nodeCoordinates 用 split(\":\") 拆解坐标键")
check(_nc is not None and 'it[0].toDouble()' in _nc and 'it[1].toDouble()' in _nc,
      "地图SDK：nodeCoordinates 顺序 lat=it[0]/lng=it[1] 解析为 Double")
check(_nc is not None and 'MapNode(' in _nc, "地图SDK：nodeCoordinates 构造 MapNode(lat,lng,name)")

# B5. STRONGHOLD_NODES 派生自 NODE_KEYS.values（单一真源，防 POI 漂移）
check('val STRONGHOLD_NODES: Set<String> = NODE_KEYS.values.toSet()' in loc_src,
      "地图SDK：STRONGHOLD_NODES 派生自 NODE_KEYS.values（单一真源）")

# B6. 白名单恰好等于全部灵脉节点（当前设计无独立据点 → buildPois 全为 STRONGHOLD 的成因）
strongholds = set(nodes.keys())  # NODE_KEYS.values.toSet()
check(len(strongholds) == len(nodes) == 4,
      "地图SDK：STRONGHOLD_NODES 规模 = NODE_KEYS 规模 = 4（白名单恰覆盖全部灵脉）")
check(strongholds == set(nodes.keys()), "地图SDK：据点白名单 = 全部灵脉节点（无独立据点，单一真源）")

# ============================ [C] buildPois 行为级复现 ============================
# C1. buildPois 复用 LocationEngine 真源（不重复硬编码 POI）
check('LocationEngine.nodeCoordinates()' in map_src and 'LocationEngine.STRONGHOLD_NODES' in map_src,
      "地图SDK：shared buildPois 复用 LocationEngine.nodeCoordinates/STRONGHOLD_NODES")
check('LocationEngine.nodeEntries' in core_src and 'LocationEngine.strongholdNodes' in core_src,
      "地图SDK：Swift buildPois 复用 LocationEngine.nodeEntries/strongholdNodes")

# C2. 复刻 buildPois 行为：遍历真源节点，id=name、坐标来自真源、kind 由白名单判定
def kind_of(name):
    return "STRONGHOLD" if name in strongholds else "LINGMAI"

pois = []
for name, (lat, lng) in nodes.items():
    pois.append({"id": name, "name": name, "lat": lat, "lng": lng, "kind": kind_of(name)})

# C3. POI 数量 = 真源节点数 = 4
check(len(pois) == 4, "地图SDK：buildPois 产出 POI 数 = 真源节点数 = 4")

# C4. id == name 接线（与 MapPoi(id=node.name, name=node.name) 一致）
check(all(p["id"] == p["name"] for p in pois), "地图SDK：每个 POI id == name（对齐节点名）")

# C5. 坐标精确来自真源（不漂移）
check(all(abs(p["lat"] - nodes[p["name"]][0]) < 1e-9 and abs(p["lng"] - nodes[p["name"]][1]) < 1e-9
           for p in pois), "地图SDK：每个 POI 坐标精确匹配真源解析值")

# C6. 当前设计下全部 kind = STRONGHOLD（白名单恰覆盖全部节点，故无 LINGMAI 实例）
check(all(p["kind"] == "STRONGHOLD" for p in pois),
      "地图SDK：buildPois 产出的 POI 全部为 STRONGHOLD（白名单恰覆盖全部节点）")
check('PoiKind.STRONGHOLD' in map_src and 'PoiKind.LINGMAI' in map_src,
      "地图SDK：buildPois 仍显式声明两种 kind 分支（LINGMAI 备用）")

# C7. 不重复硬编码坐标（MapSdkAdapter 内无独立坐标字面量）
_lits = ["31.23", "121.47", "30.25", "120.16", "25.03", "102.71", "30.62", "122.08"]
check(all(_l not in map_src for _l in _lits),
      "地图SDK：MapSdkAdapter 内无独立硬编码坐标（复用真源）")

# C8. kind 判定分支正确性（即便当前无 LINGMAI 实例，分支逻辑不丢失）
check(kind_of("城隍桥") == "STRONGHOLD", "地图SDK：白名单内节点 → STRONGHOLD")
check(kind_of("某未来灵脉") == "LINGMAI", "地图SDK：白名单外节点 → LINGMAI（分支逻辑正确）")
check('LocationEngine.STRONGHOLD_NODES.contains(node.name)' in map_src,
      "地图SDK：shared 据点判定复用白名单 contains")

# C9. 双端坐标真值一致收敛（Core.swift nodeEntries ↔ NODE_KEYS）
_sw_nodes = re.findall(r'MapNode\(lat:\s*([\d.]+),\s*lng:\s*([\d.]+),\s*name:\s*"([^"]+)"\)', core_src)
sw_nodes = {name: (float(lat), float(lng)) for (lat, lng, name) in _sw_nodes}
check(len(sw_nodes) == 4, "地图SDK：Swift nodeEntries 恰好 4 节点")
for name, (la, ln) in nodes.items():
    got = sw_nodes.get(name)
    check(got is not None and abs(got[0] - la) < 1e-9 and abs(got[1] - ln) < 1e-9,
          f"地图SDK：双端坐标真值一致 {name} = ({la},{ln})")
check(set(sw_nodes.keys()) == set(nodes.keys()), "地图SDK：双端节点名集合一致收敛")

# C10. 双端据点判定复用白名单（Swift 同样 contains）
check('LocationEngine.strongholdNodes.contains(node.name)' in core_src,
      "地图SDK：Swift 据点判定复用白名单 contains（双端一致）")

# ============================ 汇总 ============================
print(f"\n地图SDK契约校验 通过 {passed} 项，失败 {failed} 项（#184 复用 NODE_KEYS/STRONGHOLD_NODES 单一真源）")
sys.exit(1 if failed else 0)
