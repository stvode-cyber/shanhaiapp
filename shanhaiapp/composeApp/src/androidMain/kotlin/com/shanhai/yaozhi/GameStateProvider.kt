package com.shanhai.yaozhi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.shanhai.core.logic.GameState

/**
 * 让所有 Screen 通过 CompositionLocal 拿到同一个 GameState。
 *
 * 关键：用 MutableState 包装 + mutate {} helper，保证 GameState 普通 var 字段变化后
 * Compose 能触发重组。否则 UI 会"假死"（改了 lingyu 但数字不刷新）。
 */
val LocalGameState: ProvidableCompositionLocal<MutableState<GameState>> =
    staticCompositionLocalOf { mutableStateOf(GameState()) }

/** 在 CompositionLocal 的 GameState 上安全地执行 mutation，并显式触发 Compose 重组。 */
inline fun <T> MutableState<GameState>.mutate(block: (GameState) -> T): T {
    val result = block(value)
    value = value  // 显式触发重组（MutableState 对同一引用也会通知观察者）
    return result
}

@Composable
fun GameStateProvider(initial: GameState = GameState(), content: @Composable () -> Unit) {
    val state = remember { mutableStateOf(initial) }
    CompositionLocalProvider(LocalGameState provides state, content = content)
}
