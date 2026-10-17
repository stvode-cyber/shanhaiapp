package com.shanhai.yaozhi.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 山海妖志 · 中国风主题 v2
 * 主色：朱砂红 / 宣纸白 / 青黛绿 / 墨黑 / 金黄点缀
 * 字体：系统默认（中文渲染最佳）
 */

// ===== 主色板 =====
val ZhuSha       = Color(0xFFC0392B)   // 朱砂（主品牌）
val ZhuShaDark   = Color(0xFF8B291E)   // 深朱砂
val XuanZhi      = Color(0xFFF5F1E8)   // 宣纸（背景）
val XuanZhiDark  = Color(0xFF2C2C2C)   // 深色宣纸
val QingDai      = Color(0xFF2C5F4A)   // 青黛（次品牌）
val QingDaiLight = Color(0xFF4A8C74)   // 浅青黛
val MoHei        = Color(0xFF1A1A1A)   // 墨黑（文字）
val MoHeiSoft    = Color(0xFF3D3D3D)   // 软墨
val JinHuang     = Color(0xFFC89B3C)   // 金黄（点缀）
val YanHui       = Color(0xFF5C5C5C)   // 烟灰（次级文字）
val XueBai       = Color(0xFFFFFFFF)   // 雪白（反色）

// ===== Light Scheme =====
val LightColorScheme = lightColorScheme(
    primary = ZhuSha,
    onPrimary = XuanZhi,
    primaryContainer = Color(0xFFFDECEA),
    onPrimaryContainer = ZhuShaDark,
    secondary = QingDai,
    onSecondary = XuanZhi,
    secondaryContainer = Color(0xFFE4EEE9),
    onSecondaryContainer = Color(0xFF1A3D2F),
    tertiary = JinHuang,
    onTertiary = MoHei,
    tertiaryContainer = Color(0xFFFDF3DA),
    background = XuanZhi,
    onBackground = MoHei,
    surface = XuanZhi,
    onSurface = MoHei,
    surfaceVariant = Color(0xFFE8E1D4),
    onSurfaceVariant = YanHui,
    outline = Color(0xFFD4C9B5),
    outlineVariant = Color(0xFFE8E1D4),
)

// ===== Dark Scheme =====
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE57373),
    onPrimary = MoHei,
    primaryContainer = Color(0xFF6E1A12),
    onPrimaryContainer = Color(0xFFFDECEA),
    secondary = QingDaiLight,
    onSecondary = XuanZhi,
    secondaryContainer = Color(0xFF1A3D2F),
    background = Color(0xFF121212),
    onBackground = XuanZhi,
    surface = Color(0xFF1E1E1E),
    onSurface = XuanZhi,
    surfaceVariant = Color(0xFF3D3D3D),
    onSurfaceVariant = Color(0xFFB8B0A0),
    outline = Color(0xFF5A5448),
)

// ===== Typography（三档）=====
private val ShanhaiTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

// ===== Shapes（四级圆角）=====
private val ShanhaiShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun ShanhaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = ShanhaiTypography,
        shapes = ShanhaiShapes,
        content = content
    )
}
