package zar.masih.opticalcontentextractor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import zar.masih.opticalcontentextractor.ui.theme.OpticalcontentExtractorTheme
import java.io.File
import java.io.FileOutputStream

class RetouchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imagePath = intent.getStringExtra("IMAGE_PATH")
        val bitmap = imagePath?.let { BitmapFactory.decodeFile(it) }

        setContent {
            OpticalcontentExtractorTheme {
                Scaffold { padding ->
                    if (bitmap != null) {
                        RetouchScreen(bitmap, Modifier.padding(padding))
                    } else {
                        Text("No image found", modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
fun RetouchScreen(source: Bitmap, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mutableBitmap = remember { source.copy(Bitmap.Config.ARGB_8888, true) }
    var brushSize by remember { mutableFloatStateOf(50f) }
    var brushStrength by remember { mutableFloatStateOf(255f) } // 0 to 255 (Alpha/Hardness)
    var isEraser by remember { mutableStateOf(false) }
    var triggerToggle by remember { mutableStateOf(0) }

    val canvas = remember { Canvas(mutableBitmap) }
    val paint = remember(brushSize, brushStrength, isEraser) {
        Paint().apply {
            color = if (isEraser) Color.WHITE else Color.BLACK
            alpha = brushStrength.toInt()
            strokeWidth = brushSize
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Manual Retouching", style = MaterialTheme.typography.titleLarge)
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Brush Size: ${brushSize.toInt()}", modifier = Modifier.width(100.dp))
            Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 10f..200f)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Strength: ${brushStrength.toInt()}", modifier = Modifier.width(100.dp))
            Slider(value = brushStrength, onValueChange = { brushStrength = it }, valueRange = 0f..255f)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mode: ${if (isEraser) "Eraser (White)" else "Add (Black)"}")
            Switch(checked = isEraser, onCheckedChange = { isEraser = it })
        }

        Button(
            onClick = {
                val path = saveRetouchedBitmap(context, mutableBitmap)
                val intent = Intent(context, GradientExtractionActivity::class.java).apply {
                    putExtra("IMAGE_PATH", path)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Text("Amplify Gradients & Extract Objects")
        }

        Box(
            modifier = Modifier
                .height(400.dp)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color.LightGray)
                .pointerInput(brushSize, brushStrength, isEraser) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        canvas.drawPoint(change.position.x, change.position.y, paint)
                        triggerToggle++
                    }
                }
        ) {
            key(triggerToggle) {
                Image(
                    bitmap = mutableBitmap.asImageBitmap(),
                    contentDescription = "Canvas",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

fun saveRetouchedBitmap(context: Context, bitmap: Bitmap): String {
    val file = File(context.cacheDir, "retouched_image.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return file.absolutePath
}
