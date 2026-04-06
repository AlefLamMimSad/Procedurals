package zar.masih.opticalcontentextractor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun UnifiedPreview(
    modifier: Modifier = Modifier,
    initialBitmap: Bitmap,
    state: ProcessingEngine.ProcessingState
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp),
        contentAlignment = Alignment.Center
    ) {
        val bitmapToShow = when (state) {
            is ProcessingEngine.ProcessingState.Success -> state.bitmap
            is ProcessingEngine.ProcessingState.Processing -> state.lastBitmap ?: initialBitmap
            else -> initialBitmap
        }

        Image(
            bitmap = bitmapToShow.asImageBitmap(),
            contentDescription = "Preview",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        if (state is ProcessingEngine.ProcessingState.Processing) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
