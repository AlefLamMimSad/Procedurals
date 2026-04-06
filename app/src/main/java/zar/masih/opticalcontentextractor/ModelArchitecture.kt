package zar.masih.opticalcontentextractor

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Procedural Model Architecture (Sequential)
 * Treats the image processing pipeline as a series of Neural Network layers.
 */
@Parcelize
data class ModelArchitecture(
    val layers: List<LayerConfig> = listOf(
        LayerConfig.InputLayer(),
        LayerConfig.NeuralMaskLayer(),
        LayerConfig.AnalyticalCleanLayer(),      // Layer 2
        LayerConfig.ObjectRemovalLayer("Intermittent Object Removal"), // Index 3
        LayerConfig.RetouchLayer(),              // Layer 4
        LayerConfig.GradientExtractLayer(),      // Layer 5
        LayerConfig.ObjectRemovalLayer("Final Object Removal")        // Index 6
    ),
    val checkpointPaths: Map<Int, String> = emptyMap() // Stores image paths for each layer result
) : Parcelable {
    fun getLayer(index: Int) = layers[index]
    
    fun updateLayer(index: Int, newConfig: LayerConfig): ModelArchitecture {
        val newLayers = layers.toMutableList()
        newLayers[index] = newConfig
        return copy(layers = newLayers)
    }

    fun setCheckpoint(index: Int, path: String): ModelArchitecture {
        val newCheckpoints = checkpointPaths.toMutableMap()
        newCheckpoints[index] = path
        return copy(checkpointPaths = newCheckpoints)
    }
}

sealed class LayerConfig : Parcelable {
    abstract val isEnabled: Boolean
    abstract val layerName: String

    @Parcelize
    data class InputLayer(
        override val isEnabled: Boolean = true,
        override val layerName: String = "Input (Birds-Eye)"
    ) : LayerConfig()

    @Parcelize
    data class NeuralMaskLayer(
        override val isEnabled: Boolean = true,
        override val layerName: String = "Neural Masking",
        val darkThreshold: Int = 80
    ) : LayerConfig()

    @Parcelize
    data class AnalyticalCleanLayer(
        override val isEnabled: Boolean = true,
        override val layerName: String = "Analytical Cleaning",
        val contrastThreshold: Int = 180,
        val kernelSize: Int = 3
    ) : LayerConfig()

    @Parcelize
    data class ObjectRemovalLayer(
        override val layerName: String = "Object Removal",
        override val isEnabled: Boolean = true,
        val minSizeThreshold: Int = 50,
        val nToRemove: Int = 10
    ) : LayerConfig()

    @Parcelize
    data class RetouchLayer(
        override val isEnabled: Boolean = true,
        override val layerName: String = "Manual Retouch",
        val brushSize: Float = 50f,
        val brushStrength: Int = 255
    ) : LayerConfig()

    @Parcelize
    data class GradientExtractLayer(
        override val isEnabled: Boolean = true,
        override val layerName: String = "Gradient Extraction",
        val amplification: Float = 2.0f,
        val extractionThreshold: Int = 50
    ) : LayerConfig()
}
