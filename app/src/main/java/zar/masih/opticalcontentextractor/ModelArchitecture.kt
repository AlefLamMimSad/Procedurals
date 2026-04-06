package zar.masih.opticalcontentextractor

import android.content.Context
import android.os.Parcelable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.parcelize.Parcelize

@Parcelize
data class AnalyticalZone(
    val id: String = java.util.UUID.randomUUID().toString(),
    val x: Float = 0f, // 0..1 relative
    val y: Float = 0f, // 0..1 relative
    val width: Float = 1f, // 0..1 relative
    val height: Float = 1f, // 0..1 relative
    val contrastThreshold: Int = 180,
    val kernelSize: Int = 3,
    val isFullImage: Boolean = false
) : Parcelable

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
        LayerConfig.ObjectRemovalLayer(layerName = "Final Object Removal"),        // Index 7
        LayerConfig.InpaintLayer(),              // Index 8
        LayerConfig.ObjectRemovalLayer(layerName = "Biggest Object Removal")       // Index 9 (New)
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
        val prefs = context.getSharedPreferences("hyper_params_center", Context.MODE_PRIVATE).edit()
        val gson = Gson()
        layers.forEachIndexed { index, config ->
            val keyPrefix = "l${index}_"
            prefs.putBoolean("${keyPrefix}enabled", config.isEnabled)
            
            when (config) {
                is LayerConfig.NeuralMaskLayer -> {
                    prefs.putInt("${keyPrefix}dark", config.darkThreshold)
                }
                is LayerConfig.AnalyticalCleanLayer -> {
                    prefs.putString("${keyPrefix}zones", gson.toJson(config.zones))
                }
                is LayerConfig.ObjectRemovalLayer -> {
                    prefs.putInt("${keyPrefix}n", config.nToRemove)
                }
                is LayerConfig.VisibilityMaskLayer -> {
                    prefs.putInt("${keyPrefix}thresh", config.maskThreshold)
                }
                is LayerConfig.DilationLayer -> {
                    prefs.putInt("${keyPrefix}rad", config.radius)
                }
                is LayerConfig.GradientExtractLayer -> {
                    prefs.putFloat("${keyPrefix}amp", config.amplification)
                    prefs.putInt("${keyPrefix}thresh", config.extractionThreshold)
                    prefs.putInt("${keyPrefix}rad", config.expansionRadius)
                }
                is LayerConfig.InpaintLayer -> {
                    prefs.putInt("${keyPrefix}color", config.fillColor)
                }
                else -> {}
            }
        }
        prefs.apply()
    }

    companion object {
        fun loadDefaults(context: Context): ModelArchitecture {
            val prefs = context.getSharedPreferences("hyper_params_center", Context.MODE_PRIVATE)
            val base = ModelArchitecture()
            val gson = Gson()
            val loadedLayers = base.layers.mapIndexed { index, defaultConfig ->
                val keyPrefix = "l${index}_"
                if (!prefs.contains("${keyPrefix}enabled")) return@mapIndexed defaultConfig
                
                val isEnabled = prefs.getBoolean("${keyPrefix}enabled", defaultConfig.isEnabled)
                
                when (defaultConfig) {
                    is LayerConfig.NeuralMaskLayer -> defaultConfig.copy(
                        isEnabled = isEnabled,
                        darkThreshold = prefs.getInt("${keyPrefix}dark", defaultConfig.darkThreshold)
                    )
                    is LayerConfig.AnalyticalCleanLayer -> {
                        val zonesJson = prefs.getString("${keyPrefix}zones", null)
                        val zones = if (zonesJson != null) {
                            val type = object : TypeToken<List<AnalyticalZone>>() {}.type
                            gson.fromJson<List<AnalyticalZone>>(zonesJson, type)
                        } else {
                            listOf(AnalyticalZone(isFullImage = true))
                        }
                        defaultConfig.copy(isEnabled = isEnabled, zones = zones)
                    }
                    is LayerConfig.ObjectRemovalLayer -> defaultConfig.copy(
                        isEnabled = isEnabled,
                        nToRemove = prefs.getInt("${keyPrefix}n", defaultConfig.nToRemove)
                    )
                    is LayerConfig.VisibilityMaskLayer -> defaultConfig.copy(
                        isEnabled = isEnabled,
                        maskThreshold = prefs.getInt("${keyPrefix}thresh", defaultConfig.maskThreshold)
                    )
                    is LayerConfig.DilationLayer -> defaultConfig.copy(
                        isEnabled = isEnabled,
                        radius = prefs.getInt("${keyPrefix}rad", defaultConfig.radius)
                    )
                    is LayerConfig.GradientExtractLayer -> defaultConfig.copy(
                        isEnabled = isEnabled,
                        amplification = prefs.getFloat("${keyPrefix}amp", defaultConfig.amplification),
                        extractionThreshold = prefs.getInt("${keyPrefix}thresh", defaultConfig.extractionThreshold),
                        expansionRadius = prefs.getInt("${keyPrefix}rad", defaultConfig.expansionRadius)
                    )
                    is LayerConfig.InpaintLayer -> defaultConfig.copy(
                        isEnabled = isEnabled,
                        fillColor = prefs.getInt("${keyPrefix}color", defaultConfig.fillColor)
                    )
                    else -> defaultConfig
                }
            }
            return ModelArchitecture(layers = loadedLayers)
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
        val zones: List<AnalyticalZone> = listOf(AnalyticalZone(isFullImage = true))
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
        val expansionRadius: Int = 0
    ) : LayerConfig()

    @Parcelize
    data class InpaintLayer(
        override val isEnabled: Boolean = true,
        override val layerName: String = "Shape Infilling",
        val fillColor: Int = -16777216 // Default Black
    ) : LayerConfig()

    @Parcelize
    data class RetouchLayer(
        override val isEnabled: Boolean = true,
        override val layerName: String = "Manual Retouch",
        val brushSize: Float = 50f,
        val brushStrength: Int = 255
    ) : LayerConfig()
}
