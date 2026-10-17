package com.shanhai.core.logic

import com.shanhai.core.model.Rarity
import com.shanhai.core.data.defaultRandom

/**
 * 收妖引擎：实现 GDD「纯点按三步」的极简收妖。
 * 光环从最大半径收缩到最小，玩家在最接近最佳点时再点一下入葫芦。
 * - 稀有/强妖光环更快（更难卡点）；辅助模式（默认开）放慢+放宽判定。
 * - 没卡准则妖挣扎、光环重置，可再来（由 UI 控制重试）。
 */
object CaptureEngine {

    /** 光环收缩一圈所需毫秒：稀有更快；辅助模式放慢 1.4 倍更宽容 */
    fun ringCycleMs(rarity: Rarity, assist: Boolean): Long {
        val base = when (rarity) {
            Rarity.FAN -> 2600L
            Rarity.LING -> 2200L
            Rarity.XIAN -> 1900L
            Rarity.SHEN -> 1600L
        }
        return (base * if (assist) 1.4 else 1.0).toLong()
    }

    /**
     * 由点按瞬间的光环半径换算命中精度（0..1）：
     * 半径越接近 minRadius 精度越高；辅助模式整体抬升、判定更宽松。
     */
    fun accuracy(tapRadius: Double, minRadius: Double, maxRadius: Double, assist: Boolean): Double {
        val span = (maxRadius - minRadius).coerceAtLeast(1.0)
        val raw = 1.0 - ((tapRadius - minRadius) / span).coerceIn(0.0, 1.0)
        return if (assist) (raw * 0.8 + 0.2).coerceIn(0.0, 1.0) else raw
    }

    /** 收妖成功率：精度 × 稀有度系数，保底 6%（零门槛但不无脑） */
    fun successProbability(accuracy: Double, rarity: Rarity, assist: Boolean, captureMul: Double = 1.0): Double {
        val rarityFactor = when (rarity) {
            Rarity.FAN -> 1.0
            Rarity.LING -> 0.9
            Rarity.XIAN -> 0.8
            Rarity.SHEN -> 0.7
        }
        val eff = (accuracy + if (assist) 0.18 else 0.0).coerceIn(0.0, 1.0)
        return ((eff * rarityFactor * 0.92 + 0.06) * captureMul).coerceIn(0.0, 1.0)
    }

    fun resolve(
        accuracy: Double,
        rarity: Rarity,
        assist: Boolean,
        rng: () -> Double = ::defaultRandom,
        captureMul: Double = 1.0,
    ): CaptureResult {
        val p = successProbability(accuracy, rarity, assist, captureMul)
        return CaptureResult(success = rng() < p, probability = p, struggled = rng() >= p, accuracy = accuracy)
    }
}

data class CaptureResult(
    val success: Boolean,
    val probability: Double,
    val struggled: Boolean,
    val accuracy: Double,
)
