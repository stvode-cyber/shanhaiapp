package com.shanhai.core.model

/**
 * 道德选择（GDD 十七·自由度）：收妖时的「镇压 / 超度」分支。
 *
 * - SUPPRESS 镇压：以强力慑服，攻击属性 +10%（代价：灵性未净，防御/生命不变）。
 * - TRANSCEND 超度：以善法点化，防御与生命各 +8%（代价：攻击不变）。
 *
 * 该标记持久化在 [MonsterInstance.moralChoice]，影响有效属性（见 [com.shanhai.core.logic.MoralChoiceEngine]）。
 * 剧情向奖励/后续内容钩子（如超度解锁特殊图鉴条目）留作内容层实现，本层只负责标记与属性倍率，
 * 保持 shared 逻辑层「纯判定」不耦合叙事。
 */
enum class MoralChoice {
    SUPPRESS,   // 镇压
    TRANSCEND   // 超度
}
