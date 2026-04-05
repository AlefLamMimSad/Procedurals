package zar.masih.opticalcontentextractor

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * ModelArchitecture acts as the "TensorFlow Model Definition".
 * It holds the parameters for each "Layer" in the processing pipeline.
 * Each Activity serves as a Checkpoint for these layers.
 */
@Parcelize
data class ModelArchitecture(
    val layer1Config: Layer1Config = Layer1Config(),
    val layer2Config: Layer2Config = Layer2Config(),
    val layer3Config: Layer3Config = Layer3Config(),
    val layer4Config: Layer4Config = Layer4Config()
) : Parcelable

@Parcelize
data class Layer1Config(
    val darkThreshold: Int = 80
) : Parcelable

@Parcelize
data class Layer2Config(
    val contrastThreshold: Int = 180,
    val kernelSize: Int = 3
) : Parcelable

@Parcelize
data class Layer3Config(
    val brushSize: Float = 50f,
    val brushStrength: Int = 255
) : Parcelable

@Parcelize
data class Layer4Config(
    val amplification: Float = 2.0f,
    val extractionThreshold: Int = 50
) : Parcelable
