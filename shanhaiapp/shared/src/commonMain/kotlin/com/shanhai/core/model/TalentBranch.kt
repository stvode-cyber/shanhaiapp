package com.shanhai.core.model

import kotlinx.serialization.Serializable

/**
 * 天赋树分支（GDD 十六·养成扩展：每只妖点 3 层天赋树 攻/防/辅）。
 * - ATTACK   攻：每点一层 +ATK%
 * - DEFENSE  防：每点一层 +DEF% 与 +HP%
 * - SUPPORT  辅：每点一层 +SPD%
 */
@Serializable
enum class TalentBranch { ATTACK, DEFENSE, SUPPORT }
