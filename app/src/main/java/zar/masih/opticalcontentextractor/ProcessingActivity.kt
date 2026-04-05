package zar.masih.opticalcontentextractor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import zar.masih.opticalcontentextractor.ui.theme.OpticalcontentExtractorTheme
import java.io.File
import java.io.FileOutputStream

data class ProcessingPreset(val id: String, val threshold: Int, val kernelSize: Int)

class ProcessingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imagePath = intent.getStringExtra("IMAGE_PATH")
        val bitmap = imagePath?.let { BitmapFactory.decodeFile(it) }

        setContent {
            OpticalcontentExtractorTheme {
                Scaffold { padding ->
                    if (bitmap != null) {
                        ProcessingScreen(bitmap, Modifier.padding(padding))
                    } else {
                        Text("No image found", modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessingScreen(source: Bitmap, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("presets", Context.MODE_PRIVATE) }
    
    var kernelSize by remember { mutableFloatStateOf(3f) }
    var contrastThreshold by remember { mutableFloatStateOf(180f) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    
    // Favorites State
    val favorites = remember { mutableStateListOf<ProcessingPreset>() }
    
    // Load Favorites
    LaunchedEffect(Unit) {
        val saved = prefs.getStringSet("favorite_list", emptySet()) ?: emptySet()
        saved.forEach { str ->
            val parts = str.split("|")
            if (parts.size == 3) {
                favorites.add(ProcessingPreset(parts[0], parts[1].toInt(), parts[2].toInt()))
            }
        }
    }

    // Processing Logic with Debounce
    LaunchedEffect(kernelSize, contrastThreshold) {
        isProcessing = true
        delay(300) 
        val result = withContext(Dispatchers.Default) {
            applyAnalyticalDewatermark(source, contrastThreshold.toInt(), kernelSize.toInt())
        }
        processedBitmap = result
        isProcessing = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Analytical Data Cleaning", style = MaterialTheme.typography.titleLarge)
        
        Spacer(modifier = Modifier.height(16.dp))

        // Favorite Selections Bar
        Text("Favorite Selections", style = MaterialTheme.typography.labelLarge)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                OutlinedIconButton(
                    onClick = {
                        val newPreset = ProcessingPreset(
                            id = System.currentTimeMillis().toString(),
                            threshold = contrastThreshold.toInt(),
                            kernelSize = kernelSize.toInt()
                        )
                        favorites.add(newPreset)
                        val set = favorites.map { "${it.id}|${it.threshold}|${it.kernelSize}" }.toSet()
                        prefs.edit().putStringSet("favorite_list", set).apply()
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Save Current")
                }
            }
            
            items(favorites) { preset ->
                PresetItem(preset) {
                    contrastThreshold = it.threshold.toFloat()
                    kernelSize = it.kernelSize.toFloat()
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Contrast Threshold: ${contrastThreshold.toInt()}")
        Slider(value = contrastThreshold, onValueChange = { contrastThreshold = it }, valueRange = 0f..255f)

        Text("Kernel Size: ${kernelSize.toInt()}")
        Slider(value = kernelSize, onValueChange = { kernelSize = it }, valueRange = 1f..15f)

        Spacer(modifier = Modifier.height(16.dp))

        processedBitmap?.let { bitmap ->
            Button(
                onClick = {
                    val path = saveBitmapToCache(context, bitmap)
                    val intent = Intent(context, RetouchActivity::class.java).apply {
                        putExtra("IMAGE_PATH", path)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Proceed to Manual Retouch")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            processedBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Processed",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
            
            if (isProcessing) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun PresetItem(preset: ProcessingPreset, onClick: (ProcessingPreset) -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable { onClick(preset) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("T:${preset.threshold}", style = MaterialTheme.typography.bodySmall)
            Text("K:${preset.kernelSize}", style = MaterialTheme.typography.bodySmall)
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
                    outputPixels[idx] = Color.WHITE
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
