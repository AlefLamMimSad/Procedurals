package zar.masih.opticalcontentextractor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import zar.masih.opticalcontentextractor.ui.theme.OpticalcontentExtractorTheme
import java.io.File
import java.io.FileOutputStream

class ProcessingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imagePath = intent.getStringExtra("IMAGE_PATH")
        val bitmap = imagePath?.let { BitmapFactory.decodeFile(it) }
        val modelConfig = intent.getParcelableExtra<ModelArchitecture>("MODEL_CONFIG") ?: ModelArchitecture()

        setContent {
            OpticalcontentExtractorTheme {
                Scaffold { padding ->
                    if (bitmap != null) {
                        ProcessingScreen(bitmap, modelConfig, Modifier.padding(padding))
                    } else {
                        Text("No image found", modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessingScreen(source: Bitmap, initialModel: ModelArchitecture, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var model by remember { mutableStateOf(initialModel) }
    
    // Layer 2 specific state
    val layerIndex = 2
    val config = model.getLayer(layerIndex) as LayerConfig.AnalyticalCleanLayer
    
    var kernelSize by remember { mutableFloatStateOf(config.kernelSize.toFloat()) }
    var contrastThreshold by remember { mutableFloatStateOf(config.contrastThreshold.toFloat()) }
    var isLayerEnabled by remember { mutableStateOf(config.isEnabled) }
    
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Sequential NN Model Summary Header
    ModelSummaryHeader(model, currentLayerIndex = layerIndex)

    LaunchedEffect(kernelSize, contrastThreshold, isLayerEnabled) {
        if (!isLayerEnabled) {
            processedBitmap = source
            return@LaunchedEffect
        }
        
        isProcessing = true
        delay(300) 
        val result = withContext(Dispatchers.Default) {
            applyAnalyticalDewatermark(source, contrastThreshold.toInt(), kernelSize.toInt())
        }
        processedBitmap = result
        isProcessing = false
        
        // Update Layer Checkpoint
        model = model.updateLayer(layerIndex, config.copy(
            isEnabled = isLayerEnabled,
            contrastThreshold = contrastThreshold.toInt(),
            kernelSize = kernelSize.toInt()
        ))
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
        } else {
            Text("Layer Bypassed (Identity Transformation)", color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        processedBitmap?.let { bitmap ->
            Button(
                onClick = {
                    val path = saveBitmapToCache(context, bitmap)
                    val intent = Intent(context, RetouchActivity::class.java).apply {
                        putExtra("IMAGE_PATH", path)
                        putExtra("MODEL_CONFIG", model)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Forward Pass -> Layer 3")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            processedBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Layer Output",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
            if (isProcessing) CircularProgressIndicator()
        }
    }
}

@Composable
fun ModelSummaryHeader(model: ModelArchitecture, currentLayerIndex: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Sequential Model Summary", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(model.layers) { index, layer ->
                    val isActive = index == currentLayerIndex
                    val isTuned = index < currentLayerIndex
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primary 
                                    else if (isTuned) MaterialTheme.colorScheme.secondary 
                                    else MaterialTheme.colorScheme.outline
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isTuned) Icon(Icons.Default.CheckCircle, "", tint = ComposeColor.White, modifier = Modifier.size(16.dp))
                            else Text("${index}", color = ComposeColor.White, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            layer.layerName.split(" ")[0], 
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    if (index < model.layers.size - 1) {
                        Text("→", modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

fun saveBitmapToCache(context: Context, bitmap: Bitmap): String {
    val file = File(context.cacheDir, "processed_image.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return file.absolutePath
}

suspend fun applyAnalyticalDewatermark(source: Bitmap, threshold: Int, kernelSize: Int): Bitmap = withContext(Dispatchers.Default) {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    
    val outputPixels = IntArray(pixels.size)
    val halfKernel = kernelSize / 2

    for (y in 0 until height) {
        for (x in 0 until width) {
            val idx = y * width + x
            val color = pixels[idx]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val intensity = (r + g + b) / 3

            if (intensity > threshold) {
                var rSum = 0L
                var gSum = 0L
                var bSum = 0L
                var count = 0
                
                for (ky in -halfKernel..halfKernel) {
                    val ny = y + ky
                    if (ny !in 0 until height) continue
                    for (kx in -halfKernel..halfKernel) {
                        val nx = x + kx
                        if (nx in 0 until width) {
                            val nColor = pixels[ny * width + nx]
                            val nr = (nColor shr 16) and 0xFF
                            val ng = (nColor shr 8) and 0xFF
                            val nb = nColor and 0xFF
                            if ((nr + ng + nb) / 3 <= threshold) {
                                rSum += nr
                                gSum += ng
                                bSum += nb
                                count++
                            }
                        }
                    }
                }
                
                if (count > 0) {
                    outputPixels[idx] = (0xFF shl 24) or 
                                       ((rSum / count).toInt() shl 16) or 
                                       ((gSum / count).toInt() shl 8) or 
                                       (bSum / count).toInt()
                } else {
                    outputPixels[idx] = AndroidColor.WHITE
                }
            } else {
                outputPixels[idx] = color
            }
        }
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(outputPixels, 0, width, 0, 0, width, height)
    result
}
