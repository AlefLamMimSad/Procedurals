package zar.masih.opticalcontentextractor

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

/**
 * A centralized engine to handle image processing tasks.
 * Designed to be used across multiple activities.
 */
object ProcessingEngine {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val taskChannel = Channel<ProcessingTask>(Channel.CONFLATED)
    private var processingJob: Job? = null

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState

    init {
        startProcessor()
    }

    private fun startProcessor() {
        processingJob?.cancel()
        processingJob = scope.launch {
            taskChannel.consumeAsFlow().collect { task ->
                _processingState.value = ProcessingState.Processing(task.input)
                val result = when (task) {
                    is ProcessingTask.ObjectRemoval -> {
                        removeSmallestObjectsSync(task.input, task.n)
                    }
                    is ProcessingTask.RemoveBiggestObjects -> {
                        removeBiggestObjectsSync(task.input, task.n)
                    }
                    is ProcessingTask.AnalyticalClean -> {
                        applyZonalAnalyticalCleanSync(task.input, task.zones)
                    }
                    is ProcessingTask.GradientExtraction -> {
                        applyGradientExtractionSync(
                            task.input, 
                            task.amp, 
                            task.threshold, 
                            task.highlightColor,
                            task.expansionRadius
                        )
                    }
                    is ProcessingTask.VisibilityMask -> {
                        applyVisibilityMaskSync(task.input, task.mask, task.threshold)
                    }
                    is ProcessingTask.Dilation -> {
                        applyPureDilationSync(task.input, task.radius, task.highlightColor)
                    }
                    is ProcessingTask.Inpaint -> {
                        applyInpaintSync(task.input, task.fillColor)
                    }
                }
                _processingState.value = ProcessingState.Success(result)
            }
        }
    }

    fun requestProcessing(task: ProcessingTask) {
        taskChannel.trySend(task)
    }

    sealed class ProcessingTask(val input: Bitmap) {
        class ObjectRemoval(input: Bitmap, val n: Int) : ProcessingTask(input)
        class RemoveBiggestObjects(input: Bitmap, val n: Int) : ProcessingTask(input)
        class AnalyticalClean(input: Bitmap, val zones: List<AnalyticalZone>) : ProcessingTask(input)
        class GradientExtraction(
            input: Bitmap,
            val amp: Float,
            val threshold: Int,
            val highlightColor: Int,
            val expansionRadius: Int
        ) : ProcessingTask(input)
        class VisibilityMask(val original: Bitmap, val mask: Bitmap, val threshold: Int) : ProcessingTask(original)
        class Dilation(input: Bitmap, val radius: Int, val highlightColor: Int) : ProcessingTask(input)
        class Inpaint(input: Bitmap, val fillColor: Int) : ProcessingTask(input)
    }

    sealed class ProcessingState {
        object Idle : ProcessingState()
        data class Processing(val lastBitmap: Bitmap?) : ProcessingState()
        data class Success(val bitmap: Bitmap) : ProcessingState()
        data class Error(val message: String) : ProcessingState()
    }
}
