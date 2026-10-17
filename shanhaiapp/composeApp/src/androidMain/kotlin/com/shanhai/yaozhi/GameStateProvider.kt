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
 * ⚠️ 用法（Screen 里）：
 *   val gsState = LocalGameState.current     // MutableState<GameState>
 *   val version = LocalGsVersion.current     // MutableState<Long>
 *   val _v = version.value                   // 订阅 → mutate 后必重组
 *   gsState.mutate(version) { it.someMutation() }
 */
val LocalGameState: ProvidableCompositionLocal<MutableState<GameState>> =
    staticCompositionLocalOf { mutableStateOf(GameState()) }

/** 版本号：mutate{} 后自动 +1。Screen 必须 observe 它。 */
val LocalGsVersion: ProvidableCompositionLocal<MutableState<Long>> =
    staticCompositionLocalOf { mutableStateOf(0L) }

/**
 * 版本感知的 mutate（非 @Composable，可在 onClick 等任何地方调）。
 * 内部 bump version.value++ → Compose 观察到 Long 变了必重组。
 */
inline fun <T> MutableState<GameState>.mutate(
    version: MutableState<Long>,
    block: (GameState) -> T
): T {
    val result = block(value)
    version.value = version.value + 1L  // bump → 必重组
    return result
}

@Composable
fun GameStateProvider(initial: GameState = GameState(), content: @Composable () -> Unit) {
    val gsState = remember { mutableStateOf(initial) }
    val version = remember { mutableStateOf(0L) }
    CompositionLocalProvider(
        LocalGameState provides gsState,
        LocalGsVersion provides version,
        content = content,
    )
}
