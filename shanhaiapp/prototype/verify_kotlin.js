const game = require('./game.js');

// 测试 #1: 伤害公式
const attacker = game.MonsterPool.byId('yao_she').toCombatant(1, [game.SkillLibrary.get('s_jingtao')], 0);
const defender = game.MonsterPool.byId('jing_denglong').toCombatant(1, [], 0);
const skill = game.SkillLibrary.get('s_jingtao');
const res = game.BattleEngine.damage(attacker, defender, skill, () => 0.95);
console.log(`伤害公式测试:`);
console.log(`  damage=${res.damage}, elementMult=${res.elementMult}, crit=${res.crit}`);
console.log(`  (Kotlin 期望值 170 实际 155)`);

// 测试 #2: 类别克制环
const elems = Object.values(game.Element);
// 克制环检查
const catMult = game.BattleEngine.categoryMultiplier(game.Element.YAO, game.Element.JING);
console.log(`\n类别克制环: YAO vs JING 倍率=${catMult}`);
console.log(`  (Kotlin 期望值 1.0 实际 1.3)`);

// 测试 #3: 炼妖融合
console.log(`\n=== 炼妖融合测试需要看具体 case ===`);
