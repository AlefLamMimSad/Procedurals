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
        val isFinalStep = intent.getBooleanExtra("IS_FINAL_STEP", false)
        
        // If imagePath is null, try to recover from model checkpoints
        val layerIndex = if (isFinalStep) 6 else 3
        val actualInputPath = imagePath ?: modelConfig.getLastValidPath(layerIndex)
        val bitmap = actualInputPath?.let { BitmapFactory.decodeFile(it) }

        setContent {
            OpticalcontentExtractorTheme {
                Scaffold { padding ->
                    if (bitmap != null) {
                        ObjectRemovalScreen(bitmap, modelConfig, isFinalStep, Modifier.padding(padding))
                    } else {
                        Text("No image found", modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
fun ObjectRemovalScreen(source: Bitmap, initialModel: ModelArchitecture, isFinalStep: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var model by remember { mutableStateOf(initialModel) }
    val layerIndex = if (isFinalStep) 6 else 3
    val config = model.getLayer(layerIndex) as LayerConfig.ObjectRemovalLayer
    
    var nToRemove by remember { mutableFloatStateOf(config.nToRemove.toFloat()) }
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    val processingState by ProcessingEngine.processingState.collectAsState()

    LaunchedEffect(nToRemove, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        delay(150) // Debounce
        ProcessingEngine.requestProcessing(
            ProcessingEngine.ProcessingTask.ObjectRemoval(source, nToRemove.toInt())
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
            Text(if (isFinalStep) "Final Object Pruning" else "Intermittent Object Removal", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isEnabled) {
            Text("Number of smallest objects to remove: ${nToRemove.toInt()}")
            Slider(value = nToRemove, onValueChange = { nToRemove = it }, valueRange = 0f..200f)
        } else {
            Text("Layer Disabled - Skipping calculation", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
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
                
                val fileName = if (isFinalStep) "checkpoint_layer6.png" else "checkpoint_layer3.png"
                val path = if (isEnabled) saveBitmap(context, currentBitmap, fileName) else model.getLastValidPath(layerIndex) ?: ""
                
                val updatedConfig = config.copy(nToRemove = nToRemove.toInt(), isEnabled = isEnabled)
                val updatedModel = model.updateLayer(layerIndex, updatedConfig)
                
                val finalModel = if (isEnabled) updatedModel.setCheckpoint(layerIndex, path) else updatedModel
                
                // Persist hyper-parameters as defaults
                finalModel.saveAsDefaults(context)
                
                val nextIntent = if (isFinalStep) {
                    Intent(context, ProcessingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("IS_SUMMARY_MODE", true)
                    }
                } else {
                    Intent(context, VisibilityMaskActivity::class.java)
                }
                
                nextIntent.apply {
                    putExtra("IMAGE_PATH", path)
                    putExtra("MODEL_CONFIG", finalModel)
                }
                context.startActivity(nextIntent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isFinalStep) "Confirm & Continue" else "Forward Pass -> Layer 4")
        }
    }
}
