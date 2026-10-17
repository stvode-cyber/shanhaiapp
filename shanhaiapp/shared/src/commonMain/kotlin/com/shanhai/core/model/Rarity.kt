package com.shanhai.core.model

/**
 * 稀有度与成长率（GDD 十五·数值基础）。
 * 成长率放大基础属性：凡×1.0 / 灵×1.1 / 仙×1.25 / 神×1.5。
 */
enum class Rarity(val label: String, val growth: Double) {
    FAN("凡", 1.0),
    LING("灵", 1.1),
    XIAN("仙", 1.25),
    SHEN("神", 1.5);
}
