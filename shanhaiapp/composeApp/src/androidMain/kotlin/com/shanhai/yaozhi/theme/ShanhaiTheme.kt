package com.shanhai.yaozhi.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 山海妖志 · 中国风主题
 * 主色：朱砂红 / 宣纸白 / 青黛绿 / 墨黑
 */

val ZhuSha = Color(0xFFC0392B)      // 朱砂
val XuanZhi = Color(0xFFF5F1E8)      // 宣纸
val QingDai = Color(0xFF2C5F4A)      // 青黛
val MoHei = Color(0xFF1A1A1A)        // 墨黑
val JinHuang = Color(0xFFC89B3C)     // 金黄
val YanHui = Color(0xFF5C5C5C)       // 烟灰

val LightColorScheme = lightColorScheme(
    primary = ZhuSha,
    onPrimary = XuanZhi,
    primaryContainer = Color(0xFFFDECEA),
    secondary = QingDai,
    onSecondary = XuanZhi,
    secondaryContainer = Color(0xFFE4EEE9),
    tertiary = JinHuang,
    background = XuanZhi,
    onBackground = MoHei,
    surface = XuanZhi,
    onSurface = MoHei,
    surfaceVariant = Color(0xFFE8E1D4),
    outline = YanHui,
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE57373),
    onPrimary = MoHei,
    secondary = Color(0xFF66BB6A),
    background = Color(0xFF121212),
    onBackground = XuanZhi,
    surface = Color(0xFF1E1E1E),
    onSurface = XuanZhi,
)

@Composable
fun ShanhaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
