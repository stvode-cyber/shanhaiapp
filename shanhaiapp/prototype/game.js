/*
 * 《山海妖志》可运行原型核心逻辑（game.js）
 * 忠实复刻 shared Kotlin 全部数值真相，可直接在浏览器与 Node 双模运行。
 * 数据层 data.js 由 prototype/gen_data.py 直接从 MonsterPool.kt / SkillLibrary.kt 解析生成（零转录漂移）。
 *
 * 复刻引擎：
 *   CaptureEngine / GachaEngine / CultivationEngine / TalentEngine /
 *   BattleEngine / FormationEngine / SectTechEngine / Codex / SocialEngine / LocationEngine / Monster.statsAtLevel /
 *   ReputationEngine / ComboEngine / WeatherEngine / FatigueEngine
 * 以及运行时玩家状态机 Player（货币 / 拥有妖 / 图鉴 / 任务 / 派系 / 据点 / 队伍 / 抽卡保底 / 信誉 / 疲劳）。
 */
(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) module.exports = factory(require("./data.js"));
  else root.ShanHaiGame = factory(root.SH_DATA);
})(typeof self !== "undefined" ? self : this, function (SH_DATA) {
  "use strict";

  // ───────────────────────── 枚举与基础数据 ─────────────────────────
  var RARITY = { FAN: "FAN", LING: "LING", XIAN: "XIAN", SHEN: "SHEN" };
  var RARITY_ORDER = ["FAN", "LING", "XIAN", "SHEN"];
  var RARITY_LABEL = { FAN: "凡", LING: "灵", XIAN: "仙", SHEN: "神" };
  var RARITY_GROWTH = { FAN: 1.0, LING: 1.1, XIAN: 1.25, SHEN: 1.5 };

  var CATEGORY = { JING: "JING", GUAI: "GUAI", YAO: "YAO", LINGSHOU: "LINGSHOU" };
  var CATEGORY_LABEL = { JING: "精", GUAI: "怪", YAO: "妖", LINGSHOU: "灵兽" };

  var ELEMENT = {
    METAL: "METAL", WOOD: "WOOD", EARTH: "EARTH", WATER: "WATER",
    FIRE: "FIRE", WIND: "WIND", THUNDER: "THUNDER",
  };
  var ELEMENT_LABEL = {
    METAL: "金", WOOD: "木", EARTH: "土", WATER: "水",
    FIRE: "火", WIND: "风", THUNDER: "雷",
  };

  // 五行克制（攻方克制受方 → ×1.6）
  var ELEMENT_BEATS = {
    METAL: ["WOOD", "WIND"], WOOD: ["EARTH", "FIRE"], EARTH: ["WATER", "THUNDER"],
    WATER: ["FIRE", "METAL"], FIRE: ["METAL", "WIND"], WIND: ["EARTH", "THUNDER"],
    THUNDER: ["WOOD", "WATER"],
  };
  function elementMultiplier(att, def) {
    if (ELEMENT_BEATS[att] && ELEMENT_BEATS[att].indexOf(def) >= 0) return 1.6;
    if (ELEMENT_BEATS[def] && ELEMENT_BEATS[def].indexOf(att) >= 0) return 0.6;
    return 1.0;
  }
  function elementLabel(att, def) {
    var m = elementMultiplier(att, def);
    if (m > 1.0) return ELEMENT_LABEL[att] + "克" + ELEMENT_LABEL[def] + " ×1.6";
    if (m < 1.0) return ELEMENT_LABEL[def] + "克" + ELEMENT_LABEL[att] + " ×0.6";
    return "无克制 ×1.0";
  }

  // 类别克制环：妖→精→怪→灵兽→妖（×1.3）
  var CATEGORY_RING = { YAO: "JING", JING: "GUAI", GUAI: "LINGSHOU", LINGSHOU: "YAO" };
  function categoryMultiplier(att, def) {
    if (CATEGORY_RING[att] === def) return 1.3;
    if (CATEGORY_RING[def] === att) return 1.0 / 1.3;
    return 1.0;
  }

  // ───────────────────────── 数据索引 ─────────────────────────
  var SKILLS = {};
  SH_DATA.skills.forEach(function (s) { SKILLS[s.id] = s; });
  var MONSTERS = {};
  SH_DATA.monsters.forEach(function (m) { MONSTERS[m.id] = m; });

  function monsterById(id) { return MONSTERS[id] || null; }
  function skillsOf(mon) { return (mon.skillIds || []).map(function (id) { return SKILLS[id]; }); }
  function byRarity(rarity) { return SH_DATA.monsters.filter(function (m) { return m.rarity === rarity; }); }

  function rarityWeight(r) {
    return r === "FAN" ? 45.0 : r === "LING" ? 35.0 : r === "XIAN" ? 15.0 : 5.0;
  }
  // MonsterPool.randomEncounter：权重 凡45/灵35/仙15/神5
  function randomEncounter(rng) {
    var pool = SH_DATA.monsters;
    var weights = pool.map(function (m) { return rarityWeight(m.rarity); });
    var total = weights.reduce(function (a, b) { return a + b; }, 0);
    var r = rng() * total;
    for (var i = 0; i < pool.length; i++) {
      r -= weights[i];
      if (r <= 0.0) return pool[i];
    }
    return pool[pool.length - 1];
  }

  var STARTERS = ["yao_she", "jing_saozhou", "yao_hu"].map(monsterById); // 蛇妖 / 扫帚精 / 狐妖

  // ───────────────────────── CaptureEngine ─────────────────────────
  var CaptureEngine = {
    ringCycleMs: function (rarity, assist) {
      var base = rarity === "FAN" ? 2600 : rarity === "LING" ? 2200 : rarity === "XIAN" ? 1900 : 1600;
      return Math.floor(base * (assist ? 1.4 : 1.0));
    },
    accuracy: function (tapRadius, minRadius, maxRadius, assist) {
      var span = Math.max(maxRadius - minRadius, 1);
      var raw = 1.0 - clamp((tapRadius - minRadius) / span, 0.0, 1.0);
      return assist ? clamp(raw * 0.8 + 0.2, 0.0, 1.0) : raw;
    },
    successProbability: function (accuracy, rarity, assist, captureMul) {
      captureMul = captureMul || 1.0;
      var rarityFactor = rarity === "FAN" ? 1.0 : rarity === "LING" ? 0.9 : rarity === "XIAN" ? 0.8 : 0.7;
      var eff = clamp(accuracy + (assist ? 0.18 : 0.0), 0.0, 1.0);
      return clamp((eff * rarityFactor * 0.92 + 0.06) * captureMul, 0.0, 1.0);
    },
    resolve: function (accuracy, rarity, assist, rng, captureMul) {
      var p = CaptureEngine.successProbability(accuracy, rarity, assist, captureMul);
      var success = rng() < p;
      var struggled = rng() >= p;
      return { success: success, probability: p, struggled: struggled, accuracy: accuracy };
    },
  };

  // ───────────────────────── GachaEngine ─────────────────────────
  var GachaEngine = {
    POOL_LING: 0.40, POOL_XIAN: 0.45, POOL_SHEN: 0.15,
    SOFT_PITY_START: 49, HARD_PITY: 90, SOFT_PITY_STEP: 0.02,
    SINGLE_LINGYU: 60, TEN_LINGYU: 540, DUPLICATE_YAODAN: 10,
    signFor: function (rarity) {
      return rarity === "SHEN" ? "SHANG_SHANG" : rarity === "XIAN" ? "SHANG" : "ZHONG";
    },
    rollRarity: function (state, rng) {
      var pity = state.sinceShen;
      var shenRate;
      if (pity >= GachaEngine.HARD_PITY - 1) shenRate = 1.0;
      else if (pity >= GachaEngine.SOFT_PITY_START)
        shenRate = Math.min(GachaEngine.POOL_SHEN + (pity - GachaEngine.SOFT_PITY_START + 1) * GachaEngine.SOFT_PITY_STEP, 1.0);
      else shenRate = GachaEngine.POOL_SHEN;
      var r = rng();
      if (r < shenRate) return "SHEN";
      if (r < shenRate + GachaEngine.POOL_XIAN) return "XIAN";
      return "LING";
    },
    _pickFrom: function (rarity, rng) {
      var pool = byRarity(rarity);
      if (pool.length === 0) return randomEncounter(rng);
      var idx = Math.min(Math.floor(rng() * pool.length), pool.length - 1);
      return pool[idx];
    },
    pull: function (state, rng) {
      var rarity = GachaEngine.rollRarity(state, rng);
      if (rarity === "SHEN") state.sinceShen = 0; else state.sinceShen++;
      var mon = GachaEngine._pickFrom(rarity, rng);
      return { monsterId: mon.id, rarity: rarity, sign: GachaEngine.signFor(rarity) };
    },
    tenPull: function (state, rng) {
      var results = [];
      for (var i = 0; i < 10; i++) results.push(GachaEngine.pull(state, rng));
      if (!results.some(function (r) { return RARITY_ORDER.indexOf(r.rarity) >= RARITY_ORDER.indexOf("LING"); })) {
        var last = results.length - 1;
        var up = GachaEngine._pickFrom("LING", rng);
        results[last] = { monsterId: up.id, rarity: "LING", sign: "ZHONG" };
      }
      return results;
    },
  };

  // ───────────────────────── TalentEngine ─────────────────────────
  var TalentEngine = {
    MAX_LAYER: 3, BASE_TALENT: 3, TALENT_PER_BREAK: 1,
    ATK_PCT_PER_LAYER: 0.06, DEF_PCT_PER_LAYER: 0.06, HP_PCT_PER_LAYER: 0.04, SPD_PCT_PER_LAYER: 0.05,
    talentCap: function (inst) { return TalentEngine.BASE_TALENT + inst.breakthrough * TalentEngine.TALENT_PER_BREAK; },
    layersOf: function (inst, branch) { return (inst.talents && inst.talents[branch]) || 0; },
    allocated: function (inst) {
      var t = inst.talents || {}; var s = 0;
      for (var k in t) if (t.hasOwnProperty(k)) s += t[k];
      return s;
    },
    canAssign: function (inst, branch) {
      return TalentEngine.layersOf(inst, branch) < TalentEngine.MAX_LAYER &&
        TalentEngine.allocated(inst) < TalentEngine.talentCap(inst);
    },
    assign: function (inst, branch) {
      if (!TalentEngine.canAssign(inst, branch)) return null;
      var next = {};
      var t = inst.talents || {};
      for (var k in t) if (t.hasOwnProperty(k)) next[k] = t[k];
      next[branch] = TalentEngine.layersOf(inst, branch) + 1;
      return clone(inst, { talents: next });
    },
    atkMult: function (inst) { return 1.0 + TalentEngine.ATK_PCT_PER_LAYER * TalentEngine.layersOf(inst, "ATTACK"); },
    defMult: function (inst) { return 1.0 + TalentEngine.DEF_PCT_PER_LAYER * TalentEngine.layersOf(inst, "DEFENSE"); },
    hpMult: function (inst) { return 1.0 + TalentEngine.HP_PCT_PER_LAYER * TalentEngine.layersOf(inst, "DEFENSE"); },
    spdMult: function (inst) { return 1.0 + TalentEngine.SPD_PCT_PER_LAYER * TalentEngine.layersOf(inst, "SUPPORT"); },
  };

  // ───────────────────────── CultivationEngine ─────────────────────────
  function statsAtLevel(mon, level) {
    var g = RARITY_GROWTH[mon.rarity];
    var lc = 1.0 + (level - 1) * 0.02;
    return {
      atk: Math.max(Math.floor(mon.baseAtk * g * lc), 1),
      def: Math.max(Math.floor(mon.baseDef * g * lc), 1),
      hp: Math.max(Math.floor(mon.baseHp * g * lc), 1),
      spd: Math.max(Math.floor(mon.baseSpd * g), 1), // 速度不随等级系数
    };
  }
  var CultivationEngine = {
    BASE_LEVEL_CAP: 30, MAX_BREAKTHROUGH: 5, LEVEL_CAP_PER_BREAK: 5, STAT_MULT_PER_BREAK: 0.08,
    EVOLVE_LEVEL_REQ: 25, TRIBULATE_STAT_BONUS: 0.25, TRIBULATE_COST_YAODAN: 200,
    levelCap: function (inst) { return CultivationEngine.BASE_LEVEL_CAP + inst.breakthrough * CultivationEngine.LEVEL_CAP_PER_BREAK; },
    statMult: function (inst) { return 1.0 + inst.breakthrough * CultivationEngine.STAT_MULT_PER_BREAK; },
    expForLevel: function (level) { var lv = level < 1 ? 1 : level; return 100 + (lv - 1) * 25; },
    gainExp: function (inst, amount) {
      if (amount <= 0) return inst;
      var lvl = inst.level, xp = inst.exp + amount;
      var cap = CultivationEngine.levelCap(inst);
      while (lvl < cap && xp >= CultivationEngine.expForLevel(lvl)) {
        xp -= CultivationEngine.expForLevel(lvl);
        lvl += 1;
      }
      if (lvl >= cap) {
        var top = CultivationEngine.expForLevel(Math.max(cap - 1, 1));
        xp = Math.min(xp, top);
      }
      return clone(inst, { level: lvl, exp: xp });
    },
    breakthroughLevelReq: function (bt) { return 20 + bt * 5; },
    canBreakthrough: function (inst) {
      return inst.breakthrough < CultivationEngine.MAX_BREAKTHROUGH &&
        inst.level >= CultivationEngine.breakthroughLevelReq(inst.breakthrough);
    },
    breakthroughCost: function (bt) { return 20 + bt * 15; },
    breakthrough: function (inst) {
      if (!CultivationEngine.canBreakthrough(inst)) return null;
      return clone(inst, { breakthrough: inst.breakthrough + 1 });
    },
    canEvolve: function (inst) {
      var def = monsterById(inst.defId); if (!def) return false;
      return !!def.evolveTo && inst.level >= CultivationEngine.EVOLVE_LEVEL_REQ;
    },
    maxTribulations: function (rarity) { return RARITY_ORDER.indexOf("SHEN") - RARITY_ORDER.indexOf(rarity); },
    evolve: function (inst) {
      var def = monsterById(inst.defId); if (!def) return null;
      var to = def.evolveTo; if (!to) return null;
      if (inst.level < CultivationEngine.EVOLVE_LEVEL_REQ) return null;
      var newDef = monsterById(to);
      var newTrib = Math.min(inst.tribulations, CultivationEngine.maxTribulations(newDef.rarity));
      return clone(inst, { defId: to, tribulations: newTrib });
    },
    tribulateMult: function (inst) { return 1.0 + inst.tribulations * CultivationEngine.TRIBULATE_STAT_BONUS; },
    currentRarity: function (inst) {
      var def = monsterById(inst.defId); if (!def) return null;
      var idx = Math.min(def.rarity ? RARITY_ORDER.indexOf(def.rarity) + inst.tribulations : 0, RARITY_ORDER.indexOf("SHEN"));
      return RARITY_ORDER[idx];
    },
    canTribulate: function (inst) {
      var def = monsterById(inst.defId); if (!def) return false;
      if (inst.tribulations >= CultivationEngine.maxTribulations(def.rarity)) return false;
      return inst.breakthrough >= CultivationEngine.MAX_BREAKTHROUGH && inst.level >= CultivationEngine.levelCap(inst);
    },
    tribulateCost: function (inst) { return CultivationEngine.TRIBULATE_COST_YAODAN * (inst.tribulations + 1); },
    tribulate: function (inst) {
      if (!CultivationEngine.canTribulate(inst)) return null;
      return clone(inst, { tribulations: inst.tribulations + 1 });
    },
    effectiveStats: function (inst) {
      var def = monsterById(inst.defId); if (!def) return null;
      var base = statsAtLevel(def, inst.level);
      var mult = CultivationEngine.statMult(inst) * CultivationEngine.tribulateMult(inst);
      return {
        atk: Math.max(Math.floor(base.atk * mult * TalentEngine.atkMult(inst)), 1),
        def: Math.max(Math.floor(base.def * mult * TalentEngine.defMult(inst)), 1),
        hp: Math.max(Math.floor(base.hp * mult * TalentEngine.hpMult(inst)), 1),
        spd: Math.max(Math.floor(base.spd * mult * TalentEngine.spdMult(inst)), 1),
      };
    },
    evolutionChain: function (startDefId) {
      var chain = [], cur = monsterById(startDefId), seen = {};
      while (cur && !seen[cur.id]) {
        chain.push(cur.id); seen[cur.id] = true;
        cur = cur.evolveTo ? monsterById(cur.evolveTo) : null;
      }
      return chain;
    },
  };

  // ───────────────────────── BattleEngine ─────────────────────────
  function makeCombatant(inst) {
    var mon = monsterById(inst.defId);
    var skills = skillsOf(mon).slice(0, 4);
    var eff = CultivationEngine.effectiveStats(inst);
    // 连携：取前 4 出战技能，识别已满足的连携组合
    var comboIds = skills.map(function (s) { return s.id; });
    var combos = ComboEngine.availableCombos(comboIds);
    return {
      monster: mon, instance: inst, level: inst.level, skills: skills,
      effective: eff, hp: eff.hp, maxHp: eff.hp,
      combos: combos,
      comboNames: combos.map(function (c) { return c.name; }),
      comboMul: ComboEngine.damageMultiplier(combos),
      comboHeal: ComboEngine.healPercent(combos),
      isAlive: function () { return this.hp > 0; },
    };
  }
  var BattleEngine = {
    levelCoef: function (level) { return 1.0 + (level - 1) * 0.02; },
    bestCounterSkill: function (attacker, defender) {
      if (!attacker.skills.length) return null;
      var best = null, bestScore = -1;
      attacker.skills.forEach(function (sk) {
        var score = elementMultiplier(sk.element, defender.monster.element) *
          categoryMultiplier(attacker.monster.category, defender.monster.category) *
          (sk.isDamage ? sk.power : 1);
        if (score > bestScore) { bestScore = score; best = sk; }
      });
      return best;
    },
    damage: function (attacker, defender, skill, rng, teamAttackMul, envMul, sectAttackMul) {
      // 忠实复刻 Kotlin BattleEngine.damage 纯净公式（不含连携；连携由战斗会话层施加，见 BattleSession.step）
      teamAttackMul = teamAttackMul || 1.0; envMul = envMul || 1.0; sectAttackMul = sectAttackMul || 1.0;
      var atk = attacker.effective.atk;
      var def = Math.max(defender.effective.def, 1);
      var elem = elementMultiplier(skill.element, defender.monster.element);
      var cat = categoryMultiplier(attacker.monster.category, defender.monster.category);
      var critChance = clamp(0.05 + skill.critBonus, 0.0, 1.0);
      var rnd = 0.9 + rng() * 0.1;
      var crit = rng() < critChance;
      var critMult = crit ? 1.5 : 1.0;
      var raw = (atk / def) * skill.power * BattleEngine.levelCoef(attacker.level) *
        elem * cat * critMult * rnd * teamAttackMul * envMul * sectAttackMul;
      var dmg = Math.max(Math.floor(raw), 1);
      return { damage: dmg, elementMult: elem, categoryMult: cat, envMul: envMul, crit: crit, skill: skill, label: elementLabel(skill.element, defender.monster.element) };
    },
  };

  // ───────────────────────── FormationEngine ─────────────────────────
  var FIVE_ELEMENTS = ["METAL", "WOOD", "EARTH", "WATER", "FIRE"];
  var FormationEngine = {
    PER_SYNERGY_MUL: 0.12, MAX_MUL: 0.60,
    evaluate: function (teamMons) {
      if (!teamMons.length) return [];
      var syn = [];
      var eles = teamMons.map(function (m) { return m.element; });
      var water = eles.filter(function (e) { return e === "WATER"; }).length;
      var fire = eles.filter(function (e) { return e === "FIRE"; }).length;
      if (water >= 2 && fire >= 2) syn.push({ id: "YINYANG", name: "阴阳调和", atkMul: 0.12, ultimateCharges: 1 });
      var catCount = {};
      teamMons.forEach(function (m) { catCount[m.category] = (catCount[m.category] || 0) + 1; });
      var top = 0; for (var k in catCount) top = Math.max(top, catCount[k]);
      if (top >= 3) syn.push({ id: "BAIYAO", name: "百妖夜行", atkMul: 0.12, ultimateCharges: 1 });
      if (Object.keys(catCount).length >= 3) syn.push({ id: "SANCAI", name: "三才阵", atkMul: 0.12, ultimateCharges: 1 });
      var haveAll = FIVE_ELEMENTS.every(function (e) { return eles.indexOf(e) >= 0; });
      if (haveAll) syn.push({ id: "XIANGSHENG", name: "相生阵", atkMul: 0.12, ultimateCharges: 2 });
      var fox = teamMons.filter(function (m) { return m.clan === "fox"; }).length;
      if (fox >= 3) syn.push({ id: "QINGQIU", name: "青丘幻月", atkMul: 0.12, ultimateCharges: 2 });
      return syn;
    },
    teamAttackMultiplier: function (synergies) {
      var total = synergies.reduce(function (a, s) { return a + s.atkMul; }, 0);
      return 1.0 + Math.min(total, FormationEngine.MAX_MUL);
    },
  };

  // ───────────────────────── SectTechEngine ─────────────────────────
  var SectTechEngine = {
    CONTRIBUTION_PER_CAPTURE: 50,
    NODES: [
      { id: "tech_gongfa", name: "攻伐之术", type: "ATTACK", maxLevel: 5, perLevel: 0.04, costBase: 20, costGrowth: 10 },
      { id: "tech_shouyu", name: "守御心经", type: "DEFENSE", maxLevel: 5, perLevel: 0.04, costBase: 20, costGrowth: 10 },
      { id: "tech_tipu", name: "体魄淬炼", type: "HP", maxLevel: 5, perLevel: 0.04, costBase: 20, costGrowth: 10 },
      { id: "tech_yuyao", name: "御妖真诀", type: "CAPTURE", maxLevel: 4, perLevel: 0.05, costBase: 30, costGrowth: 12 },
      { id: "tech_jingxiu", name: "经修玄法", type: "EXP", maxLevel: 5, perLevel: 0.05, costBase: 25, costGrowth: 12 },
      { id: "tech_ziliao", name: "资粮广积", type: "INCOME", maxLevel: 5, perLevel: 0.04, costBase: 25, costGrowth: 12 },
      { id: "tech_judi", name: "据点经营", type: "STRONGHOLD", maxLevel: 3, perLevel: 0.10, costBase: 40, costGrowth: 20, prereq: ["tech_gongfa"] },
      { id: "tech_maoshan_fu", name: "茅山符阵", type: "ATTACK", maxLevel: 3, perLevel: 0.06, costBase: 60, costGrowth: 30, prereq: ["tech_gongfa"], faction: "MAOSHAN" },
      { id: "tech_longhu_dan", name: "龙虎丹道", type: "HP", maxLevel: 3, perLevel: 0.06, costBase: 60, costGrowth: 30, prereq: ["tech_tipu"], faction: "LONGHUSHAN" },
      { id: "tech_gezao_qi", name: "阁皂奇门", type: "CAPTURE", maxLevel: 3, perLevel: 0.06, costBase: 60, costGrowth: 30, prereq: ["tech_yuyao"], faction: "GEZAO" },
    ],
    node: function (id) { return SectTechEngine.NODES.filter(function (n) { return n.id === id; })[0] || null; },
    researchCost: function (level, node) { return level >= node.maxLevel ? Infinity : node.costBase + node.costGrowth * level; },
    prereqMet: function (node, levels) {
      return (node.prereq || []).every(function (p) { return (levels[p] || 0) >= 1; });
    },
    canResearch: function (id, levels, faction, contribution) {
      var node = SectTechEngine.node(id); if (!node) return false;
      var lv = levels[id] || 0;
      if (lv >= node.maxLevel) return false;
      if (!SectTechEngine.prereqMet(node, levels)) return false;
      if (node.faction && node.faction !== faction) return false;
      return contribution >= SectTechEngine.researchCost(lv, node);
    },
    research: function (id, levels, faction, contribution) {
      if (!SectTechEngine.canResearch(id, levels, faction, contribution)) return null;
      var node = SectTechEngine.node(id);
      var lv = levels[id] || 0;
      var cost = SectTechEngine.researchCost(lv, node);
      var next = {}; for (var k in levels) if (levels.hasOwnProperty(k)) next[k] = levels[k];
      next[id] = lv + 1;
      return { levels: next, contribution: contribution - cost };
    },
    bonuses: function (levels, faction) {
      var b = { attack: 0, defense: 0, hp: 0, capture: 0, exp: 0, income: 0, stronghold: 0 };
      SectTechEngine.NODES.forEach(function (node) {
        if (node.faction && node.faction !== faction) return;
        var lv = levels[node.id] || 0; if (lv <= 0) return;
        b[node.type.toLowerCase()] += node.perLevel * lv;
      });
      return b;
    },
  };

  // ───────────────────────── EquipmentEngine ─────────────────────────
  var EQUIPMENTS = SH_DATA.equipments || [];
  function equipDefById(id) {
    for (var i = 0; i < EQUIPMENTS.length; i++) if (EQUIPMENTS[i].id === id) return EQUIPMENTS[i];
    return null;
  }
  var EQUIP_SLOT_LABEL = { WEAPON: "武器", ARMOR: "护甲", ACCESSORY: "饰品" };
  var EQUIP_STAT_LABEL = { ATK: "攻", DEF: "防", HP: "血", SPD: "速" };

  var EquipmentEngine = {
    AFFINITY_MULT: 1.2,
    FORGE_COST_YAODAN: 10,
    MAX_STAR: 5, STAR_BONUS: 0.10,
    TIER_LEVEL_BONUS: 10, TIER_PERLEVEL_BONUS: 0.03,
    MAX_SUBSTATS: 4, SUB_CAP: 15, TEMPER_STEP: 3,
    STAR_COST_BASE: 50, TIER_COST_BASE: 80, TEMPER_COST_BASE: 30,
    INHERIT_COST_YAODAN: 20,
    SUB_ORDER: ["ATK", "DEF", "HP", "SPD"],

    maxTier: function (rarity) { return RARITY_ORDER.indexOf(rarity) + 1; },
    maxLevelOf: function (def, oe) { return def.maxLevel + (oe.tier - 1) * EquipmentEngine.TIER_LEVEL_BONUS; },
    effectivePerLevel: function (def, oe) { return def.bonusPerLevel + (oe.tier - 1) * EquipmentEngine.TIER_PERLEVEL_BONUS; },
    starMult: function (oe) { return 1.0 + (oe.stars - 1) * EquipmentEngine.STAR_BONUS; },
    addStats: function (a, b) { return { atk: a.atk + b.atk, def: a.def + b.def, hp: a.hp + b.hp, spd: a.spd + b.spd }; },
    mainBonus: function (oe, def, elementMatches) {
      var mult = (1.0 + (oe.level - 1) * EquipmentEngine.effectivePerLevel(def, oe))
        * EquipmentEngine.starMult(oe) * (elementMatches ? EquipmentEngine.AFFINITY_MULT : 1.0);
      return {
        atk: Math.max(0, Math.floor(def.baseAtk * mult)),
        def: Math.max(0, Math.floor(def.baseDef * mult)),
        hp: Math.max(0, Math.floor(def.baseHp * mult)),
        spd: Math.max(0, Math.floor(def.baseSpd * mult)),
      };
    },
    subStatsBonus: function (oe) {
      var a = 0, d = 0, h = 0, s = 0;
      for (var i = 0; i < oe.substats.length; i++) {
        var st = oe.substats[i];
        if (st.stat === "ATK") a += st.value;
        else if (st.stat === "DEF") d += st.value;
        else if (st.stat === "HP") h += st.value;
        else if (st.stat === "SPD") s += st.value;
      }
      return { atk: a, def: d, hp: h, spd: s };
    },
    bonus: function (oe, def, elementMatches) {
      return EquipmentEngine.addStats(EquipmentEngine.mainBonus(oe, def, elementMatches), EquipmentEngine.subStatsBonus(oe));
    },
    bonusAtLevel: function (def, level, elementMatches) {
      return EquipmentEngine.bonus({ equipId: def.id, instanceId: "x", level: level, stars: 1, tier: 1, substats: [] }, def, elementMatches);
    },
    totalBonus: function (monster, equips) {
      var total = { atk: 0, def: 0, hp: 0, spd: 0 };
      for (var i = 0; i < equips.length; i++) {
        var o = equips[i];
        var d = equipDefById(o.equipId); if (!d) continue;
        total = EquipmentEngine.addStats(total, EquipmentEngine.bonus(o, d, d.element === monster.element));
      }
      return total;
    },
    // 升星
    canStarUp: function (oe) { return oe.stars < EquipmentEngine.MAX_STAR; },
    starCost: function (oe) { return EquipmentEngine.STAR_COST_BASE * oe.stars; },
    starUp: function (oe) { return EquipmentEngine.canStarUp(oe) ? Object.assign({}, oe, { stars: oe.stars + 1 }) : null; },
    // 突破
    canBreakthrough: function (oe, def) { return oe.tier < EquipmentEngine.maxTier(def.rarity); },
    tierCost: function (oe) { return EquipmentEngine.TIER_COST_BASE * oe.tier; },
    breakthrough: function (oe, def) { return EquipmentEngine.canBreakthrough(oe, def) ? Object.assign({}, oe, { tier: oe.tier + 1 }) : null; },
    // 淬炼
    canTemper: function (oe) {
      if (oe.substats.length >= EquipmentEngine.MAX_SUBSTATS)
        return oe.substats.some(function (s) { return s.value < EquipmentEngine.SUB_CAP; });
      return true;
    },
    temperUsed: function (oe) {
      return oe.substats.reduce(function (acc, s) { return acc + Math.ceil(s.value / EquipmentEngine.TEMPER_STEP); }, 0);
    },
    temperCost: function (oe) { return EquipmentEngine.TEMPER_COST_BASE * (EquipmentEngine.temperUsed(oe) + 1); },
    temper: function (oe) {
      if (!EquipmentEngine.canTemper(oe)) return null;
      var existing = {};
      oe.substats.forEach(function (s) { existing[s.stat] = true; });
      var nextNew = null;
      for (var i = 0; i < EquipmentEngine.SUB_ORDER.length; i++) {
        if (!existing[EquipmentEngine.SUB_ORDER[i]]) { nextNew = EquipmentEngine.SUB_ORDER[i]; break; }
      }
      var newList;
      if (nextNew != null && oe.substats.length < EquipmentEngine.MAX_SUBSTATS) {
        newList = oe.substats.concat([{ stat: nextNew, value: EquipmentEngine.TEMPER_STEP }]);
      } else {
        var idx = -1;
        for (var j = 0; j < oe.substats.length; j++) { if (oe.substats[j].value < EquipmentEngine.SUB_CAP) { idx = j; break; } }
        if (idx < 0) return null;
        newList = oe.substats.map(function (s, k) {
          return k === idx ? { stat: s.stat, value: Math.min(s.value + EquipmentEngine.TEMPER_STEP, EquipmentEngine.SUB_CAP) } : s;
        });
      }
      return Object.assign({}, oe, { substats: newList });
    },
    // 继承（合并较优）
    canInherit: function (src, dst) { return src.instanceId !== dst.instanceId; },
    inherit: function (src, dst) {
      if (!EquipmentEngine.canInherit(src, dst)) return null;
      var map = {};
      dst.substats.forEach(function (s) { map[s.stat] = s.value; });
      src.substats.forEach(function (s) { map[s.stat] = Math.max(map[s.stat] || 0, s.value); });
      var merged = [];
      for (var i = 0; i < EquipmentEngine.SUB_ORDER.length; i++) {
        var st = EquipmentEngine.SUB_ORDER[i];
        if (map[st] == null) continue;
        merged.push({ stat: st, value: map[st] });
        if (merged.length >= EquipmentEngine.MAX_SUBSTATS) break;
      }
      return Object.assign({}, dst, {
        level: Math.max(dst.level, src.level),
        stars: Math.max(dst.stars, src.stars),
        tier: Math.max(dst.tier, src.tier),
        substats: merged,
      });
    },
    canForge: function (oe) {
      var def = equipDefById(oe.equipId); if (!def) return false;
      return oe.level < EquipmentEngine.maxLevelOf(def, oe);
    },
  };

  // ───────────────────────── FusionEngine ─────────────────────────
  function _recipeKey(a, b) { return [a, b].sort().join("|"); }
  var FusionEngine = {
    FUSE_COST_YAODAN: 30,
    LEVEL_BONUS: 3,
    RECIPES: {
      // —— 四灵神兽合体 ——
      "gui_linggui|yao_jiao": "gui_xuanwu",           // 灵龟+蛟 → 玄武
      "niao_fenghuang|yao_jiuwei": "lingshou_zhuque", // 凤凰+九尾 → 朱雀
      "jing_shijing|yao_niu": "lingshou_qilin",       // 石精+牛妖 → 麒麟
      // —— 异兽/凶兽合体 ——
      "mang_tengshe|yao_long": "guai_jiuying",        // 腾蛇+龙 → 九婴
      // —— 五行生克合体 ——
      "jing_tongjing|tu_yao": "lingshou_pixiu",       // 铜镜精+兔妖 → 貔貅
      "gui_guijia|yao_fengli": "lingshou_baihu",      // 龟妖+风狸 → 白虎
    },
    recipeOutputs: function () {
      var out = [];
      for (var k in FusionEngine.RECIPES) out.push(FusionEngine.RECIPES[k]);
      return out;
    },
    canFuse: function (a, b, yaodan) { return a.instanceId !== b.instanceId && yaodan >= FusionEngine.FUSE_COST_YAODAN; },
    plan: function (a, b) {
      if (a.instanceId === b.instanceId) return null;
      var da = monsterById(a.defId); if (!da) return null;
      var db = monsterById(b.defId); if (!db) return null;
      var newDefId = FusionEngine.RECIPES[_recipeKey(a.defId, b.defId)];
      if (!newDefId) {
        var oa = RARITY_ORDER.indexOf(da.rarity), ob = RARITY_ORDER.indexOf(db.rarity);
        if (ob > oa) newDefId = b.defId;
        else if (oa > ob) newDefId = a.defId;
        else {
          var sa = da.baseAtk + da.baseDef + da.baseHp + da.baseSpd;
          var sb = db.baseAtk + db.baseDef + db.baseHp + db.baseSpd;
          newDefId = (sb >= sa) ? b.defId : a.defId;
        }
      }
      var newDef = monsterById(newDefId); if (!newDef) return null;
      var bt = Math.min(a.breakthrough, b.breakthrough);
      var cap = CultivationEngine.BASE_LEVEL_CAP + bt * CultivationEngine.LEVEL_CAP_PER_BREAK;
      var newLevel = Math.min(Math.max(a.level, b.level) + FusionEngine.LEVEL_BONUS, cap);
      var skills = [];
      da.skillIds.concat(db.skillIds).forEach(function (s) { if (skills.indexOf(s) < 0) skills.push(s); });
      return { newDefId: newDefId, newLevel: newLevel, newBreakthrough: bt, newSkills: skills.slice(0, 4) };
    },
  };

  // ───────────────────────── Codex ─────────────────────────
  var CODEX_MILESTONES = [
    { threshold: 1, reward: "初入巡妖使（香火+20）" },
    { threshold: 5, reward: "小有所成（妖丹×50）" },
    { threshold: 10, reward: "妖王克星（十连祈妖券）" },
    { threshold: 20, reward: "图鉴近全（妖丹×100）" },
  ];
  function makeCodex() {
    var caught = {}; // id -> true (region)
    return {
      catch: function (id) { caught[id] = true; },
      has: function (id) { return !!caught[id]; },
      regionCaught: function () { return Object.keys(caught).length; },
      regionTotal: function () { return SH_DATA.monsters.length; },
      regionCompletion: function () { return Object.keys(caught).length / SH_DATA.monsters.length; },
      milestones: function () {
        var c = Object.keys(caught).length;
        return CODEX_MILESTONES.map(function (m) { return { threshold: m.threshold, reward: m.reward, reached: c >= m.threshold }; });
      },
      newlyUnlocked: function (prevCount) {
        var c = Object.keys(caught).length;
        return CODEX_MILESTONES.filter(function (m) { return m.threshold > prevCount && c >= m.threshold; })[0] || null;
      },
    };
  }

  // ───────────────────────── SocialEngine ─────────────────────────
  var FACTION = { MAOSHAN: "MAOSHAN", LONGHUSHAN: "LONGHUSHAN", GEZAO: "GEZAO" };
  var FACTION_LABEL = { MAOSHAN: "茅山", LONGHUSHAN: "龙虎山", GEZAO: "阁皂山" };
  var SocialEngine = {
    STRONGHOLD_ATTACK_BONUS: 1.10,
    canCapture: function (state, today) { return state.lastCaptureDay !== today; },
    capture: function (state, node, today) {
      if (!SocialEngine.canCapture(state, today)) return false;
      state.ownedNode = node; state.lastCaptureDay = today; return true;
    },
    attackBonus: function (owned) { return owned ? SocialEngine.STRONGHOLD_ATTACK_BONUS : 1.0; },
  };

  // ───────────────────────── LocationEngine ─────────────────────────
  var LocationEngine = {
    SOFT_SPEED_KMH: 30.0, TELEPORT_METERS: 5000.0, TELEPORT_WINDOW_SEC: 180,
    NODE_KEYS: {
      "31.23:121.47": "城隍桥", "30.25:120.16": "古庙",
      "25.03:102.71": "老槐树", "30.62:122.08": "东海之滨",
    },
    STRONGHOLD_NODES: ["城隍桥", "古庙", "老槐树", "东海之滨"],
    nodeCoordinates: function () {
      return Object.keys(LocationEngine.NODE_KEYS).map(function (k) {
        var parts = k.split(":");
        return { lat: parseFloat(parts[0]), lng: parseFloat(parts[1]), name: LocationEngine.NODE_KEYS[k] };
      });
    },
    regionElementBias: function (theme) {
      return theme === "SHANLIN" ? "WOOD" : theme === "SHUIYU" ? "WATER" : theme === "GUMU" ? "EARTH" : "WATER";
    },
    resolveRegion: function (ctx) {
      var a = (Math.floor(ctx.lat * 1000)) ^ (Math.floor(ctx.lng * 1000));
      var themes = ["SHANLIN", "SHUIYU", "GUMU", "DONGHAI"];
      return themes[Math.abs(a) % themes.length];
    },
    isNight: function (ctx) { return ctx.hourOfDay === 23 || ctx.hourOfDay === 0; },
    haversine: function (lat1, lng1, lat2, lng2) {
      var R = 6371000.0;
      var dLat = (lat2 - lat1) * Math.PI / 180, dLng = (lng2 - lng1) * Math.PI / 180;
      var a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2);
      return R * 2 * Math.asin(Math.sqrt(a));
    },
    spawnIntegrity: function (ctx) {
      if (ctx.prevLat == null || ctx.prevLng == null)
        return { ok: true, reason: "首次定位", tier: "NORMAL", degraded: false };
      var dt = ctx.ts - ctx.prevTs;
      if (dt <= 0) return { ok: true, reason: "时间无效-跳过", tier: "NORMAL", degraded: false };
      var dist = LocationEngine.haversine(ctx.prevLat, ctx.prevLng, ctx.lat, ctx.lng);
      var speed = dist / dt * 3.6;
      if (dist >= LocationEngine.TELEPORT_METERS && dt <= LocationEngine.TELEPORT_WINDOW_SEC)
        return { ok: false, reason: "检测到瞬移：" + Math.round(dist) + " 米 / " + dt + " 秒", tier: "REJECTED", degraded: true, speed: speed };
      if (speed > LocationEngine.SOFT_SPEED_KMH)
        return { ok: true, reason: "速度过快(降品质)：" + Math.round(speed) + " km/h", tier: "DEGRADED", degraded: true, speed: speed };
      return { ok: true, reason: "正常", tier: "NORMAL", degraded: false, speed: speed };
    },
    _weightOf: function (m, ctx, degraded) {
      var w = rarityWeight(m.rarity);
      if (degraded) {
        if (m.rarity === "SHEN") return 0.0;
        if (m.rarity === "XIAN") w *= 0.2;
      }
      if (m.element === LocationEngine.regionElementBias(LocationEngine.resolveRegion(ctx))) w *= 1.8;
      if (ctx.solarTerm === "ZHONGYUAN" && m.category === "GUAI") w *= 2.0;
      if (ctx.solarTerm === "CHUNJIE" && m.isFestival) w *= 3.0;
      if (LocationEngine.isNight(ctx) && m.category === "GUAI") w *= 1.6;
      return w;
    },
    nearbySpawns: function (ctx, count, rng, degraded) {
      rng = rng || Math.random;
      var pool = SH_DATA.monsters;
      var weights = pool.map(function (m) { return LocationEngine._weightOf(m, ctx, degraded); });
      var total = weights.reduce(function (a, b) { return a + b; }, 0);
      var out = [];
      for (var i = 0; i < count; i++) {
        var r = rng() * total, chosen = pool[pool.length - 1];
        for (var j = 0; j < pool.length; j++) { r -= weights[j]; if (r <= 0.0) { chosen = pool[j]; break; } }
        out.push(chosen.id);
      }
      return out;
    },
    safeSpawns: function (ctx, count, rng) {
      var res = LocationEngine.spawnIntegrity(ctx);
      if (res.tier === "REJECTED") return [];
      return LocationEngine.nearbySpawns(ctx, count, rng, res.degraded);
    },
  };

  // ───────────────────────── ReputationEngine ─────────────────────────
  // 信誉分引擎（GDD 十六·公平与稳健）。与 LocationEngine.spawnIntegrity 配合：
  // 检测异常 → 扣信誉分 → 分层 → 软限制（观察层罕见怪 −30% / 受限层禁户外探索）。
  var ReputationEngine = {
    DEFAULT_SCORE: 100, MAX_SCORE: 100, MIN_SCORE: 0,
    RESTRICTED_BELOW: 60, OBSERVED_BELOW: 80,
    PENALTY: { TELEPORT: 18, SPEED: 12, UNKNOWN: 10 },
    // 信誉分层：<60 受限 / <80 观察 / 否则优秀
    tier: function (score) {
      var s = clamp(score, ReputationEngine.MIN_SCORE, ReputationEngine.MAX_SCORE);
      if (s < ReputationEngine.RESTRICTED_BELOW) return "RESTRICTED";
      if (s < ReputationEngine.OBSERVED_BELOW) return "OBSERVED";
      return "EXCELLENT";
    },
    tierLabel: function (score) {
      var t = ReputationEngine.tier(score);
      return t === "EXCELLENT" ? "优秀" : t === "OBSERVED" ? "观察" : "受限";
    },
    // 罕见（仙/神）妖刷怪倍率：观察层 −30%，其余正常
    rareSpawnMultiplier: function (score) {
      return ReputationEngine.tier(score) === "OBSERVED" ? 0.7 : 1.0;
    },
    // 是否可进行户外 LBS 探索：受限层禁止（仅可入秘境）
    canExploreLbs: function (score) {
      return ReputationEngine.tier(score) !== "RESTRICTED";
    },
    // 施加作弊扣分（钳制 [0,100]）
    applyPenalty: function (score, kind) {
      var pen = ReputationEngine.PENALTY[kind];
      if (pen == null) pen = ReputationEngine.PENALTY.UNKNOWN;
      return clamp(score - pen, ReputationEngine.MIN_SCORE, ReputationEngine.MAX_SCORE);
    },
    // 每日缓慢恢复 +1（钳制 [0,100]）
    dailyRecover: function (score) {
      return clamp(score + 1, ReputationEngine.MIN_SCORE, ReputationEngine.MAX_SCORE);
    },
  };

  // ───────────────────────── ComboEngine ─────────────────────────
  // 连携组合技（GDD 七·技能系统「连携组合技（同妖）」）。判定纯函数式。
  var ComboEngine = {
    // 连携定义表（技能对真实共现于妖池）
    COMBOS: [
      { id: "fuyaoganlu", name: "缚妖甘露", required: ["s_kunyao", "s_ganlin"], kind: "HEAL_PERCENT", value: 0.25, cd: 3 },
      { id: "kuangzhanlianzhan", name: "狂战连斩", required: ["s_liekong", "s_wanjian"], kind: "DAMAGE_MUL", value: 1.3, cd: 2 },
      { id: "xuanbinghanbi", name: "玄冰寒壁", required: ["s_hantan", "s_xuanbing"], kind: "DAMAGE_MUL", value: 1.2, cd: 3 },
      { id: "lianyanfentian", name: "烈焰焚天", required: ["s_fentian", "s_sanmei"], kind: "DAMAGE_MUL", value: 1.35, cd: 2 },
    ],
    // 给定出战技能 id 列表，返回已满足（两组件都在）的连携
    availableCombos: function (equippedSkillIds) {
      var set = {};
      equippedSkillIds.forEach(function (id) { set[id] = true; });
      return ComboEngine.COMBOS.filter(function (combo) {
        return combo.required.every(function (r) { return set[r]; });
      });
    },
    // 已激活连携伤害放大倍率（DAMAGE_MUL 相乘，封顶 1.6）
    damageMultiplier: function (combos) {
      var mul = combos.filter(function (c) { return c.kind === "DAMAGE_MUL"; })
        .reduce(function (acc, c) { return acc * c.value; }, 1.0);
      return Math.min(mul, 1.6);
    },
    // 已激活连携中的最大回血比例（HEAL_PERCENT）
    healPercent: function (combos) {
      var hs = combos.filter(function (c) { return c.kind === "HEAL_PERCENT"; });
      if (!hs.length) return 0.0;
      return hs.reduce(function (mx, c) { return Math.max(mx, c.value); }, 0.0);
    },
  };

  // ───────────────────────── WeatherEngine ─────────────────────────
  // 天气地形战斗（GDD 十九·长线留存）。天气/地形作为战斗环境变量偏置某行元素攻击倍率。
  var WeatherEngine = {
    // 某行元素在指定天气下的攻击偏置倍率（无偏置 1.0）
    weatherAttackMul: function (elem, weather) {
      if (weather === "CLEAR") return 1.0;
      if (weather === "RAIN") {
        if (elem === "WATER") return 1.3;
        if (elem === "FIRE") return 0.8;
        return 1.0;
      }
      if (weather === "SNOW") {
        if (elem === "FIRE") return 0.7;
        if (elem === "WATER") return 1.2;
        return 1.0;
      }
      if (weather === "WINDY") {
        if (elem === "WIND") return 1.3;
        if (elem === "EARTH") return 0.8;
        return 1.0;
      }
      return 1.0;
    },
    // 地形偏向乘子：攻击元素 == 地形偏向元素 → ×1.2，否则 ×1.0
    terrainAttackMul: function (elem, bias) {
      return (bias != null && elem === bias) ? 1.2 : 1.0;
    },
    // 综合环境乘子（天气 × 地形）
    envMultiplier: function (elem, weather, bias) {
      return WeatherEngine.weatherAttackMul(elem, weather) *
        WeatherEngine.terrainAttackMul(elem, bias);
    },
  };

  // ───────────────────────── FatigueEngine ─────────────────────────
  // 疲劳值 / 单日收益封顶（GDD 十六·公平与稳健·防脚本）。倍率仅作用于收益，不影响进度。
  var FatigueEngine = {
    MAX_FATIGUE: 100, SOFT_CAP: 60,
    FATIGUE_CATCH: 2, FATIGUE_BATTLE: 4, FATIGUE_DUNGEON: 8, FATIGUE_SIGNIN: 3,
    // 收益倍率：>=100 封顶 0.0 / >=60 减半 0.5 / 否则 1.0
    incomeMul: function (fatigue) {
      if (fatigue >= FatigueEngine.MAX_FATIGUE) return 0.0;
      if (fatigue >= FatigueEngine.SOFT_CAP) return 0.5;
      return 1.0;
    },
    // 累积疲劳并夹取 0..100
    addFatigue: function (current, delta) {
      return clamp(current + delta, 0, FatigueEngine.MAX_FATIGUE);
    },
    // 跨天重置为 0
    resetDaily: function () { return 0; },
    // 疲劳状态文案
    label: function (fatigue) {
      if (fatigue >= FatigueEngine.MAX_FATIGUE) return "透支·收益封顶";
      if (fatigue >= FatigueEngine.SOFT_CAP) return "疲劳·收益减半";
      return "充沛";
    },
  };

  // ───────────────────────── RankEngine ─────────────────────────
  // 排位赛段位天梯（GDD 二十·竞技与荣誉）。5 段位：炼气→筑基→金丹→元婴→化神。
  // 每段 POINTS_TO_PROMOTE=100 积满升段；胜+20、负-15；化神封顶 99、炼气保底 0。
  // 本引擎只负责段位积分数学，胜负结果由平台对局结算后回传（原型中以战斗胜负驱动）。
  // ───────────────────────── DailyResetEngine ─────────────────────────
  // 每日 0 点静默重置（收口 GDD 十六/十九/二十「每日」语义）。
  // 与 shared DailyResetEngine.kt / DailyResetScheduler.kt 零漂移对应：
  //  - isCrossDay 为唯一权威跨天判据（首启空串或未到今日即跨天）；
  //  - TARGETS 为 7 项每日系统权威清单；
  //  - resolvePending 跨天返回全部 7 项、同日返回空（调用方不改动字段）；
  //  - DailyResetScheduler 提供 seasonsElapsed（赛季推进，复用 SeasonEngine 常量）。
  // ══════════════════════════════════════════════════════════════
  // 平台壳 Mock 层（选型落地：高德地图/AR维持降级/本地导出/前台调度）
  // shared 纯函数契约全就绪，MockPlatform 做胶水层，让平台接口在原型可调用可验证
  // ══════════════════════════════════════════════════════════════
  var MockPlatform = {
    // 🗺 地图 SDK 适配：高德地图（选型）+ 降级固定坐标 POI
    mapSdk: {
      available: true,          // mock 永远可用（原型内降级为固定坐标列表）
      overlayPois: function (pois) { return true; },
      setPlayerLayer: function (lat, lng) { return true; },
      focusRegion: function (lat, lng, zoom) { return true; },
      // buildPois 复用 LocationEngine.nodeCoordinates 真源（零漂移复刻 shared MapSdkAdapter.buildPois）
      buildPois: function () {
        var nodes = LocationEngine.nodeCoordinates();
        var strongholds = LocationEngine.STRONGHOLD_NODES;
        return nodes.map(function (node) {
          return { id: node.name, name: node.name, lat: node.lat, lng: node.lng,
                   kind: strongholds.indexOf(node.name) >= 0 ? "STRONGHOLD" : "LINGMAI" };
        });
      },
    },
    // 🎥 AR 收妖：维持 UNSUPPORTED 降级（选型结论，UI 自动切国风卷轴背景）
    arCapture: {
      status: "UNSUPPORTED",   // 默认不支持，原型内 UI 切卷轴
      requestPermission: function () { return "PERMISSION_DENIED"; },
      beginSession: function () { return false; },
      endSession: function () {},
    },
    // ☁️ 云同步：本地 JSON + 手动导出导入（选型，原型内 SaveExportEngine 已就绪）
    cloudSync: {
      // 原型内用 localStorage 模拟；shared CloudSyncEngine 的纯函数 merge/checksum 已由 Python verify 覆盖
      backendType: "LOCAL_JSON",
      upload: function (envelopeJson) { return true; },
      download: function () { return null; },
    },
    // ⏰ 每日重置调度：前台激活时检查 resolvePending（选型，零成本）
    dailyResetScheduler: {
      available: true,
      registered: true,
      // 应用前台激活时调用（原型每次 makePlayer / render 都会触发一次）
      checkAndReset: function (lastResetDay, today) {
        return DailyResetEngine.isCrossDay(lastResetDay, today) ? DailyResetEngine.TARGETS : [];
      },
    },
    // 选型决策快照（写入引擎供原型 UI 展示 + 跨会话追溯）
    decision: {
      mapSdk: "高德地图 SDK（国内双端成熟）+ 降级固定坐标",
      arCapture: "维持 UNSUPPORTED，UI 自动切国风卷轴背景（零开发成本）",
      cloudSync: "本地 JSON + SaveExportEngine 手动导出导入 + 可选 iCloud Drive",
      dailyReset: "前台激活时 resolvePending（复用 DailyResetEngine.isCrossDay 纯函数）",
      socialServer: "先不做，非核心玩法",
      date: "2026-10-01",
    },
  };

  var DailyResetEngine = {
    TARGETS: [
      { id: "quests",       desc: "每日任务刷新" },
      { id: "secret_realm", desc: "在家秘境免费次数回满 (3)" },
      { id: "dungeon",      desc: "副本当日进入计数清零" },
      { id: "shop",         desc: "引妖香每日购买上限重置" },
      { id: "stronghold",   desc: "据点战占领节点重置" },
      { id: "reputation",   desc: "信誉分每日缓慢恢复 +1" },
      { id: "fatigue",      desc: "疲劳值每日重置为 0" },
    ],
    TARGET_COUNT: 7,
    // 跨天判定（纯函数，与 Kotlin isCrossDay 一致）
    isCrossDay: function (lastResetDay, today) {
      return lastResetDay === "" || lastResetDay !== today;
    },
    // 待重置清单：跨天返 7 项，同日返空（同日调用方不应改动任何字段）
    resolvePending: function (lastResetDay, today) {
      return DailyResetEngine.isCrossDay(lastResetDay, today) ? DailyResetEngine.TARGETS : [];
    },
  };

  // 平台调度契约（与 shared DailyResetScheduler. 对应）：仅纯函数，平台壳待选型。
  // 常量与 shared SeasonEngine 同源（DAY_MS=86400000 / SEASON_LENGTH_DAYS=90）。
  var DailyResetScheduler = {
    STATUS: ["idle", "registered", "suspended", "unavailable"],
    STATUS_COUNT: 4,
    DAY_MS: 86400000,
    SEASON_LENGTH_DAYS: 90,
    // 赛季推进：给定赛季开始 ms 与当前 ms，返回已跨过的赛季数（0 仍同赛季）
    seasonsElapsed: function (startMs, nowMs) {
      if (nowMs < startMs) return 0;
      var elapsedDays = Math.floor((nowMs - startMs) / DailyResetScheduler.DAY_MS);
      return Math.floor(elapsedDays / DailyResetScheduler.SEASON_LENGTH_DAYS);
    },
  };

  // 经济与变现引擎（GDD 十四·经济与变现）：引妖香（香火回补，耗灵玉、日购上限防通胀）。
  // 纯函数复刻 shared ShopEngine.kt；商店每日购买上限重置由 GameState.maybeResetDaily 处理
  // （p.shopDay / p.shopIncenseBought，与 Kotlin ShopState 一一对应）。
  var ShopEngine = {
    INCENSE_REFILL_COST_LINGYU: 30,  // 1 份引妖香花费灵玉
    INCENSE_REFILL_AMOUNT: 60,       // 回补香火量
    INCENSE_DAILY_BUY_CAP: 3,        // 日购上限（防通胀）
    // 是否可购：灵玉足够 + 当日未达上限（lastBuyDay≠today 视为 0）
    canBuyIncense: function (state, today, lingyu) {
      if (lingyu < this.INCENSE_REFILL_COST_LINGYU) return false;
      var bought = (state.lastBuyDay === today) ? state.incenseBoughtToday : 0;
      return bought < this.INCENSE_DAILY_BUY_CAP;
    },
    // 购买引妖香：成功返回 {amount, cost} 并就地更新 state（lastBuyDay/incenseBoughtToday），否则 null。
    // 调用方负责把 amount/cost 并入玩家并写回对应字段。
    buyIncense: function (state, today, lingyu) {
      if (!this.canBuyIncense(state, today, lingyu)) return null;
      if (state.lastBuyDay !== today) { state.lastBuyDay = today; state.incenseBoughtToday = 0; }
      state.incenseBoughtToday += 1;
      return { amount: this.INCENSE_REFILL_AMOUNT, cost: this.INCENSE_REFILL_COST_LINGYU };
    },
  };

  // 赛季通行证引擎（GDD 十九·长线留存：90 天双轨）。
  // 纯函数复刻 shared SeasonEngine.kt：赛季经验→档位、免费/付费双轨奖励；时间以 ms epoch 传入，确定性可断言。
  // 本原型不引入 shared 未定义的接缝：仅复刻 SeasonEngine 纯函数 + 常量，赛季状态的累加/领取/滚动由下方 Player 方法逐一零漂移复刻 GameState 对应接线。
  var SeasonEngine = {
    SEASON_LENGTH_DAYS: 90,
    XP_PER_TIER: 100,
    TIERS: 60,
    DAY_MS: 86400000,
    XP_CATCH: 5,
    XP_BATTLE: 10,
    XP_DUNGEON: 15,
    XP_SIGNIN: 8,
    // 赛季内第几天：未开始 0；进行中 1..90；已结束 >90
    dayIndex: function (startMs, nowMs) {
      if (startMs <= 0 || nowMs < startMs) return 0;
      return Math.floor((nowMs - startMs) / this.DAY_MS) + 1;
    },
    // 是否处于赛季进行中
    inSeason: function (startMs, nowMs) {
      var d = this.dayIndex(startMs, nowMs);
      return d >= 1 && d <= this.SEASON_LENGTH_DAYS;
    },
    // 剩余天数：未开始满长；已结束 0
    daysRemaining: function (startMs, nowMs) {
      var d = this.dayIndex(startMs, nowMs);
      if (d <= 0) return this.SEASON_LENGTH_DAYS;
      if (d > this.SEASON_LENGTH_DAYS) return 0;
      return this.SEASON_LENGTH_DAYS - d + 1;
    },
    // 累计经验 → 已解锁档位（封顶 TIERS）
    tierFromXp: function (xp) { return Math.min(Math.floor(xp / this.XP_PER_TIER), this.TIERS); },
    // 达到某档所需累计经验阈值
    xpForTier: function (tier) { return tier * this.XP_PER_TIER; },
    // 免费轨道某档奖励（每 10 档里程碑更厚）
    freeReward: function (tier) {
      if (tier % 10 === 0) return { incense: 200, lingyu: 0, yaodan: 15 };
      return { incense: 30, lingyu: 0, yaodan: 3 };
    },
    // 付费轨道某档奖励（每 10 档里程碑更厚）
    paidReward: function (tier) {
      if (tier % 10 === 0) return { incense: 0, lingyu: 60, yaodan: 20 };
      return { incense: 0, lingyu: 10, yaodan: 5 };
    },
  };

  // 荣誉引擎（GDD 二十·竞技与荣誉：成就/勋章/称号，纯函数复刻 shared HonorEngine.kt）。
  // 消费 HonorProfile（玩家聚合画像），输出已解锁勋章/称号；无外部依赖，可独立断言。
  var HonorEngine = {
    NO_TITLE: "",
    MEDALS: [
      { id: "medal_bronze",  name: "青铜妖士", desc: "达成 1 项成就", req: 1 },
      { id: "medal_silver",  name: "白银妖师", desc: "达成 4 项成就", req: 4 },
      { id: "medal_gold",    name: "黄金妖尊", desc: "达成 8 项成就", req: 8 },
      { id: "medal_diamond", name: "钻石妖圣", desc: "达成全部成就",   req: 2147483647 }, // Int.MAX_VALUE 等价
    ],
    TITLES: [
      { id: "title_shouyao",   name: "收妖大师", desc: "达成任意 1 项成就" },
      { id: "title_xunyao",    name: "巡妖使",   desc: "图鉴完成度 ≥ 30%" },
      { id: "title_tuojie",    name: "渡劫真君", desc: "完成 1 次渡劫飞升" },
      { id: "title_huashen",   name: "化神尊者", desc: "段位达到化神" },
      { id: "title_tujian",    name: "山海通志", desc: "图鉴完成度 100%" },
      { id: "title_dacheng",   name: "妖志大成", desc: "达成全部成就" },
      { id: "title_yingxiong", name: "山海英雄", desc: "达成 6 项成就" },
      { id: "title_zhuanji",   name: "妖志宗师", desc: "图鉴完成度 ≥ 80%" },
      { id: "title_jiuzhuan",  name: "九转真君", desc: "完成 3 次渡劫飞升" },
      { id: "title_jindan",    name: "金丹道君", desc: "段位达到金丹" },
      { id: "title_yuanying",  name: "元婴老祖", desc: "段位达到元婴" },
      { id: "title_zhongliu",  name: "中流砥柱", desc: "达成 12 项成就" },
    ],
    unlockedMedals: function (p) {
      var maxV = 2147483647;
      return this.MEDALS.filter(function (m) {
        if (m.req === maxV) return p.achievementTotal > 0 && p.achievementCount >= p.achievementTotal;
        return p.achievementCount >= m.req;
      });
    },
    isUnlocked: function (id, p) {
      var self = this;
      switch (id) {
        case "title_shouyao":   return p.achievementCount >= 1;
        case "title_xunyao":    return p.codexRatio >= 30;
        case "title_tuojie":    return p.tribulationTotal >= 1;
        case "title_huashen":   return p.rankTierIndex >= 4;
        case "title_tujian":    return p.codexRatio >= 100;
        case "title_dacheng":   return p.achievementTotal > 0 && p.achievementCount >= p.achievementTotal;
        case "title_yingxiong": return p.achievementCount >= 6;
        case "title_zhuanji":   return p.codexRatio >= 80;
        case "title_jiuzhuan":  return p.tribulationTotal >= 3;
        case "title_jindan":    return p.rankTierIndex >= 2;
        case "title_yuanying":  return p.rankTierIndex >= 3;
        case "title_zhongliu":  return p.achievementCount >= 12;
        default: return false;
      }
    },
    unlockedTitles: function (p) {
      var self = this;
      return this.TITLES.filter(function (t) { return self.isUnlocked(t.id, p); });
    },
  };

  // 运营活动引擎（GDD「活动·节气复玩 / 年节限时」，纯函数复刻 shared ActivityEngine.kt）。
  // 日期区间 YYYY-MM-DD 字符串字典序即日期序，乘性聚合三类乘子（capture/exp/income）。
  // 活动为运营配置、不进存档，按 lastDailyReset 查询当日生效活动。
  var ActivityEngine = {
    ACTIVITIES: [
      { id: "act_zhongyuan",   name: "中元鬼节",   kind: "FESTIVAL",        start: "2026-08-28", end: "2026-09-01", captureMul: 1.2 },
      { id: "act_chunjie",     name: "新春嘉年华", kind: "FESTIVAL",        start: "2026-02-16", end: "2026-02-22", captureMul: 1.3 },
      { id: "act_capture_rally", name: "收妖大作战", kind: "CAPTURE_RALLY",   start: "2026-08-15", end: "2026-08-21", captureMul: 1.15 },
      { id: "act_battle_rally",  name: "论道大会",   kind: "BATTLE_RALLY",    start: "2026-08-15", end: "2026-08-21", expMul: 1.25, incomeMul: 1.2 },
      { id: "act_login",       name: "勤修有礼",   kind: "LOGIN",           start: "2026-08-15", end: "2026-08-21", incomeMul: 1.1 },
      { id: "act_zhongqiu",    name: "中秋月圆",   kind: "FESTIVAL",        start: "2026-09-25", end: "2026-09-27", captureMul: 1.25 },
      { id: "act_guoqing",     name: "国庆盛典",   kind: "BATTLE_RALLY",    start: "2026-10-01", end: "2026-10-07", expMul: 1.2, incomeMul: 1.15 },
    ],
    KIND_LABEL: { FESTIVAL: "节日", CAPTURE_RALLY: "收妖活动", BATTLE_RALLY: "对战活动", LOGIN: "登录有礼" },
    isActive: function (a, today) {
      return today >= a.start && today <= a.end;
    },
    active: function (today) {
      if (!today) return [];
      var self = this;
      return this.ACTIVITIES.filter(function (a) { return self.isActive(a, today); });
    },
    aggregate: function (list) {
      var c = 1.0, e = 1.0, inc = 1.0;
      for (var i = 0; i < list.length; i++) {
        if (list[i].captureMul) c *= list[i].captureMul;
        if (list[i].expMul) e *= list[i].expMul;
        if (list[i].incomeMul) inc *= list[i].incomeMul;
      }
      return { captureMul: c, expMul: e, incomeMul: inc };
    },
  };

  // 新手引导 + 7 日留存（GDD 十八，纯函数复刻 shared TutorialEngine.kt + GameState 接线）。
  // 5 步引导（前 3 步强制）+ 4 项功能按天解锁 + 签到递增奖励（封顶 70）。
  var TutorialEngine = {
    STEPS: [
      { order: 1, forceable: true,  title: "撞见凡级妖，点按+光环收服" },
      { order: 2, forceable: true,  title: "查看灵脉地图，入秘境" },
      { order: 3, forceable: true,  title: "首场 2.5D 对战，高亮克制技" },
      { order: 4, forceable: false, title: "三派择门（茅山/龙虎山/阁皂）" },
      { order: 5, forceable: false, title: "签到点 + 图鉴 + 今日目标" },
    ],
    LAST_ORDER: 5,
    REALM_FEATURES: [
      { id: "REALM",   name: "秘境", unlockDay: 2 },
      { id: "DUNGEON", name: "副本", unlockDay: 3 },
      { id: "GACHA",   name: "祈妖", unlockDay: 4 },
      { id: "SOCIAL",  name: "社交", unlockDay: 5 },
    ],
    STEP_BY_ORDER: function (order) {
      for (var i = 0; i < this.STEPS.length; i++) if (this.STEPS[i].order === order) return this.STEPS[i];
      return null;
    },
    currentStep: function (completedOrder) {
      var self = this;
      var first = null;
      this.STEPS.forEach(function (s) { if (s.order > completedOrder && first === null) first = s; });
      return first;
    },
    isForced: function (step) { return step.order <= 3; },
    allDone: function (completedOrder, skipped) {
      return completedOrder >= this.LAST_ORDER || skipped;
    },
    unlockedFeatures: function (day) {
      return this.REALM_FEATURES.filter(function (f) { return f.unlockDay <= day; });
    },
    signInReward: function (day) { return Math.min(10 * day, 70); },
  };

  var RankEngine = {
    TIERS: ["炼气", "筑基", "金丹", "元婴", "化神"],
    MIN_TIER_INDEX: 0, MAX_TIER_INDEX: 4,
    POINTS_TO_PROMOTE: 100, WIN_POINTS: 20, LOSS_POINTS: 15,
    // 段位名 → 索引（未知返回 0）
    tierIndex: function (name) {
      var i = RankEngine.TIERS.indexOf(name);
      return i < 0 ? RankEngine.MIN_TIER_INDEX : i;
    },
    // 索引 → 段位名（越界夹取）
    tierName: function (index) {
      var i = clamp(index, RankEngine.MIN_TIER_INDEX, RankEngine.MAX_TIER_INDEX);
      return RankEngine.TIERS[i];
    },
    // 应用一场对局结果，返回 { tierIndex, points, promoted, demoted }
    applyResult: function (tierIndex, points, won) {
      var t = clamp(tierIndex, RankEngine.MIN_TIER_INDEX, RankEngine.MAX_TIER_INDEX);
      var p = clamp(points, 0, RankEngine.POINTS_TO_PROMOTE - 1); // 段内合法区间，抵御坏档/越界
      var promoted = false, demoted = false;
      if (won) {
        p += RankEngine.WIN_POINTS;
        while (p >= RankEngine.POINTS_TO_PROMOTE && t < RankEngine.MAX_TIER_INDEX) {
          p -= RankEngine.POINTS_TO_PROMOTE; t += 1; promoted = true;
        }
        if (t === RankEngine.MAX_TIER_INDEX) p = Math.min(p, RankEngine.POINTS_TO_PROMOTE - 1); // 化神封顶
      } else {
        p -= RankEngine.LOSS_POINTS;
        while (p < 0 && t > RankEngine.MIN_TIER_INDEX) {
          p += RankEngine.POINTS_TO_PROMOTE; t -= 1; demoted = true;
        }
        if (t === RankEngine.MIN_TIER_INDEX) p = Math.max(p, 0); // 炼气保底
      }
      return { tierIndex: t, points: p, promoted: promoted, demoted: demoted };
    },
    // 升段荣誉奖励（按新段位发少量货币，展示型荣誉为主）
    rankReward: function (tierIndex) {
      switch (clamp(tierIndex, RankEngine.MIN_TIER_INDEX, RankEngine.MAX_TIER_INDEX)) {
        case 1: return { incense: 30, lingyu: 0, yaodan: 0 };
        case 2: return { incense: 0, lingyu: 30, yaodan: 0 };
        case 3: return { incense: 60, lingyu: 30, yaodan: 0 };
        case 4: return { incense: 0, lingyu: 80, yaodan: 10 };
        default: return { incense: 0, lingyu: 0, yaodan: 0 };
      }
    },
  };

  // ───────────────────────── RankBanPickEngine ─────────────────────────
  // 排位赛 Ban/Pick 选将（GDD 二十·竞技与荣誉，补全 RankEngine 缺口）。
  // 纯逻辑、无随机、无平台依赖；双方各 ban 2 / pick 3，ban 并集移出可用池。
  // 全部操作用 DraftResult 枚举表达成功/各类非法态，天然具备入站防护。
  var RankBanPickEngine = {
    BAN_PER_SIDE: 2, PICK_PER_SIDE: 3,
    Side: { A: "A", B: "B" },
    DraftResult: {
      OK: "OK", INVALID_MONSTER: "INVALID_MONSTER", ALREADY_BANNED: "ALREADY_BANNED",
      BAN_LIMIT: "BAN_LIMIT", PICK_BANNED: "PICK_BANNED", ALREADY_PICKED: "ALREADY_PICKED",
      PICK_LIMIT: "PICK_LIMIT",
    },
    // 当前可用池 = 全妖池 − 双方 ban 并集
    availablePool: function (state) {
      var denied = {};
      (state.bannedA || []).forEach(function (id) { denied[id] = true; });
      (state.bannedB || []).forEach(function (id) { denied[id] = true; });
      var pool = {};
      for (var id in MONSTERS) if (MONSTERS.hasOwnProperty(id) && !denied[id]) pool[id] = true;
      return Object.keys(pool);
    },
    _mineBan: function (state, side) { return side === RankBanPickEngine.Side.A ? (state.bannedA || []) : (state.bannedB || []); },
    _minePick: function (state, side) { return side === RankBanPickEngine.Side.A ? (state.pickedA || []) : (state.pickedB || []); },
    // 任一方 ban 一只妖；返回 [newState, DraftResult]
    ban: function (state, side, id) {
      if (!MONSTERS.hasOwnProperty(id)) return [state, RankBanPickEngine.DraftResult.INVALID_MONSTER];
      var denied = {};
      (state.bannedA || []).forEach(function (x) { denied[x] = true; });
      (state.bannedB || []).forEach(function (x) { denied[x] = true; });
      if (denied[id]) return [state, RankBanPickEngine.DraftResult.ALREADY_BANNED];
      var mine = RankBanPickEngine._mineBan(state, side);
      if (mine.length >= RankBanPickEngine.BAN_PER_SIDE) return [state, RankBanPickEngine.DraftResult.BAN_LIMIT];
      var ns = {
        bannedA: state.bannedA || [], bannedB: state.bannedB || [],
        pickedA: state.pickedA || [], pickedB: state.pickedB || [],
      };
      if (side === RankBanPickEngine.Side.A) ns.bannedA = mine.concat([id]);
      else ns.bannedB = mine.concat([id]);
      return [ns, RankBanPickEngine.DraftResult.OK];
    },
    // 任一方 pick 一只妖（必须从可用池、无重复、未超上限）
    pick: function (state, side, id) {
      if (!MONSTERS.hasOwnProperty(id)) return [state, RankBanPickEngine.DraftResult.INVALID_MONSTER];
      var denied = {};
      (state.bannedA || []).forEach(function (x) { denied[x] = true; });
      (state.bannedB || []).forEach(function (x) { denied[x] = true; });
      if (denied[id]) return [state, RankBanPickEngine.DraftResult.PICK_BANNED];
      var mine = RankBanPickEngine._minePick(state, side);
      if (mine.indexOf(id) >= 0) return [state, RankBanPickEngine.DraftResult.ALREADY_PICKED];
      if (mine.length >= RankBanPickEngine.PICK_PER_SIDE) return [state, RankBanPickEngine.DraftResult.PICK_LIMIT];
      var ns = {
        bannedA: state.bannedA || [], bannedB: state.bannedB || [],
        pickedA: state.pickedA || [], pickedB: state.pickedB || [],
      };
      if (side === RankBanPickEngine.Side.A) ns.pickedA = mine.concat([id]);
      else ns.pickedB = mine.concat([id]);
      return [ns, RankBanPickEngine.DraftResult.OK];
    },
    // 选将是否完成：双方都 ban 满且 pick 满
    isComplete: function (state) {
      return (state.bannedA || []).length === RankBanPickEngine.BAN_PER_SIDE &&
             (state.bannedB || []).length === RankBanPickEngine.BAN_PER_SIDE &&
             (state.pickedA || []).length === RankBanPickEngine.PICK_PER_SIDE &&
             (state.pickedB || []).length === RankBanPickEngine.PICK_PER_SIDE;
    },
    // 入站队伍合法性防护（坏档/外部输入）：不含被 ban 妖、无重复、规模不超上限
    isTeamValid: function (state, side, team) {
      var denied = {};
      (state.bannedA || []).forEach(function (x) { denied[x] = true; });
      (state.bannedB || []).forEach(function (x) { denied[x] = true; });
      for (var i = 0; i < team.length; i++) if (denied[team[i]]) return false;
      var uniq = {}; var dup = false;
      for (var j = 0; j < team.length; j++) { if (uniq[team[j]]) { dup = true; break; } uniq[team[j]] = true; }
      if (dup) return false;
      if (team.length > RankBanPickEngine.PICK_PER_SIDE) return false;
      return true;
    },
  };

  // ───────────────────────── DungeonEngine ─────────────────────────
  // 副本妖境（GDD 十二·副本系统）。四类：山海异闻(YIWEN)/灵脉试炼(SHILIAN)/渡劫秘境(DUOJIE)/年节限时(NIANJIE)。
  // 进入扣香火、每日限次；通关掉落妖丹/渡劫材料/符纸/外观/妖王概率收服（补全图鉴）。
  // 战斗结算由奖励表直接结算（本切片不进入实时战斗），引擎只负责确定性奖励（rng 顺序严格对齐 shared）。
  var DungeonEngine = {
    Type: { YIWEN: "YIWEN", SHILIAN: "SHILIAN", DUOJIE: "DUOJIE", NIANJIE: "NIANJIE" },
    // 副本库（切片：每类 1~2 个代表副本），与 DungeonLibrary.kt 逐字段一致
    Library: {
      all: [
        { id: "d_yiwen_taotie", type: "YIWEN", name: "饕餮之祸", desc: "山海异闻·封印贪食巨兽",
          costIncense: 20, dailyLimit: 3, yaodanRange: [5, 10], materialRange: [0, 0],
          talismanChance: 0.3, skinChance: 0.1, bossMonsterId: "guai_taotie", bossCaptureChance: 0.15 },
        { id: "d_shilian_lingmai", type: "SHILIAN", name: "灵脉试炼", desc: "日常刷妖丹与符纸",
          costIncense: 20, dailyLimit: 5, yaodanRange: [8, 15], materialRange: [0, 2],
          talismanChance: 0.5, skinChance: 0.0, bossMonsterId: null, bossCaptureChance: 0.0 },
        { id: "d_duojie_lei", type: "DUOJIE", name: "渡劫秘境·雷", desc: "产出渡劫材料（雷系）",
          costIncense: 30, dailyLimit: 3, yaodanRange: [6, 12], materialRange: [2, 4],
          talismanChance: 0.4, skinChance: 0.05, bossMonsterId: null, bossCaptureChance: 0.0 },
        { id: "d_nianjie_chun", type: "NIANJIE", name: "年节·除岁", desc: "春节限时活动副本",
          costIncense: 30, dailyLimit: 1, yaodanRange: [10, 20], materialRange: [1, 3],
          talismanChance: 0.6, skinChance: 0.2, bossMonsterId: "guai_nianshou", bossCaptureChance: 0.25 },
      ],
      byId: function (id) {
        for (var i = 0; i < DungeonEngine.Library.all.length; i++)
          if (DungeonEngine.Library.all[i].id === id) return DungeonEngine.Library.all[i];
        return null;
      },
    },
    // 是否可进入：香火足够且未超每日限次（today 参数对齐 Kotlin 签名，函数体内未使用）
    canEnter: function (def, state, incense, today) {
      state = state || {};
      if (incense < def.costIncense) return false;
      var entered = state[def.id] || 0;
      return entered < def.dailyLimit;
    },
    // 结算一次副本奖励（确定性依赖 rng）。调用方需先确认 canEnter。
    // rng 调用顺序严格对齐 shared：yaodan → (material 若 last>0) → talisman → skin → bossCaptured
    enter: function (def, state, rng) {
      rng = rng || Math.random;
      state = state || {};
      var span = def.yaodanRange[1] - def.yaodanRange[0] + 1;
      var yaodan = def.yaodanRange[0] + Math.floor(rng() * span);
      var material = 0;
      if (def.materialRange[1] > 0) {
        var mspan = def.materialRange[1] - def.materialRange[0] + 1;
        material = def.materialRange[0] + Math.floor(rng() * mspan);
      }
      var talisman = rng() < def.talismanChance;
      var skin = rng() < def.skinChance;
      var bossCaptured = def.bossMonsterId != null && rng() < def.bossCaptureChance;
      return { yaodan: yaodan, material: material, talisman: talisman, skin: skin,
               bossCaptured: bossCaptured, bossMonsterId: def.bossMonsterId };
    },
  };

  // ───────────────────────── 在家秘境（GDD 三·探索双模式·在家秘境） ─────────────────────────
  // 与 Shared/SecretRealmEngine.kt 零漂移：主题→五行偏向（复用 LocationEngine.regionElementBias）
  // + 秘境稀有度权重（凡/灵主导）+ 加权抽样。本引擎只做可验证派生计算，rng 由平台喂入。
  // ───────────────────────── HomesteadEngine ─────────────────────────
  var HomesteadEngine = {
    INCENSE_PER_HOUR_BASE: 20.0,   // 阵法 Lv0 香火时产
    PILL_PER_HOUR_BASE: 1.0,      // 阵法 Lv0 妖丹时产
    MAX_OFFLINE_HOURS: 12.0,      // 离线产出封顶时长（小时）
    HOUR_MS: 3600000,
    FENGSHUI_INCENSE_MUL: 0.10,   // 每级 +10% 香火时产
    FENGSHUI_PILL_PER_LEVEL: 0.1, // 每级 +0.1 妖丹时产
    FENGSHUI_ATTACK_PER_LEVEL: 0.02, // 每级 +2% 全局攻击增益（报告值）
    incenseRate: function (level) { return HomesteadEngine.INCENSE_PER_HOUR_BASE * (1.0 + HomesteadEngine.FENGSHUI_INCENSE_MUL * level); },
    pillRate: function (level) { return HomesteadEngine.PILL_PER_HOUR_BASE + HomesteadEngine.FENGSHUI_PILL_PER_LEVEL * level; },
    fengshuiAttackBonus: function (level) { return HomesteadEngine.FENGSHUI_ATTACK_PER_LEVEL * level; },
    upgradeCost: function (level) { return 100 * (level + 1); },
    // 计算 [lastMs] 到 [nowMs] 的挂起产出：last<=0 或 now<=last 返回 0（首收不补发）；封顶 MAX_OFFLINE_HOURS
    compute: function (level, nowMs, lastMs) {
      if (lastMs <= 0 || nowMs <= lastMs) return { incense: 0, pills: 0, hours: 0.0, capped: false };
      var elapsedHours = (nowMs - lastMs) / HomesteadEngine.HOUR_MS;
      var cappedHours = elapsedHours > HomesteadEngine.MAX_OFFLINE_HOURS ? HomesteadEngine.MAX_OFFLINE_HOURS : elapsedHours;
      var incense = Math.floor(HomesteadEngine.incenseRate(level) * cappedHours);
      var pills = Math.floor(HomesteadEngine.pillRate(level) * cappedHours);
      return { incense: incense, pills: pills, hours: cappedHours, capped: elapsedHours > HomesteadEngine.MAX_OFFLINE_HOURS };
    },
  };

  var SecretRealmEngine = {
    SPAWN_COUNT: 6,            // 每次入秘境生成的刷妖数量
    INCENSE_COST: 20,          // 每日免费次数耗尽后，再入秘境消耗的香火
    ELEMENT_BIAS: 3.0,        // 主题五行偏向加成（比 LBS 的 1.8 更强调主题一致性）
    RARITY_WEIGHT: { FAN: 60.0, LING: 30.0, XIAN: 8.0, SHEN: 2.0 }, // 凡/灵主导
    THEMES: ["SHANLIN", "SHUIYU", "GUMU", "DONGHAI"],              // 山林/水域/古墓/东海
    // 主题 → 五行偏向（复用 LBS 地域主题映射）
    themeElementBias: function (theme) { return LocationEngine.regionElementBias(theme); },
    // 随机选一个主题（均匀分布）
    pickTheme: function (rng) {
      rng = rng || Math.random;
      var n = SecretRealmEngine.THEMES.length;
      var idx = Math.floor(rng() * n) % n;
      return SecretRealmEngine.THEMES[idx];
    },
    _weightOf: function (m, theme) {
      var base = SecretRealmEngine.RARITY_WEIGHT[m.rarity] || 1.0;
      return (m.element === SecretRealmEngine.themeElementBias(theme)) ? base * SecretRealmEngine.ELEMENT_BIAS : base;
    },
    // 依据主题加权抽样 [count] 只妖的 id（可重复，模拟连刷）；rng 调用顺序与 shared 一致
    generateSpawns: function (theme, count, rng) {
      rng = rng || Math.random;
      var pool = SH_DATA.monsters;
      var weights = pool.map(function (m) { return SecretRealmEngine._weightOf(m, theme); });
      var total = weights.reduce(function (a, b) { return a + b; }, 0);
      var out = [];
      for (var i = 0; i < count; i++) {
        var r = rng() * total, chosen = pool[pool.length - 1];
        for (var j = 0; j < pool.length; j++) { r -= weights[j]; if (r <= 0.0) { chosen = pool[j]; break; } }
        out.push(chosen.id);
      }
      return out;
    },
  };

  // ───────────────────────── 工具 ─────────────────────────
  function clamp(x, lo, hi) { return Math.max(lo, Math.min(hi, x)); }
  function clone(inst, patch) {
    var o = {
      instanceId: inst.instanceId, defId: inst.defId, level: inst.level, exp: inst.exp,
      breakthrough: inst.breakthrough, tribulations: inst.tribulations || 0,
      talents: inst.talents || {}, moralChoice: inst.moralChoice || null,
    };
    for (var k in patch) if (patch.hasOwnProperty(k)) o[k] = patch[k];
    return o;
  }

  // ───────────────────────── Player 运行时 ─────────────────────────
  var QUEST_DEFS = [
    { id: "d_catch3", name: "今日·收妖", cat: "DAILY", obj: { type: "CATCH_TOTAL", target: 3 }, reward: { incense: 30, text: "香火+30" }, daily: true },
    { id: "d_win2", name: "今日·试炼", cat: "DAILY", obj: { type: "WIN_BATTLE", target: 2 }, reward: { lingyu: 5, text: "灵玉+5" }, daily: true },
    { id: "d_secret1", name: "今日·秘境", cat: "DAILY", obj: { type: "ENTER_SECRET", target: 1 }, reward: { yaodan: 20, text: "妖丹+20" }, daily: true },
    { id: "t_ling", name: "灵脉修行", cat: "TRIAL", obj: { type: "CATCH_RARITY", target: 1, ref: "LING" }, reward: { yaodan: 30, text: "妖丹+30" } },
    { id: "t_train", name: "勤修苦练", cat: "TRIAL", obj: { type: "TRAIN_LEVEL", target: 15 }, reward: { incense: 60, yaodan: 40, text: "香火+60 妖丹+40" } },
    { id: "t_break", name: "破而后立", cat: "TRIAL", obj: { type: "BREAKTHROUGH_TOTAL", target: 1 }, reward: { lingyu: 20, text: "灵玉+20" } },
    { id: "t_tribulate", name: "渡劫飞升", cat: "TRIAL", obj: { type: "TRIBULATE_TOTAL", target: 1 }, reward: { lingyu: 50, yaodan: 50, text: "灵玉+50 妖丹+50" } },
    { id: "t_talent", name: "天赋初开", cat: "TRIAL", obj: { type: "TALENT_TOTAL", target: 3 }, reward: { lingyu: 30, yaodan: 30, text: "灵玉+30 妖丹+30" } },
    { id: "t_gacha", name: "抽签祈妖", cat: "TRIAL", obj: { type: "GACHA_TOTAL", target: 10 }, reward: { lingyu: 60, yaodan: 40, text: "灵玉+60 妖丹+40" } },
    { id: "a_codex30", name: "图鉴新秀", cat: "ACHIEVEMENT", obj: { type: "CODEX_RATIO", target: 30 }, reward: { text: "称号「巡妖使」" } },
    { id: "a_catch10", name: "收妖大师", cat: "ACHIEVEMENT", obj: { type: "CATCH_TOTAL", target: 10 }, reward: { text: "自选灵妖" } },
    { id: "a_catch_shen", name: "神妖降世", cat: "ACHIEVEMENT", obj: { type: "CATCH_RARITY", target: 1, ref: "SHEN" }, reward: { text: "神妖专属外观" } },
    { id: "a_win50", name: "百战之师", cat: "ACHIEVEMENT", obj: { type: "WIN_BATTLE", target: 50 }, reward: { lingyu: 100, text: "灵玉+100" } },
  ];

  function makePlayer() {
    var p = {
      lingyu: 600, yaodan: 200, incense: 50,
      owned: {},          // instanceId -> instance
      team: [],           // instanceId[]
      codex: makeCodex(),
      dungeonEntered: {},
      faction: null,
      stronghold: { ownedNode: null, lastCaptureDay: "" },
      sectLevels: {},     // tech id -> level
      contribution: 0,
      gachaState: { sinceShen: 0 },
      rep: 100,           // 信誉分（ReputationEngine）
      fatigue: 0,         // 疲劳值 0..100（FatigueEngine）
      rankTierIndex: 0,   // 排位段位索引 0..4（RankEngine）
      rankPoints: 0,      // 段内积分 0..99（RankEngine）
      tribulationTotal: 0, // 累计渡劫飞升次数（HonorEngine 称号解锁统计）
      equippedTitleId: "", // 玩家装备的展示称号 id（""=无，HonorEngine.NO_TITLE）
      tutorialStep: 0,     // 已完成引导最高步序 0..5（TutorialEngine / GameState）
      tutorialSkipped: false, // 是否跳过引导
      signInDay: 0,        // 累计签到天数（驱动 7 日留存解锁 + 签到奖励）
      secretRealmLeft: 3, // 每日秘境免费次数（SecretRealmEngine / GameState.enterSecretRealm）
      homesteadLevel: 0,  // 阵法风水等级（HomesteadEngine；0=未升级但产基础产出）
      lastHomesteadCollect: 0, // 上次收取时间戳 ms（0=从未收取）
      lastDailyReset: "",     // 上次每日重置日 yyyy-MM-dd（首启空串，DailyResetEngine.isCrossDay 权威）
      shopDay: "",            // 引妖香商店购买所属日（每日重置，DailyResetEngine/shop）
      shopIncenseBought: 0,   // 当日引妖香已购数量（每日重置）
      seasonStartMs: 0,       // 当前赛季起点 ms epoch（0=未开始，GameState.ensureSeasonStart）
      seasonXp: 0,            // 当前赛季累计经验（SeasonEngine）
      hasSeasonPass: false,   // 是否已购通行证（解锁付费轨道）
      seasonClaimedFree: [],  // 已领免费档集合
      seasonClaimedPaid: [],  // 已领付费档集合
      seasonIndex: 1,         // 赛季序号（滚动 +1）
      equipment: [],      // 拥有装备实例 OwnedEquip
      equipped: {},       // 妖实例 instanceId -> { slot: equipInstanceId }
      nextEqSeq: 1,
      _seq: 1,
      quests: {
        CATCH_TOTAL: 0, WIN_BATTLE: 0, ENTER_SECRET: 0, EVOLVE_TOTAL: 0,
        BREAKTHROUGH_TOTAL: 0, TRIBULATE_TOTAL: 0, TALENT_TOTAL: 0, GACHA_TOTAL: 0,
        catchRarity: { LING: 0, SHEN: 0 }, winWithElement: {},
        claimed: {},
      },
    };

    function newInstance(defId, level) {
      var id = "i_" + (p._seq++);
      var inst = { instanceId: id, defId: defId, level: level || 1, exp: 0, breakthrough: 0, tribulations: 0, talents: {} };
      p.owned[id] = inst;
      if (p.team.length < 6) p.team.push(id);
      return inst;
    }

    function grantEquip(equipId) {
      var def = equipDefById(equipId); if (!def) return null;
      var oe = { equipId: equipId, instanceId: "e_" + (p.nextEqSeq++), level: 1, stars: 1, tier: 1, substats: [] };
      p.equipment.push(oe);
      return oe;
    }

    function record(ev) {
      var q = p.quests;
      if (ev.monsterCaught) {
        q.CATCH_TOTAL++;
        if (ev.rarity === "LING" || ev.rarity === "XIAN" || ev.rarity === "SHEN") q.catchRarity.LING++;
        if (ev.rarity === "SHEN") q.catchRarity.SHEN++;
      }
      if (ev.battleWon) { q.WIN_BATTLE++; if (ev.element) q.winWithElement[ev.element] = (q.winWithElement[ev.element] || 0) + 1; }
      if (ev.secretEntered) q.ENTER_SECRET++;
      if (ev.evolved) q.EVOLVE_TOTAL++;
      if (ev.breakthrough) q.BREAKTHROUGH_TOTAL++;
      if (ev.tribulated) q.TRIBULATE_TOTAL++;
      if (ev.talent) q.TALENT_TOTAL++;
      if (ev.gacha) q.GACHA_TOTAL++;
    }

    // 每日任务进度重置（对应 Kotlin QuestLibrary.resetDaily）。
    // 原型采用扁平共享计数，重置即清空进度与领取标记（与 Kotlin 每日/成就区分的语义略有简化，但每日重置框架一致）。
    p.quests.resetDaily = function () {
      this.CATCH_TOTAL = 0; this.WIN_BATTLE = 0; this.ENTER_SECRET = 0;
      this.EVOLVE_TOTAL = 0; this.BREAKTHROUGH_TOTAL = 0; this.TRIBULATE_TOTAL = 0;
      this.TALENT_TOTAL = 0; this.GACHA_TOTAL = 0;
      this.catchRarity = { LING: 0, SHEN: 0 };
      this.winWithElement = {};
      this.claimed = {};
    };

    return {
      raw: p,
      addStarter: function (defId) {
        var inst = newInstance(defId, 5); p.codex.catch(defId);
        if (p.equipment.length === 0) { grantEquip("eq_sword"); grantEquip("eq_armor"); }
        return inst;
      },
      instance: function (id) { return p.owned[id]; },
      allInstances: function () { return Object.keys(p.owned).map(function (k) { return p.owned[k]; }); },
      ownedMonsterView: function () {
        return Object.keys(p.owned).map(function (k) {
          var inst = p.owned[k];
          var mon = monsterById(inst.defId);
          return { instance: inst, monster: mon, stats: CultivationEngine.effectiveStats(inst), chain: CultivationEngine.evolutionChain(inst.defId) };
        });
      },
      // ---- 收妖 ----
      attemptCapture: function (monsterId, accuracy, assist, rng) {
        rng = rng || Math.random;
        var mon = monsterById(monsterId); if (!mon) return { error: "未知妖" };
        var res = CaptureEngine.resolve(accuracy, mon.rarity, assist, rng);
        res.monster = mon;
        if (res.success) {
          var dup = p.codex.has(monsterId);
          var inst = newInstance(monsterId, 1);
          p.codex.catch(monsterId);
          if (dup) { p.yaodan += GachaEngine.DUPLICATE_YAODAN; res.duplicate = true; res.yaodanGain = GachaEngine.DUPLICATE_YAODAN; }
          record({ monsterCaught: true, rarity: mon.rarity });
          p.fatigue = FatigueEngine.addFatigue(p.fatigue, FatigueEngine.FATIGUE_CATCH);
        }
        return res;
      },
      // ---- 抽卡（祈妖） ----
      praySingle: function (rng) {
        rng = rng || Math.random;
        if (p.lingyu < GachaEngine.SINGLE_LINGYU) return { error: "灵玉不足" };
        p.lingyu -= GachaEngine.SINGLE_LINGYU;
        var r = GachaEngine.pull(p.gachaState, rng);
        return applyGacha(r);
      },
      prayTen: function (rng) {
        rng = rng || Math.random;
        if (p.lingyu < GachaEngine.TEN_LINGYU) return { error: "灵玉不足" };
        p.lingyu -= GachaEngine.TEN_LINGYU;
        var rs = GachaEngine.tenPull(p.gachaState, rng);
        return rs.map(applyGacha);
      },
      // ---- 养成 ----
      gainExpTo: function (instanceId, amount) {
        var inst = p.owned[instanceId]; if (!inst) return null;
        var before = inst.level;
        var after = CultivationEngine.gainExp(inst, amount);
        p.owned[instanceId] = after;
        return { before: before, after: after.level, leveled: after.level - before };
      },
      breakthrough: function (instanceId) {
        var inst = p.owned[instanceId]; if (!inst) return { error: "无此妖" };
        if (!CultivationEngine.canBreakthrough(inst)) return { error: "未达突破条件" };
        var cost = CultivationEngine.breakthroughCost(inst.breakthrough);
        if (p.yaodan < cost) return { error: "妖丹不足(" + cost + ")" };
        p.yaodan -= cost;
        p.owned[instanceId] = CultivationEngine.breakthrough(inst);
        record({ breakthrough: true });
        return { ok: true, cost: cost };
      },
      evolve: function (instanceId) {
        var inst = p.owned[instanceId]; if (!inst) return { error: "无此妖" };
        var after = CultivationEngine.evolve(inst);
        if (!after) return { error: "未达进化条件" };
        p.owned[instanceId] = after;
        record({ evolved: true });
        return { ok: true, newDefId: after.defId };
      },
      tribulate: function (instanceId) {
        var inst = p.owned[instanceId]; if (!inst) return { error: "无此妖" };
        if (!CultivationEngine.canTribulate(inst)) return { error: "未达渡劫条件（需满级满突破）" };
        var cost = CultivationEngine.tribulateCost(inst);
        if (p.yaodan < cost) return { error: "妖丹不足(" + cost + ")" };
        p.yaodan -= cost;
        p.owned[instanceId] = CultivationEngine.tribulate(inst);
        record({ tribulated: true });
        return { ok: true, cost: cost };
      },
      assignTalent: function (instanceId, branch) {
        var inst = p.owned[instanceId]; if (!inst) return { error: "无此妖" };
        var after = TalentEngine.assign(inst, branch);
        if (!after) return { error: "无法分配（分支满或点数用尽）" };
        p.owned[instanceId] = after;
        record({ talent: true });
        return { ok: true };
      },
      // ---- 派系 / 据点 ----
      joinFaction: function (f) { p.faction = f; return { ok: true }; },
      captureStronghold: function (node, today) {
        if (LocationEngine.STRONGHOLD_NODES.indexOf(node) < 0) return { error: "非据点节点" };
        if (!SocialEngine.canCapture(p.stronghold, today)) return { error: "今日已占领" };
        if (!SocialEngine.capture(p.stronghold, node, today)) return { error: "占领失败" };
        var gain = SectTechEngine.CONTRIBUTION_PER_CAPTURE;
        p.contribution += gain;
        return { ok: true, node: node, contributionGain: gain };
      },
      // ---- 宗门科技 ----
      researchTech: function (id) {
        var r = SectTechEngine.research(id, p.sectLevels, p.faction, p.contribution);
        if (!r) return { error: "不可研习（派系/前置/贡献不足）" };
        p.sectLevels = r.levels; p.contribution = r.contribution;
        return { ok: true, levels: r.levels, contribution: r.contribution };
      },
      sectBonuses: function () { return SectTechEngine.bonuses(p.sectLevels, p.faction); },
      // ---- 战斗 ----
      teamCombatants: function () {
        var mons = p.team.map(function (id) { return monsterById(p.owned[id].defId); });
        var syn = FormationEngine.evaluate(mons);
        var feng = HomesteadEngine.fengshuiAttackBonus(p.homesteadLevel); // 风水全局攻击增益（默认 Lv0=0）
        var teamMul = FormationEngine.teamAttackMultiplier(syn) * (1.0 + feng); // 忠实复刻 GameState.battleTeamMul
        var sectAtk = 1.0 + Player_sectAtk(p);
        var strongholdBonus = SocialEngine.attackBonus(!!p.stronghold.ownedNode);
        return {
          combatants: p.team.map(function (id) { return makeCombatant(p.owned[id]); }).filter(function (c) { return c; }),
          synergies: syn, teamMul: teamMul, sectAtk: sectAtk, strongholdBonus: strongholdBonus,
        };
      },
      // ---- 任务 ----
      questStatus: function () {
        var q = p.quests, codexCount = p.codex.regionCaught();
        return QUEST_DEFS.map(function (d) {
          var cur = 0;
          switch (d.obj.type) {
            case "CATCH_TOTAL": cur = q.CATCH_TOTAL; break;
            case "WIN_BATTLE": cur = q.WIN_BATTLE; break;
            case "ENTER_SECRET": cur = q.ENTER_SECRET; break;
            case "EVOLVE_TOTAL": cur = q.EVOLVE_TOTAL; break;
            case "BREAKTHROUGH_TOTAL": cur = q.BREAKTHROUGH_TOTAL; break;
            case "TRIBULATE_TOTAL": cur = q.TRIBULATE_TOTAL; break;
            case "TALENT_TOTAL": cur = q.TALENT_TOTAL; break;
            case "GACHA_TOTAL": cur = q.GACHA_TOTAL; break;
            case "CATCH_RARITY": cur = d.obj.ref === "SHEN" ? q.catchRarity.SHEN : q.catchRarity.LING; break;
            case "CODEX_RATIO": cur = Math.floor(p.codex.regionCompletion() * 100); break;
            case "TRAIN_LEVEL":
              cur = Object.keys(p.owned).reduce(function (mx, k) { return Math.max(mx, p.owned[k].level); }, 0); break;
          }
          var done = cur >= d.obj.target;
          return { def: d, current: cur, target: d.obj.target, done: done, claimed: !!q.claimed[d.id] };
        });
      },
      claimQuest: function (id) {
        var st = this.questStatus().filter(function (s) { return s.def.id === id; })[0];
        if (!st || !st.done || st.claimed) return { error: "不可领取" };
        p.quests.claimed[id] = true;
        var rw = st.def.reward;
        if (rw.lingyu) p.lingyu += rw.lingyu;
        if (rw.yaodan) p.yaodan += rw.yaodan;
        if (rw.incense) p.incense += rw.incense;
        return { ok: true, reward: rw };
      },
      // ---- 装备 / 五路径 / 融合 ----
      findEquip: function (eid) {
        for (var i = 0; i < p.equipment.length; i++) if (p.equipment[i].instanceId === eid) return p.equipment[i];
        return null;
      },
      equipTo: function (monInstanceId, eid) {
        var inst = p.owned[monInstanceId]; if (!inst) return { error: "无此妖" };
        var oe = this.findEquip(eid); if (!oe) return { error: "无此装备" };
        var def = equipDefById(oe.equipId); if (!def) return { error: "无此装备定义" };
        for (var mid in p.equipped) {
          if (mid !== monInstanceId) {
            var slots = p.equipped[mid];
            for (var s in slots) if (slots[s] === eid) return { error: "该装备已在其他妖身上" };
          }
        }
        if (!p.equipped[monInstanceId]) p.equipped[monInstanceId] = {};
        p.equipped[monInstanceId][def.slot] = eid;
        return { ok: true, slot: def.slot };
      },
      unequip: function (monInstanceId, slot) {
        if (p.equipped[monInstanceId] && p.equipped[monInstanceId][slot]) {
          delete p.equipped[monInstanceId][slot];
          return { ok: true };
        }
        return { error: "该槽位无装备" };
      },
      forgeEquip: function (eid) {
        var oe = this.findEquip(eid); if (!oe) return { error: "无此装备" };
        if (!EquipmentEngine.canForge(oe)) return { error: "已达祭炼上限" };
        if (p.yaodan < EquipmentEngine.FORGE_COST_YAODAN) return { error: "妖丹不足" };
        p.yaodan -= EquipmentEngine.FORGE_COST_YAODAN;
        var i = p.equipment.indexOf(oe); p.equipment[i] = Object.assign({}, oe, { level: oe.level + 1 });
        return { ok: true, cost: EquipmentEngine.FORGE_COST_YAODAN };
      },
      starUpEquip: function (eid) {
        var oe = this.findEquip(eid); if (!oe) return { error: "无此装备" };
        if (!EquipmentEngine.canStarUp(oe)) return { error: "已达星级上限" };
        var cost = EquipmentEngine.starCost(oe);
        if (p.lingyu < cost) return { error: "灵玉不足(" + cost + ")" };
        p.lingyu -= cost;
        var i = p.equipment.indexOf(oe); p.equipment[i] = EquipmentEngine.starUp(oe);
        return { ok: true, cost: cost };
      },
      breakthroughEquip: function (eid) {
        var oe = this.findEquip(eid); if (!oe) return { error: "无此装备" };
        var def = equipDefById(oe.equipId); if (!def) return { error: "无此装备定义" };
        if (!EquipmentEngine.canBreakthrough(oe, def)) return { error: "已达突破上限" };
        var cost = EquipmentEngine.tierCost(oe);
        if (p.lingyu < cost) return { error: "灵玉不足(" + cost + ")" };
        p.lingyu -= cost;
        var i = p.equipment.indexOf(oe); p.equipment[i] = EquipmentEngine.breakthrough(oe, def);
        return { ok: true, cost: cost };
      },
      temperEquip: function (eid) {
        var oe = this.findEquip(eid); if (!oe) return { error: "无此装备" };
        if (!EquipmentEngine.canTemper(oe)) return { error: "不可再淬炼" };
        var cost = EquipmentEngine.temperCost(oe);
        if (p.lingyu < cost) return { error: "灵玉不足(" + cost + ")" };
        p.lingyu -= cost;
        var i = p.equipment.indexOf(oe); p.equipment[i] = EquipmentEngine.temper(oe);
        return { ok: true, cost: cost };
      },
      inheritEquip: function (srcEid, dstEid) {
        var src = this.findEquip(srcEid), dst = this.findEquip(dstEid);
        if (!src || !dst) return { error: "装备不存在" };
        if (!EquipmentEngine.canInherit(src, dst)) return { error: "不可继承自身" };
        if (p.yaodan < EquipmentEngine.INHERIT_COST_YAODAN) return { error: "妖丹不足(" + EquipmentEngine.INHERIT_COST_YAODAN + ")" };
        p.yaodan -= EquipmentEngine.INHERIT_COST_YAODAN;
        for (var mid in p.equipped) {
          var slots = p.equipped[mid];
          for (var s in slots) if (slots[s] === srcEid) delete slots[s];
        }
        var i = p.equipment.indexOf(dst); p.equipment[i] = EquipmentEngine.inherit(src, dst);
        p.equipment = p.equipment.filter(function (o) { return o.instanceId !== srcEid; });
        return { ok: true, cost: EquipmentEngine.INHERIT_COST_YAODAN };
      },
      fuseMonsters: function (aId, bId) {
        var a = p.owned[aId], b = p.owned[bId];
        if (!a || !b) return { error: "妖实例不存在" };
        if (!FusionEngine.canFuse(a, b, p.yaodan)) return { error: "不可融合(同实例或妖丹不足)" };
        var plan = FusionEngine.plan(a, b);
        if (!plan) return { error: "无融合方案" };
        p.yaodan -= FusionEngine.FUSE_COST_YAODAN;
        delete p.equipped[aId]; delete p.equipped[bId];
        delete p.owned[aId]; delete p.owned[bId];
        p.team = p.team.filter(function (t) { return t !== aId && t !== bId; });
        var inst = newInstance(plan.newDefId, plan.newLevel);
        inst.breakthrough = plan.newBreakthrough;
        return { ok: true, plan: plan, instance: inst };
      },
      monsterStats: function (instanceId) {
        var inst = p.owned[instanceId]; if (!inst) return null;
        var mon = monsterById(inst.defId);
        var cultivation = CultivationEngine.effectiveStats(inst);
        var eqList = [];
        var slots = p.equipped[instanceId] || {};
        for (var s in slots) { var oe = this.findEquip(slots[s]); if (oe) eqList.push(oe); }
        var eqBonus = EquipmentEngine.totalBonus(mon, eqList);
        return {
          cultivation: cultivation,
          equipment: eqBonus,
          total: EquipmentEngine.addStats(cultivation, eqBonus),
          slots: slots,
        };
      },
      // ---- 疲劳（FatigueEngine：单日收益封顶·防脚本） ----
      addFatigue: function (delta) { p.fatigue = FatigueEngine.addFatigue(p.fatigue, delta); return p.fatigue; },
      incomeMul: function () { return FatigueEngine.incomeMul(p.fatigue); },
      fatigueLabel: function () { return FatigueEngine.label(p.fatigue); },
      resetDailyFatigue: function () { p.fatigue = FatigueEngine.resetDaily(); return 0; },
      // 跨天静默重置（对应 Kotlin GameState.maybeResetDaily，L982-993）。
      // 唯一判据 isCrossDay；跨天则复位全部每日字段并写回 lastDailyReset。
      maybeResetDaily: function (today) {
        if (DailyResetEngine.isCrossDay(p.lastDailyReset, today)) {
          p.quests.resetDaily();
          p.secretRealmLeft = 3;
          p.dungeonEntered = {};
          if (p.shopDay !== today) { p.shopIncenseBought = 0; p.shopDay = today; }
          if (p.stronghold.lastCaptureDay !== today) { p.stronghold.ownedNode = null; p.stronghold.lastCaptureDay = today; }
          p.rep = ReputationEngine.dailyRecover(p.rep);
          p.fatigue = FatigueEngine.resetDaily();
          p.lastDailyReset = today;
          return true;
        }
        return false;
      },
      // 购买引妖香（对应 ShopEngine：耗灵玉30→回香火60，日购上限3，每日重置由 maybeResetDaily 处理）
      buyIncense: function (today) {
        var state = { incenseBoughtToday: p.shopIncenseBought, lastBuyDay: p.shopDay };
        var r = ShopEngine.buyIncense(state, today, p.lingyu);
        if (!r) return { error: "无法购买引妖香（灵玉不足或已达日购上限 " + ShopEngine.INCENSE_DAILY_BUY_CAP + "）" };
        p.shopIncenseBought = state.incenseBoughtToday;
        p.shopDay = state.lastBuyDay;
        p.lingyu -= r.cost;
        p.incense += r.amount;
        return { amount: r.amount, cost: r.cost };
      },
      // ── 赛季通行证（SeasonEngine + GameState 赛季接线，零漂移复刻 L482-657）──
      // 确保赛季已开始（首次调用以 nowMs 为赛季起点）
      ensureSeasonStart: function (nowMs) { if (p.seasonStartMs <= 0) p.seasonStartMs = nowMs; },
      // 赛季推进：超出 90 天则滚动到下一赛季（序号+1、经验/已领清零、通行证失效），返回是否滚动
      tickSeason: function (nowMs) {
        this.ensureSeasonStart(nowMs);
        if (nowMs - p.seasonStartMs >= SeasonEngine.SEASON_LENGTH_DAYS * SeasonEngine.DAY_MS) {
          p.seasonIndex += 1;
          p.seasonStartMs += SeasonEngine.SEASON_LENGTH_DAYS * SeasonEngine.DAY_MS;
          p.seasonXp = 0;
          p.seasonClaimedFree = [];
          p.seasonClaimedPaid = [];
          p.hasSeasonPass = false;
          return true;
        }
        return false;
      },
      seasonDayNow: function (nowMs) { return SeasonEngine.dayIndex(p.seasonStartMs, nowMs); },
      seasonDaysRemaining: function (nowMs) { return SeasonEngine.daysRemaining(p.seasonStartMs, nowMs); },
      isSeasonInProgress: function (nowMs) { return SeasonEngine.inSeason(p.seasonStartMs, nowMs); },
      currentSeasonTier: function () { return SeasonEngine.tierFromXp(p.seasonXp); },
      addSeasonXp: function (amount) { if (amount > 0) p.seasonXp += amount; },
      // 购买赛季通行证（耗 cost 灵玉），成功返回 true（对应 GameState.buySeasonPass）
      buySeasonPass: function (cost) {
        cost = (cost === undefined) ? 300 : cost;
        if (p.lingyu < cost) return false;
        p.lingyu -= cost;
        p.hasSeasonPass = true;
        return true;
      },
      // 领取某档位奖励：免费档始终可领；付费档需已购通行证；已领不再发放（对应 GameState.claimSeasonTier）
      claimSeasonTier: function (tier) {
        var cur = this.currentSeasonTier();
        var claimedFree = false, claimedPaid = false, inc = 0, ly = 0, yd = 0;
        if (tier >= 1 && tier <= cur && p.seasonClaimedFree.indexOf(tier) < 0) {
          var r = SeasonEngine.freeReward(tier);
          inc += r.incense; yd += r.yaodan;
          p.incense += r.incense; p.yaodan += r.yaodan;
          p.seasonClaimedFree.push(tier);
          claimedFree = true;
        }
        if (tier >= 1 && tier <= cur && p.hasSeasonPass && p.seasonClaimedPaid.indexOf(tier) < 0) {
          var r2 = SeasonEngine.paidReward(tier);
          ly += r2.lingyu; yd += r2.yaodan;
          p.lingyu += r2.lingyu; p.yaodan += r2.yaodan;
          p.seasonClaimedPaid.push(tier);
          claimedPaid = true;
        }
        return { tier: tier, claimedFree: claimedFree, claimedPaid: claimedPaid, incense: inc, lingyu: ly, yaodan: yd };
      },
      // 一键领取所有已解锁且未领的档位（免费+付费），返回逐档结果（对应 GameState.claimAllSeason）
      claimAllSeason: function () {
        var cur = this.currentSeasonTier();
        var out = [];
        for (var t = 1; t <= cur; t++) out.push(this.claimSeasonTier(t));
        return out;
      },
      // ── 荣誉/勋章/称号（HonorEngine + GameState L574-611，零漂移复刻）──
      // 成就达成数：QUEST_DEFS 中 cat=="ACHIEVEMENT" 且已达标的计数（复用 questStatus）
      achievementCount: function () {
        return this.questStatus().filter(function (s) { return s.def.cat === "ACHIEVEMENT" && s.done; }).length;
      },
      achievementTotal: function () {
        return QUEST_DEFS.filter(function (d) { return d.cat === "ACHIEVEMENT"; }).length;
      },
      // 装配 HonorProfile（供 HonorEngine 消费）
      honorProfile: function () {
        return {
          achievementCount: this.achievementCount(),
          achievementTotal: this.achievementTotal(),
          rankTierIndex: p.rankTierIndex,
          codexRatio: Math.floor(p.codex.regionCompletion() * 100),
          tribulationTotal: p.tribulationTotal,
        };
      },
      medals: function () { return HonorEngine.unlockedMedals(this.honorProfile()); },
      titles: function () { return HonorEngine.unlockedTitles(this.honorProfile()); },
      unlockedTitleIds: function () { return this.titles().map(function (t) { return t.id; }); },
      equipTitle: function (id) {
        if (id === HonorEngine.NO_TITLE) { p.equippedTitleId = HonorEngine.NO_TITLE; return true; }
        if (!HonorEngine.isUnlocked(id, this.honorProfile())) return false;
        p.equippedTitleId = id;
        return true;
      },
      displayTitle: function () {
        if (p.equippedTitleId === HonorEngine.NO_TITLE) return HonorEngine.NO_TITLE;
        var t = HonorEngine.TITLES.filter(function (x) { return x.id === p.equippedTitleId; });
        return t.length > 0 ? t[0].name : HonorEngine.NO_TITLE;
      },
      // ── 新手引导 + 7 日留存（TutorialEngine + GameState L391-428 零漂移复刻）──
      tutorialActive: function () { return !TutorialEngine.allDone(p.tutorialStep, p.tutorialSkipped); },
      currentTutorialStep: function () { return TutorialEngine.currentStep(p.tutorialStep); },
      completeTutorialStep: function (order) {
        if (order > p.tutorialStep) p.tutorialStep = order;
        return p.tutorialStep;
      },
      skipTutorial: function () { p.tutorialSkipped = true; return true; },
      // RealmFeature 解锁：引导未完成时全开放（简化，与 GameState L411 一致），否则按 signInDay
      realmFeatures: function () {
        if (this.tutorialActive()) return TutorialEngine.REALM_FEATURES.slice();
        return TutorialEngine.unlockedFeatures(p.signInDay);
      },
      // 签到：signInDay++ + 递增奖励（封顶 70）+ 自动完成引导步 ⑤
      doSignIn: function () {
        p.signInDay += 1;
        var reward = TutorialEngine.signInReward(p.signInDay);
        if (this.tutorialActive()) this.completeTutorialStep(5);
        p.incense += reward;
        return { signInDay: p.signInDay, reward: reward };
      },
      // ---- 信誉（ReputationEngine：GPS 拒收扣分 / 分层软限制） ----
      penalizeRep: function (kind) { p.rep = ReputationEngine.applyPenalty(p.rep, kind || "UNKNOWN"); return p.rep; },
      recoverRepDaily: function () { p.rep = ReputationEngine.dailyRecover(p.rep); return p.rep; },
      repTier: function () { return ReputationEngine.tier(p.rep); },
      repLabel: function () { return ReputationEngine.tierLabel(p.rep); },
      rareSpawnMul: function () { return ReputationEngine.rareSpawnMultiplier(p.rep); },
      canExplore: function () { return ReputationEngine.canExploreLbs(p.rep); },
      // ---- 排位段位（RankEngine：胜+20/负-15、满100升段、化神封顶、炼气保底） ----
      rankTierName: function () { return RankEngine.tierName(p.rankTierIndex); },
      rankInfo: function () { return { tierIndex: p.rankTierIndex, tierName: RankEngine.tierName(p.rankTierIndex), points: p.rankPoints }; },
      // 应用一场排位结果（won=true 胜 / false 负）；升段发荣誉奖励，返回 RankResult
      applyRankResult: function (won) {
        var res = RankEngine.applyResult(p.rankTierIndex, p.rankPoints, won);
        p.rankTierIndex = res.tierIndex;
        p.rankPoints = res.points;
        if (res.promoted) {
          var rw = RankEngine.rankReward(res.tierIndex);
          p.incense += rw.incense; p.lingyu += rw.lingyu; p.yaodan += rw.yaodan;
        }
        return res;
      },
      // ---- 副本妖境（DungeonEngine + GameState.enterDungeon 接线） ----
      sectIncomeMul: function () { var b = SectTechEngine.bonuses(p.sectLevels, p.faction); return 1.0 + (b.income || 0); },
      // 运营活动乘子（ActivityEngine，按 lastDailyReset 查询当日生效活动，乘性聚合）
      currentActivities: function () { return ActivityEngine.active(p.lastDailyReset); },
      activityBonuses: function () { return ActivityEngine.aggregate(this.currentActivities()); },
      activityCaptureMul: function () { return this.activityBonuses().captureMul; },
      activityExpMul: function () { return this.activityBonuses().expMul; },
      activityIncomeMul: function () { return this.activityBonuses().incomeMul; },
      // 通关副本：扣香火、记当日次数、发放奖励（妖丹受疲劳/宗门/活动倍率）；条件不足返回 null
      enterDungeon: function (defId) {
        var def = DungeonEngine.Library.byId(defId);
        if (!def) return null;
        if (!DungeonEngine.canEnter(def, p.dungeonEntered, p.incense)) return null;
        p.incense -= def.costIncense;
        var reward = DungeonEngine.enter(def, p.dungeonEntered, Math.random);
        p.dungeonEntered[defId] = (p.dungeonEntered[defId] || 0) + 1;
        var mul = FatigueEngine.incomeMul(p.fatigue)
                * (1.0 + (SectTechEngine.bonuses(p.sectLevels, p.faction).income || 0))
                * 1.0; // activityIncomeMul 默认 1.0（ActivityEngine 未原型化）
        p.yaodan += Math.floor(reward.yaodan * mul);
        if (reward.bossCaptured && reward.bossMonsterId && !p.codex.has(reward.bossMonsterId)) {
          p.codex.catch(reward.bossMonsterId);
          var inst = newInstance(reward.bossMonsterId, 1);
          p.owned[inst.instanceId] = inst;
          if (p.team.length < 6) p.team.push(inst.instanceId);
        }
        record({ dungeon: true });
        p.fatigue = FatigueEngine.addFatigue(p.fatigue, FatigueEngine.FATIGUE_DUNGEON); // 副本累积疲劳（防脚本）
        return reward;
      },
      // ---- 在家秘境（SecretRealmEngine + GameState.enterSecretRealm 接线） ----
      // 每日免费 3 次优先；耗尽则耗香火 20；次数耗尽且香火不足返回 null。
      // 忠实镜像：不加疲劳（与 Kotlin enterSecretRealm 一致）；不接引导完成（TutorialEngine 未原型化，记录为已知基线差异）。
      enterSecretRealm: function (rng) {
        rng = rng || Math.random;
        var usedIncense = p.secretRealmLeft <= 0;
        if (usedIncense) {
          if (p.incense < SecretRealmEngine.INCENSE_COST) return null;
          p.incense -= SecretRealmEngine.INCENSE_COST;
        } else {
          p.secretRealmLeft -= 1;
        }
        var theme = SecretRealmEngine.pickTheme(rng);
        var spawns = SecretRealmEngine.generateSpawns(theme, SecretRealmEngine.SPAWN_COUNT, rng);
        record({ secretEntered: true }); // 记录 ENTER_SECRET 任务
        return { theme: theme, element: SecretRealmEngine.themeElementBias(theme), spawns: spawns };
      },
      // ---- 家园洞府（HomesteadEngine + GameState 收取/升级 接线） ----
      getHomesteadLevel: function () { return p.homesteadLevel; },
      homesteadIncenseRate: function () { return HomesteadEngine.incenseRate(p.homesteadLevel); },
      homesteadPillRate: function () { return HomesteadEngine.pillRate(p.homesteadLevel); },
      homesteadAttackBonus: function () { return HomesteadEngine.fengshuiAttackBonus(p.homesteadLevel); },
      homesteadPending: function (nowMs) { return HomesteadEngine.compute(p.homesteadLevel, nowMs, p.lastHomesteadCollect); },
      // 收取挂起产出：首收(last=0)仅启动计时不补发；否则入账并推进时间戳
      collectHomestead: function (nowMs) {
        if (p.lastHomesteadCollect <= 0) { p.lastHomesteadCollect = nowMs; return { incense: 0, pills: 0, hours: 0.0, capped: false }; }
        var r = HomesteadEngine.compute(p.homesteadLevel, nowMs, p.lastHomesteadCollect);
        if (r.incense > 0 || r.pills > 0) { p.incense += r.incense; p.yaodan += r.pills; }
        p.lastHomesteadCollect = nowMs;
        return r;
      },
      canUpgradeHomestead: function () { return p.yaodan >= HomesteadEngine.upgradeCost(p.homesteadLevel); },
      upgradeHomestead: function () {
        var cost = HomesteadEngine.upgradeCost(p.homesteadLevel);
        if (p.yaodan < cost) return false;
        p.yaodan -= cost; p.homesteadLevel += 1; return true;
      },
    };

  function applyGacha(r) {
      var mon = monsterById(r.monsterId);
      var dup = p.codex.has(r.monsterId);
      var inst = newInstance(r.monsterId, 1);
      p.codex.catch(r.monsterId);
      var yaodanGain = 0;
      if (dup) { p.yaodan += GachaEngine.DUPLICATE_YAODAN; yaodanGain = GachaEngine.DUPLICATE_YAODAN; }
      record({ gacha: true });
      return { result: r, monster: mon, instance: inst, duplicate: dup, yaodanGain: yaodanGain };
    }
    p._applyGacha = applyGacha;
  }

  function Player_sectAtk(p) {
    var b = SectTechEngine.bonuses(p.sectLevels, p.faction);
    return b.attack;
  }

  // ───────────────────────── 导出 ─────────────────────────
  return {
    RARITY: RARITY, RARITY_LABEL: RARITY_LABEL, RARITY_GROWTH: RARITY_GROWTH, RARITY_ORDER: RARITY_ORDER,
    CATEGORY: CATEGORY, CATEGORY_LABEL: CATEGORY_LABEL,
    ELEMENT: ELEMENT, ELEMENT_LABEL: ELEMENT_LABEL,
    FACTION: FACTION, FACTION_LABEL: FACTION_LABEL,
    SKILLS: SKILLS, MONSTERS: MONSTERS, STARTERS: STARTERS,
    monsterById: monsterById, skillsOf: skillsOf, byRarity: byRarity, randomEncounter: randomEncounter,
    CaptureEngine: CaptureEngine, GachaEngine: GachaEngine, CultivationEngine: CultivationEngine,
    TalentEngine: TalentEngine, BattleEngine: BattleEngine, FormationEngine: FormationEngine,
    SectTechEngine: SectTechEngine, SocialEngine: SocialEngine, LocationEngine: LocationEngine,
    ReputationEngine: ReputationEngine, ComboEngine: ComboEngine, WeatherEngine: WeatherEngine, FatigueEngine: FatigueEngine,
    RankEngine: RankEngine, RankBanPickEngine: RankBanPickEngine, DungeonEngine: DungeonEngine, SecretRealmEngine: SecretRealmEngine,
    HomesteadEngine: HomesteadEngine,
    DailyResetEngine: DailyResetEngine, DailyResetScheduler: DailyResetScheduler, ShopEngine: ShopEngine, SeasonEngine: SeasonEngine,
    HonorEngine: HonorEngine, ActivityEngine: ActivityEngine, TutorialEngine: TutorialEngine,
    MockPlatform: MockPlatform,
    EquipmentEngine: EquipmentEngine, FusionEngine: FusionEngine, EQUIPMENTS: EQUIPMENTS,
    equipDefById: equipDefById, EQUIP_SLOT_LABEL: EQUIP_SLOT_LABEL, EQUIP_STAT_LABEL: EQUIP_STAT_LABEL,
    elementMultiplier: elementMultiplier, categoryMultiplier: categoryMultiplier,
    elementLabel: elementLabel, statsAtLevel: statsAtLevel, makeCombatant: makeCombatant,
    makePlayer: makePlayer, makeCodex: makeCodex, QUEST_DEFS: QUEST_DEFS,
    clamp: clamp,
  };
});
