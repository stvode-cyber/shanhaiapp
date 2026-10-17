# 《山海妖志》GDD 覆盖审计（shared 逻辑层）

> 生成时间：2026-08-16（更新：A 序列已落地——A-1 道德选择 / A-2 排位 ban/pick / A-3 妖池·技能分布断言）　|　目的：在纯客户端验证收口（1502/1502）后，逐章比对《GDD 整合版》20 章 + 附录，明确 shared 逻辑层**已实现 / 部分实现 / 缺口**。结论：**纯客户端游戏逻辑层已全部闭环**，下一阶段仅余平台/后端选型（B）与用户编译闭环（D）。
> 说明：本审计只看 **shared 游戏逻辑层**（Kotlin commonMain）。UI 渲染、美术资产、音频、平台运行时接入不在此评估。

## 一、覆盖率总览

| 类别 | 章节 |
|---|---|
| ✅ 已实现（逻辑闭环 + 已断言） | 二核心循环、三探索、四收妖、五图鉴、六战斗、七技能、八养成、九装备、十装备升级、十一妖池、十二副本、十三社交、十四经济、十五数值、十六公平与稳健（客户端 GPS 三档 + 信誉/存档稳健）、十七自由度（道德选择镇压/超度）、十八引导、十九留存、二十竞技（段位+排位 ban/pick） |
| ⚠️ 部分实现 / 占位 | 十六 GPS 防作弊服务端权威（客户端三档已对齐 GDD，服务端权威仍占位）、十九 UGC/剧情/音频/分享（未实现） |
| ❌ 纯内容/平台（非逻辑层缺口） | 剧情章节文本、商业化定价表、Live Ops 日历排期、无障碍双编码、分享/AR 合影、国风音效 BGM、美术资产 |

## 二、逐章映射（GDD → shared 实现）

| GDD 章 | 对应引擎 / 文件 | 状态 |
|---|---|---|
| 二 核心循环 | `GameState.kt`（编排 catch/capture/quest） | ✅ |
| 三 探索（双模式） | `LocationEngine.kt`（LBS 加权刷怪）、`SecretRealmEngine.kt`（在家秘境） | ✅ |
| 四 收妖 | `CaptureEngine.kt`、`ARCaptureController.kt`（status 默认 UNSUPPORTED 占位） | ✅ + AR 占位 |
| 五 图鉴 | `Codex.kt`（地域/秘境分离、完成度、里程碑） | ✅ |
| 六 战斗 | `BattleEngine.kt` / `BattleSession.kt` / `BattleIntegrityEngine.kt`（五行×1.6/类别×1.3/伤害公式/2.5D/换妖） | ✅ |
| 七 技能（73 式+组合） | `ComboEngine.kt`（连携）、`FormationEngine.kt`（队伍技）、技能池（model） | ✅ |
| 八 养成三线 | `CultivationEngine.kt`（渡劫进化）、`TalentEngine.kt`（天赋命格）、`FusionEngine.kt`（炼妖融合） | ✅ |
| 九 装备 | `EquipmentEngine.kt`（四件+符文孔） | ✅ |
| 十 装备升级（五路径） | `EquipmentEngine.kt`（祭炼/升星/突破/淬炼/继承） | ✅ |
| 十一 妖池（100 只） | `MonsterPool`（model，20 进化链） | ✅（分布计数已断言钉死，见 A-3） |
| 十二 副本 | `DungeonEngine.kt`（4 本/Boss 多波/五行护盾） | ✅（叙事剧情内容未实现） |
| 十三 社交 | `SocialEngine.kt` + `SocialService`/`GpsAntiCheatVerifier`（#182 契约占位） | ✅ + 服务端占位 |
| 十四 经济 | `ShopEngine.kt`（防通胀）、`GachaEngine.kt`（软保50/硬保90）、`FatigueEngine.kt`（单日封顶） | ✅ |
| 十五 数值 | `Element.kt`（7 行矩阵）、`MonsterCategory.kt`（类别环）、`BattleEngine` 伤害公式 | ✅（#112/#186 已数值级断言） |
| 十六 公平与稳健 | `ReputationEngine.kt`（分层）、`SaveIntegrityEngine`/`DataIntegrityEngine`（自愈+引用校验）、`LocationEngine`（GPS 客户端三档判定 NORMAL/DEGRADED/REJECTED） | ✅ 客户端已对齐 GDD（服务端权威见 #182 占位） |
| 十七 自由度 | `HomesteadEngine.kt`（洞府/命名/展示）、`GameState.catchMonster(choice:)` 道德选择（镇压/超度） | ✅（A-1 已落地，镇压 atk×1.10 / 超度 def·hp×1.08） |
| 十八 引导 | `TutorialEngine.kt`（7 日留存序列） | ✅ |
| 十九 留存 | `HomesteadEngine`（洞府）、`SeasonEngine`（90 天通行证）、`WeatherEngine`（天气地形）、`HonorEngine`（成就勋章称号）、`ActivityEngine`（活动） | ✅（UGC/剧情/音频/分享未实现） |
| 二十 待细化 | `RankEngine.kt`（5 段位）、`SectTechEngine.kt`（10 节点）、`RankBanPickEngine.kt`（ban/pick + meta 平衡） | ✅（A-2 ban/pick 已落地；商业化定价/Live Ops 仍非逻辑层） |
| 附录 五行矩阵 | `Element.kt` BEATS 矩阵 | ✅（#112 已断言 49 项全合法） |

## 三、关键发现：GPS 防作弊未完整落地（GDD 十六 vs 实现）

> **已解决（#191~#196）**：用户批准后将客户端 GPS 防作弊从「一元宽松判定」对齐到 GDD 三档——`SOFT_SPEED_KMH=30`、`TELEPORT_WINDOW_SEC=180`、`IntegrityTier{NORMAL,DEGRADED,REJECTED}`；DEGRADED 降品质（神不出、仙×0.2，不阻断不扣分）、REJECTED 瞬移收服无效+信誉扣分（`reportIntegrity` 仅 REJECTED 触发 `CheatKind.TELEPORT`）；双端 Kotlin/Swift/UI/Python 全镜像，主验证 1487→1501。下方为当时的审计上下文，保留备查。

GDD 十六明确要求**两档**防作弊：
- 软档：持续 **>30km/h** → **降遇妖品质**（不阻断，仅降品质）。
- 硬档：瞬移 **>5km 且 <3min（180s）** → **收服无效 + 信誉扣分**。

实现（`LocationEngine.spawnIntegrity`，且 #182 `GpsAntiCheatVerifier.verifyPoint` 复用同常量）只有**一元判定**：
- `MAX_SPEED_KMH = 300`（注释：远超高铁/航班才拒）——比 GDD 的 30km/h **宽 10 倍**，且是「整批拒刷」而非「降品质」。
- `TELEPORT_WINDOW_SEC = 10`——比 GDD 的 180s **窄 18 倍**，10s~180s 之间的瞬移**漏检**。
- 无「收服无效 + 信誉扣分」客户端联动（GDD 硬档的扣分部分依赖 #182 服务端权威，仍占位）。

**结论**：客户端防作弊比 GDD 设计**显著宽松**，且缺少「30km/h 软降品质」档。这是功能性覆盖缺口，不是常量 typo。是否对齐 GDD（改 300→30、10→180、补软档+捕获扣分联动）属**平衡决策**，需你拍板，我不擅自改游戏数值。

## 四、次要缺口（已全部闭环，备查）

- `MonsterPool` 分布计数与 `SkillPool` 73 式计数：**已在 A-3 中补断言钉死**（主验证 `verify_lbs_gacha_dungeon.py` L63–82：妖池 100 只、稀有度 FAN=27/LING=26/XIAN=17/SHEN=30、类别 JING=15/GUAI=15/YAO=50/LINGSHOU=20、技能 73 式）。
- `RankEngine` 排位 ban/pick + meta 平衡：**已在 A-2 中落地**（`RankBanPickEngine.kt`，BAN=2/PICK=3，不可变 `DraftState` + 枚举式非法态防护，主验证 +30 断言）。

> 注：本节原写于 A 序列落地前，上述两项当时标记为"未实现/未断言"，现已全部完成，记录保留以备查证演进。

## 五、真正的下一阶段候选（按"纯逻辑可建 / 需选型 / 纯内容"分类）

**A. 纯逻辑可建（已全部完成 ✅）：**
1. ✅ 道德选择分支（GDD 十七，A-1）：`GameState.catchMonster(choice:)` 镇压/超度 → 影响妖的有效属性（SUPPRESS atk×1.10 / TRANSCEND def·hp×1.08），双端镜像。
2. ✅ 排位 ban/pick（GDD 二十，A-2）：`RankBanPickEngine.kt`，BAN=2/PICK=3，不可变 `DraftState` + 枚举式非法态防护，+30 断言。
3. ✅ MonsterPool/SkillPool 分布计数断言（A-3）：妖池 100 / 稀有度 / 类别 / 技能 73 式，钉死防扩池漂移。

**B. 需平台/后端选型（运行时接入，非纯客户端）：**
- 六类平台契约（#180–#184）运行时实现（地图 SDK / 后端 API / ARKit / BGTask / 云账号）。
- GPS 服务端权威（#182 `GpsAntiCheatVerifier` + 信誉扣分联动）。

**C. 纯内容/资产（非逻辑）：**
- 剧情章节文本、商业化定价表、Live Ops 日历排期、UGC 投稿后端、无障碍双编码、分享/AR 合影、音效 BGM、美术资产。

**D. 用户侧动作：**
- ② 编译级验证：本机（有 JDK）按 `编译验证清单.md` 执行 `./gradlew`，闭环所有 Kotlin/Swift 改动。

## 六、建议
核心游戏逻辑已**功能完整且全部断言闭环**：20 章中 16 章逻辑闭环 + A 序列三项纯逻辑缺口已全部补齐（道德选择 / 排位 ban/pick / 分布计数断言），主验证 1502/1502、池验证 1388/1388、双端镜像 0/0/0 全绿。

**纯客户端范围内已无剩余可自主验证缺口**。下一阶段二选一，均需要外部决策：

1. **本机编译闭环（D）**：在具备 JDK/Gradle 的环境按 `编译验证清单.md` 执行 `./gradlew :shared:test`，把经 Python 等价验证的 Kotlin/Swift 改动升级为编译级确认。
2. **六类平台契约运行时接入（B）**：地图 SDK / 后端 API / ARKit / BGTask / 云账号 / GPS 服务端权威——需先确定平台与后端选型，再落地运行时代码。

请指示方向（或回"继续工作"交我按你选定的方向推进）。
