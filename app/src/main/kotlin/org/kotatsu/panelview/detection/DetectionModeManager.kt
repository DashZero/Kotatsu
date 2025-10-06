package org.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import org.kotatsu.panelview.utils.DetectionMode

object DetectionModeManager {
    fun detectMode(bitmap: Bitmap): DetectionMode {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) {
            return DetectionMode.AUTO
        }

        val aspectRatio = height.toFloat() / width.toFloat()
        val avgSaturation = computeSaturation(bitmap)

        return when {
            aspectRatio > 2.2f -> DetectionMode.WEBTOON
            aspectRatio < 0.8f -> DetectionMode.STRIP
            avgSaturation < 0.15 -> DetectionMode.MANGA
            else -> DetectionMode.WESTERN
        }
    }

    private fun computeSaturation(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) {
            return 0.0
        }

        val hsv = FloatArray(3)
        var sum = 0.0
        var count = 0
        val maxDimension = max(width, height)
        val step = max(1, min(8, maxDimension / 256))

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                sum += hsv[1].toDouble()
                count++
                x += step
            }
            y += step
        }

        return if (count == 0) 0.0 else sum / count
    }
}
