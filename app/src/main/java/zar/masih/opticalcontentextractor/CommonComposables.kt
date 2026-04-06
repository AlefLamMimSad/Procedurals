package zar.masih.opticalcontentextractor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ModelSummaryHeader(model: ModelArchitecture, currentLayerIndex: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Sequential Model Summary", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(model.layers) { index, layer ->
                    val isActive = index == currentLayerIndex
                    val isTuned = index < currentLayerIndex
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primary 
                                    else if (isTuned) MaterialTheme.colorScheme.secondary 
                                    else MaterialTheme.colorScheme.outline
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isTuned) Icon(Icons.Default.CheckCircle, "", tint = Color.White, modifier = Modifier.size(16.dp))
                            else Text("${index}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            layer.layerName.split(" ")[0], 
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    if (index < model.layers.size - 1) {
                        Text("→", modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
