package zar.masih.opticalcontentextractor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.math.sqrt

fun isNotBackground(color: Int): Boolean {
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)
    return r < 245 || g < 245 || b < 245
}

fun removeSmallestObjectsSync(source: Bitmap, n: Int): Bitmap {
    if (n <= 0) return source
    
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    
    val visited = BooleanArray(pixels.size)
    val components = mutableListOf<List<Int>>()

    for (y in 0 until height) {
        for (x in 0 until width) {
            val idx = y * width + x
            if (!visited[idx] && isNotBackground(pixels[idx])) {
                val component = mutableListOf<Int>()
                val queue: Queue<Int> = LinkedList()
                queue.add(idx)
                visited[idx] = true
                
                while (queue.isNotEmpty()) {
                    val curr = queue.poll()!!
                    component.add(curr)
                    
                    val cx = curr % width
                    val cy = curr / width
                    
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = cx + dx
                            val ny = cy + dy
                            if (nx in 0 until width && ny in 0 until height) {
                                val nIdx = ny * width + nx
                                if (!visited[nIdx] && isNotBackground(pixels[nIdx])) {
                                    visited[nIdx] = true
                                    queue.add(nIdx)
                                }
                            }
                        }
                    }
                }
                components.add(component)
            }
        }
    }

    val smallestComponents = components.sortedBy { it.size }.take(minOf(n, components.size))
    val outputPixels = pixels.copyOf()
    for (comp in smallestComponents) {
        for (idx in comp) {
            outputPixels[idx] = Color.WHITE
        }
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(outputPixels, 0, width, 0, 0, width, height)
    return result
}

fun applyAnalyticalDewatermarkSync(source: Bitmap, threshold: Int, kernelSize: Int): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    
    val outputPixels = IntArray(pixels.size)
    val halfKernel = kernelSize / 2

    for (y in 0 until height) {
        for (x in 0 until width) {
            val idx = y * width + x
            val color = pixels[idx]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val intensity = (r + g + b) / 3

            if (intensity > threshold) {
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
                    outputPixels[idx] = Color.WHITE
                }
            } else {
                outputPixels[idx] = color
            }
        }
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(outputPixels, 0, width, 0, 0, width, height)
    return result
}

fun applyGradientExtractionSync(source: Bitmap, amp: Float, threshold: Int): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    
    val outputPixels = IntArray(pixels.size)

    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val idx = y * width + x
            
            val pX1 = pixels[y * width + (x + 1)]
            val pX0 = pixels[y * width + (x - 1)]
            val pY1 = pixels[(y + 1) * width + x]
            val pY0 = pixels[(y - 1) * width + x]

            fun getInten(c: Int) = (Color.red(c) + Color.green(c) + Color.blue(c)) / 3
            
            val dx = getInten(pX1) - getInten(pX0)
            val dy = getInten(pY1) - getInten(pY0)
            
            val magnitude = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            val amplifiedMag = magnitude * amp
            
            if (amplifiedMag > threshold) {
                outputPixels[idx] = pixels[idx]
            } else {
                outputPixels[idx] = Color.WHITE
            }
        }
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(outputPixels, 0, width, 0, 0, width, height)
    return result
}

fun applyVisibilityMaskSync(original: Bitmap, mask: Bitmap, threshold: Int): Bitmap {
    val width = original.width
    val height = original.height
    
    // Scale mask to original if they differ (should be same ideally)
    val scaledMask = if (mask.width != width || mask.height != height) {
        Bitmap.createScaledBitmap(mask, width, height, true)
    } else mask
    
    val origPixels = IntArray(width * height)
    original.getPixels(origPixels, 0, width, 0, 0, width, height)
    
    val maskPixels = IntArray(width * height)
    scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)
    
    val outputPixels = IntArray(width * height)
    
    for (i in origPixels.indices) {
        val maskColor = maskPixels[i]
        val maskIntensity = (Color.red(maskColor) + Color.green(maskColor) + Color.blue(maskColor)) / 3
        
        // "This image is black anywhere that the main content is located"
        // "only the pixels which are valued the darkest; effectively a visibility mask"
        if (maskIntensity < threshold) {
            outputPixels[i] = origPixels[i]
        } else {
            outputPixels[i] = Color.WHITE
        }
    }
    
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(outputPixels, 0, width, 0, 0, width, height)
    return result
}

fun saveBitmap(context: Context, bitmap: Bitmap, fileName: String): String {
    val file = File(context.cacheDir, fileName)
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return file.absolutePath
}
