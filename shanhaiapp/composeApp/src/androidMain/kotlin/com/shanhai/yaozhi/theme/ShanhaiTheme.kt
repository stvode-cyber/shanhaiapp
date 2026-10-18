package com.shanhai.yaozhi.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.model.Rarity

/**
 * 山海妖志 · 中国风主题 v3
 * v3 新增：稀有度统一色阶 + 宣纸渐变背景 Brush + 动画公共参数
 */

// ===== 主色板 =====
val ZhuSha       = Color(0xFFC0392B)   // 朱砂（主品牌·主动作·危险·伤害）
val ZhuShaDark   = Color(0xFF8B291E)   // 深朱砂
val ZhuShaSoft   = Color(0xFFE85748)   // 浅朱砂（临界 HP 脉动）
val XuanZhi      = Color(0xFFF5F1E8)   // 宣纸（背景）
val XuanZhiDark  = Color(0xFF2C2C2C)   // 深色宣纸
val QingDai      = Color(0xFF2C5F4A)   // 青黛（次品牌·成功·我方·被克高亮）
val QingDaiLight = Color(0xFF4A8C74)   // 浅青黛
val MoHei        = Color(0xFF1A1A1A)   // 墨黑（文字）
val MoHeiSoft    = Color(0xFF3D3D3D)   // 软墨
val JinHuang     = Color(0xFFC89B3C)   // 金黄（点缀·神·高亮）
val YanHui       = Color(0xFF5C5C5C)   // 烟灰（次级文字）
val XueBai       = Color(0xFFFFFFFF)   // 雪白（反色）

// ===== 稀有度统一色阶 =====
// 所有 Screen 必须用这套色，禁止各 Screen 散写 Rarity 颜色
val RarityColors = mapOf(
    Rarity.FAN   to Color(0xFF7F8C8D),  // 凡·烟灰
    Rarity.LING  to Color(0xFF3498DB),  // 灵·靛蓝
    Rarity.XIAN  to Color(0xFF9B59B6),  // 仙·紫红
    Rarity.SHEN  to Color(0xFFF39C12),  // 神·金黄
)

fun colorForRarity(r: Rarity): Color = RarityColors[r] ?: YanHui

// ===== 伤害/状态色 =====
val DamageCritical = ZhuSha            // 暴击/大伤害
val DamageNormal  = ZhuShaSoft         // 普通伤害
val HealingGreen  = QingDaiLight       // 治疗/成功
val HitCritPulse  = ZhuSha.copy(alpha = 0.3f)   // 暴击闪光底
val HitResistPulse = QingDai.copy(alpha = 0.3f) // 被克制闪光底
val CritFloatText  = JinHuang          // 暴击飘字
val ResistFloatText = QingDai          // 被克制飘字

// ===== 宣纸渐变背景（用于各 Screen 根容器）=====
private val XuanZhiRadialBrush = Brush.radialGradient(
    colors = listOf(
        Color(0xFFFAF6EE),
        XuanZhi,
        Color(0xFFEDE8DA),
    ),
    center = androidx.compose.ui.geometry.Offset(0.5f, 0.3f),
    radius = 600f,
)

@Composable
fun XuanZhiBackground(modifier: Modifier = Modifier): Modifier =
    modifier.background(XuanZhiRadialBrush, RectangleShape)

// ===== 动画公共参数 =====
val QuickAnim: AnimationSpec<Float> = tween(150, easing = FastOutSlowInEasing)
val NormalAnim: AnimationSpec<Float> = tween(300, easing = FastOutSlowInEasing)
val SlowAnim: AnimationSpec<Float> = tween(600, easing = FastOutSlowInEasing)

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
