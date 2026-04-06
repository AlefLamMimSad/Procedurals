package zar.masih.opticalcontentextractor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
    
    var kernelSize by remember { mutableFloatStateOf(config.kernelSize.toFloat()) }
    var contrastThreshold by remember { mutableFloatStateOf(config.contrastThreshold.toFloat()) }
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    
    val processingState by ProcessingEngine.processingState.collectAsState()

    ModelSummaryHeader(model, currentLayerIndex = layerIndex)

    LaunchedEffect(kernelSize, contrastThreshold, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        delay(150)
        ProcessingEngine.requestProcessing(
            ProcessingEngine.ProcessingTask.AnalyticalClean(
                source, contrastThreshold.toInt(), kernelSize.toInt()
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Layer $layerIndex: ${config.layerName}", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isEnabled) {
            Text("Contrast Threshold: ${contrastThreshold.toInt()}")
            Slider(value = contrastThreshold, onValueChange = { contrastThreshold = it }, valueRange = 0f..255f)

            Text("Kernel Size: ${kernelSize.toInt()}")
            Slider(value = kernelSize, onValueChange = { kernelSize = it }, valueRange = 1f..15f)
        } else {
            Text("Layer Disabled - Skipping calculation", color = MaterialTheme.colorScheme.secondary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        UnifiedPreview(
            initialBitmap = source,
            state = if (isEnabled) processingState else ProcessingEngine.ProcessingState.Idle
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val currentBitmap = if (isEnabled) {
                    when (val s = processingState) {
                        is ProcessingEngine.ProcessingState.Success -> s.bitmap
                        else -> source
                    }
                } else source

                val path = if (isEnabled) saveBitmap(context, currentBitmap, "checkpoint_layer2.png") else model.getLastValidPath(layerIndex) ?: ""
                
                val updatedConfig = config.copy(
                    isEnabled = isEnabled,
                    contrastThreshold = contrastThreshold.toInt(),
                    kernelSize = kernelSize.toInt()
                )
                val updatedModel = model.updateLayer(layerIndex, updatedConfig)
                val finalModel = if (isEnabled) updatedModel.setCheckpoint(layerIndex, path) else updatedModel
                
                // Persist hyper-parameters
                finalModel.saveAsDefaults(context)

                val intent = Intent(context, ObjectRemovalActivity::class.java).apply {
                    putExtra("IMAGE_PATH", path)
                    putExtra("MODEL_CONFIG", finalModel)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Forward Pass -> Layer 3 (Object Removal)")
        }
    }
}
