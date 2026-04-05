package zar.masih.opticalcontentextractor

import android.app.Activity
import android.content.Intent
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
import java.io.File
import java.io.FileOutputStream

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
    var model by remember { mutableStateOf(ModelArchitecture()) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imagePath by remember { mutableStateOf<String?>(null) }

    val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setResultFormats(RESULT_FORMAT_JPEG)
        .setScannerMode(SCANNER_MODE_FULL)
        .setPageLimit(1)
        .build()

    val scanner = GmsDocumentScanning.getClient(options)

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.firstOrNull()?.imageUri?.let { uri ->
                val bitmap = loadBitmapFromUri(context, uri)
                originalBitmap = bitmap
                bitmap?.let {
                    val proc = applyProceduralChain(it, model.layer1Config.darkThreshold)
                    processedBitmap = proc
                    imagePath = saveBitmapToFile(context, proc)
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
            Text("Scan & Birds-Eye Selection")
        }

        Spacer(modifier = Modifier.height(16.dp))

        imagePath?.let { path ->
            Button(onClick = {
                val intent = Intent(context, ProcessingActivity::class.java).apply {
                    putExtra("IMAGE_PATH", path)
                    putExtra("MODEL_CONFIG", model)
                }
                context.startActivity(intent)
            }) {
                Text("Go to Analytical Cleaning")
            }
        }

        processedBitmap?.let {
            Text("Layer 1 (Neural Masking) Output", style = MaterialTheme.typography.titleMedium)
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Processed Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

fun applyProceduralChain(source: Bitmap, darkThreshold: Int): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    val importantMask = BooleanArray(pixels.size) { i ->
        val color = pixels[i]
        val intensity = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3
        intensity > darkThreshold
    }

    val outputPixels = IntArray(pixels.size) { i ->
        if (importantMask[i]) pixels[i] else Color.WHITE
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(outputPixels, 0, width, 0, 0, width, height)
    return result
}

fun saveBitmapToFile(context: android.content.Context, bitmap: Bitmap): String {
    val file = File(context.cacheDir, "scanned_image.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return file.absolutePath
}

fun loadBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (_: Exception) {
        null
    }
}
