package com.shanhai.core.logic

import com.shanhai.core.model.Element

/**
 * 天气地形战斗（GDD 十九·长线留存）：天气/地形作为战斗环境变量，
 * 临时偏置某行元素的攻击倍率，从而改变五行克制的实际效果。
 *
 * - 天气 [Weather]：晴 CLEAR / 雨 RAIN / 雪 SNOW / 风 WINDY。
 * - 地形复用 [LocationEngine.RegionTheme]（山林利木、水域利水、古墓利土、东海利水），偏向元素 ×1.2。
 *
 * 规则为纯函数，可 Python 等价验证；真实天气/地形由平台层（天气 API / 地图 SDK）提供，
 * 本地默认晴、无地形（属运行时环境变量，不进存档）。
 */
enum class Weather { CLEAR, RAIN, SNOW, WINDY }

object WeatherEngine {
    /** 某行元素在指定天气下的攻击偏置倍率（无偏置为 1.0） */
    fun weatherAttackMul(elem: Element, weather: Weather): Double = when (weather) {
        Weather.CLEAR -> 1.0
        Weather.RAIN  -> when (elem) { Element.WATER -> 1.3; Element.FIRE -> 0.8; else -> 1.0 }
        Weather.SNOW  -> when (elem) { Element.FIRE -> 0.7; Element.WATER -> 1.2; else -> 1.0 }
        Weather.WINDY -> when (elem) { Element.WIND -> 1.3; Element.EARTH -> 0.8; else -> 1.0 }
    }

    /** 地形偏向乘子：攻击元素 == 地形偏向元素 → ×1.2，否则 ×1.0 */
    fun terrainAttackMul(elem: Element, bias: Element?): Double =
        if (bias != null && elem == bias) 1.2 else 1.0

    /** 综合环境乘子（天气 × 地形） */
    fun envMultiplier(elem: Element, weather: Weather, bias: Element?): Double =
        weatherAttackMul(elem, weather) * terrainAttackMul(elem, bias)
}
