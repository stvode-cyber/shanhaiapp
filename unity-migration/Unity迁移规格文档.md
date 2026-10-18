# Unity 迁移规格文档 · 《山海妖志》3D 版

> 版本：1.0 | 2026-10-18 | 基于 Kotlin 引擎代码 + GDD 设定

---

## 1. 技术栈

| 层 | 选型 | 理由 |
|----|------|------|
| 引擎 | **Unity 6 LTS** | 行业标准，跨平台 60% 手游份额 |
| 渲染 | **URP** | 跨 Android + PC + 主机；HDRP 锁平台太重 |
| 语言 | **C# 原生** | 直接重写 35 个引擎文件（总量可控），比 JNI/REST 桥接稳定 10 倍 |
| 数据 | **ScriptableObject** | 可视化编辑，改数值不用编译 |
| 热路径 | **DOTS + Burst** | 战斗并行计算，同屏 100 只妖不卡 |
| 资源管理 | **Addressables** | 按需加载 + 热更支持 |
| 输入 | **Unity Input System** | 跨平台一套 API |
| UI | **UI Toolkit** (新版) | 保留中国风 CSS-like 样式 |
| 3D 美术 | **Meshy / Tripo AI 批量生成原型 + 逐步换精品手建模** | 60+ 独特妖怪，AI 原型先跑通，精品后换 |

---

## 2. Unity 项目目录结构

```
Assets/
├── Scripts/
│   ├── Core/                    ← C# 重写 Kotlin 引擎
│   │   ├── BattleSystem.cs
│   │   ├── CaptureSystem.cs
│   │   ├── GachaSystem.cs
│   │   ├── CultivationSystem.cs
│   │   ├── TalentSystem.cs
│   │   ├── FusionSystem.cs
│   │   ├── DungeonSystem.cs
│   │   ├── WeatherSystem.cs
│   │   └── ... 其余 27 个引擎
│   ├── Data/                    ← ScriptableObject 数据
│   │   ├── MonsterDataSO.asset  (86 只妖数值)
│   │   ├── SkillDataSO.asset     (73 个技能)
│   │   └── EquipmentDataSO.asset
│   ├── Gameplay/                ← 3D 场景逻辑
│   │   ├── MonsterController.cs (3D 怪物行为树)
│   │   ├── BattleController.cs  (3D 战斗流程)
│   │   ├── CaptureController.cs  (3D 收服投妖动画)
│   │   └── MapController.cs      (3D 地图探索)
│   ├── UI/                      ← UI Toolkit
│   │   ├── CodexPanel.cs
│   │   ├── BattleHUD.cs
│   │   ├── CultivatePanel.cs
│   │   └── ... 全部 19 屏幕 3D 化
│   └── Rendering/               ← 自定义 Shader / 后处理
│
├── Art/
│   ├── Monsters/                ← 3D 怪物模型 (86 只)
│   ├── Scenes/                   ← 3D 场景地图
│   ├── VFX/                      ← 特效
│   └── Audio/                    ← 音效/BGM
│
└── AddressableGroups/
```

---

## 3. C# 重写清单（35 个引擎 · 按优先级）

### 🔴 MVP 必须（第一批，6 个）

| Kotlin 源 | C# 目标 | 核心公式 |
|-----------|---------|----------|
| BattleEngine.kt | BattleSystem.cs | `floor((ATK/DEF) × power × levelCoef × elem × cat × crit × rnd × ...)` |
| CaptureEngine.kt | CaptureSystem.cs | `((accuracy + assist) × rarityFactor × 0.92 + 0.06) × mul` |
| GachaEngine.kt | GachaSystem.cs | 双池概率 + 软/硬保底 + 十连保底 |
| CultivationEngine.kt | CultivationSystem.cs | 等级/突破/进化/渡劫完整养成线 |
| MonsterPool.kt | MonsterDataSO (JSON导入) | 86 只妖完整数据 |
| SkillLibrary.kt | SkillDataSO (JSON导入) | 73 个技能 |

### 🟡 第二批（副本/养成辅助，10 个）

| Kotlin 源 | C# 目标 |
|-----------|---------|
| TalentEngine.kt | TalentSystem.cs |
| FusionEngine.kt | FusionSystem.cs |
| DungeonEngine.kt | DungeonSystem.cs |
| WeatherEngine.kt | WeatherSystem.cs |
| EquipmentEngine.kt | EquipmentSystem.cs |
| ShopEngine.kt | ShopSystem.cs |
| RankEngine.kt | RankSystem.cs |
| ActivityEngine.kt | ActivitySystem.cs |
| FormationEngine.kt | FormationSystem.cs |
| FatigueEngine.kt | FatigueEngine.cs |

### 🟢 第三批（社交/辅助，19 个）

QuestEngine, HonorEngine, SectTechEngine, SocialEngine, ReputationEngine, HomesteadEngine, SecretRealmEngine, ComboEngine, BattleSession, BattleIntegrityEngine, MoralChoiceEngine, LocationEngine, MapSdkAdapter, ARCaptureController, GpsAntiCheat, RankBanPickEngine, DailyResetScheduler, DailyResetEngine, TutorialEngine

---

## 4. ScriptableObject 数据架构

### MonsterDataSO（替代 MonsterPool + Monster 模型层）

```csharp
[CreateAssetMenu(fileName = "MonsterData", menuName = "Shanhai/MonsterData")]
public class MonsterDataSO : ScriptableObject
{
    public string id;
    public string displayName;
    public MonsterCategory category;   // JING / GUAI / YAO / LINGSHOU
    public Element element;            // 七行
    public Rarity rarity;              // FAN / LING / XIAN / SHEN
    public int baseAtk, baseDef, baseHp, baseSpd;
    public string[] skillIds;          // 从 SkillDataSO 引用
    public string evolveFromId, evolveToId;
    public bool isFestival;
    public string description;
    public string clan;                // 进化链标记（如 "fox"）
}
```

### SkillDataSO（替代 SkillLibrary + Skill 模型）

```csharp
[CreateAssetMenu(fileName = "SkillData", menuName = "Shanhai/SkillData")]
public class SkillDataSO : ScriptableObject
{
    public string id;
    public string displayName;
    public SkillType type;             // ATTACK/DEFENSE/SPELL/SUPPORT/ELEMENT/PASSIVE
    public Element element;
    public int power;
    public bool isDamage;
    public float critBonus;
    public string description;
}
```

### Element 克制矩阵（C# 静态表，替代 Element.kt）

```csharp
public static class ElementMatrix
{
    // 攻方 → 被克制方集合
    public static readonly Dictionary<Element, Element[]> Beats = new()
    {
        { Element.METAL,    new[]{ Element.WOOD, Element.WIND } },
        { Element.WOOD,     new[]{ Element.EARTH, Element.THUNDER } },
        { Element.EARTH,    new[]{ Element.WATER, Element.WIND } },
        { Element.WATER,    new[]{ Element.FIRE, Element.THUNDER } },
        { Element.FIRE,     new[]{ Element.METAL, Element.WIND } },
        { Element.WIND,     new[]{ Element.EARTH, Element.THUNDER } },
        { Element.THUNDER,  new[]{ Element.WOOD, Element.WATER } },
    };
    public const float BeatsMult = 1.6f;
    public const float BeatenMult = 0.6f;
}
```

### Rarity 数据（静态常量，替代 Rarity.kt）

```csharp
public static class RarityData
{
    public static readonly (float growth, float weight, float captureFactor)[] 
        ByRarity = /* FAN:1.0/45/1.0, LING:1.1/35/0.9, XIAN:1.25/15/0.8, SHEN:1.5/5/0.7 */;
}
```

---

## 5. Unity MVP 范围（最小可玩 3D 版本）

### 目标
3D 地图探索 → 遇怪 → 3D 战斗 → 收服（完整核心循环）

### 必做场景/系统
1. **3D 主场景**：小地图 + 几个区域（用免费 Terrain 或程序化地形）
2. **怪物遭遇**：玩家走近触发怪物 AI（3D 模型 + 待机动画 + 追击）
3. **3D 战斗场景**：切换战斗镜头 + 怪物对打 + HP 条 + 伤害飘字（复用 2D Theme v3 颜色）
4. **收服小游戏**：光环收缩（3D 化）+ 投妖葫芦动画
5. **基础 UI**：主菜单 + 图鉴（用 UI Toolkit）

### 美术临时方案
- 怪物模型：先用 Unity Asset Store 免费低模 + 贴图替换（占位，后续换 AI 生成 + 精品手建）
- 主角：用简单 capsule + 材质球（后续换）
- 场景：URP 自带 Terrain + 免费 foliage

### 完成标准
- [ ] 启动 Unity 项目，跑通 Hello World
- [ ] 导入 ScriptableObject 数据（从 monsters.json + skills.json 自动生成）
- [ ] 3D 场景加载无报错
- [ ] 怪物 AI 追击 + 战斗切换正常
- [ ] 战斗公式结果与 Kotlin 单测一致（回归对比）
- [ ] 收服光环收缩 + 概率判定正常
- [ ] Windows Build 可启动

---

## 6. 设计 IP 资产清单（全部保留，不扔！）

| 资产 | 位置 | 用途 |
|------|------|------|
| 86 只妖完整数据 | `unity-migration/design-ip/monsters.json` | ScriptableObject 导入源 |
| 73 个技能 | `unity-migration/design-ip/skills.json` | ScriptableObject 导入源 |
| 完整战斗公式 | `unity-migration/design-ip/formulas.json` | C# 重写时对照 |
| 完整收服公式 | 同上 | 同上 |
| 祈妖双池保底 | 同上 | 同上 |
| 养成线（等级/突破/进化/渡劫） | 同上 | 同上 |
| 2D Theme v3 颜色 | Kotlin Theme 文件 | Unity UI Toolkit CSS 变量 |
| GDD 原文 | 项目根目录 | 全部设定/数值来源 |

---

## 7. 2D 项目状态：**冻结为 Fallback**

- 2D 项目（Kotlin + Compose + pywebview）**不再改一行代码**
- 三重验证链（JS706 + DOM75 + jvmTest37）保持全绿
- 继续作为 Unity 数值回归的"真值来源"——C# 实现后用 Kotlin 单测对比结果
- Git 仓库保留所有 commit 历史

---

## 8. 下一步动作（阻塞 → 执行）

| 步骤 | 内容 | 阻塞？ | 产出 |
|------|------|--------|------|
| Step 1 | 安装 **Unity 6 LTS** + URP 模块 | ✅ **是**（当前机器未装） | 可用 Unity Hub |
| Step 2 | Unity 技术 Spike：3D 场景 + 一只 AI 怪物 + 相机控制 | 否（装完即做） | Spike project |
| Step 3 | 创建正式 Unity 项目 + 导入 JSON 数据到 ScriptableObject | 否 | 项目骨架 + 数据导入工具 |
| Step 4 | 重写 BattleSystem.cs（对照 Kotlin BattleEngine.kt） | 否 | C# 战斗引擎 + 回归测试 |
| Step 5 | 重写 CaptureSystem.cs + GachaSystem.cs | 否 | 收服 + 祈妖 |
| Step 6 | 重写 CultivationSystem.cs（完整养成线） | 否 | 等级/突破/进化/渡劫 |
| Step 7 | 3D 战斗场景 + UI Toolkit HUD | 否 | 可玩战斗 |
| Step 8 | 3D 地图 + 怪物 AI + 收服小游戏 | 否 | MVP 最小可玩 |

---

## 9. 质量铁律（从 2D 教训中提取）

1. **数值回归必过**：C# 实现后，同输入条件下输出必须与 Kotlin 单测完全一致
2. **不桥接**：直接 C# 原生重写引擎，比 JNI/REST 稳定 10 倍
3. **数据驱动**：怪物/技能/装备全 ScriptableObject，改数值不改代码
4. **渐进迁移**：先跑通 MVP 核心循环，再补养成/副本/社交
5. **2D 作真值**：任何 C# 公式变更，拿 Kotlin 单测对比确认数值不变
