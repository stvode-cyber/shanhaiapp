package com.shanhai.yaozhi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.shanhai.core.logic.GameState

/** 让所有 Screen 通过 CompositionLocal 拿到同一个 GameState 实例。 */
val LocalGameState: ProvidableCompositionLocal<GameState> =
    staticCompositionLocalOf { GameState() }

@Composable
fun GameStateProvider(gs: GameState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGameState provides gs, content = content)
}
