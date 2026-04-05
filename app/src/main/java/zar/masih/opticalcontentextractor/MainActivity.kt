package zar.masih.opticalcontentextractor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import zar.masih.opticalcontentextractor.ui.theme.OpticalcontentExtractorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpticalcontentExtractorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DocumentScannerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun DocumentScannerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setResultFormats(RESULT_FORMAT_JPEG)
        .setScannerMode(SCANNER_MODE_FULL)
        .build()

    val scanner = GmsDocumentScanning.getClient(options)

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.firstOrNull()?.imageUri?.let { uri ->
                val bitmap = loadBitmapFromUri(context, uri)
                originalBitmap = bitmap
                bitmap?.let {
                    processedBitmap = applyProceduralChain(it)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            scanner.getStartScanIntent(context as ComponentActivity)
                .addOnSuccessListener { intentSender ->
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
        }) {
            Text("Scan Document")
        }

        Spacer(modifier = Modifier.height(16.dp))

        processedBitmap?.let {
            Text("Processed Result (Birds-Eye + Neural Chain)", style = MaterialTheme.typography.titleMedium)
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Processed Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                contentScale = ContentScale.Fit
            )
        }

        originalBitmap?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Original Scan", style = MaterialTheme.typography.titleSmall)
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Original Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * Procedural chain operations:
 * 1. detect where it is black (contrast.min)
 * 2. mask the black parts
 * 3. apply mask to select important pixels (dominant lighting focus)
 * 4. keep selected parts, make everything else white
 */
fun applyProceduralChain(source: Bitmap, darkThreshold: Int = 80): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    // Layer 1: Detection & Masking
    // We create a boolean mask where 'true' means "important" (not black)
    val importantMask = BooleanArray(pixels.size) { i ->
        val color = pixels[i]
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        
        // Simple luminance/intensity check to detect "black" parts
        val intensity = (r + g + b) / 3
        intensity > darkThreshold // If it's brighter than threshold, it's considered "important"
    }

    // Layer 2: Transformation & Output Generation
    val outputPixels = IntArray(pixels.size) { i ->
        if (importantMask[i]) {
            // Keep original pixel if it was selected by the mask
            pixels[i]
        } else {
            // Make everything else white (procedural default)
            Color.WHITE
        }
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(outputPixels, 0, width, 0, 0, width, height)
    return result
}

fun loadBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        null
    }
}
