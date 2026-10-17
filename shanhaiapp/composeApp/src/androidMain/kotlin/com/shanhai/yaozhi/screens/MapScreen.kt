package com.shanhai.yaozhi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shanhai.yaozhi.theme.QingDai
import com.shanhai.yaozhi.theme.ZhuSha
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * 巡妖图 · V1
 * osmdroid 2D 地图 + 4 个灵脉节点（城隍桥/古庙/老槐树/东海之滨）
 * 点节点 → 弹出刷妖面板 → 跳转收妖
 */
data class SpiritNode(
    val name: String,
    val geo: GeoPoint,
    val color: Color,
    val desc: String
)

private val NODES = listOf(
    SpiritNode("城隍桥", GeoPoint(39.9042, 116.4074), ZhuSha, "京城，子时常有鬼修出没"),
    SpiritNode("古庙", GeoPoint(30.2741, 120.1551), Color(0xFF8E44AD), "江南，山中古刹藏妖"),
    SpiritNode("老槐树", GeoPoint(34.2658, 108.9541), QingDai, "长安，千年成精之地"),
    SpiritNode("东海之滨", GeoPoint(24.4798, 118.0894), Color(0xFFE67E22), "厦门，海妖登陆点"),
)

@Composable
fun MapScreen(
    onEnterCapture: (SpiritNode) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedNode by remember { mutableStateOf<SpiritNode?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setMultiTouchControls(true)
                    controller.setZoom(11.0)
                    controller.setCenter(NODES[0].geo)
                    // 加灵脉 marker
                    NODES.forEach { node ->
                        val marker = Marker(this).apply {
                            position = node.geo
                            title = node.name
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { _, _ ->
                                selectedNode = node
                                true
                            }
                        }
                        overlays.add(marker)
                    }
                }
            },
            update = { mapView -> mapView.invalidate() }
        )

        // 顶部标题条
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Place, "巡妖图", tint = ZhuSha, modifier = Modifier.size(20.dp))
                Text("巡妖图 · 踏破铁鞋", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 节点点击后弹出刷妖面板
        selectedNode?.let { node ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(node.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = node.color)
                    Spacer(Modifier.height(4.dp))
                    Text(node.desc, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { selectedNode = null },
                            modifier = Modifier.weight(1f)
                        ) { Text("关闭") }
                        Button(
                            onClick = { onEnterCapture(node); selectedNode = null },
                            modifier = Modifier.weight(1f)
                        ) { Text("前往收妖") }
                    }
                }
            }
        }

        // 底部节点快选
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = 120.dp, bottom = 100.dp, start = 12.dp, end = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NODES.forEach { n ->
                    AssistChip(
                        onClick = { selectedNode = n },
                        label = { Text(n.name, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.Place, null, tint = n.color, modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }
        }
    }
}
