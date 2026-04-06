package zar.masih.opticalcontentextractor

import android.content.Context
import android.os.Parcelable
import com.google.gson.Gson
import kotlinx.parcelize.Parcelize

@Parcelize
data class ModelArchitecture(
    val layers: List<LayerConfig> = listOf(
        LayerConfig.InputLayer(),
        LayerConfig.NeuralMaskLayer(),
        LayerConfig.AnalyticalCleanLayer(),      // Index 2
        LayerConfig.ObjectRemovalLayer(layerName = "Intermittent Object Removal"), // Index 3
        LayerConfig.VisibilityMaskLayer(),       // Index 4
        LayerConfig.DilationLayer(),             // Index 5 (Object Expansion)
        LayerConfig.GradientExtractLayer(),      // Index 6
        LayerConfig.ObjectRemovalLayer(layerName = "Final Object Removal")        // Index 7
    ),
    val checkpointPaths: Map<Int, String> = emptyMap()
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

    fun getLastValidPath(currentIndex: Int): String? {
        for (i in (currentIndex - 1) downTo 0) {
            val path = checkpointPaths[i]
            if (path != null) return path
        }
        return null
    }

    fun saveAsDefaults(context: Context) {
        val prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(layers)
        prefs.edit().putString("default_layers", json).apply()
    }

    companion object {
        fun loadDefaults(context: Context): ModelArchitecture {
            val prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("default_layers", null)
            return if (json != null) {
                try {
                    val layers = Gson().fromJson(json, Array<LayerConfig>::class.java).toList()
                    ModelArchitecture(layers = layers)
                } catch (e: Exception) {
                    ModelArchitecture()
                }
            } else {
                ModelArchitecture()
            }
        }
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
    data class VisibilityMaskLayer(
        override val isEnabled: Boolean = true,
        override val layerName: String = "Visibility Masking",
        val maskThreshold: Int = 128
    ) : LayerConfig()

    @Parcelize
    data class DilationLayer(
        override val isEnabled: Boolean = true,
        override val layerName: String = "Object Expansion",
        val radius: Int = 2
    ) : LayerConfig()

    @Parcelize
    data class GradientExtractLayer(
        override val isEnabled: Boolean = true,
        override val layerName: String = "Gradient Extraction",
        val amplification: Float = 2.0f,
        val extractionThreshold: Int = 50,
        val expansionRadius: Int = 0 // Fixed: Added this parameter
    ) : LayerConfig()

    @Parcelize
    data class RetouchLayer(
        override val isEnabled: Boolean = true,
        override val layerName: String = "Manual Retouch",
        val brushSize: Float = 50f,
        val brushStrength: Int = 255
    ) : LayerConfig()
}
