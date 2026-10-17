package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.logic.ShopEngine

@Composable
fun StoreScreen(modifier: Modifier = Modifier) {
    var lingyu by remember { mutableIntStateOf(500) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("商店", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("灵玉余额：$lingyu", fontSize = 16.sp, fontWeight = FontWeight.Medium)

        Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("引妖香·香火回补", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("花费 ${ShopEngine.INCENSE_REFILL_COST_LINGYU} 灵玉 → 补 ${ShopEngine.INCENSE_REFILL_AMOUNT} 香火（日限 ${ShopEngine.INCENSE_DAILY_BUY_CAP}）",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { lingyu -= ShopEngine.INCENSE_REFILL_COST_LINGYU },
                    enabled = lingyu >= ShopEngine.INCENSE_REFILL_COST_LINGYU,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("购买（-${ShopEngine.INCENSE_REFILL_COST_LINGYU} 灵玉）") }
            }
        }
    }
}
