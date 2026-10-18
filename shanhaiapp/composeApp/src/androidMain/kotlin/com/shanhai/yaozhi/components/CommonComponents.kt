package com.shanhai.yaozhi.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.model.Rarity
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.delay

/**
 * SectionCard — 统一的列表卡片容器
 * 替代各 Screen 里散写的 Surface(shape=RoundedCornerShape(10.dp), tonalElevation=1.dp)
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

/**
 * EmptyState — 统一的空状态组件
 * 列表全空时显示友好提示，避免白屏
 */
@Composable
fun EmptyState(
    icon: String,
    message: String,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(icon, fontSize = 40.sp)
        Text(message, fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium)
        action?.invoke()
    }
}

// ===== Round 2 新增公共润色组件 =====

/**
 * RarityChip — 稀有度标签（统一用 Theme v3 RarityColors）
 * 所有 Screen 禁止散写 Rarity 颜色，统一用这个组件
 */
@Composable
fun RarityChip(
    rarity: Rarity,
    modifier: Modifier = Modifier,
    size: ChipSize = ChipSize.MEDIUM
) {
    val color = colorForRarity(rarity)
    val bgColor by animateColorAsState(
        targetValue = color.copy(alpha = 0.15f),
        animationSpec = tween(300),
        label = "rarityChipBg"
    )
    val (hPadding, vPadding, fontSize) = when (size) {
        ChipSize.SMALL -> Triple(6.dp, 2.dp, 10.sp)
        ChipSize.MEDIUM -> Triple(10.dp, 4.dp, 12.sp)
        ChipSize.LARGE -> Triple(14.dp, 6.dp, 14.sp)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = rarity.label,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = hPadding, vertical = vPadding)
        )
    }
}

enum class ChipSize { SMALL, MEDIUM, LARGE }

/**
 * GradientHeader — 宣纸渐变标题栏
 * 所有 Screen 顶部统一用这个，替代散写的 Row + Text
 */
@Composable
fun GradientHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            JinHuang.copy(alpha = 0.08f),
                            XuanZhi,
                            QingDai.copy(alpha = 0.06f)
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MoHei)
                    if (subtitle != null) {
                        Text(subtitle, fontSize = 12.sp, color = YanHui)
                    }
                }
                if (actions != null) {
                    Row(content = actions)
                }
            }
        }
    }
}

/**
 * AnimatedStatCard — 数值动画卡片
 * 带数字递增动画 + 图标 + 标签，用于 Hub/Cultivate/Sect 等统计展示
 */
@Composable
fun AnimatedStatCard(
    label: String,
    value: Int,
    icon: String,
    accentColor: Color = QingDai,
    modifier: Modifier = Modifier,
    durationMs: Int = 600
) {
    var displayValue by remember { mutableIntStateOf(0) }
    LaunchedEffect(value) {
        if (value > 0) {
            val steps = 20
            val stepValue = value / steps
            repeat(steps) { i ->
                delay(durationMs / steps.toLong())
                displayValue = stepValue * (i + 1)
            }
            displayValue = value
        } else {
            displayValue = 0
        }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.08f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 20.sp)
            }
            Column {
                Text("$displayValue", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accentColor)
                Text(label, fontSize = 11.sp, color = YanHui)
            }
        }
    }
}

// (clip import 已在顶部引入，不需要扩展)
