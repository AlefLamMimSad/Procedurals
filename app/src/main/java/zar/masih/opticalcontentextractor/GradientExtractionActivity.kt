package zar.masih.opticalcontentextractor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zar.masih.opticalcontentextractor.ui.theme.OpticalcontentExtractorTheme
import kotlin.math.sqrt

class GradientExtractionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imagePath = intent.getStringExtra("IMAGE_PATH")
        val bitmap = imagePath?.let { BitmapFactory.decodeFile(it) }

        setContent {
            OpticalcontentExtractorTheme {
                Scaffold { padding ->
                    if (bitmap != null) {
                        GradientExtractionScreen(bitmap, Modifier.padding(padding))
                    } else {
                        Text("No image found", modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
fun GradientExtractionScreen(source: Bitmap, modifier: Modifier = Modifier) {
    var amplification by remember { mutableFloatStateOf(2f) }
    var extractionThreshold by remember { mutableFloatStateOf(50f) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(amplification, extractionThreshold) {
        isProcessing = true
        resultBitmap = withContext(Dispatchers.Default) {
            applyGradientExtraction(source, amplification, extractionThreshold.toInt())
        }
        isProcessing = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Gradient Amplification & Object Extraction", style = MaterialTheme.typography.titleLarge)
        
        Spacer(modifier = Modifier.height(16.dp))

        Text("Gradient Amplification (Power): ${String.format("%.1f", amplification)}")
        Slider(value = amplification, onValueChange = { amplification = it }, valueRange = 1f..10f)

        Text("Extraction Sensitivity (Threshold): ${extractionThreshold.toInt()}")
        Slider(value = extractionThreshold, onValueChange = { extractionThreshold = it }, valueRange = 0f..255f)

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            resultBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Extracted Objects",
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

/**
 * Procedural Gradient Amplification and Segmentation:
 * 1. Calculate pixel-wise gradients (Sobel-like magnitude).
 * 2. Amplify the gradient intensity.
 * 3. Treat amplified gradients as 'objects' and discard background (transparent/white).
 */
suspend fun applyGradientExtraction(source: Bitmap, amp: Float, threshold: Int): Bitmap = withContext(Dispatchers.Default) {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    
    val outputPixels = IntArray(pixels.size)

    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val idx = y * width + x
            
            // Simple Gradient Calculation (X and Y difference)
            val pX1 = pixels[y * width + (x + 1)]
            val pX0 = pixels[y * width + (x - 1)]
            val pY1 = pixels[(y + 1) * width + x]
            val pY0 = pixels[(y - 1) * width + x]

            fun getInten(c: Int) = (Color.red(c) + Color.green(c) + Color.blue(c)) / 3
            
            val dx = getInten(pX1) - getInten(pX0)
            val dy = getInten(pY1) - getInten(pY0)
            
            // Magnitude of gradient
            val magnitude = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            
            // Amplify
            val amplifiedMag = magnitude * amp
            
            if (amplifiedMag > threshold) {
                // This is an 'object' edge/detail - keep original pixel but potentially enhance it
                outputPixels[idx] = pixels[idx]
            } else {
                // Background - Discard (set to white or transparent)
                outputPixels[idx] = Color.WHITE
            }
        }
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(outputPixels, 0, width, 0, 0, width, height)
    result
}
