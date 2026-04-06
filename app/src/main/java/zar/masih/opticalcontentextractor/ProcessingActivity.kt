package zar.masih.opticalcontentextractor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        val bitmap = imagePath?.let { BitmapFactory.decodeFile(it) }
        val modelConfig = intent.getParcelableExtra<ModelArchitecture>("MODEL_CONFIG") ?: ModelArchitecture()
        val isSummaryMode = intent.getBooleanExtra("IS_SUMMARY_MODE", false)

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
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Sequential Pipeline Export", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        model.checkpointPaths.toSortedMap().forEach { (index, path) ->
            val layer = model.getLayer(index)
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Layer $index: ${layer.layerName}", style = MaterialTheme.typography.titleMedium)
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
                Toast.makeText(context, "Exporting ${model.checkpointPaths.size} checkpoints...", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export All Resultant Data")
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
    var isLayerEnabled by remember { mutableStateOf(config.isEnabled) }
    
    val processingState by ProcessingEngine.processingState.collectAsState()

    ModelSummaryHeader(model, currentLayerIndex = layerIndex)

    LaunchedEffect(kernelSize, contrastThreshold, isLayerEnabled) {
        if (!isLayerEnabled) {
            return@LaunchedEffect
        }
        
        // Debounce: wait for user to finish sliding
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
            Switch(checked = isLayerEnabled, onCheckedChange = { isLayerEnabled = it })
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isLayerEnabled) {
            Text("Contrast Threshold: ${contrastThreshold.toInt()}")
            Slider(value = contrastThreshold, onValueChange = { contrastThreshold = it }, valueRange = 0f..255f)

            Text("Kernel Size: ${kernelSize.toInt()}")
            Slider(value = kernelSize, onValueChange = { kernelSize = it }, valueRange = 1f..15f)
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

                val path = saveBitmap(context, currentBitmap, "checkpoint_layer2.png")
                val updatedModel = model.updateLayer(layerIndex, config.copy(
                    isEnabled = isLayerEnabled,
                    contrastThreshold = contrastThreshold.toInt(),
                    kernelSize = kernelSize.toInt()
                )).setCheckpoint(layerIndex, path)

                val intent = Intent(context, ObjectRemovalActivity::class.java).apply {
                    putExtra("IMAGE_PATH", path)
                    putExtra("MODEL_CONFIG", updatedModel)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Forward Pass -> Layer 3 (Object Removal)")
        }
    }
}
