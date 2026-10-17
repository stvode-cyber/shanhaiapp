# 《山海妖志》移动端 · 核心循环可玩切片

基于工作区 GDD《山海妖志》的 **KMP 原生双端** 起步实现：共享 Kotlin 核心 + Android(Compose) 可玩 UI + iOS(SwiftUI) 界面。
本切片打通 GDD 定义的**核心循环六支柱**中的前五步：

> 探索 → 发现（灵脉/随机妖气）→ 收妖（点按光环）→ 图鉴 → 对战

---

## 工程结构

```
shanhaiapp/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/                      # Gradle 8.7 wrapper（已含 gradle-wrapper.jar）
├── shared/                      # ★ KMP 共享核心（平台无关，全部游戏逻辑在此）
│   └── src/commonMain/kotlin/com/shanhai/core/
│       ├── model/               # Element(七行) / MonsterCategory / Rarity / Skill / Monster / MonsterInstance(拥有妖实例·含 tribulations/ talents) / Equipment(装备·OwnedEquip 五路径字段) / TalentBranch(天赋分支)
│       ├── data/                # SkillLibrary（73 式技能）/ MonsterPool（100 只妖池，含 20 条进化链） / EquipmentLibrary(9 件装备)
│       ├── logic/               # CaptureEngine / BattleEngine / BattleSession / Codex / GameState / CultivationEngine(养成·含渡劫) / EquipmentEngine(装备五路径·祭炼/升星/突破/淬炼/继承) / TalentEngine(天赋命格·3层攻防辅) / FusionEngine(炼妖融合) / LocationEngine(LBS灵脉加权刷怪) / ARCaptureController(AR契约占位) / GachaEngine(祈妖保底) / DungeonEngine(副本妖境) / SocialEngine(三派·据点战) / ShopEngine(引妖香经济)
│       ├── quest/               # ★ 任务/成就系统：Quest / QuestLibrary / QuestLog
│       └── persistence/         # ★ 存档：SaveData(可序列化) / SaveCodec(JSON 编解码)
│   └── src/commonTest/          # CoreTest.kt（五行矩阵/伤害/收妖/任务/存档 精确断言）
├── composeApp/                  # Android 端（Compose，主运行路径）
│   └── src/main/java/com/shanhai/yaozhi/ui/
│       ├── explore/  catch/  codex/  battle/  quest/  cultivate/  forge/  gacha/  dungeon/   # 核心界面（探索含 LBS/AR/社交经济，祈妖/副本独立屏，含培养 / 炼妖）
└── iosApp/                      # iOS 端（SwiftUI，调用 shared 框架；附纯 Swift 核心可独立运行）
    ├── Podfile
    └── iosApp/*.swift
```

**为什么核心逻辑放 shared**：GDD 要求「服务器权威、客户端只上报操作」。所有数值/判定（五行矩阵、伤害公式、收妖概率、图鉴、任务进度）
都被收敛到 `shared`，安卓与 iOS 共用同一份逻辑，避免双端数值漂移。

---

## 任务 / 成就系统（新增）

任务进度记录是 GDD「今日目标 + 山海异闻 + 灵脉试炼 + 成就 + 年节限时」的落地，事件驱动、跨玩法自动累计：

- **分类**：今日目标(每日重置) / 山海异闻(剧情) / 灵脉试炼(日常资源) / 成就 / 年节限时(活动)。
- **目标类型**：累计收妖、收服指定行、收服指定稀有度、累计获胜、用指定行获胜、进入秘境次数、图鉴完成度%。
- **事件驱动**：收妖→`MonsterCaught`、对战胜利→`BattleWon(获胜行)`、入秘境→`SecretRealmEntered`、图鉴变化→`CodexUpdated`。各玩法产生事件喂给 `QuestLog.record()`，自动推进进度、触发完成、支持领奖入账（香火/灵玉/妖丹）与每日重置。
- **UI**：探索页实时显示「今日目标」进度条；底部新增「任务」Tab，按分类分页、进度条、一键领奖；收妖/对战胜利后弹「任务完成」提示。iOS 镜像同名界面。

---

## 妖怪养成系统（新增）

GDD 十六·养成线落地：妖怪从「静态定义」升级为「可养成的拥有实例」。

- **拥有妖实例 `MonsterInstance`**：玩家收服后得到的是实例（`instanceId` 唯一），记录 `level / exp / breakthrough`。
  同一妖种可多次收服，各自独立养成；队伍 `team` 现存储**实例 id**（≤6），而非妖种 id。
- **`CultivationEngine`（养成引擎，全部数值规则集中于此，双端一致）**：
  - **经验升级**：`gainExp()` 自动连续升级；升级所需经验 `expForLevel(lv)=100+(lv-1)×25`；达等级上限后经验封顶不溢出。
  - **等级上限**：`BASE_LEVEL_CAP=30`，每次**突破** +5（最高 35）。
  - **突破**：消耗**妖丹**（`breakthroughCost=20+已突破次数×15`），提升等级上限并永久强化属性（`+8%`/次，最高 +40%）。条件：未达上限且等级 ≥ `20+已突破次数×5`。
  - **进化**：妖种存在 `evolveTo` 且等级 ≥ `25` 时可进化（如 蛇妖→蛟→龙、龟妖→灵龟→玄武、灵鸟→青鸾→凤凰），**保留等级/经验/突破**。
  - **有效属性** = 妖种 `statsAtLevel(level)` × 突破系数，对战直接受益。
- **与任务打通**：养成动作触发 `MonsterTrained(等级)` / `MonsterBrokenThrough` / `MonsterEvolved` 事件，推进 `TRAIN_LEVEL / BREAKTHROUGH_TOTAL / EVOLVE_TOTAL` 三类目标（已新增「勤修苦练 / 破而后立 / 蜕凡成圣」三个任务）。
- **持久化**：实例随 `SaveData.owned` 与 `nextInstSeq` 一并序列化；旧存档（无 `owned`）自动兼容（默认空）。
- **UI**：Android 新增「培养」Tab（`CultivationScreen`）——经验条、突破（显示妖丹消耗与条件）、进化按钮，变更即时落盘；对战界面按实例等级/突破加成构建战斗单位；iOS 镜像 `CultivationView` 与 `BattleView` 改实例级。

---

## 炼妖融合 / 装备祭炼（新增）

GDD 十六·养成线的深度延伸，让妖怪的「有效属性」不再只来自等级 / 突破，还可由**装备**与**融合**叠加。二者均建立在已完成的 `MonsterInstance` 养成模型之上，不依赖平台 / 后端，是养成线的低风险高收益扩展。

- **装备模型** `shared/.../model/Equipment.kt`：`EquipSlot`（WEAPON / ARMOR / ACCESSORY 三槽）、`EquipDef`（id / name / slot / element? / rarity / baseAtk / Def / Hp / Spd / bonusPerLevel=0.12 / maxLevel=10）、`OwnedEquip`（equipId / instanceId / level=1），均 `@Serializable`，与 `MonsterInstance` 同属纯数据层。
- **装备库** `EquipmentLibrary.kt`：9 件代表装备（剑·金·武器、杖·木、弓·风、三叉戟·水、甲·水、袍·火、戒·雷·仙、坠·水、符·雷），基础数值与元素分布覆盖三槽与七行。
- **装备引擎** `EquipmentEngine.kt`（数值规则集中，双端一致）：
  - 元素契合：装备元素与妖种元素相同时 ×1.2（`AFFINITY_MULT`）。
  - 等级加成：`bonusAtLevel = base × (1+(level-1)×bonusPerLevel) × (契合?1.2:1)`；`totalBonus(monster,equips)` 叠加三槽。
  - 祭炼：`forgeEquip` 提升装备等级（消耗**妖丹** `FORGE_COST_YAODAN=10`，`canForge` 限 `level<maxLevel`）。
- **炼妖融合** `FusionEngine.kt`：
  - 配方：无序 `Map<Set<String>,String>`（如 角妖 + 龟灵龟 → 玄武），匹配两妖种 id 集合。
  - 通用回退：无配方时，产物取「稀有度更高者」，并列取「基础属性更高者」。
  - 产物属性：`新等级 = min(max(a,b)+LEVEL_BONUS(3), 等级上限)`；`新突破 = min(a,b)`；`技能并集取前 4`；消耗**妖丹** `FUSE_COST_YAODAN=30`。
- **战斗有效属性**：`Combatant` 现携带 `effective: Stats`（已计入突破加成 + 三槽装备加成），`BattleEngine.damage` 直接取 `effective`，避免运行时重复叠加（`BattleSession.toCombatant` / `GameState.buildCombatant` 统一构建）。
- **与任务打通**：装备 / 祭炼 / 融合分别触发 `MonsterEquipped / MonsterForged / MonsterFused` 事件，推进 `EQUIP_TOTAL / FORGE_TOTAL / FUSE_TOTAL` 目标（新增「披甲上阵 / 祭炼之道 / 炼妖初成」三个任务）。
- **持久化**：`SaveData` 新增 `equipment:List<OwnedEquip>` / `nextEquipSeq` / `equipped:Map<实例id, Map<EquipSlot, 装备实例id>>`（默认值保证旧档兼容）；`GameState` 增 `grantEquip / ensureStarterKit`（首只妖自动发 eq_sword + eq_armor）/ `equipItem`（占用唯一性校验）/ `unequipItem` / `forgeEquip` / `effectiveStatsOf` / `buildCombatant` / `fuse` / `unequipAll`。
- **UI**：Android 新增「炼妖」Tab（`ForgeScreen`，选妖→看有效属性与各槽装配 / 祭炼 / 卸下；背包列表装备或祭炼；选妖 A/B 融合并显示结果，即时落盘）；iOS 镜像 `ForgeView`。

---

## 渡劫天劫（新增 · 养成线终章）

GDD §八「渡劫进化」落地：养成线的终点，让满级满突破的妖进一步**飞升**，是休闲向「必成、不失败」的终局成长。直接建立在已完成的 `MonsterInstance` 之上，不依赖平台 / 后端。

- **触发条件（双门槛）**：实例须同时满足 **满级（达当前等级上限 `30+5×突破 = 55`）** 与 **满突破（5 次）**，且**未满渡劫次数**。`CultivationEngine.canTribulate(inst)` 统一校验。
- **必成飞升**：`tribulate(inst)` 保证成功（休闲不失败），`tribulations + 1`。随之：
  - **稀有度升一阶**：`currentRarity = 基础稀有度 + 渡劫次数`（封顶神阶），如 蛇妖 凡→灵→仙→神。
  - **永久属性倍率**：`tribulateMult = 1 + 0.25 × 渡劫次数`；有效属性 = 妖种基础 × 突破系数（`1+0.08×突破`）× 渡劫系数。
- **神阶不可再渡（保值）**：`maxTribulations(rarity) = SHEN.ordinal - rarity.ordinal` → 凡 3 次 / 灵 2 次 / 仙 1 次 / 神 0 次。神阶妖到顶后不再飞升，保留稀缺价值。
- **消耗妖丹逐级递增**：`TRIBULATE_COST_YAODAN=200`，实际 `200 ×(渡劫次数+1)`（首次 200、二次 400、三次 600…），由 `GameState.tribulateMonster()` 扣减并校验资源。
- **与任务打通**：渡劫触发 `MonsterTribulated` 事件，推进 `TRIBULATE_TOTAL` 目标；新增试炼任务 `t_tribulate`「渡劫飞升」（目标 1 次，奖励灵玉+50 / 妖丹+50）。
- **持久化**：`MonsterInstance.tribulations` 随 `SaveData.owned` 序列化；旧存档缺失该字段时容错为 `0`（iOS `Core.swift` 自定义 `init(from:)` 用 `decodeIfPresent` 默认 0）。
- **UI**：Android「培养」页与 iOS `CultivationView` 新增「突破 / 渡劫消耗」区块；卡片展示「渡劫 N」与「属性 ×(1+25%×N)」，底部按钮三态——可渡劫 / 已至巅（神阶·不可再渡）/ 条件不足（需满级满突破）。
- **已知限制**：渡劫的**演出动画 / 天劫特效**未实现（当前为数值即时生效 + 按钮触发），属后续纯视觉工作。

---

## 装备五路径（新增 · 养成线装备深化）

GDD 十六·养成线的深度延伸，在已落地的「祭炼」之外，把装备养成扩展为**升星 / 突破 / 淬炼 / 继承**四条路径，让装备的有效属性可由多来源叠加，是养成线的低风险高收益扩展（不依赖平台 / 后端，可 shared 实现、双端镜像、Python 等价验证）。

- **装备实例字段扩展** `model/Equipment.kt`：`OwnedEquip` 新增 `stars:Int=1`（星级）/ `tier:Int=1`（突破阶）/ `substats:List<SubStat>=[]`（淬炼副属性），均带默认值以保证旧档兼容；新增 `EquipStat(ATK/DEF/HP/SPD)` 枚举与 `SubStat(stat,value)` 数据类。
- **五路径数值**（常量集中于 `EquipmentEngine`，双端一致）：

  | 路径 | 资源 | 规则 | 上限 / 成本 |
  |---|---|---|---|
  | 升星 star-up | 灵玉 | 每星 +10% 基础倍率（`STAR_BONUS=0.10`） | `MAX_STAR=5`；`starCost=50×当前星`（1→2:50 … 4→5:200） |
  | 突破 breakthrough | 灵玉 | 每阶 +10 等级上限 +0.03 每级成长 | `maxTier=稀有度 ordinal+1`（凡1/灵2/仙3/神4）；`tierCost=80×当前阶`（1→2:80 … 3→4:240） |
  | 淬炼 temper | 灵玉 | 副属性最多 4 条，每条封顶 15，每步 +3，确定性顺序 ATK→DEF→HP→SPD | `TEMPER_COST_BASE=30×(已淬炼次数+1)` 成本严格递增 |
  | 继承 inherit | 妖丹(20) | 把源养成（等级/星级/突破/副属性，取较优 maxOf）转移到目标，源被消耗 | 耗妖丹 `INHERIT_COST_YAODAN=20`；源若正被装备先卸下避免悬空引用 |
  | 祭炼 forge | 妖丹(10) | 提升装备等级（既有），上限随突破提升 | `FORGE_COST_YAODAN=10`；`canForge` 限 `level<maxLevelOf` |

- **主属性有效加成公式**（单件）：`base × (1+(L-1)×effectivePerLevel) × starMult × (元素一致?AFFINITY_MULT:1)`；其中 `effectivePerLevel = bonusPerLevel + (tier-1)×0.03`、`starMult = 1+(stars-1)×0.10`。**有效属性 = 主属性 + 副属性**（四来源叠加：等级/突破/星级/淬炼），`totalBonus` 按三槽叠加。
- **淬炼确定性**：先开新条（最多 4 条），满 4 条后强化现有未满条（≤15），顺序固定 ATK→DEF→HP→SPD，保证验证与双端一致。
- **继承合并**：目标保留自身养成，再与源取 `maxOf`（等级/星级/突破各取较高者，副属性按 stat 取较高值、最多 4 条）；源被移除，若源已装备则先卸下。
- **与任务 / 持久化 / UI**：装备动作触发 `MonsterForged` 事件（四路径均经 `GameState.starUpEquip/breakthroughEquip/temperEquip/inheritEquip`，校验资源与前置条件、失败返回 false）；`OwnedEquip.stars/tier/substats` 随 `SaveData.equipment` 序列化（旧档缺字段默认 `stars=1/tier=1/substats=[]`）；iOS `Core.swift` 自定义 `init(from:)` 用 `decodeIfPresent` 容错旧档；Android「炼妖」页与 iOS `ForgeView` 卡片显示「★stars/5 阶tier/maxT」+ 副属性行，并新增升星/突破/淬炼/继承（选源）按钮。
- **已知限制**：装备五路径的**养成演出**（升星光效 / 突破演化 / 淬炼火花 / 继承流转）已实现为双端 overlay 视觉代码（见「养成演出」章节）；数值与流程已落地并经 Python 验证，Kotlin 编译 BUILD SUCCESSFUL（需 robocopy 到 C:\temp 绕过中文路径）。

---

## 天赋 / 命格（新增 · 养成线扩展）

GDD 十六·养成扩展：每只妖点 **3 层天赋树（攻 / 防 / 辅）**，让 build 再深一层。直接在已落地的 `MonsterInstance` 上扩展，不依赖平台 / 后端，可 shared 实现、双端镜像、Python 等价验证。

- **天赋数据** `model/MonsterInstance`：`talents: Map<TalentBranch, Int>`（分支→已点层数，默认 `emptyMap()` 向后兼容）；新增 `TalentBranch`（ATTACK / DEFENSE / SUPPORT）。
- **天赋引擎** `logic/TalentEngine.kt`（数值集中，双端一致）：
  - **容量随突破**：`talentCap = BASE_TALENT(3) + 突破次数 × TALENT_PER_BREAK(1)`（凡突破 0 → 3 点，恰好每分支点 1 层；满突破 5 → 8 点），形成攻/防/辅 的 build 取舍。
  - **顺序解锁**：每分支最多 `MAX_LAYER=3` 层，层 N 需先点 N-1（由 `layersOf<MAX_LAYER` 保证）；分配受「分支未满 且 总点未达容量」约束（`canAssign`）。
  - **分支→主属性加成**（每层百分比）：攻 +ATK%（`ATK_PCT_PER_LAYER=0.06`）/ 防 +DEF%`0.06` + HP%`0.04` / 辅 +SPD%`0.05`。
- **有效属性联动**：`CultivationEngine.effectiveStats` 在「突破倍率 × 渡劫倍率」之后，再乘天赋四倍率（atk/def/hp/spd），自动惠及对战（`buildCombatant` 经 `effectiveStatsOf` 取有效属性）。
- **与任务 / 持久化 / UI**：`GameState.assignTalent(instanceId, branch)` 校验 `canAssign`、分配、记 `MonsterTalentAssigned` 事件；任务新增 `TALENT_TOTAL` 目标 + 示例任务 `t_talent`「天赋初开」（点天赋 3 次，奖励灵玉+30/妖丹+30）；`talents` 随 `SaveData.owned` 序列化（旧档缺字段默认空）；iOS `Core.swift` 自定义 `init(from:)` 用 `decodeIfPresent` 容错旧档（分支名与 Kotlin 枚举名一致：`ATTACK/DEFENSE/SUPPORT`）；Android「培养」页与 iOS `CultivateRow` 卡片新增天赋区块（攻/防/辅 三按钮，按 `canAssign` enabled，显示层数/容量）。
- **设计取舍**：点天赋**不消耗货币**（是 build 选择），资源约束来自有限天赋点（容量随突破提升）；与等级/突破/渡劫/装备/融合叠加，养成深度完整。

---

## 养成演出（天劫 / 装备五路径）「①」

GDD 十六·养成线的**纯视觉呈现层**：把已落地的「渡劫飞升」与「装备五路径（升星/突破/淬炼/继承）」的数值即时生效，包装为全屏演出，强化正反馈。属纯前端视觉，**不改动任何数值 / 逻辑 / 存档**（触发点仍为既有 `GameState` 方法），双端 Compose / SwiftUI 实现。

- **天劫演出（渡劫飞升）**：`CultivationScreen.kt`（`TribulationOverlay`）＋ `ContentView.swift`（`TribulationOverlay`）。由 `tribulateMonster` 成功触发，全屏 overlay：天劫闪电循环闪烁（白光 alpha 0↔0.5，120ms）+ 妖名金光揭示 + 「渡劫飞升！第 N 次」文案；2.6s 自动关闭，亦点击任意处关闭。
- **装备养成演出（升星 / 突破 / 淬炼）**：`ForgeScreen.kt`（`ForgeFxOverlay`）＋ `ForgeView.swift`（`ForgeFxOverlay`）。由 `starUpEquip / breakthroughEquip / temperEquip` 成功触发，全屏 overlay：星辉脉冲（缩放 0.8↔1.15，600ms）+ 「✦ 升星 / 突破 / 淬炼 成功 ✦」金色文案 + 装备名；1.3s 自动关闭，亦点击关闭。继承沿用选源 Dialog，不额外演出。
- **接入方式**：屏幕级 `@State` 持有 `tribEffect` / `equipFx`（类型 + 名称），在既有按钮回调里于 `GameState` 方法调用后写入；overlay 以 `Box`(`Modifier.fillMaxSize()`) / `ZStack` + `.overlay` 浮于内容之上，互不阻塞既有滚动与交互（逻辑层零侵入）。
- **验证状态**：⚠️ **纯视觉，本机无 JDK 无法编译验证**；代码按可编译、可直接接入既有屏幕的方式编写（全屏容器 + `LaunchedEffect`/`Task.sleep` 自动关闭 + `clickable`/`onTapGesture` 手动关闭），需在 Android Studio / Xcode 中构建确认。**无数值断言（无逻辑变更）**。

---

## LBS 灵脉 / AR 收妖 / 祈妖抽卡 / 副本妖境 / 社交与据点战 / 经济与变现（② + ③）

GDD 十六·探索（户外灵脉 LBS）、四·AR 收妖（可选）、十二·副本、十三·社交、十四·经济与变现的落地。
按「① 演出 → ② AR/LBS → ③ 社交/副本/祈妖/经济」严格顺序推进；可独立验证的核心（LBS 加权刷怪、祈妖保底、副本奖励、经济日上限）在 `shared` 实现 + Python 等价验证 + 双端 UI 入口，SDK/后端依赖部分（AR 摄像头、社交服务器）只写架构占位并标注限制。

### LBS 灵脉引擎（GDD 十六·探索）
- `logic/LocationEngine.kt`：坐标 → 确定性哈希 → 地域主题（`RegionTheme`：山林→木 / 水域→水 / 古墓→土 / 东海→水）→ 五行偏向 ×1.8；昼夜（子时 23/0 点）怪类 ×1.6；中元 `ZHONGYUAN` 怪类 ×2.0；春节 `CHUNJIE` 年兽（isFestival）×3.0；稀有度基础权重（凡45/灵35/仙15/神5）。
- `nearbySpawns(ctx, count)` 加权抽样；`nearbyNode(ctx)` 固定 POI 判定（切片；真实实现换地图 SDK 距离查询）；`SpawnContext(lat,lng,hourOfDay,solarTerm)` 由平台从 GPS / 系统时间 / 节气日历填入。
- **真实 GPS / 地图 SDK 由平台接入**：本引擎只做可验证的派生计算，平台把 `SpawnContext` 喂进来即可；双端 UI 已提供「地域 / 五行偏向 / 昼夜 / 节气」演示切换与附近妖气加权抽样展示。
- **GPS 防作弊（客户端侧校验，对齐 GDD 十六·三档）**：`SpawnContext` 增补 `ts / prevLat / prevLng / prevTs`（前后两次定位 epoch 秒与坐标）；`spawnIntegrity(ctx)` 用 Haversine 算相邻位移与速度，返回三档 `IntegrityTier`：
  - **NORMAL（正常）**：可正常收妖，稀有度权重不变；
  - **DEGRADED（降品质）**：速度 > 30 km/h（软档）但不构成瞬移 → 仍可收妖，但**神不出、仙权重 ×0.2**（不阻断、不扣分）；
  - **REJECTED（拒收）**：瞬移（位移 ≥ 5000m 且间隔 ≤ 180s）→ 收妖无效（`safeSpawns` 返回空），并触发信誉扣分。
  - `IntegrityResult` 增 `tier` / `degraded` 字段；`safeSpawns(ctx,count)` 按档返回（REJECTED 空 / DEGRADED 降品质抽样 / NORMAL 正常）；`GameState.locationIntegrity(ctx)` / `canCapture(ctx)` 提供统一入口，双端 UI 已显示「灵脉信号：正常 / 降品质 / 异常」三态。`SocialService` 仍负责**服务端权威**校验（GPS 防作弊后端部分）。

### 信誉分（GDD 十六·公平与稳健）— 与 GPS 防作弊闭环
- `logic/ReputationEngine.kt`：信誉分 100 满分，三层——**优秀（≥80）/ 观察（60–79）/ 受限（<60）**。
- `GameState.reportIntegrity(result)` 仅对 **REJECTED（瞬移）** 映射扣分（−18，`CheatKind.TELEPORT`）形成「检测 → 扣分 → 分层 → 限制」闭环；**DEGRADED（降品质，软档）按 GDD 不阻断、不扣分**。`reputation` 入存档、每日缓慢恢复 +1。
- 软限制：观察层**罕见（仙/神）妖刷怪 −30%**（`lbsRareSpawnMul`）；受限层**禁止户外 LBS 探索，仅可入秘境**（`canExploreLbs`）。双端 UI 探索页展示「信誉：优秀/观察/受限」并附「模拟 GPS 异常」按钮。
- **仍为客户端侧软限制**；权威裁决由 `SocialService` 后端做最终判定（本切片未实现）。

### AR 收妖（GDD 四·AR 可选）— 架构占位
- `logic/ARCaptureController.kt`：定义 shared↔平台 AR 层契约——`status()` 默认 `UNSUPPORTED`（平台应在获得相机权限且设备支持时返回 `READY`）；`resolveAtAnchor` 复用 `CaptureEngine` 光环判定。UI 接入后自动激活，操作与国风卷轴背景完全一致（不依赖陀螺仪/甩动）。
- 双端 UI 均已放入「AR 收妖」卡片：未启用时显示占位说明，启用后按钮激活进入收妖。

### 祈妖抽卡（GDD 十四·经济与变现 · 抽签祈妖）
- `logic/GachaEngine.kt`：国风抽卡 摇签筒→掉签→解签→亮妖；签文映射稀有度（上上=神 / 上=仙 / 中=灵，无下下签坑）。
- 出率（常规池）：**灵 40% / 仙 45% / 神 15%**；保底——十连保底 ≥ 灵；**软保底第 50 抽起**神率线性提升（`SOFT_PITY_STEP=0.02`）；**硬保底第 90 抽必神**（`HARD_PITY=90`）。
- 单抽 60 灵玉 / 十连 540（9 折）；重复妖转妖丹（+10）；**付费只卖机会不卖战力**（红线）。
- `GameState.pullGacha(ten)`：扣灵玉、维护保底状态（`gachaSinceShen`）、重复转妖丹/新妖入队、记 `MonsterGachaPulled` 事件（推进 `GACHA_TOTAL` 任务）。

### 副本妖境（GDD 十二·副本）
- `logic/DungeonEngine.kt`（`DungeonLibrary` 4 本）：山海异闻 / 灵脉试炼 / 渡劫秘境 / 年节限时。
- 进入消耗香火、每日限次（`DungeonRunState`）；通关奖励：妖丹 / 渡劫材料 / 符纸 / 外观 / 妖王概率收服（补全图鉴）。
- `GameState.enterDungeon(defId, today)`：校验 `canEnter`（香火+限次）→ 扣香火 → 结算奖励 → 记 `DungeonEntered`（推进 `DUNGEON_TOTAL` 任务）；妖王收服入队。

### 社交（GDD 十三·社交）
- `logic/SocialEngine.kt`：三派 茅山 / 龙虎山 / 阁皂山；据点战（本地占位，每日占领灵脉节点一次，占领期间攻击 +10% `STRONGHOLD_ATTACK_BONUS=1.10`）。
- `SocialService` 接口占位（好友赠妖丹每日1 / 团战 raid / 宗门排行榜）——**需服务器权威与 GPS 防作弊服务端校验**，客户端侧瞬移/超速检测已在 `LocationEngine.spawnIntegrity` 实现。

### 经济与变现（GDD 十四·经济与变现）
- `logic/ShopEngine.kt`：货币 灵玉（付费）/ 香火（体力，每日回满）/ 妖丹（重复妖转化）；红线 付费只卖「机会/外观」不卖成品战力。
- 引妖香商店：1 份 30 灵玉 → +60 香火，**日购上限 3 防通胀**（`INCENSE_DAILY_BUY_CAP`）。
- `GameState.buyIncense(today)`：校验 `canBuyIncense`（灵玉足够 + 当日未达上限）→ 购 → 写回当日计数。

### 持久化 / 双端 UI
- 存档 `SaveData` 新增 7 字段（`gachaSinceShen / dungeonEntered / faction / strongholdNode / strongholdDay / shopIncenseBought / shopDay`，全带默认值向后兼容）；iOS `Core.swift` 自定义 `init(from:)`（`decodeIfPresent`）与 Android `ignoreUnknownKeys` 对齐，旧存档不崩；跨天重置清理副本/商店/据点当日状态。
- Android：探索页新增「户外灵脉·LBS」（地域/五行偏向/昼夜/节气切换 + 附近妖气抽样 + AR 占位）、「祈妖·副本·据点」（派系选择 / 据点占领 / 购引妖香）；新增 `GachaScreen` / `DungeonScreen` 并注册 `gacha` / `dungeon` 路由（入口按钮在探索页）。
- iOS：`ExploreView` 同构新增 LBS/AR/社交经济块 + `NavigationLink` 到新增 `GachaView` / `DungeonView`。

### 在家秘境（GDD 三·探索双模式·在家秘境）
- `logic/SecretRealmEngine.kt`：复用 `RegionTheme`/`regionElementBias`（山林→木 / 水域→水 / 古墓→土 / 东海→水）；秘境稀有度权重（凡 60 / 灵 30 / 仙 8 / 神 2，凡/灵主导）与主题五行偏向 ×3.0 加权抽样 `generateSpawns(theme,count)`；`pickTheme(rng)` 随机选主题。
- `GameState.enterSecretRealm(rng)`：每日 `secretRealmLeft=3` 次优先（免费），耗尽则耗 `SecretRealmEngine.INCENSE_COST=20` 香火；随机主题 → 生成刷妖 → 记 `SecretRealmEntered` 事件；无可用次数且香火不足返回 null。秘境所收不计入地域图鉴（`Codex.fromSecretRealm` 已分离两套集合）。
- 双端探索页新增「在家秘境」卡：展示主题/五行偏向/本次刷妖一览与剩余次数/香火消耗提示；「入秘境」按钮调用 `enterSecretRealm`（修复了此前 UI 直接扣香火且同时扣每日次数、且无主题刷妖的残缺实现）。

### 新手引导 + 7 日留存（GDD 十八）
- `logic/TutorialEngine.kt`：`TutorialStep`（5 步，前 3 步 forceable 轻量强制，全程可跳过）、`RealmFeature`（秘境/副本/祈妖/社交）、`unlockedFeatures(day)` 逐日解锁（第 2/3/4/5 日依次开放）、`signInReward(day)` 签到递增奖励（10→70 封顶）。
- `GameState`：`tutorialActive`/`currentTutorialStep`/`completeTutorialStep`/`skipTutorial`/`unlockedFeatures`（引导进行中全部开放，完成后按签到天解锁）/`canSignIn`/`claimSignIn`（每日首次领香火并标记步 ⑤）；步骤自然挂接——收服(①)/入秘境(②)/战斗结束(③)/择门(④)/签到(⑤)。
- 双端探索页新增「新手引导」卡（步骤清单 + 跳过），「在家秘境」卡在引导完成且未到解锁日时显示「第 2 日签到后解锁」。

### 家园洞府（GDD 十九·长线留存）
- `logic/HomesteadEngine.kt`：`incenseRate(level)`/`pillRate(level)`（香火/妖丹时产，阵法每级 +10% 香火、+0.1 妖丹）、`fengshuiAttackBonus(level)`（阵法每级 +2% 全局攻击增益，报告值）、`upgradeCost(level)`（升阵法耗妖丹 100×(level+1)）、`compute(level,nowMs,lastMs)`（按真实时长累计产出、封顶 12h）。
- `GameState`：`homesteadLevel`/`lastHomesteadCollect` 字段 + `collectHomestead(nowMs)`（首次进入启动计时不补发历史、之后按时长入账香火/妖丹）、`upgradeHomestead()`（耗妖丹升阵法）、`homesteadPending`/`homesteadAttackBonus` 预览。`SaveData` 加 `homesteadLevel`/`lastHomesteadCollect`(ms)。
- 双端探索页新增「家园洞府」卡：阵法等级、时产（香火/妖丹 + 攻击增益）、收取产出、升阵法（妖丹花费）。

### 天气地形战斗（GDD 十九·长线留存）
- `logic/WeatherEngine.kt`：`Weather`(晴/雨/雪/风) + `weatherAttackMul(elem, weather)`（雨→水×1.3/火×0.8、雪→火×0.7/水×1.2、风→风×1.3/土×0.8、晴无偏置）、`terrainAttackMul(elem, bias)`（复用 `RegionTheme` 五行偏向 ×1.2）、`envMultiplier`（天气×地形）。
- `BattleEngine.damage` 增 `envMul` 乘子（与阵容羁绊 `teamAttackMul` 同缝）；`BattleSession` 持 `weather`/`terrainBias`，每步按攻击者元素实时算环境乘子并写入 `BattleEvent`。**缝合 #85**：`GameState.battleTeamMul()` = 阵容羁绊 × (1 + 阵法风水攻击增益)，阵法增益正式接入战斗。
- `GameState` 增运行时 `currentWeather`/`currentTerrain`（不进存档，由平台提供真实天气/地形）；双端战斗页新增「天气地形」卡（晴/雨/雪/风 + 山林/水域/古墓/东海选择 + 首发元素预览 + 应用并重开）。

### 赛季通行证（GDD 十九·长线留存·90 天双轨）
- `logic/SeasonEngine.kt`：`SEASON_LENGTH_DAYS=90`/`XP_PER_TIER=100`/`TIERS=60` + `dayIndex`(未开始0/进行中1..90/已结束>90)/`inSeason`/`daysRemaining`/`tierFromXp`(封顶60)/`freeReward`/`paidReward`（每 10 档为里程碑，奖励更厚）。`SeasonReward`/`SeasonClaimResult` 数据类。
- `GameState`：`seasonStartMs`/`seasonXp`/`hasSeasonPass`/`seasonClaimedFree`/`seasonClaimedPaid`/`seasonIndex` 字段 + `tickSeason(nowMs)`(平台启动/前台调用，超 90 天滚动到下一赛季：序号+1、经验/已领清零、通行证失效) + `addSeasonXp`(收妖/对战/副本/签到接入) + `buySeasonPass(300灵玉)` + `claimSeasonTier`(免费轨常领、付费轨需通行证、已领不重复发放) + `claimAllSeason` + `seasonDayNow`/`seasonDaysRemaining`/`currentSeasonTier`；`SaveData` 加 6 字段（默认向后兼容）。
- 双端探索页新增「赛季通行证」卡：第 N 赛季/剩余天数、档位进度条、购买通行证（300 灵玉）/领取已解锁奖励（免费+付费双轨）。

### 疲劳值 / 单日收益封顶（GDD 十六·公平与稳健·防脚本）
- `logic/FatigueEngine.kt`：`MAX_FATIGUE=100`/`SOFT_CAP=60` + `incomeMul(fatigue)`(充沛×1.0 / 疲劳×0.5 / 透支×0.0 三层阈值) + `addFatigue`(夹取 0..100) + `resetDaily`(跨天归零) + `label`(状态文案)。`FATIGUE_CATCH=2`/`FATIGUE_BATTLE=4`/`FATIGUE_DUNGEON=8`/`FATIGUE_SIGNIN=3`。
- `GameState`：`fatigue` 字段 + `incomeMul()`/`addFatigue`/`resetFatigueDaily`/`fatigueLabel`；收妖/对战/副本/签到四个核心循环接入疲劳累积，且**副本妖丹**与**签到香火**按当前收益倍率发放（`maybeResetDaily` 每日重置疲劳）；`SaveData` 加 1 字段（默认向后兼容）。
- 双端探索页新增「疲劳值 · 单日收益封顶」卡：疲劳进度条 + 当前倍率 + 状态文案（透支标红）。
- 设计取舍：收妖/对战本身不产货币，仅累积疲劳；疲劳主要封顶「副本妖丹」与「签到香火」两类可重复收益，契合「单日收益封顶（疲劳值）」防脚本意图。GDD 未细化，阈值与常量均为合理默认，标注待调优。

### 排位赛段位天梯（GDD 二十·竞技与荣誉）
- `logic/RankEngine.kt`：5 段位 `TIERS=炼气/筑基/金丹/元婴/化神`、`POINTS_TO_PROMOTE=100`、`WIN_POINTS=20`/`LOSS_POINTS=15`、`tierIndex`/`tierName`/`applyResult`(胜满100升段、负不足0降段、化神封顶/炼气保底、支持连跳连降) + `rankReward`(升段按新段位发少量货币荣誉奖励)。纯函数可 Python 镜像。
- `GameState`：`rankTierIndex`/`rankPoints` 字段 + `rankedBattle(won)`(平台侧 PvP 结算后回传胜负，本端只做段位积分数学；升段一次发荣誉奖励) + `rankTierName`/`rankProgress`；`SaveData` 加 2 字段（默认向后兼容）。
- 双端探索页新增「段位天梯」卡：当前段位名 + 积分进度条 + 模拟胜/负按钮（演示用，真实对手由平台匹配层 ban/pick + meta 平衡负责）+ 升降段结果提示。
- 设计取舍：ban/pick + meta 平衡与真实对手匹配属平台/后端职责（需联网匹配），本引擎仅落地可独立验证的段位积分数学；升段奖励为展示型荣誉 + 少量货币。

### 成就 / 勋章 / 称号（GDD 二十·竞技与荣誉·展示型荣誉）
- `logic/HonorEngine.kt`：纯函数引擎，输入玩家聚合画像 `HonorProfile(achievementCount/achievementTotal/rankTierIndex/codexRatio/tribulationTotal)`，输出已解锁**勋章**（青铜→白银→黄金→钻石 四阶，钻石需达成全部成就）与**称号**（12 个，覆盖成就数/图鉴完成度/渡劫次数/段位等维度，各自解锁条件）。不重复定义成就——成就直接复用任务系统 `QuestCategory.ACHIEVEMENT` 分类的统计（共 22 项）。
- `GameState`：`tribulationTotal`/`equippedTitleId` 字段 + `achievementCount()`/`honorProfile()`/`medals()`/`titles()`/`unlockedTitleIds()`/`equipTitle(id)`(仅已解锁可装备)/`displayTitle()`；`tribulateMonster` 累加 `tribulationTotal`；`SaveData` 加 2 字段（默认向后兼容）。
- 双端探索页新增「成就 · 勋章 · 称号」卡：成就进度 X/Y、已解锁勋章名、可装备称号列表 + 装备按钮 + 当前展示称号。
- 设计取舍：成就/勋章/称号为**展示型荣誉**，不产生货币；称号可由玩家在已解锁集合中任选其一装备展示。GDD 未细化条件，勋章阶梯与称号阈值均为合理默认，标注待调优。

---

## 持久化存档（新增）

解决「退出即丢」问题：游戏进度现在会落到本地存储，重启后自动恢复。

- **存档格式**：`shared/persistence/SaveData` 用 `kotlinx-serialization` 序列化为 JSON。
  **只存进度不存定义**——妖怪全量数据取自 `MonsterPool`、任务定义取自 `QuestLibrary`，存档仅存已收/出战妖 ID、货币、任务进度与状态，体积小且与代码版本解耦（新增妖/任务不会让旧存档失效）。`ignoreUnknownKeys` 让旧存档在字段扩展后仍可兼容读取。
- **架构**：`GameState.toSaveData()` 导出快照，`applySaveData()` 恢复；`Codex` 区分「地域/秘境」两套图鉴集合独立存档；`QuestLog.snapshot()/applySnapshot()` 按任务 id 匹配恢复进度（长度不对齐只恢复前 N 个，避免越界）。
- **跨天重置**：`maybeResetDaily(today)` 检测 `lastDailyReset` 与当日不同则重置每日任务并把秘境次数回满；首启也会规整一次。
- **平台落地**：
  - **Android**：`data/GameRepository`（SharedPreferences）在 `Game.boot(context)` 加载并跨天重置，`Game.persist()` 在状态变更后落盘，`MainActivity.onPause` 兜底；各界面（收妖/对战/探索/任务）变更后即时 `persist()`。
  - **iOS**：`Core.swift` 的 `SaveData/QuestSave` 为 `Codable`（字段名与 Android 端 JSON 对齐），`GameState.save()/loadSave()` 经 `UserDefaults` 落地，`ContentView.onAppear` 加载+重置+落盘，各玩法变更点调用 `save()`。
- **注意**：切片为单机本地存档，未做服务器权威与云同步；跨天重置判定已抽成纯函数 `DailyResetEngine.isCrossDay`（可 Python 验证），`maybeResetDaily` 当前仍由启动/进入前台时机触发；真正的「后台定时 0 点静默触发」由平台层调度接入（shared 判定逻辑已就绪）。

---

## 运行方式

### Android（推荐先跑这个，本切片主路径）
1. 安装 [Android Studio](https://developer.android.com/studio)（含 JDK 17 + Android SDK）。
2. 复制 `local.properties.sample` 为 `local.properties`，填入你的 `sdk.dir`。
3. 打开 `shanhaiapp/` 目录（Android Studio 会自动 `./gradlew` 同步，下载 Gradle 8.7 与依赖）。
4. 连接安卓手机（开启 USB 调试）或用模拟器，点击 ▶ Run `composeApp`，
   或命令行：`./gradlew :composeApp:installDebug`。
5. 真机预览：探索→点灵脉节点→收妖光环→图鉴→对战。

### iOS（SwiftUI）
- **方式 A（生产推荐，KMP）**：在 `shared` 模块执行 `./gradlew :shared:podspec`，
  于 `iosApp/` 执行 `pod install`，用 Xcode 打开生成的 `.xcworkspace`，链接 `shared` 框架。
  将各 View 里对 `Core.swift`（纯 Swift 核心）的调用替换为 `import shared` 后的 Kotlin 类即可（API 一一对应）。
- **方式 B（立即可见效果）**：直接用 Xcode 打开 `iosApp/`，`Core.swift` 已内置与 `shared`
  完全一致的纯 Swift 核心，无需先配置 KMP 即可在模拟器/真机预览核心循环。

---

## 可运行原型（浏览器试玩）

> 本切片附一套**零依赖 JS 原型**，忠实复刻 Kotlin 数值真相。原型独立可玩、可直接在浏览器打开试玩；Kotlin 主代码也已通过编译验证（需 robocopy 到 C:\temp 绕过中文路径+emoji JNI error）。

**位置**：`shanhaiapp/prototype/`
- `data.js` —— 由 `gen_data.py` **直接解析** `MonsterPool.kt` / `SkillLibrary.kt` 生成（100 妖 / 73 技能，零手工转录漂移）。
- `game.js` —— 双模（浏览器 + Node）核心逻辑，复刻 Capture / Gacha / Cultivation / Talent / Battle / Formation / SectTech / Codex / Social / Location 全引擎 + Reputation / Combo / Weather / Fatigue 四元系统 + Rank / RankBanPick 排位赛 + Dungeon 副本妖境（四类副本 + enterDungeon 接线）+ SecretRealm 在家秘境（主题五行偏向·凡灵主导加权抽样 + enterSecretRealm 接线）+ 运行时 Player 状态机。
- `index.html` —— 中国风可试玩原型（水墨/卷轴基调），串联：巡妖图（4 节点 + 模拟 GPS 防作弊）→ 聚灵环收妖 → 妖典图鉴 → 祈妖抽卡 → 试炼对战（含天气/地形五行偏置 + 连携组合技 + 段位天梯结算 + 排位 Ban/Pick 演练）→ 洞天养成 → 敕令任务 → 宗门据点科技 → 副本妖境（四类副本进入 + 通关记录）→ 在家秘境（免费次数/耗香火·主题刷妖）→ 家园洞府（放置产出 · 模拟跨天每日重置）→ 商店（引妖香 · 耗灵玉回补香火 · 日购上限3）。
- `test_game.js` + `gen_data.py` —— Node 等价断言（**706/706 全过**，含 IEEE-754 截断陷阱 `2600*1.4`→3639 一致复现；覆盖全部引擎 + 信誉/连携/天气地形/疲劳四元系统 + 段位天梯·Ban/Pick + 副本妖境 + 在家秘境 + 家园洞府 + 每日重置 + 赛季通行证 + 商店引妖香 + FusionEngine 6 配方）。

**打开方式**：直接用浏览器打开 `index.html` 即可（无需服务器、无需构建）。在巡妖图切换「正常 / 降速 / 瞬移」三种定位模式，可实时观察 GPS 防作弊（DEGRADED 降品质 / REJECTED 拒收扣信誉）如何作用到刷怪与收妖。

**验证**：Node **706/706 数值断言全过** + 最小 DOM 垫片 **smoke_dom.js 75/75 冒烟流程零异常**（21 tab 全引擎覆盖）。Kotlin 端 `:shared:jvmTest` 30/30 BUILD SUCCESSFUL + `:composeApp:assembleDebug/Release` 双 BUILD SUCCESSFUL（Debug 9.3 MB / Release 3.4 MB）。

---

## 已实现玩法（对照 GDD）

- **探索**：灵脉节点 + 出门随机遇妖 + 入秘境（随机主题小地图，主题决定五行偏向、多见凡/灵级妖；每日 3 次免费，耗尽耗香火）+ **新手引导（5 步 + 7 日留存逐日解锁秘境/副本/祈妖/社交）** + **家园洞府（放置产香火/妖丹，阵法风水升级增益）** + **赛季通行证（90 天双轨，收妖/对战/副本/签到积累经验解锁 60 档，免费+付费轨道奖励，购买通行证解锁付费轨）** + **天气地形战斗（天气/地形临时偏置五行攻击，接入 BattleEngine 伤害与阵法风水增益）** + **疲劳值 / 单日收益封顶（收妖/对战/副本/签到累积疲劳，副本妖丹与签到香火按倍率发放，每日跨天重置，防脚本长时间刷资源）** + **排位赛段位天梯（GDD 二十·竞技与荣誉：炼气→筑基→金丹→元婴→化神 五段位，胜+20/负-15、满100升段/不足0降段、升段发荣誉奖励；ban/pick+meta平衡由平台匹配层负责）** + **成就/勋章/称号（GDD 二十·展示型荣誉：成就复用任务·成就分类统计，勋章按成就数阶梯青铜→白银→黄金，6 称号各自解锁条件，玩家可装备其一展示）**。
- **收妖**（GDD 四·纯点按三步）：点妖身→光环收缩→在最小点再点入葫芦；光环速度随稀有度加快；
  **辅助模式**（默认开）放慢+放宽判定；卡准则妖挣扎、可再试。
- **图鉴**：四类别网格、剪影/收录态、地域完成度百分比、秘境不计地域图鉴。
- **对战**（GDD 六·2.5D 六宠）：五行×1.6/被克×0.6、类别环×1.3、等级系数、暴击×1.5、随机 0.9~1.0；
  自动选**高亮克制技**、阵亡自动换妖、自动战斗开关、伤害/倍率浮窗、战斗日志；按出战实例等级与突破加成结算。
- **阵容羁绊（GDD 七·技能系统·队伍组合技）**：`FormationEngine` 判定出战阵容激活的组合技——
  阴阳调和（水≥2 且 火≥2）、百妖夜行（同类别≥3）、三才阵（类别≥3，近似 GDD「三派混编」）、相生阵（五行齐全）、青丘幻月（狐族≥3）；
  每条给全队攻击增伤 +12%、叠加上限 +60%（×1.6），并计入战斗伤害（`BattleEngine.damage` 增 `teamAttackMul`、`BattleSession` 增 `playerTeamMul`，仅作用于玩家出招）；
  `Monster` 新增 `clan` 字段（狐妖/妖狐/九尾狐 标 `clan="fox"`）；双端对战页新增「阵容羁绊」卡展示激活羁绊与全队增伤倍率。
- **连携组合技（GDD 七·技能系统·连携）**：`ComboEngine` 判定同妖出战 4 技能中是否满足特定技能对，触发额外效果——缚妖甘露（捆妖索+甘霖 回血+25%）、狂战连斩（裂空斩+万剑 伤害×1.3）、玄冰寒壁（玄冰+寒壁 伤害×1.2）、烈焰焚天（焚天+三昧真火 伤害×1.35）；占槽（取前 4 技能）+ 有 CD（每场限次，由 `BattleSession.comboCd` 控制，治愈类在玩家回合回血、伤害类放大本回合伤害，并通过 `BattleEvent.comboName` 记录）；技能对均取自妖池中真实共现组合；双端对战页新增「连携组合技」卡展示首发妖可触发的连携。
- **养成**（GDD 十六·养成线）：经验升级、突破（耗妖丹+提升上限/属性）、进化（达级进化，保留养成数据）、
  **天赋 / 命格**（每只妖点 3 层天赋树 攻/防/辅，容量随突破提升，分支→主属性百分比加成）、
  **渡劫飞升**（满级满突破+耗妖丹，稀有度升阶 + 永久属性 ×(1+0.25×渡劫次数)，神阶不可再渡）；「培养」页可修炼/突破/进化/点天赋/渡劫，并与任务系统联动。
- **炼妖 / 装备 / 融合**（GDD 十六·养成延伸）：9 件装备三槽装配（元素契合 ×1.2）、祭炼（耗妖丹升装备级）、**升星**（耗灵玉 +10%/星，上限 5 星）、**突破**（耗灵玉提等级上限与每级成长，随稀有度 1..4 阶）、**淬炼**（耗灵玉开/强副属性，封顶 4 条×15）、**继承**（耗妖丹合并较优养成）、炼妖融合（配方 + 通用回退，耗妖丹产出更强妖）；
  战斗有效属性计入装备（主+副）加成；「炼妖」页可装配 / 祭炼 / 升星 / 突破 / 淬炼 / 继承 / 融合，并与任务系统联动；  「培养」「炼妖」页已接入养成演出 overlay（天劫闪电 / 装备星辉脉冲，纯视觉）。
- **LBS 灵脉 / AR 收妖（GDD 十六·探索 / 四·AR 可选）**：`LocationEngine` 坐标确定性解析地域主题→五行偏向×1.8、昼夜子时怪类×1.6、中元怪类×2.0、春节年兽×3.0 加权刷怪与 `nearbySpawns`/`nearbyNode`；真实 GPS/地图 SDK 由平台接入（UI 已提供地域/五行/昼夜/节气演示切换与附近妖气抽样）；`ARCaptureController` 定义 shared↔平台 AR 契约（`status()` 默认 UNSUPPORTED，复用 CaptureEngine 判定），双端 UI 放「AR 收妖」占位卡片，接入后自动激活。
- **祈妖抽卡（GDD 十四·经济与变现）**：`GachaEngine` 签文映射稀有度（上上=神/上=仙/中=灵）、常规池 灵40%/仙45%/神15%、十连保底≥灵、软保底第50抽起升神率、硬保底第90抽必神；单抽60/十连540灵玉、重复转妖丹+10、付费只卖机会不卖战力；双端「祈妖抽卡」屏（单抽/十连、保底进度条、签文/稀有度/新妖入队）。
- **副本妖境（GDD 十二·副本）**：`DungeonLibrary` 4 本（山海异闻/灵脉试炼/渡劫秘境/年节限时），进入耗香火+每日限次，通关掉落妖丹/渡劫材料/符纸/外观/妖王概率收服；双端「副本妖境」屏（副本列表+奖励弹窗+妖王收服）。
- **社交与据点战（GDD 十三·社交）**：三派（茅山/龙虎山/阁皂山）+ 据点战（每日占领一次，占领期间攻击+10%）；`SocialService` 接口占位（好友/团战/宗门，需服务器）；双端探索页「祈妖·副本·据点」块（派系选择/据点占领/购引妖香）。
- **经济与变现（GDD 十四·经济与变现）**：`ShopEngine` 引妖香商店（30灵玉→+60香火，日购上限3防通胀），付费只卖机会不卖战力；双端购引妖香按钮（显示当日剩余可购）。

## 验证结论
- 本会话内用等价逻辑脚本验证了共享核心数值：五行矩阵、伤害公式（蛇妖 Lv1 惊涛拍岸 vs 灯笼精 = **170**）、
  类别克制（妖克精 ×1.3）、收妖概率（辅助满精度 0.98 / 神无辅助最低 0.06 保底）全部通过。
- **存档系统**：等价脚本 **19 项断言全部通过**——收妖/秘境/对战 → 快照 → 清空 → 恢复字段一致；地域与秘境图鉴分离恢复；
  任务进度逐任务恢复且新任务保持默认；领奖状态（CLAIMED）持久化后不可重复领取；跨天重置归零每日任务并回满秘境次数；
  缺/多字段向后兼容。
- **养成系统**：`verify/verify_cultivation.py` 等价镜像 `CultivationEngine / MonsterInstance / GameState(养成) / SaveData`，
  **44 项断言全部通过**——经验升级与满级封顶、突破条件/消耗/上限/属性系数、进化条件/链完整/数据保留、任务推进（培养/突破/进化）、
  以及**存档往返**（实例等级/突破/进化结果/序号/妖丹 全部恢复，旧存档兼容）。
- **装备 / 融合系统**：`verify/verify_equipment_fusion.py` 等价镜像 `EquipmentLibrary / EquipmentEngine / FusionEngine / GameState(装备·融合·有效属性) / SaveData`，**39 项断言全部通过**——装备等级加成与元素契合、有效属性含突破与装备、祭炼消耗妖丹、融合产物（配方 / 回退 / 上限 / 突破 / 技能并集）与妖丹消耗、存档往返（装备 / 装配 / 妖丹，旧档兼容）。
- **渡劫天劫系统**：`verify/verify_tribulation.py` 等价镜像 `CultivationEngine(渡劫) / MonsterInstance.tribulations / GameState.tribulateMonster / QuestLog(TRIBULATE_TOTAL) / SaveData`，**36 项断言全部通过**——`maxTribulations` 阶序（凡3/灵2/仙1/神0）、`canTribulate` 前置（未突破 / 未满级 / 满级满突破 / 神阶）、`tribulateCost` 逐级递增（200/400/600）、`tribulateMult×effectiveStats` 升阶升属性、稀有度升阶、GameState 渡劫+任务+资源/条件失败、存档往返容错。
- **装备五路径**：`verify/verify_equipment_paths.py` 等价镜像 `EquipmentLibrary / EquipmentEngine(五路径) / GameState(升星·突破·淬炼·继承·祭炼) / SaveData`，**53 项断言全部通过**——`maxTier` 阶序（凡1/灵2/仙3/神4）、`maxLevelOf/effectivePerLevel` 随 tier、`starMult`、`bonus` 含 level/tier/stars/affinity、`subStatsBonus`、`totalBonus` 含副属性、`starCost` 递增/上限、`tierCost` 递增/灵阶 tier2 不可突破、`temper` 确定性（20 次填满 4×15）/成本递增/满后不可、`inherit` 合并较优、GameState 四方法+资源/条件失败、旧档兼容（仅 level 默认 `stars=1/tier=1/substats=[]`）。
- **天赋 / 命格**：`verify/verify_talent.py` **直接解析 `TalentEngine.kt` 与 `MonsterInstance.kt` 源码**抽取常量/字段，等价镜像 `TalentEngine / CultivationEngine.effectiveStats(含天赋倍率) / GameState.assignTalent / QuestLog(TALENT_TOTAL) / SaveData`，**36 项断言全部通过**——`talentCap` 随突破（0→3 / 5→8）、顺序解锁、容量上限、`atk/def/hp/spd` 四倍率叠加、`effectiveStats` 含天赋（组合倍率）、`assignTalent` 成功/容量满失败/不存在返回 false、任务事件累计、存档往返容错（旧档缺 `talents` 默认空）。
- 共享核心另含 `CoreTest.kt`，可在本机执行 `./gradlew :shared:test` 跑 JUnit 级断言（含存档往返 / 装备 / 融合 / 有效属性 / **渡劫**测试，本次新增 5 例）。
- **妖池完整性**：`verify/verify_pool.py` **直接解析 `MonsterPool.kt` 源码**校验（不依赖编译器）—— id 唯一、枚举合法、技能 id 全部命中 `SkillLibrary`、四项基础属性在 1..250、进化链闭合（evolveTo 目标存在且反向 `evolveFrom` 一致、稀有度严格递增）。**1388 项断言全过**，妖池 100 只（凡27/灵26/仙17/神30），20 条完整进化链（含 3 段链、2 段链与龙子/瑞兽/五行精灵/上古凶怪补全）。
- **技能池完整性**：`verify/verify_skills.py` **直接解析 `SkillLibrary.kt` 与 `Core.swift`** 双端校验——总数 73、id 唯一、SkillType·Element 枚举合法、威力∈[0,200]、暴击∈[0,0.5]、伤害技能威力>0 且非伤害=0、6 大类全覆盖、Kotlin↔Swift id 集合完全一致；并统计妖池装配（四神兽签名技已接线）。**739 项断言全过**。
- **LBS / 祈妖 / 副本 / 社交 / 经济 / 阵容羁绊（②+③）**：`verify/verify_lbs_gacha_dungeon.py` **直接解析 `MonsterPool.kt` 源码抽 80+ 妖池**（id/cat/element/rar/fest/clan）并镜像 `LocationEngine`/`GachaEngine`/`DungeonEngine`/`SocialEngine`/`ShopEngine`/`FormationEngine`（不手抄漂移）。断言覆盖：nearbySpawns 确定性、区域五行偏向（木在山林显著高于水在非偏向木）、夜间怪类×1.6、中元/春节加权、出率分布、签文映射、软/硬保底（第90抽必神）、十连兜底≥灵、重复转妖丹、副本 canEnter/奖励区间、据点加成/每日限、引妖香日上限/跨日重置、**GPS 防作弊**（首次/时间无效正常、正常步行通过、瞬移/超速判定异常、safeSpawns 异常返回空）、**阵容羁绊**（空队/无羁绊、阴阳调和/百妖夜行/三才阵/相生阵/青丘幻月各条命中、增伤叠加封顶 ×1.6、倍率进伤害）、**信誉分**（分层边界 80/60、观察层罕见 −30%、受限禁止户外 LBS、瞬移/超速扣分钳制、每日恢复 +1、连续瞬移落入受限闭环）、**连携组合技**（青蛇缚妖甘露/铜镜精狂战连斩/九尾狐烈焰焚天/蛟玄冰寒壁各条命中、扫帚精无可触发连携、倍率放大与回血镜像）；**在家秘境**（主题→五行偏向映射、generateSpawns 确定性、五行偏向高于非偏向主题、凡+灵稀有度主导、pickTheme 合法、GameState 每日3次免费/耗尽耗香火20/香火不足阻断）、**新手引导**（5 步定义/前3步 forceable/currentStep 递增至空/allDone、7日留存逐日解锁 REALM→第2日/DUNGEON→3/GACCHA→4/SOCIAL→5、签到奖励 10→70 递增封顶、引导中全部开放覆盖、签到每日首次领10/同日二次返回0/跨天递增至20）、**家园洞府**（Lv0 香火时产20/妖丹1、Lv5 香火30/妖丹1.5、风水攻击增益 0%→Lv10 20%、升级花费 100→600、封顶12h→香火240、首次进入启动计时不补发、再收3h→香火60/妖丹3、增量1h→香火20、妖丹不足不可升级/足100升阵法至Lv1）、**天气地形战斗**（晴无偏置、雨→水×1.3/火×0.8、雪→火×0.7/水×1.2、风→风×1.3/土×0.8、地形偏向×1.2、雨+水域水行×1.56、晴+山林木行×1.2、阵法 Lv0 无增益 / 羁绊×1.2×阵法Lv5+10%、**赛季通行证**（未开始/进行中/已结束 dayIndex、剩余天数 90/1/0、档位 0~99=0/100→1/250→2/满经验封顶60、免费轨里程碑200香火15妖丹/普通30/3、付费轨里程碑60灵玉20妖丹/普通10/5、累计250经验→2档、领免费档入账不重复、付费档需通行证/灵玉不足不可购/耗300购成功/购后领付费档、claimAll 免费2付费2、满90天滚动下一赛季清零、存档往返已领不重复）、**疲劳值 / 单日收益封顶**（倍率三层 ×1.0/×0.5/×0.0、addFatigue 夹取 0..100、跨天重置、收妖×5→疲劳10、收妖×5+对战→14、副本(20) 充沛全额+疲劳8 / 疲劳态减半→10 / 透支态封顶→0、签到(70) 充沛全额+疲劳3 / 透支态封顶→0）、**排位赛段位天梯**（tierIndex/tierName 互转与越界夹取、炼气80胜→升筑基满100、筑基90胜→升金丹余10、化神封顶80胜→99不升段、炼气10负→保底0不降、筑基5负→降炼气余90、金丹0负→降筑基余85、升段荣誉奖励 筑基30香火/金丹30灵玉/元婴60香火30灵玉/化神80灵玉10妖丹/炼气无、对局钩子升段发奖 120→150、对局钩子降级无奖、连续胜筑基95→金丹35、存档往返元婴42一致）、**成就/勋章/称号（GDD 二十·展示型荣誉）**（成就任务库 22 项 ACHIEVEMENT 复用现有 ObjectiveType/GameEvent 自动追踪、勋章阶梯 4 阶 成就0→无/1→青铜/4→青铜+白银/8→+黄金/22→钻石(全部成就)、称号 12 个 收妖大师成就≥1/巡妖使图鉴≥30%/渡劫真君渡劫≥1/化神尊者段位≥化神/山海通志图鉴100%/妖志大成全成就/山海英雄成就≥6/妖志宗师图鉴≥80%/九转真君渡劫≥3/金丹道君段位≥金丹/元婴老祖段位≥元婴/中流砥柱成就≥12 各真+假、全条件→解锁12称号、零进度→0、GameState 装备已解锁成功/装备未解锁失败/展示名=化神尊者/取消装备→无/存档往返默认空串兼容）、**宗门科技树（GDD 二十·公会建设+科技加成）**（科技节点 10=基础7+三派专属3、researchCost 递增 20/30/…、前置依赖 tech_judi←tech_gongfa、派系专属仅对应派系可研、bonuses 加性汇总 攻伐满级+20%/守御/体魄/御妖/经修/资粮/据点经营、未择门不计茅山专属、收妖率按 captureMul 线性提升、伤害公式含 sectAttackMul 乘子×1.2、收益×疲劳×资粮、经验×经修、据点占领贡献 50×(1+据点经营)、存档含 sectTech/sectContribution 向后兼容、BattleEngine/CaptureEngine/BattleSession/GameState 接缝已接）；**活动系统（GDD·活动·节气复玩 / 年节限时 + 运营活动）**：并入活动断言——ACTIVITIES 5 项（中元鬼节/新春嘉年华/收妖大作战/论道大会/勤修有礼）、日期区间含起止日、active 过滤（2026-08-15→收妖大作战/论道大会/勤修有礼，2026-08-28→仅中元）、aggregate 乘性叠加（收妖×1.15/经验×1.25/收益×1.32）、接线乘率（经验 经修满级×论道 ×1.5625、收益 资粮满级×活动 ×1.584）、双端「限时活动」卡 + CatchScreen/CatchView 收妖已乘、Core.swift 镜像）；**存档导出/导入（跨端云同步基础层）**：并入存档断言——SaveExportEngine 便携包装（EXPORT_VERSION + PortableSave{v,save}）、双端 toPortable/fromPortable 一致、导出结构顶层 v==1/save 对象、fromPortable 往返 sectTech/贡献/灵玉 一致、版本容错 v 缺失/未知→null、旧存档缺 sectTech→默认空、双端「存档备份·云同步」卡 + Core.swift 镜像）；**每日 0 点静默重置（DailyResetEngine 纯函数提取）**：并入每日重置断言——object DailyResetEngine + isCrossDay 纯判定（首启空串/同日/昨日/未来日）、TARGETS 7 项权威清单（quests/secret_realm/dungeon/shop/stronghold/reputation/fatigue）、双端 maybeResetDaily 已用 isCrossDay、旧裸判定已移除、Core.swift 镜像 struct DailyResetEngine + 双端「每日 0 点刷新」数据驱动卡）。**502 项断言全过**。
- **战斗稳健性 + 七行平衡自检（GDD 十五·数值基础）**：`verify/verify_lbs_gacha_dungeon.py` 新增 **20 项断言**——`BattleSession` 增回合上限 `MAX_TURNS=100` 与 `resolveByHp`（平局按剩余血量比例裁决，保证战斗必然终结、必有结果，零 UI 改动）；新增 `BattleIntegrityEngine`（纯函数：七行基准妖对等对战、固定种子确定性模拟、`elementMatchupMatrix` 七行胜率矩阵、`balanceReport` 含每元素均胜率/克制胜率/被克胜率/全终止/无退化统治/克制优势/被克劣势）；Python 重实现七行战斗验证：七行 42 场对局 **全部终结**、**克制方稳定占优（A克B→玩家胜率≥0.8）**、**被克方稳定劣势（A被B克→玩家胜率≤0.2）**、**无元素退化统治（每元素均胜率∈(0,1)）**、每次出招伤害 ≥1 且为整数、回合上限按血量比例裁决产出定义胜者（平局判玩家胜 / 高血方胜）。自检过程中**发现并修复七行相克矩阵缺陷**：原 `Element.kt` 存在 3 组互克（WOOD↔THUNDER、EARTH↔WIND、WATER↔THUNDER），克制关系不可对称，已重写为合法非对称竞赛图（每元素恰好克制 2 个、被 2 个克制、无互克），Kotlin/Swift/Python 三端一致。
- **存档语义完整性校验 + 自愈（存档稳健性）**：`verify/verify_lbs_gacha_dungeon.py` 新增 **32 项断言**——`object SaveIntegrityEngine`/`fun validate`/`fun sanitize`/`data class Violation`/`TEAM_CAP` 源码断言；Python 等价重实现 `sie_validate`/`sie_sanitize`：校验 版本/货币非负(incense·lingyu·yaodan)/标量边界(fatigue 0..100·reputation 0..100·rankTier 0..4·rankPoints 0..99·nextInstSeq≥1)/owned 实例(instanceId 唯一非空·defId∈MonsterPool·level/breakthrough/exp/tribulations/talents 越界)/team 引用+上限6/equipped 悬空引用/quests id+进度/图鉴 id/faction/sectTech id+等级；`sanitize` 钳制标量·丢弃悬空引用·钳制养成越界·去重，返回新合法档；Core.swift 镜像 `struct Violation`/`struct SaveIntegrityEngine`(validate/sanitize/teamCap/monsterIds/questIds/sectTechMax)；场景覆盖干净档空校验、坏档各 code 检出、sanitize 后 validate 空（修复闭环）、货币归 0、标量钳边界、丢弃未知 defId、重复 instanceId→保留、team 清理悬空、faction→None、丢弃未知 sectTech、等级/突破钳制；并接入三条输入边界（`SaveCodec.decode`/`CloudSyncEngine.merge`/`SaveExportEngine.fromPortable` + Swift `GameState.loadSave` 镜像），坏档进入游戏前即自愈；复跑 **598 → 611/611 全过 (+13 断言)**。随后新增**性质测试（固定种子随机损坏档 400 例 + 云合并 200 例）**断言 sanitize 的**全满性 / 幂等性 / 不动点**，并**抓出真实缺陷**：Kotlin/Swift 的 `sectTech` 仅钳下界（越界等级残留 `SECTTECH_LVL`）、`sanitize` 未归一 `version`；Python 镜像天赋回收循环用冻结元组致 `INST_TALENT_CAP` 残留 + 幂等漂移。修复三端后复跑 **611 → 620/620 全过 (+9 断言，累计 566→620 +54)**。
- **静态数据定义完整性校验 `DataIntegrityEngine`（定义层）**：`verify/verify_lbs_gacha_dungeon.py` 新增 **21 项断言**——`object DataIntegrityEngine`/`struct DataIntegrityEngine`(validate/isClean/assertClean) 双端源码断言；Python 解析 `MonsterPool.kt`/`SkillLibrary.kt`/`ComboEngine.kt`/`QuestLibrary.kt` 源码，断言**妖池技能⊆技能池 / 进化链⊆妖池 / 连携技能⊆技能池 / 任务 ref∈合法元素·稀有度枚举名**四项引用全部解析合法（Kotlin `SkillLibrary.get` 对未知 id 启动即崩溃、Swift `pick` 用 compactMap 静默丢弃→两端失败模式分歧，统一引擎同跑把分歧变确定性失败）；复跑 **620 → 641/641 全过 (+21 断言，累计 566→641 +75)**。
- **养成操作产出合法实例（运行期存档污染防护）**：审计发现 `CultivationEngine.evolve` 进化时只替换 `defId`、保留 `tribulations`，而 `maxTribulations` 依赖稀有度——真实进化链 `蛇妖(FAN)→蛟(LING)→龙(SHEN)` 跨稀有度，满渡劫(trib=3)后进化成 LING(maxTrib=2) 会产出 `tribulations=3>2` → `INST_TRIB` 非法存档 + `tribulateMult(1+0.75)` 把属性算超（越界/超模），普通长线玩法（渡劫后进化）即可触发。`evolve` 现把 `tribulations` 钳制到新稀有度上限（Kotlin/Swift 双端 `maxTribulations(newDef.rarity)`）；`verify` 新增 **8 项断言**——复现 OLD evolve 产生 INST_TRIB、确认 NEW 钳制后合法且未越界值原样保留、双端源码断言、沿真实进化链证明满渡劫后 NEW evolve 在所有边均合法且确有跨稀有度边必须钳制；复跑 **641 → 649/649 全过 (+8 断言，累计 566→649 +83)**。
- **收妖入口边界守卫（运行期污染防护）**：审计发现 `GameState.catchMonster(id)` 是平台收妖唯一入口 API，却不对 `id` 做存在性校验——`MonsterPool.byId(id)` 返回可空却仍 `codex.catch(id)` 并创建 `defId=id` 的实例；传入 MonsterPool 中不存在的 id（平台误传 / 过期数据 / 外挂注入）会污染图鉴并产生 defId 悬空、effectiveStatsOf/buildCombatant 回退首妖的孤儿实例。存档层 `SaveIntegrityEngine` 已兜底，但运行期入口漏。`CatchOutcome` 增 `val success: Boolean = true`（向后兼容默认），`catchMonster` 现于任何变更前守卫 `MonsterPool.byId(id) == null → return CatchOutcome(null, emptyList(), success = false)`（零变更拒绝）；Swift 镜像 `guard MonsterPool.byId(id) != nil else { return [] }`。`verify` 新增 **6 项断言**——双端源码断言守卫存在 + `CatchOutcome.success`、OLD 复现（未知 id 污染图鉴+孤儿实例）、NEW 拒绝零变更、合法 id 仍正常收服（守卫不误伤）；复跑 **649 → 655/655 全过 (+6 断言，累计 566→655 +89)**。

- **据点战占领节点边界守卫（运行期污染防护）**：审计收妖入口后继续扫运行期入口，`GameState.captureNode(node)` 是据点战占领唯一 API，却不对 `node` 做白名单校验——`SocialEngine.capture` 原样把任意 `node` 写入 `strongholdNode`（悬空引用），且 `strongholdAttackBonus` 仅判 `strongholdNode != null` 仍触发虚假 +10% 攻击加成。`LocationEngine` 新增派生白名单 `STRONGHOLD_NODES`（= NODE_KEYS 值，单一真源 {城隍桥/古庙/老槐树/东海之滨}）；`captureNode` 现于任何变更前守卫 `node !in STRONGHOLD_NODES → return false`（零变更拒绝）；Swift 镜像 `guard LocationEngine.strongholdNodes.contains(node) else { return false }`。`verify` 新增 **9 项断言**——双端白名单定义 + 双端守卫源码断言 + 白名单与 Kotlin NODE_KEYS/Swift nearbyNode 地图值一致收敛 + OLD 复现（未知节点写悬空引用 + 虚假 +10% 加成）/ NEW 拒绝零变更 / 合法节点仍正常占领（守卫不误伤）；复跑 **655 → 664/664 全过 (+9 断言，累计 566→664 +98)**。
- **每日重置契约（#181 平台契约）**：`verify/verify_daily_reset.py` **直接解析 `DailyResetEngine.kt` / `DailyResetScheduler.kt` / `SeasonEngine.kt` 源码**校验（不依赖编译器）—— `Target` 枚举 7 项（与 GDD 每日语义对应）、`isCrossDay` 跨天判定真值表（首启空串/同日/跨日/跨月/跨年/时钟回拨）、`resolvePending` 跨天返回 7 项清单·同日返回空、赛季常量 `SEASON_LENGTH_DAYS=90`/`DAY_MS=86400000` 与 `seasonsElapsed` 边界（满 90 天→1 赛季、满 180→2、now<start→0）、`SchedulerStatus` 4 态、`GameState`/`resolvePending` 均复用 `DailyResetEngine.isCrossDay` 唯一权威（防旁路漂移）。**29 项断言全过**。
- **云同步契约（#180 平台契约）**：`verify/verify_cloud_sync.py` **直接解析 `CloudSyncEngine.kt` / `SaveExportEngine.kt` / `SaveData.kt` 源码**校验（不依赖编译器）—— FNV-1a 32-bit 校验和（复现 + 标准参考向量 `""`→0x811c9dc5、`"a"`→0xe40c292c，证明等同标准算法）、`merge` 的 LWW（modifiedAt 大者胜）+ deviceId 字典序平局打破 + 三货币（灵玉/妖丹/香火）取 max、非货币字段一律采用 LWW 胜方、`toEnvelope`/`fromEnvelope` 版本门控（`e.v != EXPORT_VERSION → null`）、合并结果经 `SaveIntegrityEngine.sanitize` 收口、`Json.ignoreUnknownKeys=true` 旧存档兼容。**27 项断言全过**。
- **社交据点战契约（#182 平台契约·本地规则）**：`verify/verify_social_stronghold.py` **直接解析 `SocialEngine.kt` / `LocationEngine.kt` / `GameState.kt` / `SectTechEngine.kt` 源码**校验（不依赖编译器）—— `STRONGHOLD_NODES` 白名单（派生自 `NODE_KEYS.values`，4 节点真值 = 城隍桥/古庙/老槐树/东海之滨）、`Faction` 三派枚举（MAOSHAN/LONGHUSHAN/GEZAO）、`STRONGHOLD_ATTACK_BONUS=1.10`（占领据点攻击 +10%）、`CONTRIBUTION_PER_CAPTURE=50`、`SocialEngine` 每日限占领状态机（`canCapture`/`capture` 真值表：`lastCaptureDay != today` 可占、同日二次被拒、跨天恢复）、占领加成 `attackBonus(owned)→1.10 / 未占→1.0`、`GameState.captureNode` 白名单守卫（`node !in STRONGHOLD_NODES → false`，防平台误传/外挂注入假据点 + 虚假 +10%）、`maybeResetDaily` 据点每日重置（跨天清空 strongholdNode）。**33 项断言全过**（GPS 防作弊服务端权威部分仍属运行时接入、需后端选型）。
- **AR 收妖契约（#183 平台契约·本地规则）**：`verify/verify_ar_capture.py` **直接解析 `ARCaptureController.kt` / `CaptureEngine.kt` / `GameState.kt` 源码**校验（不依赖编译器）—— `ARStatus` 三态（UNSUPPORTED/PERMISSION_DENIED/READY）、`status()` 默认 UNSUPPORTED（平台占位）、`resolveAtAnchor` 纯委托 `CaptureEngine.resolve`（AR 不引入独立收妖率）、`ArCaptureBackend` 五方法（isCameraAvailable/requestPermission/beginSession/endSession/resolveCapture）且 `resolveCapture` 契约复用 `CaptureEngine.resolve`、`GameState.arStatus()` 委托接线；`CaptureEngine` 公式权威值（环周期基数 FAN=2600/LING=2200/XIAN=1900/SHEN=1600·辅助×1.4、稀有度系数 1.0/0.9/0.8/0.7·0.92 缩放·0.06 地板·辅助 +0.18、accuracy 辅助 raw×0.8+0.2）及行为复现（含 p=1.0 必成·p=0.06 双次独立 rng 解耦边界、AR 路径 ≡ 普通收妖路径）。**40 项断言全过**（平台摄像头/ARKit·ARCore 壳仍属运行时接入、需平台选型）。
- **地图 SDK 契约（#184 平台契约·本地规则）**：`verify/verify_map_sdk.py` **直接解析 `MapSdkAdapter.kt` / `LocationEngine.kt` 源码**校验（不依赖编译器）—— `NODE_KEYS` 4 节点真值坐标（31.23:121.47→城隍桥 / 30.25:120.16→古庙 / 25.03:102.71→老槐树 / 30.62:122.08→东海之滨）、`STRONGHOLD_NODES` 派生单一真源（`NODE_KEYS.values.toSet()`，恰好等于全部灵脉节点）、`nodeCoordinates()` 解析（split(":")+toDouble·lat=lng 顺序）、`MapNode`/`PoiKind`/`MapPoi` 类型、`MapSdkAdapter` 5 方法双端一致；`buildPois()` 复用真源构建 POI（id=name、坐标精确映射、kind 全 STRONGHOLD 成因、不硬编码坐标、kind 判定分支正确、双端真值收敛一致）。**51 项断言全过**（平台地图 SDK 壳 ARKit/MapKit/高德/腾讯 仍属运行时接入、需平台选型）。
- 注：本会话环境无 JDK，未对 Kotlin 做编译级验证；Android/iOS UI 需你在对应 IDE 编译运行。**养成演出（天劫 / 装备五路径）为纯视觉 overlay，本机未编译验证，无数值断言。**

---

## 已知限制 / 后续细化（对照 GDD 待办）
- 妖池已达成 GDD 目标 **100 只**（20 条进化链）；技能池已达成 GDD 目标 **73 式**（6 大类：攻伐17/守御10/术法10/辅助10/五行专精17/心法被动9），四神兽签名技已接线实战。
- AR 收妖（ARKit/ARCore 摄像头）、LBS 真实地图 SDK、社交服务器权威（好友/团战/宗门排行榜）尚未实现；**天气/地形当前为本地可调环境变量**（`GameState.currentWeather`/`currentTerrain` 运行时，由平台层接入真实天气 API / 地图 SDK 后自动驱动），天气地形战斗 / 在家秘境 / 新手引导(7日留存) / 家园洞府 / **赛季通行证（90 天双轨）** / **疲劳值·单日收益封顶** / **排位赛段位天梯（段位积分数学 + 升段荣誉奖励）** / **成就·勋章·称号（22 项成就复用任务统计、4 阶勋章阶梯、12 称号可装备展示）** / **宗门科技树（公会建设+科技加成：10 节点/三派专属/贡献研习/攻击·防御·体力·收妖·经验·收益·据点 全局加成）** / **活动系统（运营限时：7 项活动 / 收妖·经验·收益乘子缝接 capture/exp/income / 双端「限时活动」卡）** / **存档导出/导入 + 跨端云同步冲突合并（SaveExportEngine 便携包装{v,save} + 双端 toPortable/fromPortable；CloudSyncEngine 在 shared 侧做 LWW 合并+货币max+ FNV-1a 校验，双端一致 + 双端「存档备份·云同步」卡）** / **每日 0 点静默重置（DailyResetEngine 纯函数提取：isCrossDay 跨天判定 + 7 项每日重置系统权威清单，平台层静默触发待接入）** / **战斗稳健性 + 七行平衡自检（BattleSession 回合上限 MAX_TURNS=100 + 平局按血量比例裁决；BattleIntegrityEngine 七行胜率矩阵/平衡报告；并修复原七行相克 3 组互克缺陷，重写为合法非对称竞赛图）** / 据点战 / 副本妖境 / 阵容羁绊 / 连携组合技的 `shared` 逻辑与双端 UI 已落地。
- **进化 / 突破 / 炼妖融合 / 装备祭炼 / 装备五路径（升星·突破·淬炼·继承）/ 天赋命格（3 层攻防辅）/ 渡劫飞升（数值与流程）已落地**；渡劫的**演出动画 / 天劫特效**与装备五路径的**养成演出**（升星/突破/淬炼/继承光效）**已写入双端 overlay 视觉代码（天劫闪电循环 / 装备星辉脉冲），本机无 JDK 未编译验证，需在 Android Studio / Xcode 构建确认**；数值与流程此前已落地并经 Python 验证。
- **② AR/LBS 与 ③ 社交/副本/祈妖/经济 的可验证核心已全部落地**：`LocationEngine`(LBS 加权刷怪)、`ARCaptureController`(AR 契约占位)、`GachaEngine`(祈妖保底)、`DungeonEngine`(副本妖境)、`SocialEngine`(三派·据点战)、`ShopEngine`(引妖香经济)、`FormationEngine`(阵容羁绊) 均在 `shared` 实现并经 Python **199 项等价验证**；双端 UI 入口（探索页 LBS/AR/社交经济块 + 祈妖/副本独立屏 + 对战页阵容羁绊卡）已接入；iOS `SaveData` 自定义 `init(from:)` 对齐 Android 旧档兼容。
- 仅余**平台 / 后端依赖占位**：AR 摄像头与 ARKit/ARCore 由平台层接入（`status()` 默认 UNSUPPORTED）；社交服务器权威、好友/团战/宗门排行榜、GPS 防作弊由 `SocialService` 接口预留，本切片不实现；每日静默重置的 **shared 纯函数 `DailyResetEngine.isCrossDay` 已提取并可验证**，但后台定时 0 点触发（平台层调度）未接入，当前仍仅启动/进入前台触发跨天重置与赛季推进 `tickSeason`。
- **装备/存档 defId 完整性（运行期污染防护收口 + 存档自愈补全）**：审计收妖/据点两处外部 id 入口后，扫到最后一处「从外部 id 创建游戏对象」的公开 API `GameState.grantEquip(equipId)` 不校验 `equipId ∈ EquipmentLibrary`，会写入 defId 悬空的死装备；且 `SaveIntegrityEngine` 此前未校验 `equipment[].equipId`，坏档死装备可越过自愈残留。`grantEquip` 现校验 `EquipmentLibrary.byId(equipId) == null → return null`（双端同构，返回类型改可空）；`SaveIntegrityEngine` 增 `equipmentIds` 查询表 + `EQUIP_DEF` 违规 + `sanitize` 丢弃 defId 不存在的死装备（自愈）。`verify` 新增 **22 项断言**——双端 grantEquip 守卫 + 返回可空、双端 EQUIP_DEF/equipmentIds/丢弃守卫、新手装备 id 真实存在于 EquipmentLibrary、OLD 复现(sanitize 残留死装备)/NEW 丢弃仅留 2 件合法、边界审计收口（所有接受外部 String id 的公开变更 API 均已防御性校验）；复跑 **664 → 686/686 全过 (+22 断言，累计 566→686 +120)**。至此**外部 id 入口（收妖/据点/装备）+ 全部存档对象 defId 类别（妖/装备/任务/图鉴/装配）均闭环**，纯客户端可验证运行期污染防护与存档自愈完整收口。

- **排位赛段位天梯积分数学验证（GDD 二十·竞技与荣誉·RankEngine，补齐此前 0 断言缺口）**：审计发现 `RankEngine`（排位天梯：5 段炼气→化神、满 100 升段、胜+20/负−15、化神封顶/炼气保底）此前**无任何 Python 断言覆盖**（宣称落地却未验证）。补齐验证并发现一真实防御缺口：`applyResult` 仅对 `points` 做 `coerceAtLeast(0)`，未夹上界——而 `rankPoints` 直接来自存档（不可信），坏档 `rankPoints=160` 且失败时静默产出非法内存态（p=145>99），与历轮「不可信输入须守卫」原则同一类。`applyResult` 两端同改 `coerceIn(0, POINTS_TO_PROMOTE-1)`（合法输入 no-op）。`verify` 新增 **30 项断言**——双端源码段位表/常量一致收敛、单段晋升、单胜至多升一段、连续两胜未达100不升段、6 连胜真实跨阶、化神封顶不溢出、炼气保底不欠负、升段荣誉奖励表 5 档精确值、tierIndex/tierName 往返、性质测试（任意输入→输出恒合法 段∈[0,4] 且 积分∈[0,99] 无溢出/欠负）；复跑 **686 → 716/716 全过 (+30 断言，累计 566→716 +150)**。
- **养成三引擎公式验证 + 装备存档上界越界修复（FusionEngine / TalentEngine / EquipmentEngine，补齐此前 0 断言缺口）**：审计发现三引擎此前**零 Python 断言覆盖**（宣称落地却未验证），并发现一真实防御缺口——`SaveIntegrityEngine` 对装备仅校验 `level/stars/tier < 1` 下界、`sanitize` 仅钳下界，**缺上界钳制**：坏档/外挂写入 `stars=99` 即触发 `starMult=10.8x` 等战力过度膨胀（与 RankEngine 同类不可信输入未守卫）。双端 `validate` 新增 `EQUIP_STAR_HI/EQUIP_TIER_HI/EQUIP_SUBN/EQUIP_SUBVAL` 四类上界违规 + `sanitize` 钳星级≤5/突破阶≤maxTier/副属性值≤15/副属性条数≤4（与 `talent` 上界钳制一致）。另 `DataIntegrityEngine` 新增 `RECIPE_OUT` 校验融合配方产物⊆妖池（防「玄武」类死配方）。`verify` 新增 **56 项断言**——配方产物真实存在+双端源码一致、plan 配方/稀有度/属性择优+newLevel=max+3 clamp+bt=min+技能并集取前4、canFuse 前置、talent 容量/分支/倍率数值、装备 maxTier/starMult/mainBonus 真实数据(eq_sword)+边界、装备上界坏档钳制(stars99→5/tier9→2/level999→20/6条→4/值999→15)+合法档不误伤；复跑 **716 → 772/772 全过 (+56 断言，累计 566→772 +206)**。
- **信誉分引擎 / 图鉴功能断言补全（ReputationEngine / Codex，补齐此前 0 功能断言缺口）**：audit 发现 `ReputationEngine`（GDD 十六·公平与稳健：分层/扣分/恢复/稀有倍率/可探索判定）与 `Codex`（GDD 三·探索：地域/秘境分离、完成度、里程碑、新解锁）此前**无任何 Python 功能断言覆盖**（仅有 SaveIntegrity 标量钳制与 GameState 捕获逻辑间接覆盖，宣称落地却未验证）。补齐验证时抓出一真实防御缺口：`dailyRecover` 两端均只夹上界、不夹下界（`Kotlin` `(score+1).coerceAtMost(MAX)` / `Swift` `min(MAX, score+1)`），与 `tier`/`applyPenalty` 的双边界钳制不一致——正常流程信誉分经 `SaveIntegrity` 已钳到 [0,100] 不触发，但作为有界评分恢复操作，对任意输入应保证不出界（与历轮「不可信/边界输入须守卫」同类）。双端 `dailyRecover` 改 `coerceIn(MIN,MAX)` / `min(MAX, max(MIN, score+1))`。`verify` 新增 **35 项断言**——常量/扣分表与 Kotlin 单一真源解析、分层边界(60/80 临界精确)、tierLabel 中文映射、观察层稀有倍率 0.7、受限层禁户外 LBS、扣分精确值(瞬移18/超速12/未知10)+钳制、恢复封顶/下界钳制、性质测试(任意输入→恢复与扣分恒∈[0,100])、双端源码常量+dailyRecover 修复落地；Codex 地域/秘境分离、完成度∈[0,1]、空池防除零、里程碑 reached 单调、newlyUnlocked 跨阈值语义、关键方法存在；复跑 **772 → 807/807 全过 (+35 断言)**。
- **养成核心引擎断言补全（CultivationEngine，补齐此前 0 功能断言缺口）**：audit 发现「养成」最核心的 `CultivationEngine`（GDD 十六·养成线：等级/经验/突破/进化/渡劫/有效属性/进化链）此前**零 Python 功能断言覆盖**（#167 只补 Fusion/Talent/Equipment，漏了核心养成），属宣称落地却未验证。双端 `Core.swift` 已完整镜像（含运行期 `breakthrough` 钳制）。`verify` 新增 **72 项断言**——双端源码解析 7 常量一致性护栏+关键方法双端存在、expForLevel 公式(100+(lv-1)*25)锁定、levelCap(30→55)/statMult(1.0→1.40)/突破需求与妖丹消耗/渡劫倍率与消耗/tribulations 上限(神阶封顶 FAN3·LING2·XIAN1·SHEN0)、gainExp 自动升级+溢量封顶、突破/进化(null 守卫+保留等级经验突破+进化钳制 tribulations 到新稀有度上限防 INST_TRIB/属性超模)、渡劫(满级满突破守卫)、effectiveStats 公式(base×statMult×tribulateMult×talentMult 且 ≥1，对齐 Kotlin/Swift 左结合分组)、进化链(yao_she→yao_jiao→yao_long)、性质测试 4000 例(等级/突破/渡劫上界+有效属性≥1 恒成立)+单调性(突破↑/渡劫↑→有效攻击单调↑)；复跑 **807 → 879/879 全过 (+72 断言)**。
- **活动/荣誉/疲劳三引擎功能断言补全（ActivityEngine / HonorEngine / FatigueEngine，补齐此前 0 功能断言缺口）**：audit 发现活动系统/荣誉/疲劳三引擎此前**零 Python 功能断言覆盖**（仅结构存在性检查，无 isActive/aggregate/unlockedMedals/incomeMul/addFatigue/label 等语义断言），属宣称落地却未验证。`verify` 新增 **38 项断言**——双端 ACTIVITIES 配置(id 集合+完整乘子三元组)一致护栏、isActive 含起止日边界、active(2026-08-16) 聚合数值(captureMul=1.15/expMul=1.25/incomeMul=1.32 交叉验证解析+公式)、配置乘子≥1.0 约束、聚合空列表=1.0；双端 MEDALS(4)/TITLES(12) 数量+req 阶梯(1/4/8/MAX)一致、unlockedMedals 阶梯边界、isUnlocked 各阈值精确(codexRatio 29/30、rankTier 2/3/4、tribulation 2/3、achievementCount 11/12)、解锁集合随成就单调不减；双端 MAX_FATIGUE=100/SOFT_CAP=60 一致、incomeMul 三档边界(59→1.0/60→0.5/100→0.0)、addFatigue 钳制[0,100]、label 三档文案、resetDaily=0、性质测试(任意疲劳 incomeMul∈{0,0.5,1.0} 且 addFatigue∈[0,100])；复跑 **879 → 917/917 全过 (+38 断言)**。
- **战斗引擎功能断言补全（BattleEngine + Element/MonsterCategory 五行/类别矩阵，补齐此前 0 数值级断言缺口）**：audit 按方法级复核发现核心战斗系统 `BattleEngine.damage` 此前仅"关系式"简化镜像（`b_raw` 写死 level=10、elem/cat/crit 置 1，只验证 sect 线性），**五行相克矩阵(×1.6/×0.6)、类别克制环(×1.3/×1/1.3)、levelCoef 公式、floor+coerceAtLeast(1)、暴击率 clamp、bestCounterSkill 均零数值级断言**。`verify` 新增 **45 项断言**——双端五行矩阵(BEATS/beatsMap 7 元素)与类别环(RING/ring 4-循环)解析+一致性护栏(抓 desync)、levelCoef(1)=1/(51)=2、五行边界(金克木×1.6/水克金×0.6/无克制×1.0)+所有 49 项∈{1.6,0.6,1.0}+无互克(合法非对称竞赛图)+每元素出度=2、类别环边界(妖→精×1.3/精被克×1/1.3)+所有 16 项∈{1.3,1/1.3,1.0}+单一 4-循环、伤害数值((100/50)*100*1.18*1.6*1.3*0.95=466.336→floor 466)+极小伤害 coerceAtLeast(1)、sect 在 raw 上线性、双端公式存在性(levelCoef+coerceAtLeast/floor)、暴击率 clamp[0,1](critBonus=2.0→1.0/-1.0→0.0)、bestCounterSkill 加权选最优、性质测试(等级系数单调+300 例任意输入伤害恒为整数且≥1)；复跑 **917 → 962/962 全过 (+45 断言)**。
- **祈妖抽卡软保底精确边界 + 单调性增强（GachaEngine，#114）**：audit 按方法级复核（trust-but-verify）确认早期段 GachaEngine 镜像已是功能级（蒙特卡洛基础出率/签文映射/软保底升率/硬保底第 90 抽必神/十连/重复转妖丹/花费），但缺**软保底精确边界值**与**单调性性质测试**；本轮补 `shenRateAt` 镜像 + 精确断言（第 50 抽神率=0.17/第 89 抽=0.95/第 90 抽=1.0 必神）+ 软保底区间单调不减 + 性质测试（任意保底计数神率恒∈[0.15,1.0]），`verify` 新增 **5 项断言**；复跑 **962 → 967/967 全过 (+5)**。
- **存档语义完整性校验 + 自愈 `SaveIntegrityEngine` 现已落地**：审计发现此前后所有系统均缺失「存档语义校验」——外部篡改 / 坏档 / 旧版本迁移可能产生合法 JSON 但非法游戏状态（负货币、defId 指向不存在的妖、出战队伍引用遗失实例、等级/突破越界），会在 UI 按 id 查表时崩溃。`SaveIntegrityEngine` 提供 `validate`（全量收集违规）+ `sanitize`（钳制/丢弃/去重，返回新合法档）+ `isClean`，与 `CloudSyncEngine` 合并链路闭环（云合并后先 `sanitize` 再落盘）。GDD 一~二十中可纯客户端验证的核心系统(信誉分/赛季/疲劳/段位天梯/成就勋章称号/宗门科技树/活动系统/存档导出·云同步基础层/**存档语义完整性校验+自愈**/**静态数据定义完整性校验**/**养成操作合法产出(运行期污染防护)**/**收妖入口边界守卫(运行期污染防护)**/**装备/存档 defId 完整性(运行期污染防护收口+存档自愈补全)**/**据点战占领节点边界守卫(运行期污染防护)**/**排位天梯积分数学验证(补齐此前0断言+性质测试抓出 rankPoints 越界缺陷修复)**/**养成三引擎公式验证及装备存档上界越界修复(补齐0断言)**)**已全部落地**，验证 **566 → 967（全过，+401 断言，含存档完整性校验 + 边界接线 + 性质测试抓出的真实缺陷修复 + 定义层引用校验 + 养成操作合法产出(运行期污染防护) + 收妖入口边界守卫(运行期污染防护) + 装备/存档 defId 完整性(运行期污染防护收口+存档自愈补全) + 据点战占领节点边界守卫(运行期污染防护) + 排位天梯积分数学验证(补齐0断言+rankPoints越界修复) + 养成三引擎公式验证(补齐0断言+装备存档上界越界修复+配方产物校验) + **信誉分引擎/图鉴功能断言补全(补齐0功能断言+修复 dailyRecover 下界+35断言)** + **养成核心引擎断言补全(CultivationEngine 补齐0功能断言+72断言)** + **活动/荣誉/疲劳三引擎功能断言补全(补齐0功能断言+38断言)** + **战斗引擎功能断言补全(补齐0数值级断言+五行/类别矩阵双端护栏+45断言)**）**；后续仅余**平台/后端依赖占位**（AR 摄像头、SocialService 后端权威、GPS 防作弊服务端、真实地图 SDK）；每日 0 点静默重置的 shared 纯函数已落地，真正的「后台定时 0 点静默触发」由平台层调度接入；真正的云同步上传/下载（账号 + 服务器）由平台层接入，shared 侧已完成导出/导入格式基础层 + 冲突合并（LWW+货币max）+ FNV-1a 完整性校验（CloudSyncEngine）。纯客户端可验证系统至此完整闭环（内容扩充第二轮：成就 22 项 / 称号 12 / 活动 7 / 存档完整性 32 项断言已落地）。
- **平台/后端契约占位阶段（#180 起·新阶段）**：纯客户端可验证功能断言已完整闭环（967/967），按用户「进入下个阶段」转入「平台/后端依赖占位」阶段，目标为六类平台依赖定义 shared 接口契约 + 双端镜像 + Python 契约断言，使运行时由平台层接入且双端不漂移。`BattleIntegrityEngine` 运行时胜率验证（高成本）已于 **#186** 复刻 `BattleSession` 战斗循环收官（见下）。`verify` 累计升至 **985/985（+18 断言）**：新增 **云同步平台层契约（#180）**——`shared` 新增 `CloudSyncBackend` interface（`isAvailable`/`upload(SaveData,String,String):UploadAck`/`download(String):CloudEnvelope?`）+ `UploadAck` 数据类（accepted:Boolean / storedModifiedAt:Long / checksum:Int），`Core.swift` 镜像 `struct CloudSyncBackend`/`UploadAck`（未登录空 token→accepted=false），契约上行复用 `CloudSyncEngine.toEnvelope`（EXPORT_VERSION 锁定=1），双端一致 + 版本不漂移。
- **每日静默重置调度契约（#181）**：`shared` 新增 `DailyResetScheduler` interface（`isAvailable`/`register`/`unregister`/`status`/`resolvePending`）+ `SchedulerStatus` 枚举(IDLE/REGISTERED/SUSPENDED/UNAVAILABLE) + `DailyResetSchedulerContracts.seasonsElapsed` 纯函数（复用 `SeasonEngine.SEASON_LENGTH_DAYS=90`/`DAY_MS=86_400_000`，返回已跨赛季数）；`resolvePending` 默认实现复用 `DailyResetEngine.isCrossDay` 作唯一跨天判据（跨天→TARGETS 7 项、否则空列表，调用方不改动字段），判定收敛 shared 不重复实现；`Core.swift` 镜像 `struct DailyResetScheduler`/`SchedulerStatus`/`DailyResetSchedulerContracts`（seasonsElapsed 复用 `SeasonEngine.dayMs`/`seasonLengthDays`）。`verify` 新增 **32 项断言**；复跑 **985 → 1017/1017 全过 (+32 断言)**。每日重置平台调度契约双端闭环；运行时后台定时 0 点触发由平台层接入。
- **社交 / GPS 服务端契约（#182）**：完善 `SocialEngine.kt:54` 既有 `SocialService`（4→5 方法：`friends`/`giftYaodan`/`raidBoss`(返回 `RaidReceipt` 服务端回执)/`factionRank`/`factionRankboard` 宗门排行榜）+ 新增 `RaidReceipt`/`FactionRankEntry` 数据类；新建 `GpsAntiCheat.kt`：`GpsAntiCheatVerifier` interface（`isAvailable`/`verify`/`verifyPoint`）+ `GpsSample`/`AntiCheatVerdict` 数据类，`verifyPoint` 默认实现复用 `LocationEngine.haversine` 与客户端同套判定常量（`SOFT_SPEED_KMH=30`/`TELEPORT_METERS=5000`/`TELEPORT_WINDOW_SEC=180`，服务端单步判据与客户端 `spawnIntegrity` 三档同构、不重复实现；客户端 GPS 严格度已于 #191~#196 对齐 GDD 十六）；`Core.swift` 镜像 `SocialService`/`RaidReceipt`/`FactionRankEntry`/`GpsAntiCheatVerifier`/`GpsSample`/`AntiCheatVerdict`（`verifyPoint` 复用 `LocationEngine` 常量+haversine）。`verify` 新增 **44 项断言**；复跑 **1017 → 1061/1061 全过 (+44 断言)**。社交/GPS 服务端契约双端闭环；运行时后端 API 由平台层接入。
- **AR 摄像头后端契约（#183）**：在 `ARCaptureController.kt` 既有 `ARStatus`(UNSUPPORTED/PERMISSION_DENIED/READY) + `resolveAtAnchor`(复用 `CaptureEngine.resolve`) 基础上，新增 `interface ArCaptureBackend`（`isCameraAvailable`/`requestPermission():ARStatus`/`beginSession(anchorMonsterId):Boolean`/`endSession`/`resolveCapture`(复用 `CaptureEngine.resolve`，与 `resolveAtAnchor` 同义)）；`Core.swift` 镜像 `struct ArCaptureBackend`（占位 `isCameraAvailable→false`/`requestPermission→.unsupported`）。`verify` 新增 **22 项断言**（双端 5 方法签名一致 + `ARStatus` 三态一致 + `resolveCapture` 复用 `CaptureEngine.resolve` + 占位默认 `status=.unsupported`/摄像头不可用不误报 READY）；复跑 **1061 → 1083/1083 全过 (+22 断言)**。AR 后端契约双端闭环；运行时 ARKit/ARCore 摄像头能力由平台层接入。
- **真实地图 SDK 适配契约（#184）**：在 `LocationEngine.kt` 的 `NODE_KEYS`(4 灵脉节点：31.23:121.47→城隍桥 / 30.25:120.16→古庙 / 25.03:102.71→老槐树 / 30.62:122.08→东海之滨) + `STRONGHOLD_NODES`(据点白名单) 单一真源基础上，新增受控访问器 `LocationEngine.nodeCoordinates():List<MapNode>`(派生自 `NODE_KEYS`，不改 private 本质) + 新建 `MapSdkAdapter.kt`(`interface MapSdkAdapter`(`isAvailable`/`overlayPois(pois):Boolean`/`setPlayerLayer(lat,lng):Boolean`/`focusRegion(lat,lng,zoom):Boolean`) + `enum PoiKind`(LINGMAI/STRONGHOLD) + `data class MapNode`/`MapPoi`)；`buildPois()` 默认实现复用 `LocationEngine.nodeCoordinates()` + `STRONGHOLD_NODES` 构建 POI（灵脉全量 + 据点标记 STRONGHOLD），不重复硬编码坐标；`Core.swift` 镜像 `MapNode`/`PoiKind`/`MapPoi`/`MapSdkAdapter`(`buildPois` 复用 `LocationEngine.nodeEntries`/`strongholdNodes`)。`verify` 新增 **26 项断言**（双端类型/4 方法签名一致 + buildPois 复用真源 + 4 节点坐标真值 + 据点白名单双端存在）；复跑 **1083 → 1109/1109 全过 (+26 断言)**。地图 SDK 适配契约双端闭环，平台契约占位阶段**六类全部落地**；运行时地图 SDK(ARKit/MapKit/高德/腾讯)由平台层接入。
- **BattleIntegrityEngine 运行时验证（#186，高成本残留收官）**：`BattleIntegrityEngine.runMatchup` 依赖 `BattleSession.simulateToEnd()` 完整回合模拟，Python 无法等价运行 Kotlin RNG；但「克制方必胜/被克方必负」由伤害碾压保证（基准妖 LING·base=100·level=50 → atk=def=hp=217，克制首击 285~475 ≫ 217 必首杀、被克首击 ≤178 < 217 必被反手击杀），**与 RNG 无关**。在 Python 侧忠实复刻 `BattleSession` 战斗循环（属性读取/五行×1.6·类别×1.3/伤害公式 floor+coerceAtLeast(1)/回合推进/MAX_TURNS=100 上限/平局血量比例裁决），复刻驱动真实源码常量（BEATS/RING 解析自 Element.kt/MonsterCategory.kt、LING 成长率=1.1 重校验），`seed` 多跑(8 组)证明确定性单元与 RNG 无关；断言七行胜率矩阵确定性单元（克制→1.0/被克→0.0/对角 0.5）+ 四布尔平衡不变量（allTerminated/noDegenerateDominance/beatsAdvantage/beatenDisadvantage 全真）。`verify` 新增 **316 项断言**；复跑 **1109 → 1425/1425 全过 (+316 断言)**。唯一高成本残留正式闭环，全工程纯客户端可验证断言完整收口。
- 服务器权威、云同步等后端逻辑未接入（当前为单机本地存档）；信誉分**客户端侧软限制已落地**（`ReputationEngine`），权威裁决仍由 `SocialService` 后端负责。

- **妖池分布钉死 + 道德选择分支（GDD 覆盖审计 A 序列：A-3 + A-1）**：审计（见 `GDD覆盖审计.md`）在纯客户端断言收口后定位 A 序列纯逻辑缺口。`verify_pool.py` 此前仅**打印**妖池分布、未断言，现已钉死真实分布（稀有度 凡27/灵26/仙17/神30、类别 精15/怪15/妖50/灵兽20、总计100）与技能池 **73 式**（`verify_lbs_gacha_dungeon.py` 同步 +12 断言、`verify_pool.py` +11）。审计原列分布数字为陈旧错误，已按源码真实值修正。道德选择分支（GDD 十七·自由度）落地：`model/MoralChoice.kt`(SUPPRESS 镇压/TRANSCEND 超度) + `logic/MoralChoiceEngine.kt`(`modifiers()` 纯函数：镇压 atk×1.10、超度 def/hp×1.08、中性×1.0) + `MonsterInstance.moralChoice` 持久化标记(旧档 null 兼容) + `GameState.catchMonster(choice)` + `moralModsFor()`；双端 `Core.swift` 完整镜像；`verify` 新增 **20 项断言**（枚举/字段/入口参数/倍率规则/双端一致）。复跑 **1425 → 1457/1457 全过（+32 断言：A-3 +12 + A-1 +20）**。

- **排位 Ban/Pick 选将（GDD 覆盖审计 A 序列：A-2）**：补全 `RankEngine` 注释中"ban/pick 依赖平台侧 PvP"缺口（GDD 二十·竞技与荣誉）。新建 `logic/RankBanPickEngine.kt`（`BAN_PER_SIDE=2`/`PICK_PER_SIDE=3`、双端一致；`DraftState` 不可变 + `denied` 双方共享禁用池；操作结果用 `DraftResult` 枚举表达成功/7 类非法态，不用异常，天然具备坏档/外部输入防护；`availablePool`/`isComplete`/`isTeamValid` 入站队伍合法性校验）；`MonsterPool` 双端新增 `ALL_IDS`/`allIds`（由 `all` 推导，引擎真实引用）；`Core.swift` 完整镜像（`RankBanPickEngine`/`RBPPSide`/`RBPPResult`/`RBPPDraftState`）。`verify` 新增 **30 项断言**——双端常量解析+一致+BAN=2/PICK=3、引擎 wiring 引用真实池、ban 链路(OK/ALREADY_BANNED/BAN_LIMIT/INVALID)、pick 链路(PICK_BANNED/ALREADY_PICKED/PICK_LIMIT/INVALID)、availablePool 递减、isComplete 完成判定、isTeamValid 三类非法(含 ban/重复/超规模)。复跑 **1457 → 1487/1487 全过（+30 断言）**。

- **双端镜像一致性审计（#197 类型级 + #198 成员级可靠版）**：新增 `verify/verify_mirror.py` 扫描 shared 全部顶层类型与 `Core.swift` 做差集；#197 落地**类型级零漂移**并修复 Swift `MoralChoice` 由 `String?` 升级为 `enum MoralChoice: String, Codable`（与 Kotlin `enum class` 一一对应）；#198 升级为**可信成员级比对**（函数级 `[3a]` 高信号真实逻辑漂移 + 属性级 `[3b]` 备查），修复采集器括号深度感知 bug（Kotlin 嵌套 data class 构造参数曾被误算为外层属性，伪造 54 个 `[3b]` 缺口），并就地补齐 5 处真实镜像漂移（`MonsterPool.byName`/`starters`、`SectTechEngine.nodeIds`、`TalentEngine.layersOf`、`Codex.milestones`/`newlyUnlocked`/`MILESTONE_DEFS`、`GachaEngine.softPityStart`/`softPityStep`）。现**类型漂移 0 / 函数级漂移 0 / 属性级差异 0**，Core.swift 与 shared API 真正一一对应、零漂移；主验证 **1502/1502**、池验证 **1388/1388** 全过。
