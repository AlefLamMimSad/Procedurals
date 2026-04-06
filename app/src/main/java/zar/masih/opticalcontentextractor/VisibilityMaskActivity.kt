package zar.masih.opticalcontentextractor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import zar.masih.opticalcontentextractor.ui.theme.OpticalcontentExtractorTheme

class VisibilityMaskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imagePath = intent.getStringExtra("IMAGE_PATH")
        val modelConfig = intent.getParcelableExtra<ModelArchitecture>("MODEL_CONFIG") ?: ModelArchitecture()
        
        val layerIndex = 4
        val originalPath = modelConfig.checkpointPaths[0] ?: modelConfig.checkpointPaths[1]
        val originalBitmap = originalPath?.let { BitmapFactory.decodeFile(it) }

        val actualInputPath = imagePath ?: modelConfig.getLastValidPath(layerIndex)
        val maskBitmap = actualInputPath?.let { BitmapFactory.decodeFile(it) }

        setContent {
            OpticalcontentExtractorTheme {
                Scaffold { padding ->
                    if (maskBitmap != null && originalBitmap != null) {
                        VisibilityMaskScreen(originalBitmap, maskBitmap, modelConfig, Modifier.padding(padding))
                    } else {
                        Text("Missing image or mask", modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
fun VisibilityMaskScreen(original: Bitmap, mask: Bitmap, initialModel: ModelArchitecture, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var model by remember { mutableStateOf(initialModel) }
    val layerIndex = 4
    val config = model.getLayer(layerIndex) as LayerConfig.VisibilityMaskLayer
    
    var threshold by remember { mutableFloatStateOf(config.maskThreshold.toFloat()) }
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    val processingState by ProcessingEngine.processingState.collectAsState()

    LaunchedEffect(threshold, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        delay(150)
        ProcessingEngine.requestProcessing(
            ProcessingEngine.ProcessingTask.VisibilityMask(original, mask, threshold.toInt())
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ModelSummaryHeader(model, currentLayerIndex = layerIndex)
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Visibility Masking", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isEnabled) {
            Text("Mask Sensitivity: ${threshold.toInt()}")
            Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 0f..255f)
        } else {
            Text("Layer Disabled - Skipping calculation", color = MaterialTheme.colorScheme.secondary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        UnifiedPreview(
            initialBitmap = mask,
            state = if (isEnabled) processingState else ProcessingEngine.ProcessingState.Idle
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val currentBitmap = if (isEnabled) {
                    when (val s = processingState) {
                        is ProcessingEngine.ProcessingState.Success -> s.bitmap
                        else -> mask
                    }
                } else mask
                
                val fileName = "checkpoint_layer4.png"
                val path = if (isEnabled) saveBitmap(context, currentBitmap, fileName) else model.getLastValidPath(layerIndex) ?: ""
                
                val updatedConfig = config.copy(maskThreshold = threshold.toInt(), isEnabled = isEnabled)
                val updatedModel = model.updateLayer(layerIndex, updatedConfig)
                val finalModel = if (isEnabled) updatedModel.setCheckpoint(layerIndex, path) else updatedModel
                
                finalModel.saveAsDefaults(context)
                
                val nextIntent = Intent(context, GradientExtractionActivity::class.java).apply {
                    putExtra("IMAGE_PATH", path)
                    putExtra("MODEL_CONFIG", finalModel)
                }
                context.startActivity(nextIntent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Forward Pass -> Layer 5 (Gradient Extraction)")
        }
    }
}
