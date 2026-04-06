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

class ObjectRemovalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imagePath = intent.getStringExtra("IMAGE_PATH")
        val modelConfig = intent.getParcelableExtra<ModelArchitecture>("MODEL_CONFIG") ?: ModelArchitecture()
        val step = intent.getStringExtra("REMOVAL_STEP") ?: "INTERMITTENT"
        
        val layerIndex = when(step) {
            "BIGGEST" -> 9
            "FINAL" -> 7
            else -> 3
        }
        
        val actualInputPath = imagePath ?: modelConfig.getLastValidPath(layerIndex)
        val bitmap = actualInputPath?.let { BitmapFactory.decodeFile(it) }

        setContent {
            OpticalcontentExtractorTheme {
                Scaffold { padding ->
                    if (bitmap != null) {
                        ObjectRemovalScreen(bitmap, modelConfig, step, Modifier.padding(padding))
                    } else {
                        Text("No image found", modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
fun ObjectRemovalScreen(source: Bitmap, initialModel: ModelArchitecture, step: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var model by remember { mutableStateOf(initialModel) }
    
    val layerIndex = when(step) {
        "BIGGEST" -> 9
        "FINAL" -> 7
        else -> 3
    }
    
    val config = model.getLayer(layerIndex) as LayerConfig.ObjectRemovalLayer
    
    var nToRemove by remember { mutableFloatStateOf(config.nToRemove.toFloat()) }
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    val processingState by ProcessingEngine.processingState.collectAsState()

    LaunchedEffect(nToRemove, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        delay(150)
        val task = if (step == "BIGGEST") {
            ProcessingEngine.ProcessingTask.RemoveBiggestObjects(source, nToRemove.toInt())
        } else {
            ProcessingEngine.ProcessingTask.ObjectRemoval(source, nToRemove.toInt())
        }
        ProcessingEngine.requestProcessing(task)
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
            Text(config.layerName, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isEnabled) {
            Text("Number of objects to remove: ${nToRemove.toInt()}")
            Slider(value = nToRemove, onValueChange = { nToRemove = it }, valueRange = 0f..if (step == "BIGGEST") 20f else 200f)
        } else {
            Text("Layer Disabled - Skipping", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
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
                
                val fileName = "checkpoint_layer$layerIndex.png"
                val path = if (isEnabled) saveBitmap(context, currentBitmap, fileName) else model.getLastValidPath(layerIndex) ?: ""
                
                val updatedConfig = config.copy(nToRemove = nToRemove.toInt(), isEnabled = isEnabled)
                val updatedModel = model.updateLayer(layerIndex, updatedConfig)
                val finalModel = if (isEnabled) updatedModel.setCheckpoint(layerIndex, path) else updatedModel
                
                finalModel.saveAsDefaults(context)
                
                val nextIntent = when(step) {
                    "INTERMITTENT" -> Intent(context, VisibilityMaskActivity::class.java)
                    "FINAL" -> Intent(context, InpaintActivity::class.java)
                    else -> {
                        Intent(context, ProcessingActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("IS_SUMMARY_MODE", true)
                        }
                    }
                }
                
                nextIntent.putExtra("IMAGE_PATH", path)
                nextIntent.putExtra("MODEL_CONFIG", finalModel)
                context.startActivity(nextIntent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            val nextText = when(step) {
                "INTERMITTENT" -> "Forward Pass -> Layer 4"
                "FINAL" -> "Forward Pass -> Layer 8"
                else -> "Complete & View Summary"
            }
            Text(nextText)
        }
    }
}
