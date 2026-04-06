package zar.masih.opticalcontentextractor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import zar.masih.opticalcontentextractor.ui.theme.OpticalcontentExtractorTheme

class ProcessingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imagePath = intent.getStringExtra("IMAGE_PATH")
        val modelConfig = intent.getParcelableExtra<ModelArchitecture>("MODEL_CONFIG") ?: ModelArchitecture()
        val isSummaryMode = intent.getBooleanExtra("IS_SUMMARY_MODE", false)

        val layerIndex = 2
        val actualInputPath = imagePath ?: modelConfig.getLastValidPath(layerIndex)
        val bitmap = actualInputPath?.let { BitmapFactory.decodeFile(it) }

        setContent {
            OpticalcontentExtractorTheme {
                Scaffold { padding ->
                    if (bitmap != null) {
                        if (isSummaryMode) {
                            SummaryScreen(modelConfig, Modifier.padding(padding))
                        } else {
                            ProcessingScreen(bitmap, modelConfig, Modifier.padding(padding))
                        }
                    } else {
                        Text("No image found", modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryScreen(model: ModelArchitecture, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sortedCheckpoints = remember(model.checkpointPaths) { model.checkpointPaths.toSortedMap() }
    val lastLayerIndex = sortedCheckpoints.keys.lastOrNull() ?: 0
    var selectedLayerIndex by remember { mutableStateOf<Int?>(null) }
    
    val effectiveExportIndex = selectedLayerIndex ?: lastLayerIndex
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pipeline Export Selection", style = MaterialTheme.typography.headlineMedium)
        Text("Select a checkpoint to export (defaults to last)", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        sortedCheckpoints.forEach { (index, path) ->
            val layer = model.getLayer(index)
            val isSelected = selectedLayerIndex == index || (selectedLayerIndex == null && index == lastLayerIndex)
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { selectedLayerIndex = index },
                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) CardDefaults.outlinedCardBorder() else null
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Layer $index: ${layer.layerName}", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Checkpoint $index",
                            modifier = Modifier.height(200.dp).fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                val exportPath = model.checkpointPaths[effectiveExportIndex]
                val layer = model.getLayer(effectiveExportIndex)
                if (exportPath != null) {
                    exportBitmapToGallery(context, exportPath, layer.layerName)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export Layer $effectiveExportIndex Result")
        }

        OutlinedButton(
            onClick = {
                val intent = Intent(context, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Restart New Project")
        }
    }
}

@Composable
fun ProcessingScreen(source: Bitmap, initialModel: ModelArchitecture, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var model by remember { mutableStateOf(initialModel) }
    
    val layerIndex = 2
    val config = model.getLayer(layerIndex) as LayerConfig.AnalyticalCleanLayer
    
    var zones by remember { mutableStateOf(config.zones) }
    var selectedZoneId by remember { mutableStateOf(zones.firstOrNull()?.id) }
    var isLayerEnabled by remember { mutableStateOf(config.isEnabled) }
    
    val processingState by ProcessingEngine.processingState.collectAsState()

    val currentZone = zones.find { it.id == selectedZoneId }

    LaunchedEffect(zones, isLayerEnabled) {
        if (!isLayerEnabled) return@LaunchedEffect
        delay(150)
        ProcessingEngine.requestProcessing(
            ProcessingEngine.ProcessingTask.AnalyticalClean(source, zones)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ModelSummaryHeader(model, currentLayerIndex = layerIndex)
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Layer $layerIndex: Zonal Cleaning", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = isLayerEnabled, onCheckedChange = { isLayerEnabled = it })
        }

        if (isLayerEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Analytical Zones", style = MaterialTheme.typography.titleMedium)
            
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    ScrollableTabRow(selectedTabIndex = zones.indexOfFirst { it.id == selectedZoneId }.coerceAtLeast(0), edgePadding = 0.dp) {
                        zones.forEach { zone ->
                            Tab(
                                selected = selectedZoneId == zone.id,
                                onClick = { selectedZoneId = zone.id },
                                text = { Text(if (zone.isFullImage) "Full" else "Zone ${zones.indexOf(zone)}") }
                            )
                        }
                    }
                }
                IconButton(onClick = {
                    val newZone = AnalyticalZone(isFullImage = false, x = 0.25f, y = 0.25f, width = 0.5f, height = 0.5f)
                    zones = zones + newZone
                    selectedZoneId = newZone.id
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Zone")
                }
            }

            currentZone?.let { zone ->
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Region Settings", style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.weight(1f))
                            if (!zone.isFullImage) {
                                IconButton(onClick = {
                                    val newZones = zones.filter { it.id != zone.id }
                                    zones = newZones
                                    selectedZoneId = newZones.firstOrNull()?.id
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Zone", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        if (!zone.isFullImage) {
                            Text("Position X: ${(zone.x * 100).toInt()}%")
                            Slider(value = zone.x, onValueChange = { newValue -> 
                                zones = zones.map { z -> if (z.id == zone.id) z.copy(x = newValue) else z } 
                            }, valueRange = 0f..1f)
                            
                            Text("Position Y: ${(zone.y * 100).toInt()}%")
                            Slider(value = zone.y, onValueChange = { newValue -> 
                                zones = zones.map { z -> if (z.id == zone.id) z.copy(y = newValue) else z } 
                            }, valueRange = 0f..1f)

                            Text("Width: ${(zone.width * 100).toInt()}%")
                            Slider(value = zone.width, onValueChange = { newValue -> 
                                zones = zones.map { z -> if (z.id == zone.id) z.copy(width = newValue) else z } 
                            }, valueRange = 0.05f..1f)

                            Text("Height: ${(zone.height * 100).toInt()}%")
                            Slider(value = zone.height, onValueChange = { newValue -> 
                                zones = zones.map { z -> if (z.id == zone.id) z.copy(height = newValue) else z } 
                            }, valueRange = 0.05f..1f)
                        } else {
                            Text("Full Image Mode - Processing entire canvas", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Tuning Step", style = MaterialTheme.typography.labelLarge)

                        Text("Contrast Threshold: ${zone.contrastThreshold}")
                        Slider(
                            value = zone.contrastThreshold.toFloat(),
                            onValueChange = { newVal -> zones = zones.map { z -> if (z.id == zone.id) z.copy(contrastThreshold = newVal.toInt()) else z } },
                            valueRange = 0f..255f
                        )

                        Text("Kernel Size: ${zone.kernelSize}")
                        Slider(
                            value = zone.kernelSize.toFloat(),
                            onValueChange = { newVal -> zones = zones.map { z -> if (z.id == zone.id) z.copy(kernelSize = newVal.toInt()) else z } },
                            valueRange = 1f..15f
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        UnifiedPreview(
            initialBitmap = source,
            state = if (isLayerEnabled) processingState else ProcessingEngine.ProcessingState.Idle
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val currentBitmap = if (isLayerEnabled) {
                    when (val s = processingState) {
                        is ProcessingEngine.ProcessingState.Success -> s.bitmap
                        else -> source
                    }
                } else source

                val path = if (isLayerEnabled) saveBitmap(context, currentBitmap, "checkpoint_layer2.png") else model.getLastValidPath(layerIndex) ?: ""
                
                val updatedConfig = config.copy(
                    isEnabled = isLayerEnabled,
                    zones = zones
                )
                val updatedModel = model.updateLayer(layerIndex, updatedConfig)
                val finalModel = if (isLayerEnabled) updatedModel.setCheckpoint(layerIndex, path) else updatedModel
                
                finalModel.saveAsDefaults(context)

                val intent = Intent(context, ObjectRemovalActivity::class.java).apply {
                    putExtra("IMAGE_PATH", path)
                    putExtra("MODEL_CONFIG", finalModel)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Forward Pass -> Layer 3")
        }
    }
}
