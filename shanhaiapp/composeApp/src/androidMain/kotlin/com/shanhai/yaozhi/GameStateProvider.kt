package com.shanhai.yaozhi

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shanhai.core.logic.GameState
import com.shanhai.core.persistence.SaveData

/**
 * 让所有 Screen 通过 CompositionLocal 拿到同一个 GameState。
 *
 * ⚠️ Screen 用法：
 *   val gsState = LocalGameState.current
 *   val versionState = LocalGsVersion.current
 *   val _v = versionState.value           // 订阅 → mutate 后必重组
 *   gsState.mutate(versionState) { it.someMutation() }
 *
 * 自动存档：
 *   · 启动时：SaveManager.load() → applySaveData() 恢复进度
 *   · ON_PAUSE：SaveManager.save(gs.toSaveData()) 写盘
 *   · Screen 里也可手动调 SaveManager 存/读/重置
 */
val LocalGameState: ProvidableCompositionLocal<MutableState<GameState>> =
    staticCompositionLocalOf { mutableStateOf(GameState()) }

/** 版本号：mutate{} 后自动 +1。Screen 必须 observe 它触发重组。 */
val LocalGsVersion: ProvidableCompositionLocal<MutableState<Long>> =
    staticCompositionLocalOf { mutableStateOf(0L) }

/**
 * 版本感知的 mutate（非 @Composable，可在 onClick/AlertDialog lambda 里调）。
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

/**
 * GameState 工厂函数：从存档恢复进度（如果有）。
 * 没有存档 → 返回空 GameState()。
 */
fun loadInitialGameState(context: Context): GameState {
    val mgr = SaveManager(context.applicationContext)
    val save: SaveData? = mgr.load()
    return GameState().apply { save?.let { applySaveData(it) } }
}

@Composable
fun GameStateProvider(initial: GameState = GameState(), content: @Composable () -> Unit) {
    val gsState = remember { mutableStateOf(initial) }
    val version = remember { mutableStateOf(0L) }

    // 自动存档：监听 App 生命周期 ON_PAUSE
    val context = LocalContext.current
    val mgr = remember(context) { SaveManager(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                mgr.save(gsState.value.toSaveData())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(
        LocalGameState provides gsState,
        LocalGsVersion provides version,
        content = content,
    )
}
