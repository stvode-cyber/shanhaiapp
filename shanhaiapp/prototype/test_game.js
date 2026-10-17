/*
 * test_game.js — 用 Node 实跑 game.js，复刻 verify 脚本中的关键数值断言，
 * 确认原型逻辑与 Kotlin 真相零漂移（不依赖编译器）。
 */
var G = require("./game.js");
var SH = G; // 与 index.html 命名一致（window.ShanHaiGame）
var DATA = require("./data.js"); // 与 game.js 内部 SH_DATA 同源（妖池数组 DATA.monsters）
var pass = 0, fail = 0, fails = [];
function ok(name, cond, extra) {
  if (cond) { pass++; }
  else { fail++; fails.push(name + (extra ? "  [" + extra + "]" : "")); }
}
function approx(a, b, eps) { return Math.abs(a - b) <= (eps || 1e-9); }

// ───────── CaptureEngine ─────────
ok("ringCycle FAN no-assist=2600", G.CaptureEngine.ringCycleMs("FAN", false) === 2600);
ok("ringCycle SHEN no-assist=1600", G.CaptureEngine.ringCycleMs("SHEN", false) === 1600);
ok("ringCycle FAN assist=IEEE截断(2600*1.4)=3639", G.CaptureEngine.ringCycleMs("FAN", true) === 3639);
ok("ringCycle SHEN assist=floor(1600*1.4)=2240", G.CaptureEngine.ringCycleMs("SHEN", true) === 2240);

// accuracy 边界
ok("accuracy tap=min -> raw 1 (no assist)", approx(G.CaptureEngine.accuracy(0, 0, 100, false), 1.0));
ok("accuracy tap=max -> raw 0 (no assist)", approx(G.CaptureEngine.accuracy(100, 0, 100, false), 0.0));
ok("accuracy tap=max assist -> 0.2 floor", approx(G.CaptureEngine.accuracy(100, 0, 100, true), 0.2));

// successProbability 稀有度系数（accuracy=1, assist=false）
var sp = function (r) { return G.CaptureEngine.successProbability(1.0, r, false, 1.0); };
ok("sp FAN = 1*0.92+0.06 = 0.98", approx(sp("FAN"), 0.98));
ok("sp LING = 0.9*0.92+0.06 = 0.888", approx(sp("LING"), 0.888));
ok("sp XIAN = 0.8*0.92+0.06 = 0.796", approx(sp("XIAN"), 0.796));
ok("sp SHEN = 0.7*0.92+0.06 = 0.704", approx(sp("SHEN"), 0.704));

// p=0.06 地板（accuracy=0, assist=false, SHEN）
ok("sp floor = 0.06", approx(G.CaptureEngine.successProbability(0, "SHEN", false, 1.0), 0.06));

// p=1.0 必成（captureMul 抬高后 clamp）
ok("sp clamp to 1.0 with captureMul=2", G.CaptureEngine.successProbability(1.0, "FAN", true, 2.0) === 1.0);

// resolve：p=1.0 必成；p=0.06 双次独立 rng
var seq = function (arr) { var i = 0; return function () { return arr[i++]; }; };
var r1 = G.CaptureEngine.resolve(1.0, "FAN", true, seq([0, 0]), 2.0);
ok("resolve p=1.0 -> success", r1.success === true && approx(r1.probability, 1.0));
var r2 = G.CaptureEngine.resolve(0, "SHEN", false, seq([0.0, 0.0]), 1.0);
ok("resolve p=0.06 独立 rng: success=true, struggled=false", r2.success === true && r2.struggled === false);
var r3 = G.CaptureEngine.resolve(0, "SHEN", false, seq([0.5, 0.5]), 1.0);
ok("resolve p=0.06 独立 rng: success=false, struggled=true", r3.success === false && r3.struggled === true);

// ───────── GachaEngine ─────────
ok("gacha 单价 60/十连540/重复10", G.GachaEngine.SINGLE_LINGYU === 60 && G.GachaEngine.TEN_LINGYU === 540 && G.GachaEngine.DUPLICATE_YAODAN === 10);
// 分布抽样（大样本）
(function () {
  var st = { sinceShen: 0 }; var cnt = { LING: 0, XIAN: 0, SHEN: 0 };
  for (var i = 0; i < 20000; i++) { var r = G.GachaEngine.pull(st, Math.random); cnt[r.rarity]++; }
  var tot = 20000;
  ok("gacha 分布 灵≈40%", approx(cnt.LING / tot, 0.40, 0.03), (cnt.LING / tot).toFixed(3));
  ok("gacha 分布 仙≈45%", approx(cnt.XIAN / tot, 0.45, 0.03), (cnt.XIAN / tot).toFixed(3));
  ok("gacha 分布 神≈15%", approx(cnt.SHEN / tot, 0.15, 0.03), (cnt.SHEN / tot).toFixed(3));
})();
// 硬保底：连续 89 次非神，第 90 次必神
(function () {
  var st = { sinceShen: 0 };
  for (var i = 0; i < 89; i++) { var r = G.GachaEngine.pull(st, function () { return 0.99; }); if (r === "SHEN") {} }
  // sinceShen 此时应为 89
  ok("gacha 硬保底前 sinceShen=89", st.sinceShen === 89, "sinceShen=" + st.sinceShen);
  var last = G.GachaEngine.pull(st, function () { return 0.0; });
  ok("gacha 第90抽(硬保底)必神", last.rarity === "SHEN");
})();
// 软保底：第 50 抽起神率提升（第 49 抽后 sinceShen=49，第50抽 shenRate=0.15+1*0.02）
(function () {
  var st = { sinceShen: 0 };
  for (var i = 0; i < 49; i++) G.GachaEngine.pull(st, function () { return 0.99; });
  // 此时 sinceShen=49；下一次 rollRarity：pity=49 >= SOFT_PITY_START(49) → shenRate=0.15+1*0.02=0.17
  var pityRate = G.GachaEngine.rollRarity(st, function () { return 0.16; });
  ok("gacha 软保底第50抽神率=0.17生效", pityRate === "SHEN");
})();

// ───────── CultivationEngine ─────────
ok("expForLevel(1)=100", G.CultivationEngine.expForLevel(1) === 100);
ok("expForLevel(2)=125", G.CultivationEngine.expForLevel(2) === 125);
ok("levelCap bt0=30", G.CultivationEngine.levelCap({ breakthrough: 0 }) === 30);
ok("levelCap bt5=55", G.CultivationEngine.levelCap({ breakthrough: 5 }) === 55);
ok("breakthroughCost(0)=20", G.CultivationEngine.breakthroughCost(0) === 20);
ok("breakthroughCost(4)=80", G.CultivationEngine.breakthroughCost(4) === 80);
ok("EVOLVE_LEVEL_REQ=25", G.CultivationEngine.EVOLVE_LEVEL_REQ === 25);
ok("TRIBULATE_COST base=200", G.CultivationEngine.TRIBULATE_COST_YAODAN === 200);
// statsAtLevel：龙(SHEN) level1
(function () {
  var mon = G.monsterById("yao_long");
  var s = G.statsAtLevel(mon, 1);
  ok("龙 Lv1 atk=floor(88*1.5)=132", s.atk === 132, s.atk);
  ok("龙 Lv1 def=floor(72*1.5)=108", s.def === 108, s.def);
  ok("龙 Lv1 hp=floor(170*1.5)=255", s.hp === 255, s.hp);
  ok("龙 Lv1 spd=floor(80*1.5)=120 (不随等级系数)", s.spd === 120, s.spd);
})();
// effectiveStats：含突破+渡劫+天赋
(function () {
  var inst = { instanceId: "x", defId: "yao_long", level: 1, exp: 0, breakthrough: 1, tribulations: 0, talents: { ATTACK: 3 } };
  var eff = G.CultivationEngine.effectiveStats(inst);
  // base 龙 Lv1: 132/108/255/120; statMult=1+1*0.08=1.08; tribMult=1; atkMult=1+0.06*3=1.18
  var expAtk = Math.floor(132 * 1.08 * 1.18);
  ok("effectiveStats atk 含突破1.08×天赋1.18", eff.atk === expAtk, eff.atk + " vs " + expAtk);
  ok("effectiveStats spd 含突破1.08", eff.spd === Math.floor(120 * 1.08), eff.spd);
})();
// gainExp 升级
(function () {
  var inst = { instanceId: "x", defId: "yao_she", level: 1, exp: 0, breakthrough: 0, tribulations: 0, talents: {} };
  var after = G.CultivationEngine.gainExp(inst, 100 + 125); // 升到 3 级
  ok("gainExp 100+125 -> Lv3", after.level === 3, "level=" + after.level);
})();
// maxTribulations
ok("maxTrib FAN=3", G.CultivationEngine.maxTribulations("FAN") === 3);
ok("maxTrib SHEN=0", G.CultivationEngine.maxTribulations("SHEN") === 0);

// ───────── TalentEngine ─────────
ok("talentCap bt0=3", G.TalentEngine.talentCap({ breakthrough: 0 }) === 3);
ok("talentCap bt2=5", G.TalentEngine.talentCap({ breakthrough: 2 }) === 5);

// ───────── SocialEngine / Sect ─────────
ok("STRONGHOLD_ATTACK_BONUS=1.10", G.SocialEngine.STRONGHOLD_ATTACK_BONUS === 1.10);
ok("CONTRIBUTION_PER_CAPTURE=50", G.SectTechEngine.CONTRIBUTION_PER_CAPTURE === 50);
ok("sect nodes = 10", G.SectTechEngine.NODES.length === 10);

// ───────── LocationEngine ─────────
ok("4 灵脉节点", G.LocationEngine.nodeCoordinates().length === 4);
ok("STRONGHOLD_NODES = 4", G.LocationEngine.STRONGHOLD_NODES.length === 4);
(function () {
  var ctx = { lat: 31.23, lng: 121.47, hourOfDay: 12, ts: 1000, prevLat: 31.23, prevLng: 121.47, prevTs: 0 };
  var res = G.LocationEngine.spawnIntegrity(ctx);
  ok("GPS 正常（无位移）", res.tier === "NORMAL");
  // 瞬移：>5000m 内 180s -> REJECTED（0.13°≈14.4km）
  var r2 = G.LocationEngine.spawnIntegrity({ lat: 31.23, lng: 121.47, ts: 1000, prevLat: 31.10, prevLng: 121.47, prevTs: 900 });
  ok("GPS 瞬移 -> REJECTED", r2.tier === "REJECTED");
  // 速度过快：30km/h 以上但非瞬移 -> DEGRADED（~0.008°≈889m / 100s ≈ 32km/h）
  var r3 = G.LocationEngine.spawnIntegrity({ lat: 31.23, lng: 121.47, ts: 1000, prevLat: 31.222, prevLng: 121.47, prevTs: 900 });
  ok("GPS 速度过快 -> DEGRADED", r3.tier === "DEGRADED");
})();

// ───────── BattleEngine ─────────
ok("levelCoef(1)=1", approx(G.BattleEngine.levelCoef(1), 1.0));
ok("levelCoef(11)=1.2", approx(G.BattleEngine.levelCoef(11), 1.2));
(function () {
  var atkInst = { instanceId: "a", defId: "yao_long", level: 1, exp: 0, breakthrough: 0, tribulations: 0, talents: {} };
  var defInst = { instanceId: "d", defId: "jing_saozhou", level: 1, exp: 0, breakthrough: 0, tribulations: 0, talents: {} };
  var a = G.makeCombatant(atkInst), d = G.makeCombatant(defInst);
  // s_hantan WATER power90 crit0.08
  var skill = G.SKILLS["s_hantan"];
  // rng=[0,0] -> rnd=0.9, crit=true
  var res = G.BattleEngine.damage(a, d, skill, seq([0, 0]), 1.0, 1.0, 1.0);
  // (132/38)*90*1.0*1.3*1.5*0.9 = 548.68 -> floor 548
  ok("battle damage 期望=548 crit", res.damage === 548 && res.crit === true, "dmg=" + res.damage + " crit=" + res.crit);
  // rng=[0.5,0.5] -> rnd=0.95, crit=false(0.5<0.13 false)
  var res2 = G.BattleEngine.damage(a, d, skill, seq([0.5, 0.5]), 1.0, 1.0, 1.0);
  // (132/38)*90*1.0*1.3*1.0*0.95 = 386.10 -> floor 386
  ok("battle damage 期望=386 no-crit", res2.damage === 386 && res2.crit === false, "dmg=" + res2.damage);
})();

// ───────── FormationEngine ─────────
(function () {
  var team = ["yao_long", "jing_saozhou", "yao_hu"].map(G.monsterById); // 水/水/火 -> 水2火1
  var syn = G.FormationEngine.evaluate(team);
  ok("阵容 阴阳调和(水≥2且火≥2) 需火≥2", !syn.some(function (s) { return s.id === "YINYANG"; }));
  var team2 = ["yao_long", "yao_jiao", "yao_jiuwei", "niao_jinwu"].map(G.monsterById); // 水,水,火,火
  var syn2 = G.FormationEngine.evaluate(team2);
  ok("阵容 阴阳调和 激活(水≥2且火≥2)", syn2.some(function (s) { return s.id === "YINYANG"; }));
  ok("teamMul 上限 1.6", G.FormationEngine.teamAttackMultiplier([{ atkMul: 0.12 }, { atkMul: 0.12 }, { atkMul: 0.12 }, { atkMul: 0.12 }, { atkMul: 0.12 }]) === 1.6);
})();

// ───────── Player 端到端 ─────────
(function () {
  var p = G.makePlayer();
  p.addStarter("yao_hu");
  ok("玩家初始货币 灵玉600", p.raw.lingyu === 600);
  // 收妖成功（accuracy=1, assist）
  var cap = p.attemptCapture("jing_saozhou", 1.0, true, seq([0, 0]));
  ok("收妖成功入账", cap.success === true && p.raw.codex.has("jing_saozhou"));
  // 抽卡扣灵玉
  var before = p.raw.lingyu;
  var g = p.praySingle(seq([0.99, 0.99]));
  ok("单抽扣 60 灵玉", p.raw.lingyu === before - 60 && !g.error);
  // 据点占领
  var today = "2026-08-18";
  var sh = p.captureStronghold("城隍桥", today);
  ok("据点占领 +50 贡献", sh.ok && p.raw.contribution === 50);
  // 据点加成 1.10 体现在 teamCombatants
  var tc = p.teamCombatants();
  ok("占领后 strongholdBonus=1.10", approx(tc.strongholdBonus, 1.10));
  // 任务进度
  var qs = p.questStatus();
  var dcatch = qs.filter(function (s) { return s.def.id === "d_catch3"; })[0];
  ok("今日收妖进度=1/3", dcatch.current === 1 && dcatch.target === 3);
})();

// ───────── EquipmentEngine（五路径 + 加成） ─────────
ok("EQUIPMENTS 解析=9", G.EQUIPMENTS.length === 9);
var EE = G.EquipmentEngine;
var swordDef = G.equipDefById("eq_sword");
ok("maxTier FAN/LING/XIAN/SHEN = 1/2/3/4",
  EE.maxTier("FAN") === 1 && EE.maxTier("LING") === 2 && EE.maxTier("XIAN") === 3 && EE.maxTier("SHEN") === 4);
ok("maxLevelOf eq_sword tier1=10", EE.maxLevelOf(swordDef, { tier: 1 }) === 10);
ok("maxLevelOf eq_sword tier2=20", EE.maxLevelOf(swordDef, { tier: 2 }) === 20);
ok("effectivePerLevel tier1=0.12", approx(EE.effectivePerLevel(swordDef, { tier: 1 }), 0.12));
ok("effectivePerLevel tier2=0.15", approx(EE.effectivePerLevel(swordDef, { tier: 2 }), 0.15));
ok("starMult stars1=1.0", approx(EE.starMult({ stars: 1 }), 1.0));
ok("starMult stars5=1.4", approx(EE.starMult({ stars: 5 }), 1.4));
var oe1 = { equipId: "eq_sword", instanceId: "e1", level: 1, stars: 1, tier: 1, substats: [] };
ok("bonus L1 亲和 atk=int(18*1.2)=21", EE.bonus(oe1, swordDef, true).atk === 21);
var oe2 = { equipId: "eq_sword", instanceId: "e2", level: 2, stars: 1, tier: 1, substats: [] };
ok("bonus L2 亲和 atk=int(18*1.12*1.2)=24", EE.bonus(oe2, swordDef, true).atk === 24);
var oe3 = { equipId: "eq_sword", instanceId: "e3", level: 1, stars: 2, tier: 1, substats: [] };
ok("bonus stars2 亲和 atk=int(18*1.1*1.2)=23", EE.bonus(oe3, swordDef, true).atk === 23);
ok("bonus L1 无亲和 atk=18", EE.bonus(oe1, swordDef, false).atk === 18);
ok("subStatsBonus ATK+5 HP+10", (function () {
  var b = EE.subStatsBonus({ substats: [{ stat: "ATK", value: 5 }, { stat: "HP", value: 10 }] });
  return b.atk === 5 && b.hp === 10;
})());
var monMetal = { element: "METAL" };
ok("totalBonus METAL+青锋剑(亲和atk21)+副ATK5 = 26",
  EE.totalBonus(monMetal, [{ equipId: "eq_sword", instanceId: "x", level: 1, stars: 1, tier: 1, substats: [{ stat: "ATK", value: 5 }] }]).atk === 26);
var monMetal2 = { element: "METAL" };
ok("totalBonus 两件 玄龟甲(无亲和) def16 hp30", (function () {
  var t = EE.totalBonus(monMetal2, [
    { equipId: "eq_sword", instanceId: "a", level: 1, stars: 1, tier: 1, substats: [] },
    { equipId: "eq_armor", instanceId: "b", level: 1, stars: 1, tier: 1, substats: [] },
  ]);
  return t.def === 16 && t.hp === 30;
})());
// 升星成本递增 + 前置
[1, 2, 3, 4].forEach(function (s) { ok("starCost stars" + s + "=" + (50 * s), EE.starCost({ stars: s }) === 50 * s); });
ok("canStarUp stars5=false", EE.canStarUp({ stars: 5 }) === false);
ok("starUp stars3→4", EE.starUp({ stars: 3 }).stars === 4);
// 突破成本递增 + 前置（灵阶 maxTier=2）
[1, 2].forEach(function (t) { ok("tierCost tier" + t + "=" + (80 * t), EE.tierCost({ tier: t }) === 80 * t); });
ok("canBreakthrough 灵阶 tier2=false", EE.canBreakthrough({ tier: 2 }, swordDef) === false);
ok("breakthrough tier1→2", EE.breakthrough({ tier: 1 }, swordDef).tier === 2);
// 淬炼确定性 + 成本递增
(function () {
  var o = { equipId: "eq_sword", instanceId: "e", level: 1, stars: 1, tier: 1, substats: [] };
  var costs = [], cnt = 0;
  while (EE.canTemper(o) && cnt < 30) { costs.push(EE.temperCost(o)); o = EE.temper(o); cnt++; }
  ok("淬炼填满 4 条副属性", o.substats.length === 4, "n=" + o.substats.length);
  ok("淬炼顺序 ATK→DEF→HP→SPD", o.substats.map(function (s) { return s.stat; }).join(",") === "ATK,DEF,HP,SPD");
  ok("每条副属性封顶 15", o.substats.every(function (s) { return s.value === 15; }));
  ok("淬炼成本严格递增", costs.every(function (c, i) { return i === 0 || c > costs[i - 1]; }));
  ok("temperCost 公式 = 30*(used+1)", EE.temperCost(o) === 30 * (EE.temperUsed(o) + 1));
  ok("满后 canTemper=false", EE.canTemper(o) === false);
})();
// 继承合并较优
(function () {
  var src = { equipId: "eq_sword", instanceId: "s", level: 3, stars: 2, tier: 1, substats: [{ stat: "ATK", value: 9 }] };
  var dst = { equipId: "eq_armor", instanceId: "d", level: 5, stars: 1, tier: 2, substats: [{ stat: "HP", value: 6 }] };
  var inh = EE.inherit(src, dst);
  ok("继承取较优 level/stars/tier", inh.level === 5 && inh.stars === 2 && inh.tier === 2);
  ok("继承合并 src ATK=9", inh.substats.some(function (s) { return s.stat === "ATK" && s.value === 9; }));
  ok("继承保留 dst HP=6", inh.substats.some(function (s) { return s.stat === "HP" && s.value === 6; }));
})();
ok("canForge eq_sword L10=false", EE.canForge({ equipId: "eq_sword", tier: 1, level: 10 }) === false);
ok("canForge eq_sword L1=true", EE.canForge({ equipId: "eq_sword", tier: 1, level: 1 }) === true);

// ───────── FusionEngine（炼妖融合） ─────────
ok("FUSE_COST_YAODAN=30 / LEVEL_BONUS=3", G.FusionEngine.FUSE_COST_YAODAN === 30 && G.FusionEngine.LEVEL_BONUS === 3);
ok("recipeOutputs 含 gui_xuanwu", G.FusionEngine.recipeOutputs().indexOf("gui_xuanwu") >= 0);
ok("canFuse 同实例=false", G.FusionEngine.canFuse({ instanceId: "i1" }, { instanceId: "i1" }, 30) === false);
ok("canFuse 妖丹不足=false", G.FusionEngine.canFuse({ instanceId: "i1" }, { instanceId: "i2" }, 10) === false);
ok("canFuse 正常=true", G.FusionEngine.canFuse({ instanceId: "i1" }, { instanceId: "i2" }, 30) === true);
(function () {
  var FE = G.FusionEngine;
  ok("plan 同实例=null", FE.plan({ instanceId: "i1" }, { instanceId: "i1" }) === null);
  // 配方：蛟 + 灵龟 → 玄武
  var r2 = FE.plan({ instanceId: "a", defId: "yao_jiao", level: 1, breakthrough: 0 }, { instanceId: "b", defId: "gui_linggui", level: 1, breakthrough: 0 });
  ok("融合配方 蛟+灵龟→玄武", r2.newDefId === "gui_xuanwu");
  // 通用回退：两凡妖，基础属性高者胜
  var r1 = FE.plan({ instanceId: "a", defId: "yao_she", level: 1, breakthrough: 0 }, { instanceId: "b", defId: "yao_hu", level: 1, breakthrough: 0 });
  ok("融合回退 取属性更高者(yao_hu)", r1.newDefId === "yao_hu");
  ok("融合回退 新等级=1+3=4", r1.newLevel === 4);
  ok("融合回退 突破=min=0", r1.newBreakthrough === 0);
  // 等级/突破：双 Lv20 bt1 → 新等级 min(23,35)=23，突破=1
  var r3 = FE.plan({ instanceId: "a", defId: "yao_long", level: 20, breakthrough: 1 }, { instanceId: "b", defId: "gui_xuanwu", level: 20, breakthrough: 1 });
  ok("融合 双Lv20bt1 新等级=23", r3.newLevel === 23);
  ok("融合 双Lv20bt1 突破=1", r3.newBreakthrough === 1);
  ok("融合 技能并集≤4", r3.newSkills.length <= 4 && r3.newSkills.length >= 1);
  // 等级上限封顶：双 Lv30 bt0 → 33 但 cap=30 → 30
  var r4 = FE.plan({ instanceId: "a", defId: "yao_long", level: 30, breakthrough: 0 }, { instanceId: "b", defId: "gui_xuanwu", level: 30, breakthrough: 0 });
  ok("融合 等级上限封顶=30", r4.newLevel === 30);
})();

// ───────── Player 装备/融合 集成 ─────────
(function () {
  var p = G.makePlayer();
  p.addStarter("yao_hu");
  ok("新手获得 2 件起始装备", p.raw.equipment.length === 2, "n=" + p.raw.equipment.length);
  var sword = p.raw.equipment[0];
  var monId = p.raw.team[0];
  ok("装备穿戴成功", p.equipTo(monId, sword.instanceId).ok === true);
  var st = p.monsterStats(monId);
  ok("妖有效属性含装备加成(atk=养成+装备)", st.total.atk === st.cultivation.atk + st.equipment.atk);
  // 祭炼扣妖丹
  var yd = p.raw.yaodan;
  var fz = p.forgeEquip(sword.instanceId);
  ok("祭炼成功扣 10 妖丹", fz.ok && p.raw.yaodan === yd - 10 && p.findEquip(sword.instanceId).level === 2);
  // 升星扣灵玉
  var ly = p.raw.lingyu;
  var su = p.starUpEquip(sword.instanceId);
  ok("升星成功扣 50 灵玉", su.ok && p.raw.lingyu === ly - 50 && p.findEquip(sword.instanceId).stars === 2);
  // 融合：第二只妖
  p.addStarter("yao_she");
  var a = p.raw.team[0], b = p.raw.team[1];
  var yd2 = p.raw.yaodan;
  var fu = p.fuseMonsters(a, b);
  ok("融合成功扣 30 妖丹", fu.ok && p.raw.yaodan === yd2 - 30);
  ok("融合移除两原实例", p.raw.owned[a] === undefined && p.raw.owned[b] === undefined);
  ok("融合新增 1 个实例", Object.keys(p.raw.owned).length === 1);
})();

// ───────── ReputationEngine（GDD 十六·公平与稳健） ─────────
ok("REP 常量 MIN=0/MAX=100/RES<60/OBS<80",
  SH.ReputationEngine.MIN_SCORE===0 && SH.ReputationEngine.MAX_SCORE===100 &&
  SH.ReputationEngine.RESTRICTED_BELOW===60 && SH.ReputationEngine.OBSERVED_BELOW===80);
ok("REP 扣分表 T18/S12/U10",
  SH.ReputationEngine.PENALTY.TELEPORT===18 && SH.ReputationEngine.PENALTY.SPEED===12 && SH.ReputationEngine.PENALTY.UNKNOWN===10);
ok("REP tier 0/59 → 受限", SH.ReputationEngine.tier(0)==="RESTRICTED" && SH.ReputationEngine.tier(59)==="RESTRICTED");
ok("REP tier 60/79 → 观察（临界60不落受限）", SH.ReputationEngine.tier(60)==="OBSERVED" && SH.ReputationEngine.tier(79)==="OBSERVED");
ok("REP tier 80/100 → 优秀（临界80不落观察）", SH.ReputationEngine.tier(80)==="EXCELLENT" && SH.ReputationEngine.tier(100)==="EXCELLENT");
ok("REP tier 越界 -20/999 被钳", SH.ReputationEngine.tier(-20)==="RESTRICTED" && SH.ReputationEngine.tier(999)==="EXCELLENT");
ok("REP tierLabel 受限/观察/优秀", SH.ReputationEngine.tierLabel(50)==="受限" && SH.ReputationEngine.tierLabel(70)==="观察" && SH.ReputationEngine.tierLabel(90)==="优秀");
ok("REP rareSpawnMul 观察0.7 其余1.0", approx(SH.ReputationEngine.rareSpawnMultiplier(70),0.7) && SH.ReputationEngine.rareSpawnMultiplier(100)===1.0 && SH.ReputationEngine.rareSpawnMultiplier(50)===1.0);
ok("REP canExploreLbs 受限禁 其余允", SH.ReputationEngine.canExploreLbs(50)===false && SH.ReputationEngine.canExploreLbs(70)===true && SH.ReputationEngine.canExploreLbs(90)===true);
ok("REP applyPenalty 100-18=82（瞬移）", SH.ReputationEngine.applyPenalty(100,"TELEPORT")===82);
ok("REP applyPenalty 100-12=88（超速）", SH.ReputationEngine.applyPenalty(100,"SPEED")===88);
ok("REP applyPenalty 100-10=90（未知）", SH.ReputationEngine.applyPenalty(100,"UNKNOWN")===90);
ok("REP applyPenalty 5-18 钳0（不下溢）", SH.ReputationEngine.applyPenalty(5,"TELEPORT")===0);
ok("REP applyPenalty 未知kind默认扣10", SH.ReputationEngine.applyPenalty(50,"NONE")===40);
ok("REP dailyRecover 99→100 100→100（封顶）", SH.ReputationEngine.dailyRecover(99)===100 && SH.ReputationEngine.dailyRecover(100)===100);
ok("REP dailyRecover 0→1 59→60", SH.ReputationEngine.dailyRecover(0)===1 && SH.ReputationEngine.dailyRecover(59)===60);

// ───────── ComboEngine（GDD 七·连携组合技） ─────────
ok("combo 青蛇→缚妖甘露（kunyao+ganlin）", SH.ComboEngine.availableCombos(SH.monsterById("yao_she").skillIds).some(function(c){return c.id==="fuyaoganlu";}));
ok("combo 铜镜精→狂战连斩（liekong+wanjian）", SH.ComboEngine.availableCombos(SH.monsterById("jing_tongjing").skillIds).some(function(c){return c.id==="kuangzhanlianzhan";}));
ok("combo 九尾狐→烈焰焚天（sanmei+fentian）", SH.ComboEngine.availableCombos(SH.monsterById("yao_jiuwei").skillIds).some(function(c){return c.id==="lianyanfentian";}));
ok("combo 蛟→玄冰寒壁（hantan+xuanbing）", SH.ComboEngine.availableCombos(SH.monsterById("yao_jiao").skillIds).some(function(c){return c.id==="xuanbinghanbi";}));
ok("combo 扫帚精(仅kunyao) 无可触发连携", SH.ComboEngine.availableCombos(SH.monsterById("jing_saozhou").skillIds).length===0);
ok("combo damageMultiplier 狂战连斩=1.3", approx(SH.ComboEngine.damageMultiplier(SH.ComboEngine.availableCombos(["s_liekong","s_wanjian"])),1.3));
ok("combo damageMultiplier 双DAMAGE封顶1.56", approx(SH.ComboEngine.damageMultiplier(SH.ComboEngine.availableCombos(["s_liekong","s_wanjian","s_hantan","s_xuanbing"])),1.56));
ok("combo healPercent 缚妖甘露=0.25", approx(SH.ComboEngine.healPercent(SH.ComboEngine.availableCombos(["s_kunyao","s_ganlin"])),0.25));
// 连携施加（复刻 BattleSession.step：整段伤害 × combo.value）
(function(){
  var j = SH.makeCombatant({instanceId:"j",defId:"yao_jiao",level:1,exp:0,breakthrough:0,tribulations:0,talents:{}});
  var d = SH.makeCombatant({instanceId:"d",defId:"jing_saozhou",level:1,exp:0,breakthrough:0,tribulations:0,talents:{}});
  var sk = SH.SKILLS["s_hantan"];
  var base = SH.BattleEngine.damage(j,d,sk,seq([0,0]),1.0,1.0,1.0).damage;
  var combo = j.combos[0];
  ok("连携施加：玄冰寒壁 floor(base×1.2)", combo.kind==="DAMAGE_MUL" && combo.value===1.2 && Math.max(1,Math.floor(base*1.2))===Math.floor(base*1.2));
})();
// 回血连携（缚妖甘露 25%）
(function(){
  var s = SH.makeCombatant({instanceId:"s",defId:"yao_she",level:1,exp:0,breakthrough:0,tribulations:0,talents:{}});
  var combo = s.combos[0];
  ok("连携 heal：缚妖甘露 25% 回血 = floor(maxHp×0.25)", combo.kind==="HEAL_PERCENT" && combo.value===0.25 && Math.floor(s.maxHp*combo.value)===Math.floor(s.maxHp*0.25));
})();

// ───────── WeatherEngine（GDD 十九·天气地形战斗） ─────────
ok("weather CLEAR 无偏置", SH.WeatherEngine.weatherAttackMul("WATER","CLEAR")===1.0);
ok("weather RAIN 水×1.3", SH.WeatherEngine.weatherAttackMul("WATER","RAIN")===1.3);
ok("weather RAIN 火×0.8（压火）", SH.WeatherEngine.weatherAttackMul("FIRE","RAIN")===0.8);
ok("weather SNOW 火×0.7（严寒抑火）", SH.WeatherEngine.weatherAttackMul("FIRE","SNOW")===0.7);
ok("weather SNOW 水×1.2（寒水更锐）", SH.WeatherEngine.weatherAttackMul("WATER","SNOW")===1.2);
ok("weather WINDY 风×1.3", SH.WeatherEngine.weatherAttackMul("WIND","WINDY")===1.3);
ok("weather WINDY 土×0.8（风蚀土）", SH.WeatherEngine.weatherAttackMul("EARTH","WINDY")===0.8);
ok("weather 无关元素无偏置", SH.WeatherEngine.weatherAttackMul("METAL","RAIN")===1.0);
ok("terrain 木行在山林 ×1.2", SH.WeatherEngine.terrainAttackMul("WOOD","WOOD")===1.2);
ok("terrain 水行在水域/东海 ×1.2", SH.WeatherEngine.terrainAttackMul("WATER","WATER")===1.2);
ok("terrain 木行在水域无偏置", SH.WeatherEngine.terrainAttackMul("WOOD","WATER")===1.0);
ok("terrain 无地形 ×1.0", SH.WeatherEngine.terrainAttackMul("FIRE",null)===1.0);
ok("env 雨+水域水行 ×1.56", approx(SH.WeatherEngine.envMultiplier("WATER","RAIN","WATER"),1.56));
ok("env 晴+山林木行 ×1.2", approx(SH.WeatherEngine.envMultiplier("WOOD","CLEAR","WOOD"),1.2));

// ───────── FatigueEngine（GDD 十六·公平与稳健·防脚本） ─────────
ok("FAT MAX=100 SOFT=60", SH.FatigueEngine.MAX_FATIGUE===100 && SH.FatigueEngine.SOFT_CAP===60);
ok("incomeMul <60 充沛→×1.0", SH.FatigueEngine.incomeMul(0)===1.0 && SH.FatigueEngine.incomeMul(59)===1.0);
ok("incomeMul 60..99 疲劳→×0.5", SH.FatigueEngine.incomeMul(60)===0.5 && SH.FatigueEngine.incomeMul(99)===0.5);
ok("incomeMul 100 透支→×0.0", SH.FatigueEngine.incomeMul(100)===0.0);
ok("addFatigue 钳[0,100]", SH.FatigueEngine.addFatigue(50,20)===70 && SH.FatigueEngine.addFatigue(90,20)===100 && SH.FatigueEngine.addFatigue(10,-50)===0 && SH.FatigueEngine.addFatigue(-10,5)===0);
ok("label 充沛/疲劳减半/透支封顶", SH.FatigueEngine.label(59)==="充沛" && SH.FatigueEngine.label(60)==="疲劳·收益减半" && SH.FatigueEngine.label(100)==="透支·收益封顶");
ok("resetDaily=0", SH.FatigueEngine.resetDaily()===0);

// ───────── Player 信誉/疲劳 集成 ─────────
(function(){
  var p = SH.makePlayer(); p.addStarter("yao_hu");
  ok("玩家初始 信誉100 疲劳0", p.raw.rep===100 && p.raw.fatigue===0);
  ok("penalizeRep TELEPORT→82", p.penalizeRep("TELEPORT")===82);
  ok("repTier 82 → 优秀/优秀", p.repTier()==="EXCELLENT" && p.repLabel()==="优秀");
  p.raw.rep = 50;
  ok("repTier 50 → 受限/受限", p.repTier()==="RESTRICTED" && p.repLabel()==="受限");
  p.raw.rep = 100;
  ok("addFatigue 4 → incomeMul 1.0 充沛", p.addFatigue(4)===4 && p.incomeMul()===1.0 && p.fatigueLabel()==="充沛");
  ok("addFatigue 至100 → incomeMul 0.0", p.addFatigue(200)===100 && p.incomeMul()===0.0);
  ok("recoverRepDaily 82→83", (p.raw.rep=82, p.recoverRepDaily()===83));
})();

// ───────── RankEngine（GDD 二十·竞技与荣誉·段位天梯） ─────────
ok("Rank TIERS 5 段顺序 炼气→化神", SH.RankEngine.TIERS.length===5 && SH.RankEngine.TIERS[0]==="炼气" && SH.RankEngine.TIERS[4]==="化神");
ok("Rank 常量 100/20/15", SH.RankEngine.POINTS_TO_PROMOTE===100 && SH.RankEngine.WIN_POINTS===20 && SH.RankEngine.LOSS_POINTS===15);
ok("Rank tierIndex 筑基=1 化神=4 未知=0", SH.RankEngine.tierIndex("筑基")===1 && SH.RankEngine.tierIndex("化神")===4 && SH.RankEngine.tierIndex("未知段")===0);
ok("Rank tierName 0=炼气 4=化神 越界夹取", SH.RankEngine.tierName(0)==="炼气" && SH.RankEngine.tierName(4)==="化神" && SH.RankEngine.tierName(-1)==="炼气" && SH.RankEngine.tierName(9)==="化神");
ok("Rank 胜 炼气80→升筑基(满100升段) 余0", (function(){var r=SH.RankEngine.applyResult(0,80,true); return r.tierIndex===1 && r.points===0 && r.promoted;})());
ok("Rank 胜 筑基90→升金丹 余10", (function(){var r=SH.RankEngine.applyResult(1,90,true); return r.tierIndex===2 && r.points===10;})());
ok("Rank 胜 化神80→封顶99 不升段", (function(){var r=SH.RankEngine.applyResult(4,80,true); return r.tierIndex===4 && r.points===99 && !r.promoted;})());
ok("Rank 负 炼气10→保底0 不降段", (function(){var r=SH.RankEngine.applyResult(0,10,false); return r.tierIndex===0 && r.points===0 && !r.demoted;})());
ok("Rank 负 筑基5→降炼气 余90", (function(){var r=SH.RankEngine.applyResult(1,5,false); return r.tierIndex===0 && r.points===90 && r.demoted;})());
ok("Rank 负 金丹0→降筑基 余85", (function(){var r=SH.RankEngine.applyResult(2,0,false); return r.tierIndex===1 && r.points===85 && r.demoted;})());
ok("Rank 奖励 炼气=无", SH.RankEngine.rankReward(0).incense===0 && SH.RankEngine.rankReward(0).lingyu===0 && SH.RankEngine.rankReward(0).yaodan===0);
ok("Rank 奖励 筑基=香火30", SH.RankEngine.rankReward(1).incense===30 && SH.RankEngine.rankReward(1).lingyu===0);
ok("Rank 奖励 金丹=灵玉30", SH.RankEngine.rankReward(2).lingyu===30 && SH.RankEngine.rankReward(2).incense===0);
ok("Rank 奖励 元婴=香火60+灵玉30", SH.RankEngine.rankReward(3).incense===60 && SH.RankEngine.rankReward(3).lingyu===30);
ok("Rank 奖励 化神=灵玉80+妖丹10", SH.RankEngine.rankReward(4).lingyu===80 && SH.RankEngine.rankReward(4).yaodan===10);
ok("Rank 性质 任意输入→输出恒合法(段∈[0,4] 积分∈[0,99])", (function(){
  var a=SH.RankEngine.applyResult(-5,200,true), b=SH.RankEngine.applyResult(9,-50,false);
  return a.tierIndex>=0 && a.tierIndex<=4 && a.points>=0 && a.points<=99 && b.tierIndex>=0 && b.tierIndex<=4 && b.points>=0 && b.points<=99;
})());

// ───────── RankBanPickEngine（GDD 二十·竞技与荣誉·选将） ─────────
(function(){
  var R = SH.RankBanPickEngine, A = R.Side.A, B = R.Side.B, DR = R.DraftResult;
  var ids = Object.keys(SH.MONSTERS);
  var id1 = ids[0], id2 = ids[1], id3 = ids[2], id4 = ids[3], id5 = ids[4];
  ok("BanPick 常量 BAN=2 PICK=3", R.BAN_PER_SIDE===2 && R.PICK_PER_SIDE===3);
  ok("ban 非法妖→INVALID_MONSTER", R.ban({bannedA:[],bannedB:[],pickedA:[],pickedB:[]}, A, "nope_x")[1]===DR.INVALID_MONSTER);
  var st = { bannedA: [], bannedB: [], pickedA: [], pickedB: [] };
  var r = R.ban(st, A, id1);
  ok("ban 有效妖→OK (bannedA=1)", r[1]===DR.OK && r[0].bannedA.length===1); st = r[0];
  r = R.ban(st, A, id1);
  ok("ban 同妖重复→ALREADY_BANNED (state 不变)", r[1]===DR.ALREADY_BANNED && r[0]===st); st = r[0];
  r = R.ban(st, A, id2);
  ok("ban 第二妖→OK (bannedA=2)", r[1]===DR.OK && r[0].bannedA.length===2); st = r[0];
  r = R.ban(st, A, id3);
  ok("ban 超限→BAN_LIMIT", r[1]===DR.BAN_LIMIT);
  r = R.pick(st, A, id1);
  ok("pick 被ban妖→PICK_BANNED", r[1]===DR.PICK_BANNED);
  r = R.pick(st, A, id3);
  ok("pick 可用妖→OK (pickedA=1)", r[1]===DR.OK && r[0].pickedA.length===1); st = r[0];
  r = R.pick(st, A, id3);
  ok("pick 重复→ALREADY_PICKED", r[1]===DR.ALREADY_PICKED); st = r[0];
  r = R.pick(st, A, id4);
  ok("pick id4→OK", r[1]===DR.OK); st = r[0];
  r = R.pick(st, A, id5);
  ok("pick id5→OK (2/3)", r[1]===DR.OK); st = r[0];
  r = R.pick(st, A, ids[10]);
  ok("pick 超限→PICK_LIMIT", r[1]===DR.PICK_LIMIT);
  ok("availablePool 随 ban 缩减 = 总-1", R.availablePool({bannedA:[id1],bannedB:[],pickedA:[],pickedB:[]}).length === ids.length - 1);
  ok("isComplete 未完成=false", R.isComplete(st) === false);
  ok("isTeamValid 合法队=true", R.isTeamValid(st, A, [id3,id4,id5]) === true);
  ok("isTeamValid 含ban妖=false", R.isTeamValid(st, A, [id1,id4,id5]) === false);
  ok("isTeamValid 重复=false", R.isTeamValid(st, A, [id3,id3,id4]) === false);
  ok("isTeamValid 超3=false", R.isTeamValid(st, A, [id3,id4,id5,ids[10]]) === false);
})();

// ───────── Player 排位 集成 ─────────
(function(){
  var p = SH.makePlayer();
  ok("玩家初始 段位 炼气0 积分0", p.raw.rankTierIndex===0 && p.raw.rankPoints===0 && p.rankTierName()==="炼气");
  var res = p.applyRankResult(true);
  ok("排位胜 炼气0→积分20 未升段", res.tierIndex===0 && res.points===20 && !res.promoted);
  p.raw.rankTierIndex=0; p.raw.rankPoints=80;
  var r2 = p.applyRankResult(true);
  ok("排位胜 炼气80→升筑基 余0 奖励香火30(50→80)", r2.tierIndex===1 && r2.points===0 && r2.promoted && p.raw.incense===80);
  p.raw.rankTierIndex=1; p.raw.rankPoints=5;
  var r3 = p.applyRankResult(false);
  ok("排位负 筑基5→降炼气 余90 无奖励(incense不变)", r3.tierIndex===0 && r3.points===90 && r3.demoted && p.raw.incense===80);
  p.raw.rankTierIndex=4; p.raw.rankPoints=80;
  var r4 = p.applyRankResult(true);
  ok("排位胜 化神80→封顶99 不升段", r4.tierIndex===4 && r4.points===99 && !r4.promoted);
})();

// ───────────────── DungeonEngine 镜像（GDD 十二·副本系统） ─────────────────
(function () {
  var DE = SH.DungeonEngine, FE = SH.FatigueEngine;
  // 库与 byId
  ok("Library 4 副本", DE.Library.all.length === 4);
  ok("byId 灵脉试炼", DE.Library.byId("d_shilian_lingmai").name === "灵脉试炼");
  ok("byId 未知→null", DE.Library.byId("nope") === null);
  // canEnter 边界（香火 / 每日限次）
  var shi = DE.Library.byId("d_shilian_lingmai");
  ok("canEnter 香火足未超限→true", DE.canEnter(shi, {}, 20) === true);
  ok("canEnter 香火不足→false", DE.canEnter(shi, {}, 10) === false);
  ok("canEnter 达每日限次→false", DE.canEnter(shi, { "d_shilian_lingmai": 5 }, 20) === false);
  // enter 区间（种子 rng，确定性）
  function seeded(seed) { var s = seed >>> 0; return function () { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }
  for (var i = 0; i < 50; i++) {
    var rw = DE.enter(shi, {}, seeded(42 + i));
    ok("enter 妖丹在[8,15]", rw.yaodan >= 8 && rw.yaodan <= 15);
    ok("enter 材料在[0,2]", rw.material >= 0 && rw.material <= 2);
    ok("enter 符纸布尔", typeof rw.talisman === "boolean");
    ok("enter 外观布尔", typeof rw.skin === "boolean");
  }
  var yiwen = DE.Library.byId("d_yiwen_taotie");
  var rw2 = DE.enter(yiwen, {}, seeded(1));
  ok("enter 妖王副本返回 boss id", rw2.bossMonsterId === "guai_taotie");
  ok("enter 妖王收服布尔", typeof rw2.bossCaptured === "boolean");

  // enterDungeon 接线（GameState.enterDungeon 镜像：扣香火/记次数/yaodan×倍率/疲劳/妖王收服）
  var p = SH.makePlayer();
  var inc0 = p.raw.incense, yao0 = p.raw.yaodan, fat0 = p.raw.fatigue;
  var realEnter = DE.enter;
  DE.enter = function (def) { return { yaodan: 20, material: 2, talisman: true, skin: false, bossCaptured: false, bossMonsterId: def.bossMonsterId }; };
  var r = p.enterDungeon("d_shilian_lingmai");
  ok("enterDungeon 返回 reward", r && r.yaodan === 20);
  ok("enterDungeon 扣香火=costIncense(20)", p.raw.incense === inc0 - 20);
  ok("enterDungeon 记当日次数=1", p.raw.dungeonEntered["d_shilian_lingmai"] === 1);
  ok("enterDungeon 疲劳+8(FATIGUE_DUNGEON)", p.raw.fatigue === fat0 + FE.FATIGUE_DUNGEON);
  ok("enterDungeon yaodan 充沛×1.0=+20", p.raw.yaodan === yao0 + 20);
  // 疲劳态减半
  p.raw.fatigue = 60;
  var yao1 = p.raw.yaodan, inc1 = p.raw.incense;
  p.enterDungeon("d_shilian_lingmai");
  ok("enterDungeon 疲劳态 yaodan 减半(+10)", p.raw.yaodan === yao1 + 10);
  ok("enterDungeon 疲劳态仍扣香火", p.raw.incense === inc1 - 20);
  // 透支态封顶
  p.raw.fatigue = 100;
  var yao2 = p.raw.yaodan;
  p.enterDungeon("d_shilian_lingmai");
  ok("enterDungeon 透支态 yaodan 封顶(+0)", p.raw.yaodan === yao2);
  // 次数耗尽→null
  p.raw.fatigue = 0; p.raw.dungeonEntered = { "d_shilian_lingmai": 5 };
  ok("enterDungeon 达限次→null", p.enterDungeon("d_shilian_lingmai") === null);
  // 妖王收服（补全图鉴 + 入队）
  DE.enter = function (def) { return { yaodan: 5, material: 0, talisman: false, skin: false, bossCaptured: true, bossMonsterId: "guai_nianshou" }; };
  p.raw.dungeonEntered = {}; p.raw.incense = 100; p.raw.fatigue = 0;
  var ownedBefore = Object.keys(p.raw.owned).length;
  p.enterDungeon("d_nianjie_chun");
  ok("enterDungeon 妖王收服补全图鉴", p.raw.codex.has("guai_nianshou") === true);
  ok("enterDungeon 妖王入队(+1 owned)", Object.keys(p.raw.owned).length === ownedBefore + 1);
  DE.enter = realEnter; // 还原
})();

// ───────── SecretRealmEngine 镜像（GDD 三·在家秘境） ─────────
(function () {
  var RE = SH.SecretRealmEngine;
  // 常量（与 shared/SecretRealmEngine.kt 一致）
  ok("秘境 SPAWN_COUNT=6", RE.SPAWN_COUNT === 6);
  ok("秘境 INCENSE_COST=20", RE.INCENSE_COST === 20);
  ok("秘境 ELEMENT_BIAS=3.0", RE.ELEMENT_BIAS === 3.0);
  ok("秘境 稀有度权重 凡60/灵30/仙8/神2",
     RE.RARITY_WEIGHT.FAN === 60.0 && RE.RARITY_WEIGHT.LING === 30.0 && RE.RARITY_WEIGHT.XIAN === 8.0 && RE.RARITY_WEIGHT.SHEN === 2.0);
  ok("秘境 4 主题", RE.THEMES.length === 4 && RE.THEMES.indexOf("DONGHAI") >= 0);
  // 主题 → 五行偏向（复用 LocationEngine.regionElementBias）
  ok("秘境 山林→木", RE.themeElementBias("SHANLIN") === "WOOD");
  ok("秘境 水域→水", RE.themeElementBias("SHUIYU") === "WATER");
  ok("秘境 古墓→土", RE.themeElementBias("GUMU") === "EARTH");
  ok("秘境 东海→水", RE.themeElementBias("DONGHAI") === "WATER");
  // pickTheme 返回合法主题
  ok("秘境 pickTheme(0.5) 合法主题", RE.THEMES.indexOf(RE.pickTheme(function(){return 0.5;})) >= 0);
  // generateSpawns：数量 + id 合法 + 确定性
  function seeded(seed) { var s = seed >>> 0; return function () { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }
  var sp = RE.generateSpawns("SHANLIN", RE.SPAWN_COUNT, seeded(7));
  ok("秘境 generateSpawns 数量=SPAWN_COUNT", sp.length === RE.SPAWN_COUNT);
  ok("秘境 generateSpawns id 均属妖池", sp.every(function (id) { return DATA.monsters.some(function (m) { return m.id === id; }); }));
  var spA = RE.generateSpawns("SHUIYU", 8, seeded(99));
  var spB = RE.generateSpawns("SHUIYU", 8, seeded(99));
  ok("秘境 generateSpawns 确定性（同 rng 同结果）", JSON.stringify(spA) === JSON.stringify(spB));
  // 五行偏向：木在山林显著高于水域（统计）
  function elemFrac(elem, theme, n, seed) {
    var rng = seeded(seed), cnt = 0;
    for (var i = 0; i < n; i++) {
      var id = RE.generateSpawns(theme, 1, rng)[0];
      var mon = DATA.monsters.filter(function (m) { return m.id === id; })[0];
      if (mon && mon.element === elem) cnt++;
    }
    return cnt / n;
  }
  var sb = elemFrac("WOOD", "SHANLIN", 20000, 21);
  var sn = elemFrac("WOOD", "SHUIYU", 20000, 21);
  ok("秘境偏向 木在山林显著高于水域", sb > sn * 1.25);
  // 稀有度偏向：凡+灵主导（统计）
  function rarFrac(rar, theme, n, seed) {
    var rng = seeded(seed), cnt = 0;
    for (var i = 0; i < n; i++) {
      var id = RE.generateSpawns(theme, 1, rng)[0];
      var mon = DATA.monsters.filter(function (m) { return m.id === id; })[0];
      if (mon && mon.rarity === rar) cnt++;
    }
    return cnt / n;
  }
  var fanLing = rarFrac("FAN", "SHANLIN", 20000, 31) + rarFrac("LING", "SHANLIN", 20000, 31);
  var xianShen = rarFrac("XIAN", "SHANLIN", 20000, 31) + rarFrac("SHEN", "SHANLIN", 20000, 31);
  ok("秘境稀有度 凡+灵主导高于仙+神×3", fanLing > xianShen * 3);

  // ───────── Player 在家秘境 集成（GameState.enterSecretRealm 镜像） ─────────
  var p = SH.makePlayer();
  ok("玩家初始 秘境免费次数=3", p.raw.secretRealmLeft === 3);
  // 免费路径：剩3次 → 进入后剩2次，香火不变
  var incFree = p.raw.incense;
  var res = p.enterSecretRealm(seeded(1));
  ok("秘境 免费路径返回合法结果", res && res.spawns.length === RE.SPAWN_COUNT);
  ok("秘境 免费路径 element=主题偏向", res.element === RE.themeElementBias(res.theme));
  ok("秘境 免费路径扣次数不扣香火", p.raw.secretRealmLeft === 2 && p.raw.incense === incFree);
  // 香火路径：次数耗尽 → 扣香火(20)，次数保持0
  p.raw.secretRealmLeft = 0; var incPaid = p.raw.incense;
  var res2 = p.enterSecretRealm(seeded(2));
  ok("秘境 耗尽后耗香火20进入", p.raw.secretRealmLeft === 0 && p.raw.incense === incPaid - RE.INCENSE_COST);
  ok("秘境 香火充足时可进入", res2 !== null);
  // 阻断：次数耗尽且香火不足 → 返回 null 且不扣费
  p.raw.secretRealmLeft = 0; p.raw.incense = 10;
  var res3 = p.enterSecretRealm(seeded(3));
  ok("秘境 香火不足时阻断且不扣费", res3 === null && p.raw.incense === 10);
  // 记录 ENTER_SECRET 任务
  p.raw.secretRealmLeft = 3; p.raw.incense = 100; var before = p.raw.quests.ENTER_SECRET;
  p.enterSecretRealm(seeded(4));
  ok("秘境 进入记录 ENTER_SECRET 任务", p.raw.quests.ENTER_SECRET === before + 1);
})();

// ───────── HomesteadEngine 镜像（GDD 十九·长线留存：家园洞府） ─────────
(function () {
  var HE = SH.HomesteadEngine;
  var HOUR = HE.HOUR_MS;
  // 常量
  ok("洞府：常量 MAX_OFFLINE_HOURS=12", HE.MAX_OFFLINE_HOURS === 12.0);
  ok("洞府：常量 HOUR_MS=3600000", HE.HOUR_MS === 3600000);
  ok("洞府：常量 FENGSHUI_INCENSE_MUL=0.10", Math.abs(HE.FENGSHUI_INCENSE_MUL - 0.10) < 1e-9);
  ok("洞府：常量 FENGSHUI_PILL_PER_LEVEL=0.1", Math.abs(HE.FENGSHUI_PILL_PER_LEVEL - 0.1) < 1e-9);
  ok("洞府：常量 FENGSHUI_ATTACK_PER_LEVEL=0.02", Math.abs(HE.FENGSHUI_ATTACK_PER_LEVEL - 0.02) < 1e-9);
  // 时产随等级
  ok("洞府：Lv0 香火时产 20", Math.abs(HE.incenseRate(0) - 20.0) < 1e-9);
  ok("洞府：Lv5 香火时产 30", Math.abs(HE.incenseRate(5) - 30.0) < 1e-9);
  ok("洞府：Lv0 妖丹时产 1", Math.abs(HE.pillRate(0) - 1.0) < 1e-9);
  ok("洞府：Lv5 妖丹时产 1.5", Math.abs(HE.pillRate(5) - 1.5) < 1e-9);
  // 风水攻击增益
  ok("洞府：Lv0 风水增益 0", Math.abs(HE.fengshuiAttackBonus(0)) < 1e-9);
  ok("洞府：Lv10 风水增益 0.20", Math.abs(HE.fengshuiAttackBonus(10) - 0.20) < 1e-9);
  // 升级花费
  ok("洞府：升级花费 Lv0→100", HE.upgradeCost(0) === 100);
  ok("洞府：升级花费 Lv5→600", HE.upgradeCost(5) === 600);
  // compute
  var base = 1000000;
  var c1 = HE.compute(0, base + HOUR, base);
  ok("洞府：Lv0 满1h → 香火20/妖丹1 不封顶", c1.incense === 20 && c1.pills === 1 && c1.capped === false);
  var c12 = HE.compute(0, base + 24 * HOUR, base);
  ok("洞府：Lv0 24h 封顶12h → 香火240 且 capped", c12.incense === 240 && c12.capped === true);
  var c3 = HE.compute(5, base + HOUR, base);
  ok("洞府：Lv5 满1h → 香火30", c3.incense === 30 && c3.pills === 1);
  var c4 = HE.compute(5, base + 2 * HOUR, base);
  ok("洞府：Lv5 满2h → 香火60/妖丹3", c4.incense === 60 && c4.pills === 3);
  var c0 = HE.compute(0, 0, 0);
  ok("洞府：从未收取(last=0) → 0 产出", c0.incense === 0 && c0.pills === 0 && c0.capped === false);
  // Player 收取/升级 接线（逐行复刻 GameState）
  var p = SH.makePlayer();
  var t0 = 3 * HOUR;
  var r0 = p.collectHomestead(t0);
  ok("洞府：首次进入启动计时不补发（返回0 且推进时间戳）", r0.incense === 0 && r0.pills === 0 && p.raw.lastHomesteadCollect === t0);
  var t1 = 6 * HOUR;
  var r1 = p.collectHomestead(t1);
  ok("洞府：再收3h → 香火60/妖丹3 并推进时间戳", r1.incense === 60 && r1.pills === 3 && p.raw.lastHomesteadCollect === t1);
  var incBefore = p.raw.incense;
  var r2 = p.collectHomestead(7 * HOUR);
  ok("洞府：再次收取1h → 增量香火20（时间戳推进）", r2.incense === 20 && p.raw.lastHomesteadCollect === 7 * HOUR);
  ok("洞府：香火累计增量正确", p.raw.incense === incBefore + 20);
  p.raw.yaodan = 0;
  ok("洞府：妖丹不足时不可升级", p.canUpgradeHomestead() === false);
  p.raw.yaodan = 100;
  ok("洞府：耗100妖丹升阵法至Lv1", p.upgradeHomestead() === true && p.getHomesteadLevel() === 1 && p.raw.yaodan === 0);
  // 风水增益接入战斗乘子（默认 Lv0=0 不破坏战斗值）
  ok("洞府：Lv0 风水乘子系数 1.0（战斗无影响）", 1.0 + HE.fengshuiAttackBonus(0) === 1.0);
})();

// ───────── DailyResetEngine 镜像（#181 每日重置·跨天静默重置） ─────────
ok("每日重置 TARGET_COUNT= 7", SH.DailyResetEngine.TARGET_COUNT === 7);
ok("每日重置 TARGETS id 顺序与 GDD 一致", (function(){
  var ids = SH.DailyResetEngine.TARGETS.map(function(t){return t.id;});
  var expect = ["quests","secret_realm","dungeon","shop","stronghold","reputation","fatigue"];
  return JSON.stringify(ids) === JSON.stringify(expect);
})());
ok("isCrossDay 首启空串→true", SH.DailyResetEngine.isCrossDay("", "2026-08-19") === true);
ok("isCrossDay 昨日→今日→true", SH.DailyResetEngine.isCrossDay("2026-08-16","2026-08-17") === true);
ok("isCrossDay 同日→false", SH.DailyResetEngine.isCrossDay("2026-08-17","2026-08-17") === false);
ok("isCrossDay 跨月→true", SH.DailyResetEngine.isCrossDay("2026-08-31","2026-09-01") === true);
ok("isCrossDay 跨年→true", SH.DailyResetEngine.isCrossDay("2025-12-31","2026-01-01") === true);
ok("isCrossDay 时钟回拨→true", SH.DailyResetEngine.isCrossDay("2026-08-17","2026-08-16") === true);
ok("resolvePending 跨天=7项", SH.DailyResetEngine.resolvePending("2026-08-16","2026-08-17").length === 7);
ok("resolvePending 同日=0（调用方不应改动字段）", SH.DailyResetEngine.resolvePending("2026-08-17","2026-08-17").length === 0);
ok("Scheduler STATUS 4 态 idle/registered/suspended/unavailable",
   SH.DailyResetScheduler.STATUS.length === 4 &&
   SH.DailyResetScheduler.STATUS.indexOf("idle") >= 0 && SH.DailyResetScheduler.STATUS.indexOf("unavailable") >= 0);
ok("seasonsElapsed 第0天=0", SH.DailyResetScheduler.seasonsElapsed(0,0) === 0);
ok("seasonsElapsed 第89天=0", SH.DailyResetScheduler.seasonsElapsed(0, 89*86400000) === 0);
ok("seasonsElapsed 满90天=1", SH.DailyResetScheduler.seasonsElapsed(0, 90*86400000) === 1);
ok("seasonsElapsed 满180天=2", SH.DailyResetScheduler.seasonsElapsed(0, 180*86400000) === 2);
ok("seasonsElapsed now<start=0", SH.DailyResetScheduler.seasonsElapsed(1000,500) === 0);

// GameState.maybeResetDaily 集成（对应 Kotlin GameState.maybeResetDaily L982-993）
(function(){
  var p = SH.makePlayer();
  p.addStarter("yao_hu");
  p.raw.secretRealmLeft = 0;
  p.raw.dungeonEntered = { "d_shilian_lingmai": 4 };
  p.raw.rep = 80;
  p.raw.fatigue = 50;
  p.raw.stronghold.ownedNode = "城隍桥";
  p.raw.stronghold.lastCaptureDay = "2026-08-17";
  p.raw.shopDay = "2026-08-17"; p.raw.shopIncenseBought = 5;
  p.raw.quests.CATCH_TOTAL = 7; p.raw.quests.claimed["d_catch3"] = true;
  var trig = p.maybeResetDaily("2026-08-18");
  ok("maybeResetDaily 跨天触发=true", trig === true);
  ok("maybeResetDaily secretRealmLeft→3", p.raw.secretRealmLeft === 3);
  ok("maybeResetDaily dungeonEntered→{}", Object.keys(p.raw.dungeonEntered).length === 0);
  ok("maybeResetDaily rep 80→81(+1)", p.raw.rep === 81);
  ok("maybeResetDaily fatigue→0", p.raw.fatigue === 0);
  ok("maybeResetDaily stronghold.ownedNode→null", p.raw.stronghold.ownedNode === null);
  ok("maybeResetDaily stronghold.lastCaptureDay→today", p.raw.stronghold.lastCaptureDay === "2026-08-18");
  ok("maybeResetDaily shopIncenseBought→0", p.raw.shopIncenseBought === 0);
  ok("maybeResetDaily shopDay→today", p.raw.shopDay === "2026-08-18");
  ok("maybeResetDaily quests.resetDaily(claimed清空)", p.raw.quests.claimed["d_catch3"] === undefined);
  ok("maybeResetDaily lastDailyReset→today", p.raw.lastDailyReset === "2026-08-18");
  p.raw.rep = 50; p.raw.fatigue = 10; p.raw.secretRealmLeft = 1;
  var trig2 = p.maybeResetDaily("2026-08-18");
  ok("maybeResetDaily 同日→不触发(false)", trig2 === false);
  ok("maybeResetDaily 同日 rep 不重置(仍50)", p.raw.rep === 50);
  ok("maybeResetDaily 同日 fatigue 不重置(仍10)", p.raw.fatigue === 10);
  ok("maybeResetDaily 同日 secretRealmLeft 不重置(仍1)", p.raw.secretRealmLeft === 1);
})();

// ───────── ShopEngine 镜像（#225 经济与变现·引妖香） ─────────
ok("ShopEngine 引妖香花费灵玉=30", SH.ShopEngine.INCENSE_REFILL_COST_LINGYU === 30);
ok("ShopEngine 引妖香回补香火=60", SH.ShopEngine.INCENSE_REFILL_AMOUNT === 60);
ok("ShopEngine 日购上限=3", SH.ShopEngine.INCENSE_DAILY_BUY_CAP === 3);
ok("canBuyIncense 灵玉不足→false", SH.ShopEngine.canBuyIncense({incenseBoughtToday:0,lastBuyDay:"2026-08-19"},"2026-08-19",20) === false);
ok("canBuyIncense 当日已购满3→false", SH.ShopEngine.canBuyIncense({incenseBoughtToday:3,lastBuyDay:"2026-08-19"},"2026-08-19",99) === false);
ok("canBuyIncense 灵玉足且今日0→true", SH.ShopEngine.canBuyIncense({incenseBoughtToday:0,lastBuyDay:"2026-08-19"},"2026-08-19",99) === true);
ok("canBuyIncense lastBuyDay≠today 视为0→true", SH.ShopEngine.canBuyIncense({incenseBoughtToday:3,lastBuyDay:"2026-08-18"},"2026-08-19",99) === true);
ok("buyIncense 成功返回 {amount:60,cost:30} 并写回 state", (function(){
  var s={incenseBoughtToday:0,lastBuyDay:""}; var r=SH.ShopEngine.buyIncense(s,"2026-08-19",99);
  return r && r.amount===60 && r.cost===30 && s.incenseBoughtToday===1 && s.lastBuyDay==="2026-08-19";
})());
ok("buyIncense 灵玉不足→null", SH.ShopEngine.buyIncense({incenseBoughtToday:0,lastBuyDay:""},"2026-08-19",10) === null);
ok("buyIncense 达日上限→null", SH.ShopEngine.buyIncense({incenseBoughtToday:3,lastBuyDay:"2026-08-19"},"2026-08-19",99) === null);
// Player.buyIncense 端到端：连买3次成功、第4次封顶、跨天重置后可再购
(function(){
  var p = SH.makePlayer(); p.addStarter("yao_hu");
  p.raw.lingyu = 999; p.raw.incense = 0; p.raw.shopDay = ""; p.raw.shopIncenseBought = 0;
  var t = "2026-08-19";
  var r1 = p.buyIncense(t); ok("Player.buyIncense 第1次成功(+60香火/-30灵玉)", r1.amount===60 && p.raw.incense===60 && p.raw.lingyu===969 && p.raw.shopIncenseBought===1);
  var r2 = p.buyIncense(t); ok("Player.buyIncense 第2次成功", r2.amount===60 && p.raw.shopIncenseBought===2 && p.raw.lingyu===939);
  var r3 = p.buyIncense(t); ok("Player.buyIncense 第3次成功(达上限)", r3.amount===60 && p.raw.shopIncenseBought===3 && p.raw.lingyu===909);
  var r4 = p.buyIncense(t); ok("Player.buyIncense 第4次封顶→error", !!r4.error && p.raw.shopIncenseBought===3 && p.raw.lingyu===909);
  p.maybeResetDaily("2026-08-20");
  ok("跨天重置后 shopIncenseBought→0", p.raw.shopIncenseBought===0 && p.raw.shopDay==="2026-08-20");
  var r5 = p.buyIncense("2026-08-20"); ok("跨天重置后引妖香可再购", r5.amount===60 && p.raw.shopIncenseBought===1);
})();

// ───────── SeasonEngine 镜像（#226 长线留存·90 天双轨） ─────────
ok("SeasonEngine SEASON_LENGTH_DAYS=90", SH.SeasonEngine.SEASON_LENGTH_DAYS === 90);
ok("SeasonEngine XP_PER_TIER=100", SH.SeasonEngine.XP_PER_TIER === 100);
ok("SeasonEngine TIERS=60", SH.SeasonEngine.TIERS === 60);
ok("SeasonEngine DAY_MS=86400000", SH.SeasonEngine.DAY_MS === 86400000);
ok("SeasonEngine XP_CATCH=5", SH.SeasonEngine.XP_CATCH === 5);
ok("SeasonEngine XP_BATTLE=10", SH.SeasonEngine.XP_BATTLE === 10);
ok("SeasonEngine XP_DUNGEON=15", SH.SeasonEngine.XP_DUNGEON === 15);
ok("SeasonEngine XP_SIGNIN=8", SH.SeasonEngine.XP_SIGNIN === 8);
// dayIndex 边界：未开始 / 倒退 / 第 1 天 / 89 / 90 / 91
ok("dayIndex startMs=0→0（未开始）", SH.SeasonEngine.dayIndex(0, 1000) === 0);
ok("dayIndex startMs=1 nowMs=0→0（now<start）", SH.SeasonEngine.dayIndex(1, 0) === 0);
var _sb = 1700000000000; // 任意 epoch，作为"已开赛"基线
ok("dayIndex nowMs=startMs→第1天", SH.SeasonEngine.dayIndex(_sb, _sb) === 1);
ok("dayIndex +89d-1ms→第89天", SH.SeasonEngine.dayIndex(_sb, _sb + 89*86400000 - 1) === 89);
ok("dayIndex +89d整→第90天", SH.SeasonEngine.dayIndex(_sb, _sb + 89*86400000) === 90);
ok("dayIndex +90d整→第91天(已结束)", SH.SeasonEngine.dayIndex(_sb, _sb + 90*86400000) === 91);
// inSeason
ok("inSeason 未开始→false", SH.SeasonEngine.inSeason(0, 1000) === false);
ok("inSeason 赛季中→true", SH.SeasonEngine.inSeason(_sb, _sb + 30*86400000) === true);
ok("inSeason 已结束→false", SH.SeasonEngine.inSeason(_sb, _sb + 91*86400000) === false);
// daysRemaining
ok("daysRemaining 未开始→满长90", SH.SeasonEngine.daysRemaining(0, 1000) === 90);
ok("daysRemaining 第1天剩余90", SH.SeasonEngine.daysRemaining(_sb, _sb) === 90);
ok("daysRemaining 第89天剩余2", SH.SeasonEngine.daysRemaining(_sb, _sb + 88*86400000) === 2);
ok("daysRemaining 第90天剩余1", SH.SeasonEngine.daysRemaining(_sb, _sb + 89*86400000) === 1);
ok("daysRemaining 已结束→0", SH.SeasonEngine.daysRemaining(_sb, _sb + 91*86400000) === 0);
// tierFromXp / xpForTier
ok("tierFromXp(0)=0", SH.SeasonEngine.tierFromXp(0) === 0);
ok("tierFromXp(99)=0", SH.SeasonEngine.tierFromXp(99) === 0);
ok("tierFromXp(100)=1", SH.SeasonEngine.tierFromXp(100) === 1);
ok("tierFromXp(250)=2", SH.SeasonEngine.tierFromXp(250) === 2);
ok("tierFromXp(6000)=60(封顶)", SH.SeasonEngine.tierFromXp(6000) === 60);
ok("xpForTier(0)=0", SH.SeasonEngine.xpForTier(0) === 0);
ok("xpForTier(1)=100", SH.SeasonEngine.xpForTier(1) === 100);
ok("xpForTier(10)=1000", SH.SeasonEngine.xpForTier(10) === 1000);
// freeReward / paidReward 里程碑
ok("freeReward 第1档=香火30/妖丹3", (function(){var r=SH.SeasonEngine.freeReward(1);return r.incense===30&&r.lingyu===0&&r.yaodan===3;})());
ok("freeReward 第9档=香火30/妖丹3", (function(){var r=SH.SeasonEngine.freeReward(9);return r.incense===30&&r.yaodan===3;})());
ok("freeReward 第10档里程碑=香火200/妖丹15", (function(){var r=SH.SeasonEngine.freeReward(10);return r.incense===200&&r.lingyu===0&&r.yaodan===15;})());
ok("freeReward 第20档里程碑", (function(){var r=SH.SeasonEngine.freeReward(20);return r.incense===200&&r.yaodan===15;})());
ok("paidReward 第1档=灵玉10/妖丹5", (function(){var r=SH.SeasonEngine.paidReward(1);return r.incense===0&&r.lingyu===10&&r.yaodan===5;})());
ok("paidReward 第10档里程碑=灵玉60/妖丹20", (function(){var r=SH.SeasonEngine.paidReward(10);return r.incense===0&&r.lingyu===60&&r.yaodan===20;})());
// Player 赛季接线
(function(){
  var p = SH.makePlayer(); p.addStarter("yao_hu");
  var start = 1700000000000; // 2023-11-14 ms epoch
  p.ensureSeasonStart(start);
  ok("Player.ensureSeasonStart 首启写入", p.raw.seasonStartMs === start);
  p.ensureSeasonStart(start + 1000000);
  ok("Player.ensureSeasonStart 已存在则不变", p.raw.seasonStartMs === start);
  // tickSeason：未到 90 天
  ok("Player.tickSeason 未满90天→false", p.tickSeason(start + 89*86400000) === false);
  // tickSeason：恰好满 90 天
  ok("Player.tickSeason 满90天→true 滚动", p.tickSeason(start + 90*86400000) === true);
  ok("tickSeason 滚动后 seasonIndex+1", p.raw.seasonIndex === 2);
  ok("tickSeason 滚动后 seasonXp=0", p.raw.seasonXp === 0);
  ok("tickSeason 滚动后 hasSeasonPass=false", p.raw.hasSeasonPass === false);
  // addSeasonXp / currentSeasonTier
  p.addSeasonXp(0); ok("addSeasonXp(0) 不变", p.raw.seasonXp === 0);
  p.addSeasonXp(-5); ok("addSeasonXp 负值不变", p.raw.seasonXp === 0);
  p.addSeasonXp(250); ok("addSeasonXp(250)→seasonXp=250", p.raw.seasonXp === 250);
  ok("currentSeasonTier(250)=2", p.currentSeasonTier() === 2);
  // buySeasonPass
  p.raw.lingyu = 100; ok("buySeasonPass 灵玉不足→false", p.buySeasonPass() === false);
  p.raw.lingyu = 600; ok("buySeasonPass 灵足→true", p.buySeasonPass() === true);
  ok("buySeasonPass 扣300灵玉", p.raw.lingyu === 300);
  ok("buySeasonPass hasSeasonPass=true", p.raw.hasSeasonPass === true);
  // claimSeasonTier: 无通行证时只发免费（解耦验证）
  p.raw.hasSeasonPass = false; p.raw.seasonClaimedFree = []; p.raw.seasonClaimedPaid = [];
  var rcA = p.claimSeasonTier(2);
  ok("claimSeasonTier 无通行证时：第2档仅免费发放（香火30/妖丹3/灵玉0）", rcA.claimedFree === true && rcA.incense === 30 && rcA.yaodan === 3 && rcA.lingyu === 0 && rcA.claimedPaid === false);
  // 二次领取同档→全 false
  var rcB = p.claimSeasonTier(2);
  ok("claimSeasonTier 同档二次领取→全false", rcB.claimedFree === false && rcB.claimedPaid === false);
  // claimSeasonTier 未解锁档
  var rcC = p.claimSeasonTier(3);
  ok("claimSeasonTier 未解锁档→全false", rcC.claimedFree === false && rcC.claimedPaid === false);
  // claimSeasonTier 有通行证时付费档也发（注意 yaodan 是 free+paid 累计 3+5=8）
  p.raw.hasSeasonPass = true;
  var rcD = p.claimSeasonTier(2);
  ok("claimSeasonTier 有通行证时：第2档付费发放（灵玉10/妖丹5，已领免费不重复）", rcD.claimedFree === false && rcD.claimedPaid === true && rcD.lingyu === 10 && rcD.yaodan === 5);
  // claimAllSeason：累 seasonXp=300 让 tier=3，验证累计汇总
  p.raw.seasonClaimedFree = []; p.raw.seasonClaimedPaid = []; p.raw.seasonXp = 0;
  p.addSeasonXp(300);
  var all = p.claimAllSeason();
  ok("claimAllSeason 返回3档结果（tier=3）", all.length === 3);
  var tI=0, tL=0, tY=0;
  all.forEach(function(r){ tI+=r.incense; tL+=r.lingyu; tY+=r.yaodan; });
  ok("claimAllSeason 累计香火90（3×30 来自免费）", tI === 90);
  ok("claimAllSeason 累计灵玉30（3×10 来自付费）", tL === 30);
  ok("claimAllSeason 累计妖丹24（免费9+付费15）", tY === 24);
  // 二次调用：返回 3 档但全部 false（无新增发放）
  var all2 = p.claimAllSeason();
  ok("claimAllSeason 二次调用仍返回3档", all2.length === 3);
  var all2New = all2.every(function(r){ return !r.claimedFree && !r.claimedPaid; });
  ok("claimAllSeason 二次调用无新增发放", all2New);
  // 关闭通行证：付费档不再发放
  p.raw.hasSeasonPass = false; p.raw.seasonClaimedFree = []; p.raw.seasonClaimedPaid = []; p.raw.seasonXp = 0;
  p.addSeasonXp(600); // tier 6
  ok("赛季经验 600→档位 6", p.currentSeasonTier() === 6);
  var rcE = p.claimSeasonTier(5);
  ok("无通行证时第5档仅免费（不付费）", rcE.claimedFree === true && rcE.claimedPaid === false && rcE.lingyu === 0);
})();

// ───────── HonorEngine 镜像（#T01 荣誉/勋章/称号·GDD 二十·展示型荣誉） ─────────
(function(){
  // 常量与数据定义零漂移
  ok("HonorEngine.NO_TITLE 是空串", SH.HonorEngine.NO_TITLE === "");
  ok("HonorEngine.MEDALS 数量=4", SH.HonorEngine.MEDALS.length === 4);
  ok("HonorEngine.TITLES 数量=12", SH.HonorEngine.TITLES.length === 12);
  ok("MEDAL[0] medal_bronze req=1", (function(){var m=SH.HonorEngine.MEDALS[0];return m.id==="medal_bronze"&&m.name==="青铜妖士"&&m.req===1;})());
  ok("MEDAL[3] medal_diamond req=MAX", SH.HonorEngine.MEDALS[3].req === 2147483647);
  ok("TITLE title_huashen 化神尊者", (function(){var t=SH.HonorEngine.TITLES.filter(function(x){return x.id==="title_huashen";})[0];return t&&t.name==="化神尊者";})());
  ok("TITLE title_tujian 图鉴100%条件", (function(){var t=SH.HonorEngine.TITLES.filter(function(x){return x.id==="title_tujian";})[0];return t&&t.desc.indexOf("100")>=0;})());
  // unlockedMedals 阶梯过滤
  ok("achievementCount=0 → 无勋章", SH.HonorEngine.unlockedMedals({achievementCount:0,achievementTotal:4,rankTierIndex:0,codexRatio:0,tribulationTotal:0}).length === 0);
  ok("achievementCount=1 → 仅青铜", (function(){var ms=SH.HonorEngine.unlockedMedals({achievementCount:1,achievementTotal:4,rankTierIndex:0,codexRatio:0,tribulationTotal:0});return ms.length===1&&ms[0].id==="medal_bronze";})());
  ok("achievementCount=4,total=10 → 青铜+白银=2（全成就未达成）", SH.HonorEngine.unlockedMedals({achievementCount:4,achievementTotal:10,rankTierIndex:0,codexRatio:0,tribulationTotal:0}).length === 2);
  ok("achievementCount=4,total=4 → 含钻石=3（全成就达成）", SH.HonorEngine.unlockedMedals({achievementCount:4,achievementTotal:4,rankTierIndex:0,codexRatio:0,tribulationTotal:0}).length === 3);
  ok("achievementCount=8 → 青铜+白银+黄金=3", SH.HonorEngine.unlockedMedals({achievementCount:8,achievementTotal:10,rankTierIndex:0,codexRatio:0,tribulationTotal:0}).length === 3);
  ok("全成就达成 → 含钻石勋章=4", (function(){var ms=SH.HonorEngine.unlockedMedals({achievementCount:10,achievementTotal:10,rankTierIndex:0,codexRatio:0,tribulationTotal:0});return ms.length===4&&ms[3].id==="medal_diamond";})());
  ok("achievementTotal=0 → 钻石不解锁（防除零）", SH.HonorEngine.unlockedMedals({achievementCount:0,achievementTotal:0,rankTierIndex:0,codexRatio:0,tribulationTotal:0}).length === 0);
  // isUnlocked 条件判定（各分支至少一条）
  ok("title_shouyao achievementCount≥1 → true", SH.HonorEngine.isUnlocked("title_shouyao", {achievementCount:1,achievementTotal:4,rankTierIndex:0,codexRatio:0,tribulationTotal:0}) === true);
  ok("title_shouyao achievementCount=0 → false", SH.HonorEngine.isUnlocked("title_shouyao", {achievementCount:0,achievementTotal:4,rankTierIndex:0,codexRatio:0,tribulationTotal:0}) === false);
  ok("title_xunyao codexRatio≥30 → true", SH.HonorEngine.isUnlocked("title_xunyao", {achievementCount:0,achievementTotal:4,rankTierIndex:0,codexRatio:30,tribulationTotal:0}) === true);
  ok("title_huashen rankTierIndex≥4 → true", SH.HonorEngine.isUnlocked("title_huashen", {achievementCount:0,achievementTotal:4,rankTierIndex:4,codexRatio:0,tribulationTotal:0}) === true);
  ok("title_tuojie tribulationTotal≥1 → true", SH.HonorEngine.isUnlocked("title_tuojie", {achievementCount:0,achievementTotal:4,rankTierIndex:0,codexRatio:0,tribulationTotal:1}) === true);
  ok("title_jiuzhuan tribulationTotal≥3 → true, 2→false", SH.HonorEngine.isUnlocked("title_jiuzhuan", {achievementCount:0,achievementTotal:4,rankTierIndex:0,codexRatio:0,tribulationTotal:3}) === true && SH.HonorEngine.isUnlocked("title_jiuzhuan", {achievementCount:0,achievementTotal:4,rankTierIndex:0,codexRatio:0,tribulationTotal:2}) === false);
  ok("unknown id → false", SH.HonorEngine.isUnlocked("title_nonexist", {achievementCount:5,achievementTotal:4,rankTierIndex:4,codexRatio:100,tribulationTotal:5}) === false);
  ok("title_dacheng 全成就 → true", SH.HonorEngine.isUnlocked("title_dacheng", {achievementCount:4,achievementTotal:4,rankTierIndex:0,codexRatio:0,tribulationTotal:0}) === true);
  // Player 荣誉接线（honorProfile / medals / titles / equipTitle / displayTitle）
  var p = SH.makePlayer();
  ok("fresh player achievementTotal=4（原型 4 个 ACHIEVEMENT）", p.achievementTotal() === 4);
  ok("fresh player achievementCount=0", p.achievementCount() === 0);
  ok("fresh player medals=0", p.medals().length === 0);
  ok("fresh player titles=0", p.titles().length === 0);
  ok("fresh player unlockedTitleIds=[]", p.unlockedTitleIds().length === 0);
  ok("fresh player displayTitle 是空串", p.displayTitle() === "");
  ok("equipTitle 未解锁称号→false", p.equipTitle("title_tuojie") === false);
  ok("equipTitle NO_TITLE→true", p.equipTitle("") === true);
  // 灌满成就 → 验证解锁
  p.raw.quests.CATCH_TOTAL = 15;   // a_catch10 收妖大师
  p.raw.quests.catchRarity.LING = 10; p.raw.quests.catchRarity.SHEN = 1; // a_catch_shen
  p.raw.quests.WIN_BATTLE = 50;  // a_win50
  // a_codex30 需要 CODEX_RATIO >= 30，先不灌，保持 3/4
  ok("灌满 3/4 成就后 achievementCount=3", p.achievementCount() === 3);
  ok("3成就 → medals 仅青铜（白银需4）", (function(){var ms=p.medals();return ms.length===1&&ms[0].id==="medal_bronze";})());
  ok("3成就 → title_shouyao 解锁", p.unlockedTitleIds().indexOf("title_shouyao") >= 0);
  // 装备已解锁称号
  ok("equipTitle title_shouyao→true", p.equipTitle("title_shouyao") === true);
  ok("displayTitle→收妖大师", p.displayTitle() === "收妖大师");
  // 清空称号
  ok("equipTitle NO_TITLE→true", p.equipTitle("") === true);
  ok("displayTitle 清空→空串", p.displayTitle() === "");
  // tribulationTotal → 渡劫真君
  p.raw.tribulationTotal = 1;
  ok("加渡劫1次 → title_tuojie 解锁", p.unlockedTitleIds().indexOf("title_tuojie") >= 0);
  // rankTierIndex=4 → 化神尊者
  p.raw.rankTierIndex = 4;
  ok("段位化神 → title_huashen 解锁", p.unlockedTitleIds().indexOf("title_huashen") >= 0);
  // honorProfile 装配
  var hp = p.honorProfile();
  ok("honorProfile.rankTierIndex=4", hp.rankTierIndex === 4);
  ok("honorProfile.tribulationTotal=1", hp.tribulationTotal === 1);
  ok("honorProfile.achievementTotal=4", hp.achievementTotal === 4);
})();

// ───────── ActivityEngine 镜像（#T02 运营活动·GDD 活动·节气复玩 / 年节限时） ─────────
(function(){
  // 常量与数据零漂移
  ok("ActivityEngine.ACTIVITIES 数量=7", SH.ActivityEngine.ACTIVITIES.length === 7);
  ok("ACTIVITIES[0] act_zhongyuan 中元鬼节 8/28-9/1 captureMul=1.2", (function(){var a=SH.ActivityEngine.ACTIVITIES[0];return a.id==="act_zhongyuan"&&a.kind==="FESTIVAL"&&a.start==="2026-08-28"&&a.end==="2026-09-01"&&a.captureMul===1.2;})());
  ok("ACTIVITIES[3] act_battle_rally 论道大会 expMul=1.25 incomeMul=1.2", (function(){var a=SH.ActivityEngine.ACTIVITIES[3];return a.id==="act_battle_rally"&&a.expMul===1.25&&a.incomeMul===1.2;})());
  ok("ACTIVITIES[6] act_guoqing 国庆盛典 10/1-10/7", (function(){var a=SH.ActivityEngine.ACTIVITIES[6];return a.id==="act_guoqing"&&a.kind==="BATTLE_RALLY"&&a.start==="2026-10-01"&&a.expMul===1.2;})());
  ok("KIND_LABEL FESTIVAL=节日", SH.ActivityEngine.KIND_LABEL.FESTIVAL === "节日");
  // isActive（含起止日）
  ok("8/28 在中元区间内 → true", SH.ActivityEngine.isActive(SH.ActivityEngine.ACTIVITIES[0], "2026-08-28") === true);
  ok("9/1 中元区间末尾 → true", SH.ActivityEngine.isActive(SH.ActivityEngine.ACTIVITIES[0], "2026-09-01") === true);
  ok("9/2 中元区间外 → false", SH.ActivityEngine.isActive(SH.ActivityEngine.ACTIVITIES[0], "2026-09-02") === false);
  ok("8/14 收妖大作战区间前一天 → false", SH.ActivityEngine.isActive(SH.ActivityEngine.ACTIVITIES[2], "2026-08-14") === false);
  ok("9/25 中秋月圆 → true", SH.ActivityEngine.isActive(SH.ActivityEngine.ACTIVITIES[5], "2026-09-25") === true);
  ok("9/28 中秋月圆区间外 → false", SH.ActivityEngine.isActive(SH.ActivityEngine.ACTIVITIES[5], "2026-09-28") === false);
  // active（当日所有生效）
  ok("8/17 → 收妖大作战+论道大会+勤修有礼=3个", SH.ActivityEngine.active("2026-08-17").length === 3);
  ok("2026-01-01 → 无活动=0个", SH.ActivityEngine.active("2026-01-01").length === 0);
  ok("空串日期 → 无活动=0个（首启边界）", SH.ActivityEngine.active("").length === 0);
  ok("8/28 → 中元鬼节单独生效=1个", (function(){var a=SH.ActivityEngine.active("2026-08-28");return a.length===1&&a[0].id==="act_zhongyuan";})());
  ok("10/1 → 国庆盛典单独生效=1个", (function(){var a=SH.ActivityEngine.active("2026-10-01");return a.length===1&&a[0].id==="act_guoqing";})());
  // aggregate（乘性聚合，无活动全 1.0）
  ok("无活动聚合 → 全 1.0", (function(){var b=SH.ActivityEngine.aggregate([]);return b.captureMul===1.0&&b.expMul===1.0&&b.incomeMul===1.0;})());
  ok("8/17 三活动聚合：capture=1.15 / exp=1.25 / income=1.2×1.1=1.32", (function(){var b=SH.ActivityEngine.aggregate(SH.ActivityEngine.active("2026-08-17"));return Math.abs(b.captureMul-1.15)<1e-9&&Math.abs(b.expMul-1.25)<1e-9&&Math.abs(b.incomeMul-1.32)<1e-9;})());
  ok("国庆单活动聚合：exp=1.2 / income=1.15 / capture=1.0", (function(){var b=SH.ActivityEngine.aggregate(SH.ActivityEngine.active("2026-10-01"));return b.captureMul===1.0&&b.expMul===1.2&&b.incomeMul===1.15;})());
  ok("中元单活动聚合：capture=1.2 / exp=1.0 / income=1.0", (function(){var b=SH.ActivityEngine.aggregate(SH.ActivityEngine.active("2026-08-28"));return b.captureMul===1.2&&b.expMul===1.0&&b.incomeMul===1.0;})());
  // Player 活动接线
  var p = SH.makePlayer();
  ok("fresh player lastDailyReset='' → currentActivities=0", p.currentActivities().length === 0);
  ok("fresh player activityIncomeMul=1.0（基线）", p.activityIncomeMul() === 1.0);
  ok("fresh player activityCaptureMul=1.0", p.activityCaptureMul() === 1.0);
  ok("fresh player activityExpMul=1.0", p.activityExpMul() === 1.0);
  p.raw.lastDailyReset = "2026-10-01";
  ok("设 lastDailyReset=10/1 → currentActivities=1（国庆）", p.currentActivities().length === 1 && p.currentActivities()[0].id === "act_guoqing");
  ok("国庆日 incomeMul=1.15", p.activityIncomeMul() === 1.15);
  ok("国庆日 expMul=1.2", p.activityExpMul() === 1.2);
  ok("国庆日 captureMul=1.0（国庆无收妖加成）", p.activityCaptureMul() === 1.0);
  // 活动结束后复原
  p.raw.lastDailyReset = "2026-11-01";
  ok("11月1日 → 无活动，乘子全 1.0", (function(){var b=p.activityBonuses();return b.captureMul===1.0&&b.expMul===1.0&&b.incomeMul===1.0;})());
})();

// ───────── TutorialEngine 镜像（#T03 新手引导 + 7 日留存·GDD 十八） ─────────
(function(){
  // 常量与数据零漂移
  ok("TutorialEngine.STEPS 数量=5", SH.TutorialEngine.STEPS.length === 5);
  ok("TutorialEngine.LAST_ORDER=5", SH.TutorialEngine.LAST_ORDER === 5);
  ok("STEPS[0] CATCH order=1 forceable=true", (function(){var s=SH.TutorialEngine.STEPS[0];return s.order===1&&s.forceable===true&&s.title.indexOf("收服")>=0;})());
  ok("STEPS[3] FACTION order=4 forceable=false", (function(){var s=SH.TutorialEngine.STEPS[3];return s.order===4&&s.forceable===false&&s.title.indexOf("择门")>=0;})());
  ok("REALM_FEATURES 数量=4", SH.TutorialEngine.REALM_FEATURES.length === 4);
  ok("REALM_FEATURES[0] REALM unlockDay=2", SH.TutorialEngine.REALM_FEATURES[0].unlockDay === 2);
  ok("REALM_FEATURES[3] SOCIAL unlockDay=5", SH.TutorialEngine.REALM_FEATURES[3].unlockDay === 5);
  // currentStep / isForced / allDone
  ok("currentStep(0).order=1（第一步）", SH.TutorialEngine.currentStep(0).order === 1);
  ok("currentStep(3).order=4（第4步）", SH.TutorialEngine.currentStep(3).order === 4);
  ok("currentStep(5)=null（全完成）", SH.TutorialEngine.currentStep(5) === null);
  ok("currentStep(99)=null（超范围）", SH.TutorialEngine.currentStep(99) === null);
  ok("isForced(order=1)=true", SH.TutorialEngine.isForced(SH.TutorialEngine.STEPS[0]) === true);
  ok("isForced(order=3)=true", SH.TutorialEngine.isForced(SH.TutorialEngine.STEPS[2]) === true);
  ok("isForced(order=4)=false", SH.TutorialEngine.isForced(SH.TutorialEngine.STEPS[3]) === false);
  ok("isForced(order=5)=false", SH.TutorialEngine.isForced(SH.TutorialEngine.STEPS[4]) === false);
  ok("allDone(5,false)=true（步骤全过）", SH.TutorialEngine.allDone(5, false) === true);
  ok("allDone(2,false)=false（未完成）", SH.TutorialEngine.allDone(2, false) === false);
  ok("allDone(0,true)=true（已跳过）", SH.TutorialEngine.allDone(0, true) === true);
  // unlockedFeatures
  ok("unlockedFeatures(1)=0（day1 无解锁）", SH.TutorialEngine.unlockedFeatures(1).length === 0);
  ok("unlockedFeatures(2)=1（REALM）", (function(){var f=SH.TutorialEngine.unlockedFeatures(2);return f.length===1&&f[0].id==="REALM";})());
  ok("unlockedFeatures(3)=2（REALM+DUNGEON）", SH.TutorialEngine.unlockedFeatures(3).length === 2);
  ok("unlockedFeatures(5)=4（全解锁）", SH.TutorialEngine.unlockedFeatures(5).length === 4);
  ok("unlockedFeatures(100)=4（封顶）", SH.TutorialEngine.unlockedFeatures(100).length === 4);
  // signInReward（10*day 封顶 70）
  ok("signInReward(1)=10", SH.TutorialEngine.signInReward(1) === 10);
  ok("signInReward(3)=30", SH.TutorialEngine.signInReward(3) === 30);
  ok("signInReward(7)=70", SH.TutorialEngine.signInReward(7) === 70);
  ok("signInReward(8)=70（封顶）", SH.TutorialEngine.signInReward(8) === 70);
  ok("signInReward(100)=70", SH.TutorialEngine.signInReward(100) === 70);
  // Player 引导接线
  var p = SH.makePlayer();
  ok("fresh tutorialActive=true", p.tutorialActive() === true);
  ok("fresh currentTutorialStep.order=1", p.currentTutorialStep().order === 1);
  ok("completeStep(1) → tutorialStep=1", p.completeTutorialStep(1) === 1);
  ok("completeStep(5) jump → tutorialStep=5", p.completeTutorialStep(5) === 5);
  ok("step5后 tutorialActive=false", p.tutorialActive() === false);
  // fresh 跳过
  var p2 = SH.makePlayer();
  ok("skipTutorial → tutorialSkipped=true", p2.skipTutorial() === true);
  ok("跳过后 tutorialActive=false", p2.tutorialActive() === false);
  ok("跳过后 completeStep(1) 仍可更新 completedOrder（currentStep 只看 order）", p2.completeTutorialStep(1) === 1);
  // doSignIn
  var p3 = SH.makePlayer();
  var r1 = p3.doSignIn();
  ok("doSignIn day1: reward=10, signInDay=1", r1.reward === 10 && r1.signInDay === 1);
  ok("首次签到自动完成 step5（引导未完成）", p3.raw.tutorialStep === 5);
  ok("签到后 tutorialActive=false", p3.tutorialActive() === false);
  // 连签 7 天
  var p4 = SH.makePlayer();
  var last = null;
  for (var d = 0; d < 7; d++) last = p4.doSignIn();
  ok("连签 7 天 reward=70 封顶", last.reward === 70 && last.signInDay === 7);
  ok("连签 8 天仍封顶 70", p4.doSignIn().reward === 70);
  // realmFeatures：引导未完成时全开放，完成后按 signInDay
  var p5 = SH.makePlayer();
  ok("引导进行中 realmFeatures=全 4 项开放", p5.realmFeatures().length === 4);
  var p6 = SH.makePlayer(); p6.skipTutorial(); p6.raw.signInDay = 1;
  ok("跳过引导+day1 realmFeatures=0", p6.realmFeatures().length === 0);
  p6.raw.signInDay = 5;
  ok("跳过引导+day5 realmFeatures=4（全解锁）", p6.realmFeatures().length === 4);
})();

console.log("\n通过 " + pass + " / " + (pass + fail));
if (fail > 0) { console.log("失败："); fails.forEach(function (f) { console.log("  ✗ " + f); }); process.exit(1); }
else console.log("✅ 全部数值断言与 Kotlin 真相一致（零漂移）");
