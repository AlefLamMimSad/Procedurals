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
        val maskPath = intent.getStringExtra("IMAGE_PATH")
        val maskBitmap = maskPath?.let { BitmapFactory.decodeFile(it) }
        val modelConfig = intent.getParcelableExtra<ModelArchitecture>("MODEL_CONFIG") ?: ModelArchitecture()
        
        // Find the original image from the first checkpoint (index 0 or 1)
        val originalPath = modelConfig.checkpointPaths[0] ?: modelConfig.checkpointPaths[1]
        val originalBitmap = originalPath?.let { BitmapFactory.decodeFile(it) }

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
    val processingState by ProcessingEngine.processingState.collectAsState()

    LaunchedEffect(threshold) {
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
        
        Text("Visibility Masking", style = MaterialTheme.typography.titleLarge)
        Text("Using Layer 3 as a mask over Original Image", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(16.dp))

        Text("Mask Sensitivity: ${threshold.toInt()}")
        Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 0f..255f)

        Spacer(modifier = Modifier.height(16.dp))

        UnifiedPreview(
            initialBitmap = mask,
            state = processingState
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val currentBitmap = when (val s = processingState) {
                    is ProcessingEngine.ProcessingState.Success -> s.bitmap
                    else -> mask
                }
                
                val path = saveBitmap(context, currentBitmap, "checkpoint_layer4.png")
                val updatedModel = model.updateLayer(layerIndex, config.copy(maskThreshold = threshold.toInt()))
                                        .setCheckpoint(layerIndex, path)
                
                val nextIntent = Intent(context, GradientExtractionActivity::class.java).apply {
                    putExtra("IMAGE_PATH", path)
                    putExtra("MODEL_CONFIG", updatedModel)
                }
                context.startActivity(nextIntent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Forward Pass -> Layer 5 (Gradient Extraction)")
        }
    }
}
