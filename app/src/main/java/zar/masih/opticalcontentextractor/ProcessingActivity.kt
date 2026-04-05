package zar.masih.opticalcontentextractor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import zar.masih.opticalcontentextractor.ui.theme.OpticalcontentExtractorTheme

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
    var contrastThreshold by remember { mutableFloatStateOf(180f) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Use LaunchedEffect with debounce to prevent over-processing and lag
    LaunchedEffect(kernelSize, contrastThreshold) {
        isProcessing = true
        // Debounce: Wait for slider to stop moving for a short duration
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
        Text("Analytical Data Cleaning (Dewatermarking)", style = MaterialTheme.typography.titleLarge)
        
        Spacer(modifier = Modifier.height(16.dp))

        Text("Contrast Threshold (Watermark Target): ${contrastThreshold.toInt()}")
        Slider(
            value = contrastThreshold, 
            onValueChange = { contrastThreshold = it }, 
            valueRange = 0f..255f
        )

        Text("Kernel Size (Reconstruction Area): ${kernelSize.toInt()}")
        Slider(
            value = kernelSize, 
            onValueChange = { kernelSize = it }, 
            valueRange = 1f..15f
        )

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
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/**
 * Enhanced Analytical Dewatermarking Algorithm
 * Based on research: Detection -> Segmentation -> Inpainting (Background Reconstruction)
 * 1. Pixel Intensity Analysis: Segment pixels likely to be watermark (mid-tone light colors).
 * 2. Background Estimation: For segmented pixels, use a sliding window (kernel) to 
 *    procedurally reconstruct the missing document data using weighted neighboring pixels.
 */
suspend fun applyAnalyticalDewatermark(source: Bitmap, threshold: Int, kernelSize: Int): Bitmap = withContext(Dispatchers.Default) {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    
    val outputPixels = IntArray(pixels.size)
    val halfKernel = kernelSize / 2

    // Performance optimization: Using a direct loop with bit-shifting
    for (y in 0 until height) {
        for (x in 0 until width) {
            val idx = y * width + x
            val color = pixels[idx]
            
            // Extract components manually for speed
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            
            val intensity = (r + g + b) / 3

            // Research-based Segmentation: 
            // Most watermarks reside in the light-gray/mid-tone spectrum.
            // We select pixels brighter than the threshold (which excludes text/ink).
            if (intensity > threshold) {
                // Procedural Reconstruction Layer:
                // We emulate an analytical 'Inpainting' kernel.
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
                            
                            // Only use neighbors that are NOT part of the detected watermark
                            // to avoid blurring the noise itself into the output.
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
                    // Fallback: If no document neighbors found, push to white
                    outputPixels[idx] = Color.WHITE
                }
            } else {
                // Keep the document content (ink/text)
                outputPixels[idx] = color
            }
        }
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(outputPixels, 0, width, 0, 0, width, height)
    result
}
