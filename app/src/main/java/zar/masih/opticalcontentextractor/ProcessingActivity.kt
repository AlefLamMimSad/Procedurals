package zar.masih.opticalcontentextractor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import zar.masih.opticalcontentextractor.ui.theme.OpticalcontentExtractorTheme
import java.io.File
import kotlin.math.max
import kotlin.math.min

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
    var kernelSize by remember { mutableFloatStateOf(3f) }
    var contrastThreshold by remember { mutableFloatStateOf(80f) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(kernelSize, contrastThreshold) {
        processedBitmap = applyDewatermarkChain(source, contrastThreshold.toInt(), kernelSize.toInt())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Analytical Data Cleaning (Dewatermarking)", style = MaterialTheme.typography.titleLarge)
        
        Text("Contrast Threshold: ${contrastThreshold.toInt()}")
        Slider(value = contrastThreshold, onValueChange = { contrastThreshold = it }, valueRange = 0f..255f)

        Text("Kernel Size (Blur/Clean): ${kernelSize.toInt()}")
        Slider(value = kernelSize, onValueChange = { kernelSize = it }, valueRange = 1f..15f)

        processedBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Processed",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * Procedural Pixel-Wide Data Cleaning:
 * 1. Inverse selection from high contrast (finding watermark/noise)
 * 2. Pixel-level manipulation with adjustable kernel
 */
fun applyDewatermarkChain(source: Bitmap, threshold: Int, kernelSize: Int): Bitmap {
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
            val intensity = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3

            // Inverse selection: identify pixels likely to be "noise" or watermark
            // (Lower contrast/lighter colors that aren't the main document content)
            if (intensity > threshold) {
                // Analytical "Clean": Replace noisy pixel with average of neighbors (blur/inpainting logic)
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                
                for (ky in -halfKernel..halfKernel) {
                    for (kx in -halfKernel..halfKernel) {
                        val nx = x + kx
                        val ny = y + ky
                        if (nx in 0 until width && ny in 0 until height) {
                            val nColor = pixels[ny * width + nx]
                            rSum += Color.red(nColor)
                            gSum += Color.green(nColor)
                            bSum += Color.blue(nColor)
                            count++
                        }
                    }
                }
                outputPixels[idx] = Color.rgb(rSum / count, gSum / count, bSum / count)
            } else {
                // Keep high-contrast/dark content (the text/strokes)
                outputPixels[idx] = color
            }
        }
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(outputPixels, 0, width, 0, 0, width, height)
    return result
}
