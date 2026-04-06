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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import zar.masih.opticalcontentextractor.ui.theme.ObjectHighlightColor
import zar.masih.opticalcontentextractor.ui.theme.OpticalcontentExtractorTheme

class GradientExtractionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imagePath = intent.getStringExtra("IMAGE_PATH")
        val modelConfig = intent.getParcelableExtra<ModelArchitecture>("MODEL_CONFIG") ?: ModelArchitecture()
        
        val layerIndex = 5
        // If the previous layer was disabled, it passes the input path along. 
        // We use getLastValidPath as a fallback to find the last modified bitmap in the pipeline.
        val actualInputPath = imagePath ?: modelConfig.getLastValidPath(layerIndex)
        val bitmap = actualInputPath?.let { BitmapFactory.decodeFile(it) }

        setContent {
            OpticalcontentExtractorTheme {
                Scaffold { padding ->
                    if (bitmap != null) {
                        GradientExtractionScreen(bitmap, modelConfig, Modifier.padding(padding))
                    } else {
                        Text("No image found", modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
fun GradientExtractionScreen(source: Bitmap, initialModel: ModelArchitecture, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val layerIndex = 5
    var model by remember { mutableStateOf(initialModel) }
    val config = model.getLayer(layerIndex) as LayerConfig.GradientExtractLayer
    
    var amplification by remember { mutableFloatStateOf(config.amplification) }
    var extractionThreshold by remember { mutableFloatStateOf(config.extractionThreshold.toFloat()) }
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    
    val processingState by ProcessingEngine.processingState.collectAsState()
    val highlightColor = ObjectHighlightColor.toArgb()

    LaunchedEffect(amplification, extractionThreshold, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        delay(150) // Debounce
        ProcessingEngine.requestProcessing(
            ProcessingEngine.ProcessingTask.GradientExtraction(
                source, amplification, extractionThreshold.toInt(), highlightColor
            )
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
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Layer $layerIndex: ${config.layerName}", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isEnabled) {
            Text("Gradient Amplification (Power): ${String.format("%.1f", amplification)}")
            Slider(value = amplification, onValueChange = { amplification = it }, valueRange = 1f..10f)

            Text("Extraction Sensitivity (Threshold): ${extractionThreshold.toInt()}")
            Slider(value = extractionThreshold, onValueChange = { extractionThreshold = it }, valueRange = 0f..255f)
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Layer Disabled - Input will be passed to next stage.", color = MaterialTheme.colorScheme.secondary)
            }
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
                
                // Save the result only if enabled. If disabled, we pass the input path forward.
                val fileName = "checkpoint_layer5.png"
                val path = if (isEnabled) {
                    saveBitmap(context, currentBitmap, fileName)
                } else {
                    initialModel.getLastValidPath(layerIndex) ?: ""
                }
                
                val updatedConfig = config.copy(
                    amplification = amplification,
                    extractionThreshold = extractionThreshold.toInt(),
                    isEnabled = isEnabled
                )
                
                val updatedModel = model.updateLayer(layerIndex, updatedConfig)
                val finalModel = if (isEnabled) updatedModel.setCheckpoint(layerIndex, path) else updatedModel
                
                // Persist hyper-parameters as defaults for future runs
                finalModel.saveAsDefaults(context)

                val nextIntent = Intent(context, ObjectRemovalActivity::class.java).apply {
                    putExtra("IMAGE_PATH", path)
                    putExtra("IS_FINAL_STEP", true)
                    putExtra("MODEL_CONFIG", finalModel)
                }
                context.startActivity(nextIntent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirm & Forward -> Layer 6")
        }
    }
}
