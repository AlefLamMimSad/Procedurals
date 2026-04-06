package zar.masih.opticalcontentextractor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import zar.masih.opticalcontentextractor.ui.theme.OpticalcontentExtractorTheme

class InpaintActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imagePath = intent.getStringExtra("IMAGE_PATH")
        val modelConfig = intent.getParcelableExtra<ModelArchitecture>("MODEL_CONFIG") ?: ModelArchitecture()
        
        val layerIndex = 8
        val actualInputPath = imagePath ?: modelConfig.getLastValidPath(layerIndex)
        val bitmap = actualInputPath?.let { BitmapFactory.decodeFile(it) }

        setContent {
            OpticalcontentExtractorTheme {
                Scaffold { padding ->
                    if (bitmap != null) {
                        InpaintScreen(bitmap, modelConfig, Modifier.padding(padding))
                    } else {
                        Text("No image found", modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
fun InpaintScreen(source: Bitmap, initialModel: ModelArchitecture, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val layerIndex = 8
    var model by remember { mutableStateOf(initialModel) }
    val config = model.getLayer(layerIndex) as LayerConfig.InpaintLayer
    
    var fillColor by remember { mutableIntStateOf(config.fillColor) }
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    
    val processingState by ProcessingEngine.processingState.collectAsState()

    LaunchedEffect(fillColor, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        delay(150)
        ProcessingEngine.requestProcessing(
            ProcessingEngine.ProcessingTask.Inpaint(source, fillColor)
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
            Text("Fill Color Selection")
            Row(modifier = Modifier.padding(8.dp)) {
                listOf(0xFF000000.toInt(), 0xFF444444.toInt(), 0xFF888888.toInt(), 0xFFFF0000.toInt(), 0xFF0000FF.toInt()).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(4.dp)
                            .background(Color(color), CircleShape)
                            .clickable { fillColor = color }
                            .let { if (fillColor == color) it.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape) else it }
                    )
                }
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
                
                val fileName = "checkpoint_layer8.png"
                val path = if (isEnabled) saveBitmap(context, currentBitmap, fileName) else model.getLastValidPath(layerIndex) ?: ""
                
                val updatedModel = model.updateLayer(layerIndex, config.copy(fillColor = fillColor, isEnabled = isEnabled))
                    .let { if (isEnabled) it.setCheckpoint(layerIndex, path) else it }
                
                updatedModel.saveAsDefaults(context)

                val intent = Intent(context, ProcessingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("IS_SUMMARY_MODE", true)
                    putExtra("MODEL_CONFIG", updatedModel)
                    putExtra("IMAGE_PATH", path)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirm & Export")
        }
    }
}
