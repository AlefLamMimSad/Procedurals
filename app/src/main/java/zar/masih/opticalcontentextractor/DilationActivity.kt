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

class DilationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imagePath = intent.getStringExtra("IMAGE_PATH")
        val modelConfig = intent.getParcelableExtra<ModelArchitecture>("MODEL_CONFIG") ?: ModelArchitecture()
        
        val layerIndex = 5
        val actualInputPath = imagePath ?: modelConfig.getLastValidPath(layerIndex)
        val bitmap = actualInputPath?.let { BitmapFactory.decodeFile(it) }

        setContent {
            OpticalcontentExtractorTheme {
                Scaffold { padding ->
                    if (bitmap != null) {
                        DilationScreen(bitmap, modelConfig, Modifier.padding(padding))
                    } else {
                        Text("No image found", modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
fun DilationScreen(source: Bitmap, initialModel: ModelArchitecture, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val layerIndex = 5
    var model by remember { mutableStateOf(initialModel) }
    val config = model.getLayer(layerIndex) as LayerConfig.DilationLayer
    
    var radius by remember { mutableIntStateOf(config.radius) }
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    
    val processingState by ProcessingEngine.processingState.collectAsState()
    val highlightColor = ObjectHighlightColor.toArgb()

    LaunchedEffect(radius, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        delay(150)
        ProcessingEngine.requestProcessing(
            ProcessingEngine.ProcessingTask.Dilation(source, radius, highlightColor)
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
            Text("Expansion Radius: $radius")
            Slider(
                value = radius.toFloat(),
                onValueChange = { radius = it.toInt() },
                valueRange = 1f..10f,
                steps = 9
            )
        } else {
            Text("Layer Disabled - Input will be passed through.", color = MaterialTheme.colorScheme.secondary)
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
                
                val fileName = "checkpoint_layer5.png"
                val path = if (isEnabled) saveBitmap(context, currentBitmap, fileName) else model.getLastValidPath(layerIndex) ?: ""
                
                val updatedModel = model.updateLayer(layerIndex, config.copy(radius = radius, isEnabled = isEnabled))
                    .let { if (isEnabled) it.setCheckpoint(layerIndex, path) else it }
                
                updatedModel.saveAsDefaults(context)

                val intent = Intent(context, GradientExtractionActivity::class.java).apply {
                    putExtra("IMAGE_PATH", path)
                    putExtra("MODEL_CONFIG", updatedModel)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Forward Pass -> Layer 6 (Gradient)")
        }
    }
}
