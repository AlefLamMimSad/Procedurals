package zar.masih.opticalcontentextractor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
    
    // Text Detection State
    var detectedText by remember { mutableStateOf<Text?>(null) }
    var isDetectingText by remember { mutableStateOf(false) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    LaunchedEffect(fillColor, isEnabled) {
        if (!isEnabled) return@LaunchedEffect
        delay(150)
        ProcessingEngine.requestProcessing(
            ProcessingEngine.ProcessingTask.Inpaint(source, fillColor)
        )
    }

    fun detectText() {
        isDetectingText = true
        val image = InputImage.fromBitmap(source, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                detectedText = visionText
                isDetectingText = false
            }
            .addOnFailureListener {
                isDetectingText = false
            }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fill Color Selection", modifier = Modifier.weight(1f))
                Button(onClick = { detectText() }, enabled = !isDetectingText) {
                    if (isDetectingText) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Detect Text")
                }
            }
            Row(modifier = Modifier.padding(8.dp)) {
                listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF444444.toInt(), 0xFFFF0000.toInt(), 0xFF0000FF.toInt()).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(4.dp)
                            .background(Color(color), CircleShape)
                            .border(if (fillColor == color) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { fillColor = color }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preview with Text Overlay
        Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
            UnifiedPreview(
                initialBitmap = source,
                state = if (isEnabled) processingState else ProcessingEngine.ProcessingState.Idle
            )

            // ML Kit Text Overlay
            detectedText?.let { visionText ->
                Canvas(modifier = Modifier.matchParentSize()) {
                    // Note: This overlay logic assumes the Preview matches image aspect ratio exactly.
                    // For a robust implementation, we'd need to scale the Rects based on image vs container size.
                    val scaleX = size.width / source.width
                    val scaleY = size.height / source.height

                    visionText.textBlocks.forEach { block ->
                        block.boundingBox?.let { rect ->
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                                size = Size(rect.width() * scaleX, rect.height() * scaleY),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            
                            // Font replacement simulation
                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = fillColor
                                    textSize = (rect.height() * scaleY) * 0.8f
                                    isAntiAlias = true
                                    // Here we could set a custom Typeface if available
                                }
                                drawText(
                                    block.text,
                                    rect.left * scaleX,
                                    rect.bottom * scaleY,
                                    paint
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val currentBitmap = if (isEnabled) {
                    when (val s = processingState) {
                        is ProcessingEngine.ProcessingState.Success -> s.bitmap
                        else -> source
                    }
                } else source
                
                // If text was detected and we want to "burn it in", 
                // we would ideally create a new bitmap with the text drawn on it.
                // For now, we'll just save the processed image.
                
                val fileName = "checkpoint_layer8.png"
                val path = if (isEnabled) saveBitmap(context, currentBitmap, fileName) else model.getLastValidPath(layerIndex) ?: ""
                
                val updatedModel = model.updateLayer(layerIndex, config.copy(fillColor = fillColor, isEnabled = isEnabled))
                    .let { if (isEnabled) it.setCheckpoint(layerIndex, path) else it }
                
                updatedModel.saveAsDefaults(context)

                val intent = Intent(context, ObjectRemovalActivity::class.java).apply {
                    putExtra("IMAGE_PATH", path)
                    putExtra("MODEL_CONFIG", updatedModel)
                    putExtra("REMOVAL_STEP", "BIGGEST")
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirm & Next (Remove Biggest)")
        }
    }
}
