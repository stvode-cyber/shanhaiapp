# 《山海妖志》移动端项目 · 长期记忆

## 项目定位
- 工作区：捉宠游项目戏。基于 GDD《山海妖志》（LBS 山海经收妖 + 图鉴 + 对战 + 养成）。
- 技术路线：**KMP 原生双端**：`shared` 共享 Kotlin 核心（commonMain）+ Android Compose 主路径 + iOS `Core.swift` 纯 Swift 镜像。工程目录 `shanhaiapp/`。
- 当前范围：核心循环 + 任务 + 持久化 + 养成 的可玩切片。

## 关键约定（务必遵守）
- **本机无 JDK/Gradle**：任何 Kotlin/Swift 逻辑改动都用**等价 Python 脚本**断言验证（`shanhaiapp/verify/verify_lbs_gacha_dungeon.py` 主 + `verify_pool.py` 池），不依赖编译；用户本机 `./gradlew :shared:test` 才做编译级验证。
- 数值/判定全部收敛到 `shared`，双端共用；`Core.swift` 须与 shared API 一一对应。
- 存档**只存进度不存定义**（妖取 MonsterPool、任务取 QuestLibrary），`ignoreUnknownKeys` 保证旧存档兼容。
- 用户偏好：全权委托，沟通简洁行动导向（"继续工作"），无需逐步审核。

## 当前状态（2026-08-19）
- **纯客户端可验证断言已完整收口**：主验证 **1502/1502 全过**（1487 基线 + GPS 两档 +14 + 双端镜像审计 +1），池验证 **1388/1388 全过**。
- **可运行游戏原型已交付（#206/#208~#223，2026-08-18）**：零依赖 JS 原型 `shanhaiapp/prototype/`（`data.js` 由 `gen_data.py` 解析 Kotlin 生成，含 100 妖 / 73 技能 / 9 装备 / `game.js` 双模复刻全引擎+Player / `index.html` 中国风可试玩 / `test_game.js` **591 项 Node 断言全过**（63 基线 + 58 装备五路径·炼妖融合 + 54 四元系统：信誉/连携/天气地形/疲劳 + 39 段位天梯·Ban/Pick + 22 副本妖境：四类副本/canEnter 边界/enter 区间·布尔·妖王/enterDungeon 接线含疲劳封顶与妖王收服 + 23 在家秘境：主题五行偏向/稀有度凡灵主导/加权抽样确定性/enterSecretRealm 免费3次·耗香火·阻断·记录任务/ + 25 家园洞府：香火·妖丹时产/风水攻击增益/升级花费/compute 封顶12h/collectHomestead 首收不补发·升级 Lv1/风水增益接入战斗乘子 + 31 每日重置：Targets 7 项/isCrossDay 真值表/resolvePending 跨天 7 项·同日 0/seasonsElapsed 边界/Scheduler 4 态/maybeResetDaily 集成 跨天复位 secretRealmLeft=3·fatigue=0·rep+1·stronghold 清空·lastDailyReset 写入/ + 16 商店：引妖香耗灵玉30→回香火60/INCENSE_DAILY_BUY_CAP=3/canBuyIncense 真值/buyIncense 端到端+Player.buyIncense 连买3次封顶·跨天重置清零可再购）/ `smoke_dom.js` **54/54 最小 DOM 垫片零异常**，覆盖全 13 tab（含副本妖境/在家秘境）+ 四元系统 + 段位 + 副本 + 秘境 UI 路径）。作为"本机无 JDK 致编译闭环阻塞"的替代交付，使核心循环真实可运行、可直接浏览器打开试玩；GPS 防作弊三态（正常/DEGRADED/REJECTED 扣信誉）已在原型内置演示；**装备系统（9 件灵装 + 祭炼/升星/突破/淬炼/继承五路径 + 炼妖融合）已原型化**（数值忠实复刻 `EquipmentEngine.kt`/`FusionEngine.kt`）；**四元系统（ReputationEngine/ComboEngine/WeatherEngine/FatigueEngine）已原型化并接入 Player/Battle/Location/UI**（信誉分档+扣减/连携组合技增伤·回血/天气地形五行偏置伤害/疲劳累积+单日收益封顶+跨天重置），数值零漂移复刻对应 `*.kt`；**排位赛（RankEngine/RankBanPickEngine）已原型化并接入战斗结算（胜+20/负-15/满100升段/化神封顶/炼气保底/升段发荣誉奖励）+ 对战页「排位选将 Ban/Pick 演练」面板（双方各 ban2/pick3、ban 并集移出可用池、isComplete 校验）**，数值零漂移复刻 `RankEngine.kt`/`RankBanPickEngine.kt`；**副本妖境（DungeonEngine）已原型化并接入 Player/UI**（四类：山海异闻/灵脉试炼/渡劫秘境/年节限时；`enterDungeon` 接线：扣香火/记当日次数/yaodan 受疲劳·宗门科技「资粮」倍率/妖王概率收服补全图鉴/加疲劳 `FATIGUE_DUNGEON`），数值零漂移复刻 `DungeonEngine.kt`/`GameState.enterDungeon`）；**在家秘境（SecretRealmEngine）已原型化并接入 Player/UI**（每日免费 3 次优先、耗尽耗香火 20、主题→五行偏向复用 `LocationEngine.regionElementBias`、秘境稀有度权重凡/灵主导加权抽样、`enterSecretRealm` 接线忠实不接疲劳、记录 `ENTER_SECRET` 任务），数值零漂移复刻 `SecretRealmEngine.kt`/`GameState.enterSecretRealm`；**家园洞府（HomesteadEngine）已原型化并接入 Player/UI/战斗**（阵法风水每级+10%香火·+0.1妖丹·+2%攻击增益/离线产出封顶12h/首收不补发/collectHomestead 入账·upgradeHomestead 升级/风水增益接入战斗 teamMul 忠实复刻 GameState.battleTeamMul），数值零漂移复刻 `HomesteadEngine.kt`/`GameState.collectHomestead`·`upgradeHomestead`。
- 演进路径：核心功能 967 → 平台契约占位 +142(#180~#184) → 战斗自检复刻 +316(#186)=1425 → A 序列纯逻辑 +62 → GPS 防作弊对齐 GDD +14 → 双端镜像审计 +1 = **1502**。
  - A 序列（GDD 覆盖审计定位的纯逻辑缺口，全部落地）：**A-3 妖池分布钉死** + **A-1 道德选择分支**(SUPPRESS 镇压 atk×1.10 / TRANSCEND 超度 def·hp×1.08) + **A-2 排位 Ban/Pick**(BAN=2/PICK=3、不可变 DraftState、枚举式非法态防护)。
  - **GPS 防作弊对齐 GDD（#191~#196，已落地）**：废弃一元 `MAX_SPEED_KMH=300`/瞬移窗口 `10s`；改 `SOFT_SPEED_KMH=30` + `TELEPORT_WINDOW_SEC=180` + 三档 `IntegrityTier`(NORMAL/DEGRADED/REJECTED)；DEGRADED 降品质（神不出、仙×0.2，不阻断不扣分）、REJECTED 瞬移收服无效+信誉扣分（`reportIntegrity` 仅 REJECTED 触发 `CheatKind.TELEPORT`）；双端 Kotlin/Swift/UI/Python 全镜像。
  - **双端镜像一致性审计（#197，已落地）**：新增 `verify/verify_mirror.py`（花括号深度感知，类型级差集为真实漂移信号）；**类型漂移 0**；并修复唯一真实类型安全漂移——Swift `MoralChoice` 由 `String?` 升级为 `enum MoralChoice: String, Codable`（与 Kotlin `enum class` 一一对应），重构 `MonsterInstance`/`MoralChoiceEngine.modifiers`/`GameState.catchMonster` 三处。
  - **双端成员级镜像审计（可靠版）（#198，已落地）**：升级 `verify/verify_mirror.py` 做可信成员级比对（函数级 `[3a]` 高信号 + 属性级 `[3b]` 备查；蛇形↔camel 归一、Swift 同行多声明拆分、实例/静态合并、剔除 private 非公开 API）；**修复采集器括号深度感知 bug**（原仅跟踪花括号深度，导致 Kotlin 嵌套 `data class Node(...)` 构造参数被误算为外层 `object` 属性，伪造 54 个 `[3b]` 缺口），新增圆括号深度跟踪后成员差集彻底归零；并就地补齐 5 处真实镜像漂移（Core.swift `MonsterPool.byName`/`starters`、`SectTechEngine.nodeIds`、`TalentEngine.layersOf`、`Codex.milestones`/`newlyUnlocked`/`MILESTONE_DEFS`、`GachaEngine.softPityStart`/`softPityStep`）。现 **类型漂移 0 / 函数级漂移 0 / 属性级差异 0**。
- 平台/后端契约占位已落地六类（#180 云同步 / #181 重置调度 / #182 社交+GPS / #183 AR / #184 地图 SDK），仅缺运行时接入。
- **双端结构护栏**：`verify_mirror.py` 已作为常驻一致性护栏——类型级 + 成员级（函数/属性）三重零漂移（排除 3 个表示差异 + 24 个 Swift 专有 iOS 辅助类型）；采集器经 #198 修复括号深度感知，可可靠甄别真实漂移 vs 嵌套类字段误报。

## 待办 / 未实现（对照 GDD）
- **纯客户端游戏逻辑层已全闭环（2026-08-17 确认）**：20 章逻辑 + A 序列三项缺口（道德选择/排位 ban-pick/分布计数断言）全部断言；主 1502 / 池 1388 / 双端镜像 0/0/0 全绿。**无任何剩余可自主验证缺口**。注意：`GDD覆盖审计.md` 原"未断言计数"与"A 序列待办"为 A 序列落地前的过期记录，已于 2026-08-17 同步修正；**装备/技能/渡劫的 verify 脚本（verify_equipment_fusion.py / verify_equipment_paths.py / verify_skills.py / verify_tribulation.py）已落地并可独立验证**，原型已覆盖装备五路径·炼妖融合·四元系统（信誉/连携/天气地形/疲劳，均已在 #213~#217 原型化并接入 Player/Battle/UI），纯客户端逻辑层持续全闭环。
- **6 类平台契约运行时接入（需选型）**：AR 摄像头 / 社交服务器权威 / GPS 防作弊服务端 / 真实地图 SDK / 云同步账号 / 每日重置后台调度。其**共享契约逻辑**已陆续用独立 verify 脚本钉死——#181 每日重置（`verify_daily_reset.py` 29 项）、#180 云同步（`verify_cloud_sync.py` 27 项：FNV-1a 校验和复现 + LWW 合并 + 版本门控 + sanitize 接线）、#182 社交据点战（`verify_social_stronghold.py` 33 项：STRONGHOLD_NODES 白名单 4 节点 / Faction 三派 / 1.10 加成 / 50 贡献 / 每日限占领状态机 / 守卫抗注入）、#183 AR 收妖（`verify_ar_capture.py` 40 项：ARStatus 三态 / status 默认 / resolveAtAnchor 纯委托 CaptureEngine.resolve / CaptureEngine 公式权威值+行为边界 / AR≡普通收妖）、#184 地图 SDK（`verify_map_sdk.py` 51 项：NODE_KEYS 4 节点真值 / STRONGHOLD_NODES 派生单一真源 / nodeCoordinates 解析 / MapSdkAdapter 5 方法 / buildPois 复用真源+坐标精确+kind 全 STRONGHOLD 成因+双端真值收敛）。仅**平台运行时壳**（BGTask/WorkManager 调度、Http/URLSession 账号网络、GPS 服务端权威、ARKit/ARCore、地图 SDK）仍待平台/后端选型接入。
  - **#181 每日重置**：shared 契约逻辑（isCrossDay/TARGETS 7 项/resolvePending/seasonsElapsed/SchedulerStatus）**已可 Python 等价验证并钉死**（`verify/verify_daily_reset.py`，29 断言全过）；仅其**平台调度壳**（iOS BGTask / Android WorkManager 何时触发）仍属运行时接入、需平台选型。
  - **#180 云同步**：shared 契约逻辑（FNV-1a/LWW/版本门控/sanitize）**已钉死**（27 断言）；仅**平台网络/账号壳**待接入。
  - **#182 社交据点战本地规则**：`SocialEngine` 状态机 + 白名单守卫 + 加成 + 贡献 + 每日重置 **已钉死**（33 断言）；仅 **GPS 防作弊服务端权威**（`GpsAntiCheatVerifier`/`verifyPoint`）待后端选型。
  - **#183 AR 收妖本地规则**：`ARCaptureController` 三态 + `status()` 默认 + `resolveAtAnchor` 纯委托 `CaptureEngine.resolve`（AR 不引入独立收妖率）+ `ArCaptureBackend` 五方法契约 + `GameState.arStatus` 委托 + `CaptureEngine` 公式（环周期/稀有度系数/0.92·0.06·0.18·1.4·0.8·0.2）与边界（p=1.0 必成·p=0.06 双次独立 rng 解耦）**已钉死**（40 断言）；仅**平台摄像头/ARKit·ARCore 壳**待接入。
  - 其余一类（#182 GPS 服务端权威）的运行时接通依赖后端选型；#184 地图 SDK 的 shared 契约逻辑（NODE_KEYS 4 节点真值 / STRONGHOLD_NODES 派生单一真源 / nodeCoordinates 解析 / MapSdkAdapter 5 方法 / buildPois 复用真源+坐标精确+kind 全 STRONGHOLD 成因+双端真值收敛）**已钉死**（`verify/verify_map_sdk.py`，51 断言全过）；仅**平台地图 SDK 壳**（ARKit/MapKit/高德/腾讯 接入）仍属运行时接入、需平台选型。
- **本机编译闭环（需用户 JDK）**：所有 Kotlin/Swift 改动仅经 Python 等价验证，待 IDE 构建确认；`shanhaiapp/编译验证清单.md` 已列步骤。"核心功能实际可运行"一项已由 `prototype/` JS 原型补齐（见上「可运行游戏原型已交付」）。

## 项目流程约定
- `shanhaiapp/开发进度.md`：进程跟踪器，每动作在「动作日志」记录(时间+改动+验证结论)。
- 工作节奏：方法级审计真实缺口 → 改 shared → Core.swift 镜像 → Python 等价断言 → 五处文档同步(README/开发进度/工作区MEMORY/编译验证清单/今日日志) → 复跑验证。
